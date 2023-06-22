/*
 * Copyright (C) 2023 Hedera Hashgraph, LLC
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

package com.hedera.node.app.service.contract.impl.exec.gas;

import com.hedera.node.app.service.contract.impl.hevm.HederaEvmContext;
import com.hedera.node.app.service.contract.impl.hevm.HederaEvmTransaction;
import com.hedera.node.app.service.contract.impl.hevm.HederaWorldUpdater;
import com.hedera.node.app.service.contract.impl.state.HederaEvmAccount;
import com.hedera.node.app.spi.workflows.HandleException;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import javax.inject.Singleton;

import org.apache.tuweni.bytes.Bytes;
import org.hyperledger.besu.evm.gascalculator.GasCalculator;

import static com.hedera.hapi.node.base.ResponseCodeEnum.*;
import static com.hedera.node.app.spi.workflows.HandleException.validateTrue;
import static java.util.Objects.requireNonNull;

@Singleton
public class CustomGasCharging {
    private final GasCalculator gasCalculator;

    public CustomGasCharging(@NonNull final GasCalculator gasCalculator) {
        this.gasCalculator = gasCalculator;
    }

    /**
     * Tries to charge gas for the given transaction based on the pre-fetched sender and relayer accounts,
     * within the given context and world updater.
     *
     * @param sender  the sender account
     * @param relayer the relayer account
     * @param context the context of the transaction, including the network gas price
     * @param worldUpdater the world updater for the transaction
     * @param transaction the transaction to charge gas for
     * @return the gas allowance charged to the relayer, if any
     * @throws HandleException if the gas charging fails for any reason
     */
    public long chargeGasAllowance(
            @NonNull final HederaEvmAccount sender,
            @Nullable final HederaEvmAccount relayer,
            @NonNull final HederaEvmContext context,
            @NonNull final HederaWorldUpdater worldUpdater,
            @NonNull final HederaEvmTransaction transaction) {
        if (context.staticCall()) {
            return 0L;
        }

        final var intrinsicGas = gasCalculator.transactionIntrinsicGasCost(Bytes.EMPTY, transaction.isCreate());
        validateTrue(transaction.gasLimit() >= intrinsicGas, INSUFFICIENT_GAS);

        final var gasCost = transaction.gasCostGiven(context.gasPrice());
        if (transaction.isEthereumTransaction())  {
            if (transaction.requiresFullRelayerAllowance()) {
                validateTrue(transaction.maxGasAllowance() >= gasCost, INSUFFICIENT_TX_FEE);
                validateAndCharge(gasCost, requireNonNull(relayer), worldUpdater);
                return gasCost;
            } else if (transaction.offeredGasPrice() >= context.gasPrice()) {
                validateAndCharge(gasCost, sender, worldUpdater);
                return 0L;
            } else {
                final var relayerGasCost = gasCost - transaction.offeredGasCost();
                validateTrue(transaction.maxGasAllowance() >= relayerGasCost, INSUFFICIENT_TX_FEE);
                validateAndCharge(
                        transaction.offeredGasCost(),
                        relayerGasCost,
                        sender,
                        requireNonNull(relayer),
                        worldUpdater);
                return relayerGasCost;
            }
        } else {
            final var upfrontCost = transaction.upfrontCostGiven(context.gasPrice());
            // Validate up-front cost is covered just for consistency with existing code
            validateTrue(sender.getBalance().toLong() >= upfrontCost, INSUFFICIENT_PAYER_BALANCE);
            validateAndCharge(gasCost, sender, worldUpdater);
            // We don't even have a relayer to charge in this code path
            return 0L;
        }
    }


    private void validateAndCharge(
            final long amount,
            @NonNull final HederaEvmAccount payer,
            @NonNull final HederaWorldUpdater worldUpdater) {
        validateTrue(payer.getBalance().toLong() >= amount, INSUFFICIENT_PAYER_BALANCE);
        worldUpdater.collectFee(payer.hederaId(), amount);
    }

    private void validateAndCharge(
            final long aAmount,
            final long bAmount,
            @NonNull final HederaEvmAccount aPayer,
            @NonNull final HederaEvmAccount bPayer,
            @NonNull final HederaWorldUpdater worldUpdater) {
        validateTrue(aPayer.getBalance().toLong() >= aAmount, INSUFFICIENT_PAYER_BALANCE);
        validateTrue(bPayer.getBalance().toLong() >= bAmount, INSUFFICIENT_PAYER_BALANCE);
        worldUpdater.collectFee(aPayer.hederaId(), aAmount);
        worldUpdater.collectFee(bPayer.hederaId(), bAmount);
    }
}
