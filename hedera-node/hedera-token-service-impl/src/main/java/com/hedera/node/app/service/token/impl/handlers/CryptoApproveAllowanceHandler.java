/*
 * Copyright (C) 2022-2023 Hedera Hashgraph, LLC
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

package com.hedera.node.app.service.token.impl.handlers;

import static com.hedera.hapi.node.base.ResponseCodeEnum.INVALID_ALLOWANCE_OWNER_ID;
import static com.hedera.hapi.node.base.ResponseCodeEnum.INVALID_DELEGATING_SPENDER;
import static java.util.Collections.emptyList;
import static java.util.Objects.requireNonNull;

import com.hedera.hapi.node.base.AccountID;
import com.hedera.hapi.node.base.HederaFunctionality;
import com.hedera.node.app.spi.workflows.HandleContext;
import com.hedera.node.app.spi.workflows.HandleException;
import com.hedera.node.app.spi.workflows.PreCheckException;
import com.hedera.node.app.spi.workflows.PreHandleContext;
import com.hedera.node.app.spi.workflows.TransactionHandler;
import edu.umd.cs.findbugs.annotations.NonNull;
import javax.inject.Inject;
import javax.inject.Singleton;

/**
 * This class contains all workflow-related functionality regarding {@link
 * HederaFunctionality#CRYPTO_APPROVE_ALLOWANCE}.
 */
@Singleton
public class CryptoApproveAllowanceHandler implements TransactionHandler {
    @Inject
    public CryptoApproveAllowanceHandler() {
        // Exists for injection
    }

    @Override
    public void preHandle(@NonNull final PreHandleContext context) throws PreCheckException {
        requireNonNull(context);
        final var op = context.body().cryptoApproveAllowanceOrThrow();
        var failureStatus = INVALID_ALLOWANCE_OWNER_ID;

        for (final var allowance : op.cryptoAllowancesOrElse(emptyList())) {
            context.requireKeyOrThrow(allowance.ownerOrElse(AccountID.DEFAULT), failureStatus);
        }
        for (final var allowance : op.tokenAllowancesOrElse(emptyList())) {
            context.requireKeyOrThrow(allowance.ownerOrElse(AccountID.DEFAULT), failureStatus);
        }
        for (final var allowance : op.nftAllowancesOrElse(emptyList())) {
            final var ownerId = allowance.ownerOrElse(AccountID.DEFAULT);
            // If a spender who is granted approveForAll from owner and is granting
            // allowance for a serial to another spender, need signature from the approveForAll
            // spender
            var operatorId = allowance.delegatingSpenderOrElse(ownerId);
            // If approveForAll is set to true, need signature from owner
            // since only the owner can grant approveForAll
            if (allowance.hasApprovedForAll() && allowance.approvedForAllOrThrow()) {
                operatorId = ownerId;
            }
            if (operatorId != ownerId) {
                failureStatus = INVALID_DELEGATING_SPENDER;
            }
            context.requireKeyOrThrow(operatorId, failureStatus);
        }
    }

    @Override
    public void handle(@NonNull final HandleContext context) throws HandleException {
        throw new UnsupportedOperationException("Not implemented");
    }
}
