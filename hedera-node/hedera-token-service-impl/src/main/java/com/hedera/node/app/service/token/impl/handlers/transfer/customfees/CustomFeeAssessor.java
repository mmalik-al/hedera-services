/*
 * Copyright (C) 2021-2023 Hedera Hashgraph, LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.hedera.node.app.service.token.impl.handlers.transfer.customfees;

import static com.hedera.hapi.node.base.ResponseCodeEnum.CUSTOM_FEE_CHARGING_EXCEEDED_MAX_ACCOUNT_AMOUNTS;
import static com.hedera.hapi.node.base.ResponseCodeEnum.INSUFFICIENT_SENDER_ACCOUNT_BALANCE_FOR_CUSTOM_FEE;
import static com.hedera.hapi.node.base.ResponseCodeEnum.TOKEN_NOT_ASSOCIATED_TO_ACCOUNT;
import static com.hedera.node.app.spi.workflows.HandleException.validateFalse;
import static com.hedera.node.app.spi.workflows.HandleException.validateTrue;
import static java.util.Collections.emptyList;

import com.hedera.hapi.node.base.AccountID;
import com.hedera.hapi.node.base.TokenID;
import com.hedera.hapi.node.base.TokenType;
import com.hedera.hapi.node.token.CryptoTransferTransactionBody;
import com.hedera.node.app.service.token.ReadableAccountStore;
import com.hedera.node.app.service.token.ReadableTokenRelationStore;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.Map;
import javax.inject.Inject;
import javax.inject.Singleton;

/**
 * Assesses custom fees for a given crypto transfer transaction.
 */
@Singleton
public class CustomFeeAssessor {
    private final CustomFixedFeeAssessor fixedFeeAssessor;
    private final CustomFractionalFeeAssessor fractionalFeeAssessor;
    private final CustomRoyaltyFeeAssessor royaltyFeeAssessor;
    private int initialNftChanges = 0;

    @Inject
    public CustomFeeAssessor(
            @NonNull final CustomFixedFeeAssessor fixedFeeAssessor,
            @NonNull final CustomFractionalFeeAssessor fractionalFeeAssessor,
            @NonNull final CustomRoyaltyFeeAssessor royaltyFeeAssessor) {
        this.fixedFeeAssessor = fixedFeeAssessor;
        this.fractionalFeeAssessor = fractionalFeeAssessor;
        this.royaltyFeeAssessor = royaltyFeeAssessor;
    }

    private int numNftTransfers(final CryptoTransferTransactionBody op) {
        final var tokenTransfers = op.tokenTransfersOrElse(emptyList());
        var nftTransfers = 0;
        for (final var xfer : tokenTransfers) {
            nftTransfers += xfer.nftTransfersOrElse(emptyList()).size();
        }
        return nftTransfers;
    }

    public void assess(
            final AccountID sender,
            final CustomFeeMeta feeMeta,
            final int maxTransfersSize,
            final AccountID receiver,
            final AssessmentResult result,
            final ReadableTokenRelationStore tokenRelStore,
            final ReadableAccountStore accountStore) {
        fixedFeeAssessor.assessFixedFees(feeMeta, sender, result);

        validateBalanceChanges(result, maxTransfersSize, tokenRelStore, accountStore);

        // A FUNGIBLE_COMMON token can have fractional fees but not royalty fees.
        // A NON_FUNGIBLE_UNIQUE token can have royalty fees but not fractional fees.
        // So check token type and do further assessment
        if (feeMeta.tokenType().equals(TokenType.FUNGIBLE_COMMON)) {
            fractionalFeeAssessor.assessFractionalFees(feeMeta, sender, result);
        } else {
            royaltyFeeAssessor.assessRoyaltyFees(feeMeta, sender, receiver, result);
        }
        validateBalanceChanges(result, maxTransfersSize, tokenRelStore, accountStore);
    }

    private void validateBalanceChanges(
            final AssessmentResult result,
            final int maxTransfersSize,
            final ReadableTokenRelationStore tokenRelStore,
            final ReadableAccountStore accountStore) {
        var inputFungibleTransfers = 0;
        var newFungibleTransfers = 0;

        for (final var entry : result.getMutableInputTokenAdjustments().entrySet()) {
            final var entryValue = entry.getValue();
            inputFungibleTransfers += entry.getValue().size();
            validateAdjustmentEntry(tokenRelStore, accountStore, entry, entryValue, true);
        }

        for (final var entry : result.getHtsAdjustments().entrySet()) {
            final var entryValue = entry.getValue();
            newFungibleTransfers += entryValue.size();
            validateAdjustmentEntry(tokenRelStore, accountStore, entry, entryValue, false);
        }

        for (final var entry : result.getHbarAdjustments().entrySet()) {
            final Long hbarBalanceChange = entry.getValue();
            if (hbarBalanceChange < 0) {
                final var hasAlias = entry.getKey().hasAlias();
                var accountId = entry.getKey();
                if (hasAlias) {
                    accountId = accountStore.getAccountIDByAlias(entry.getKey().alias());
                }
                // Account does not exist or it's not auto created yet, therefore we cannot charge it.
                validateTrue(accountId != null, INSUFFICIENT_SENDER_ACCOUNT_BALANCE_FOR_CUSTOM_FEE);

                final var account = accountStore.getAccountById(accountId);
                validateTrue(
                        account.tinybarBalance() + hbarBalanceChange >= 0,
                        INSUFFICIENT_SENDER_ACCOUNT_BALANCE_FOR_CUSTOM_FEE);
            }
        }

        final var balanceChanges = result.getHbarAdjustments().size()
                + newFungibleTransfers
                + result.getInputHbarAdjustments().size()
                + inputFungibleTransfers
                + initialNftChanges;
        validateFalse(balanceChanges > maxTransfersSize, CUSTOM_FEE_CHARGING_EXCEEDED_MAX_ACCOUNT_AMOUNTS);
    }

    /**
     * Validates an adjustment entry for a given token transaction.
     * This method performs validation checks on the adjustment entry for a specific token transaction. It ensures that
     * the balance changes associated with the transaction are valid based on the current state of the token's
     * relationship with an account.
     *
     * @param tokenRelStore The ReadableTokenRelationStore used to retrieve token relationship information.
     * @param accountStore The ReadableAccountStore used to retrieve account information.
     * @param entry The entry representing the token transaction, consisting of a token identifier and a map of
     *              account identifiers to balance changes.
     * @param entryValue The map of account identifiers to balance changes for the token transaction.
     *
     * @throws IllegalArgumentException If the balance change for any account associated with the transaction is
     *                                  negative and there is no valid token relationship, or if the sender's account
     *                                  balance becomes insufficient for a custom fee.
     */
    private static void validateAdjustmentEntry(
            ReadableTokenRelationStore tokenRelStore,
            ReadableAccountStore accountStore,
            Map.Entry<TokenID, Map<AccountID, Long>> entry,
            Map<AccountID, Long> entryValue,
            boolean isInputFT) {
        for (final var entryTx : entryValue.entrySet()) {
            final Long htsBalanceChange = entryTx.getValue();
            if (htsBalanceChange < 0) {
                final var tokenRel = tokenRelStore.get(entryTx.getKey(), entry.getKey());
                final var account = accountStore.getAccountById(entryTx.getKey());
                // If token relation is null and account balance is also 0, that means that this account is newly
                // created from the same tx
                // AutoAssociate is next step after custom fee
                validateFalse(
                        tokenRel == null && account != null && account.tinybarBalance() == 0,
                        INSUFFICIENT_SENDER_ACCOUNT_BALANCE_FOR_CUSTOM_FEE);
                if (!isInputFT) {
                    validateTrue(tokenRel != null, TOKEN_NOT_ASSOCIATED_TO_ACCOUNT);
                    validateTrue(
                            tokenRel.balance() + htsBalanceChange >= 0, INSUFFICIENT_SENDER_ACCOUNT_BALANCE_FOR_CUSTOM_FEE);
                }
            }
        }
    }

    /**
     * Sets the initial NFT changes for the transaction. These are not going to change in the course of
     * assessing custom fees.
     * @param op the transaction body
     */
    public void calculateAndSetInitialNftChanges(final CryptoTransferTransactionBody op) {
        initialNftChanges = numNftTransfers(op);
    }

    public void resetInitialNftChanges() {
        initialNftChanges = 0;
    }
}
