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

package com.hedera.node.app.service.contract.impl.handlers;

import static com.hedera.node.app.hapi.utils.ethereum.EthTxData.populateEthTxData;
import static java.util.Objects.requireNonNull;

import com.hedera.hapi.node.base.HederaFunctionality;
import com.hedera.node.app.service.contract.impl.exec.TransactionComponent;
import com.hedera.node.app.service.contract.impl.infra.EthereumSignatures;
import com.hedera.node.app.service.contract.impl.records.EthereumTransactionRecordBuilder;
import com.hedera.node.app.spi.workflows.HandleContext;
import com.hedera.node.app.spi.workflows.HandleException;
import com.hedera.node.app.spi.workflows.PreHandleContext;
import com.hedera.node.app.spi.workflows.TransactionHandler;
import com.hedera.pbj.runtime.io.buffer.Bytes;
import edu.umd.cs.findbugs.annotations.NonNull;
import javax.inject.Inject;
import javax.inject.Provider;
import javax.inject.Singleton;

/**
 * This class contains all workflow-related functionality regarding {@link
 * HederaFunctionality#ETHEREUM_TRANSACTION}.
 */
@Singleton
public class EthereumTransactionHandler implements TransactionHandler {
    private final EthereumSignatures ethereumSignatures;
    private final Provider<TransactionComponent.Factory> provider;

    @Inject
    public EthereumTransactionHandler(
            @NonNull final EthereumSignatures ethereumSignatures,
            @NonNull final Provider<TransactionComponent.Factory> provider) {
        this.ethereumSignatures = ethereumSignatures;
        this.provider = requireNonNull(provider);
    }

    @Override
    public void preHandle(@NonNull final PreHandleContext context) {
        requireNonNull(context);
        final var rawEthTx =
                context.body().ethereumTransactionOrThrow().ethereumData().toByteArray();
        final var maybeParsedEthTx = populateEthTxData(rawEthTx);
        if (maybeParsedEthTx != null) {
            ethereumSignatures.impliedBy(maybeParsedEthTx);
        }
    }

    @Override
    public void handle(@NonNull final HandleContext context) throws HandleException {
        // Create the transaction-scoped component
        final var component = provider.get().create(context);

        // Run its in-scope transaction and get the outcome
        final var outcome = component.contextTransactionProcessor().call();

        // Assemble the appropriate top-level record for the result
        final var ethTxData = requireNonNull(component.ethTxData());
        final var recordBuilder = context.recordBuilder(EthereumTransactionRecordBuilder.class)
                .ethereumHash(Bytes.wrap(ethTxData.getEthereumHash()))
                .status(outcome.status());
        if (ethTxData.hasToAddress()) {
            // The Ethereum transaction was a top-level MESSAGE_CALL
            recordBuilder.contractID(outcome.recipientIdIfCalled()).contractCallResult(outcome.result());
        } else {
            // The Ethereum transaction was a top-level CONTRACT_CREATION
            recordBuilder.contractID(outcome.recipientIdIfCreated()).contractCreateResult(outcome.result());
        }
    }
}
