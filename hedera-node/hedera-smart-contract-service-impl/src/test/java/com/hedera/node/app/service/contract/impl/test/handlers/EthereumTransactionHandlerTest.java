/*
 * Copyright (C) 2023-2024 Hedera Hashgraph, LLC
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

package com.hedera.node.app.service.contract.impl.test.handlers;

import static com.hedera.hapi.node.base.HederaFunctionality.ETHEREUM_TRANSACTION;
import static com.hedera.hapi.node.base.ResponseCodeEnum.INVALID_ETHEREUM_TRANSACTION;
import static com.hedera.node.app.service.contract.impl.hevm.HederaEvmVersion.VERSION_038;
import static com.hedera.node.app.service.contract.impl.test.TestHelpers.CALLED_CONTRACT_ID;
import static com.hedera.node.app.service.contract.impl.test.TestHelpers.DEFAULT_CONFIG;
import static com.hedera.node.app.service.contract.impl.test.TestHelpers.ETH_DATA_WITHOUT_TO_ADDRESS;
import static com.hedera.node.app.service.contract.impl.test.TestHelpers.ETH_DATA_WITH_TO_ADDRESS;
import static com.hedera.node.app.service.contract.impl.test.TestHelpers.HEVM_CREATION;
import static com.hedera.node.app.service.contract.impl.test.TestHelpers.SUCCESS_RESULT;
import static com.hedera.node.app.spi.fixtures.Assertions.assertThrowsPreCheck;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import com.hedera.hapi.node.contract.EthereumTransactionBody;
import com.hedera.hapi.node.transaction.TransactionBody;
import com.hedera.node.app.service.contract.impl.exec.CallOutcome;
import com.hedera.node.app.service.contract.impl.exec.ContextTransactionProcessor;
import com.hedera.node.app.service.contract.impl.exec.TransactionComponent;
import com.hedera.node.app.service.contract.impl.exec.TransactionProcessor;
import com.hedera.node.app.service.contract.impl.handlers.EthereumTransactionHandler;
import com.hedera.node.app.service.contract.impl.hevm.ActionSidecarContentTracer;
import com.hedera.node.app.service.contract.impl.hevm.HederaEvmContext;
import com.hedera.node.app.service.contract.impl.hevm.HederaWorldUpdater;
import com.hedera.node.app.service.contract.impl.hevm.HydratedEthTxData;
import com.hedera.node.app.service.contract.impl.infra.EthTxSigsCache;
import com.hedera.node.app.service.contract.impl.infra.EthereumCallDataHydration;
import com.hedera.node.app.service.contract.impl.infra.HevmTransactionFactory;
import com.hedera.node.app.service.contract.impl.records.EthereumTransactionRecordBuilder;
import com.hedera.node.app.service.contract.impl.state.RootProxyWorldUpdater;
import com.hedera.node.app.service.contract.impl.test.TestHelpers;
import com.hedera.node.app.service.file.ReadableFileStore;
import com.hedera.node.app.service.token.ReadableAccountStore;
import com.hedera.node.app.spi.workflows.HandleContext;
import com.hedera.node.app.spi.workflows.PreCheckException;
import com.hedera.node.app.spi.workflows.PreHandleContext;
import com.hedera.node.config.data.ContractsConfig;
import com.hedera.pbj.runtime.io.buffer.Bytes;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class EthereumTransactionHandlerTest {
    @Mock
    private EthereumCallDataHydration callDataHydration;

    @Mock
    private EthTxSigsCache ethereumSignatures;

    @Mock
    private ReadableFileStore fileStore;

    @Mock
    private TransactionComponent component;

    @Mock
    private HandleContext handleContext;

    @Mock
    private PreHandleContext preHandleContext;

    @Mock
    private TransactionComponent.Factory factory;

    @Mock
    private EthereumTransactionRecordBuilder recordBuilder;

    @Mock
    private RootProxyWorldUpdater baseProxyWorldUpdater;

    @Mock
    private HevmTransactionFactory hevmTransactionFactory;

    @Mock
    private HederaEvmContext hederaEvmContext;

    @Mock
    private ActionSidecarContentTracer tracer;

    @Mock
    private Supplier<HederaWorldUpdater> feesOnlyUpdater;

    @Mock
    private TransactionProcessor transactionProcessor;

    @Mock
    private ReadableAccountStore readableAccountStore;

    private EthereumTransactionHandler subject;

    @BeforeEach
    void setup() {
        subject = new EthereumTransactionHandler(ethereumSignatures, callDataHydration, () -> factory);
    }

    void setUpTransactionProcessing() {
        final var contractsConfig = DEFAULT_CONFIG.getConfigData(ContractsConfig.class);
        final var processors = Map.of(VERSION_038, transactionProcessor);

        final var contextTransactionProcessor = new ContextTransactionProcessor(
                HydratedEthTxData.successFrom(ETH_DATA_WITH_TO_ADDRESS),
                handleContext,
                contractsConfig,
                DEFAULT_CONFIG,
                hederaEvmContext,
                tracer,
                baseProxyWorldUpdater,
                hevmTransactionFactory,
                feesOnlyUpdater,
                processors);

        given(component.contextTransactionProcessor()).willReturn(contextTransactionProcessor);
        given(hevmTransactionFactory.fromHapiTransaction(handleContext.body())).willReturn(HEVM_CREATION);

        given(transactionProcessor.processTransaction(
                        HEVM_CREATION,
                        baseProxyWorldUpdater,
                        feesOnlyUpdater,
                        hederaEvmContext,
                        tracer,
                        DEFAULT_CONFIG))
                .willReturn(SUCCESS_RESULT);
    }

    @Test
    void delegatesToCreatedComponentAndExposesEthTxDataCallWithToAddress() {
        given(factory.create(handleContext, ETHEREUM_TRANSACTION)).willReturn(component);
        given(component.hydratedEthTxData()).willReturn(HydratedEthTxData.successFrom(ETH_DATA_WITH_TO_ADDRESS));
        setUpTransactionProcessing();
        given(handleContext.recordBuilder(EthereumTransactionRecordBuilder.class))
                .willReturn(recordBuilder);
        final var expectedResult = SUCCESS_RESULT.asProtoResultOf(ETH_DATA_WITH_TO_ADDRESS, baseProxyWorldUpdater);
        final var expectedOutcome = new CallOutcome(
                expectedResult,
                SUCCESS_RESULT.finalStatus(),
                CALLED_CONTRACT_ID,
                SUCCESS_RESULT.gasPrice(),
                null,
                null);
        given(recordBuilder.contractID(CALLED_CONTRACT_ID)).willReturn(recordBuilder);
        given(recordBuilder.contractCallResult(expectedResult)).willReturn(recordBuilder);
        given(recordBuilder.ethereumHash(Bytes.wrap(ETH_DATA_WITH_TO_ADDRESS.getEthereumHash())))
                .willReturn(recordBuilder);
        given(recordBuilder.withCommonFieldsSetFrom(expectedOutcome)).willReturn(recordBuilder);

        assertDoesNotThrow(() -> subject.handle(handleContext));
    }

    @Test
    void delegatesToCreatedComponentAndExposesEthTxDataCreateWithoutToAddress() {
        given(factory.create(handleContext, ETHEREUM_TRANSACTION)).willReturn(component);
        given(component.hydratedEthTxData()).willReturn(HydratedEthTxData.successFrom(ETH_DATA_WITHOUT_TO_ADDRESS));
        setUpTransactionProcessing();
        given(handleContext.recordBuilder(EthereumTransactionRecordBuilder.class))
                .willReturn(recordBuilder);
        given(baseProxyWorldUpdater.getCreatedContractIds()).willReturn(List.of(CALLED_CONTRACT_ID));
        final var expectedResult = SUCCESS_RESULT.asProtoResultOf(ETH_DATA_WITHOUT_TO_ADDRESS, baseProxyWorldUpdater);
        final var expectedOutcome = new CallOutcome(
                expectedResult,
                SUCCESS_RESULT.finalStatus(),
                CALLED_CONTRACT_ID,
                SUCCESS_RESULT.gasPrice(),
                null,
                null);

        given(recordBuilder.contractID(CALLED_CONTRACT_ID)).willReturn(recordBuilder);
        given(recordBuilder.contractCreateResult(expectedResult)).willReturn(recordBuilder);
        given(recordBuilder.ethereumHash(Bytes.wrap(ETH_DATA_WITHOUT_TO_ADDRESS.getEthereumHash())))
                .willReturn(recordBuilder);
        given(recordBuilder.withCommonFieldsSetFrom(expectedOutcome)).willReturn(recordBuilder);

        assertDoesNotThrow(() -> subject.handle(handleContext));
    }

    @Test
    void preHandleCachesTheSignaturesIfDataCanBeHydrated() throws PreCheckException {
        final var ethTxn = EthereumTransactionBody.newBuilder()
                .ethereumData(TestHelpers.ETH_WITH_TO_ADDRESS)
                .build();
        final var body =
                TransactionBody.newBuilder().ethereumTransaction(ethTxn).build();
        given(preHandleContext.body()).willReturn(body);
        given(preHandleContext.createStore(ReadableFileStore.class)).willReturn(fileStore);
        given(preHandleContext.configuration()).willReturn(DEFAULT_CONFIG);
        given(callDataHydration.tryToHydrate(ethTxn, fileStore, 1001L))
                .willReturn(HydratedEthTxData.successFrom(ETH_DATA_WITH_TO_ADDRESS));
        subject.preHandle(preHandleContext);
        verify(ethereumSignatures).computeIfAbsent(ETH_DATA_WITH_TO_ADDRESS);
    }

    @Test
    void preHandleDoesNotIgnoreFailureToHydrate() throws PreCheckException {
        final var ethTxn =
                EthereumTransactionBody.newBuilder().ethereumData(Bytes.EMPTY).build();
        final var body =
                TransactionBody.newBuilder().ethereumTransaction(ethTxn).build();
        given(preHandleContext.body()).willReturn(body);
        given(preHandleContext.createStore(ReadableFileStore.class)).willReturn(fileStore);
        given(preHandleContext.configuration()).willReturn(DEFAULT_CONFIG);
        given(callDataHydration.tryToHydrate(ethTxn, fileStore, 1001L))
                .willReturn(HydratedEthTxData.failureFrom(INVALID_ETHEREUM_TRANSACTION));
        assertThrowsPreCheck(() -> subject.preHandle(preHandleContext), INVALID_ETHEREUM_TRANSACTION);
        verifyNoInteractions(ethereumSignatures);
    }
}
