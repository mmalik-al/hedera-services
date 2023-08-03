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

package com.hedera.node.app.service.contract.impl.test.exec.scope;

import static com.hedera.node.app.service.contract.impl.test.TestHelpers.DEFAULT_LEDGER_CONFIG;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

import com.hedera.hapi.node.base.AccountID;
import com.hedera.hapi.node.contract.ContractCreateTransactionBody;
import com.hedera.node.app.service.contract.impl.exec.scope.HandleHederaOperations;
import com.hedera.node.app.service.contract.impl.state.WritableContractStateStore;
import com.hedera.node.app.service.contract.impl.test.TestHelpers;
import com.hedera.node.app.service.token.api.TokenServiceApi;
import com.hedera.node.app.spi.records.BlockRecordInfo;
import com.hedera.node.app.spi.workflows.HandleContext;
import com.hedera.pbj.runtime.io.buffer.Bytes;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class HandleHederaOperationsTest {
    @Mock
    private HandleContext.SavepointStack savepointStack;

    @Mock
    private TokenServiceApi tokenServiceApi;

    @Mock
    private BlockRecordInfo blockRecordInfo;

    @Mock
    private HandleContext context;

    @Mock
    private WritableContractStateStore stateStore;

    private HandleHederaOperations subject;

    @BeforeEach
    void setUp() {
        subject = new HandleHederaOperations(DEFAULT_LEDGER_CONFIG, context);
    }

    @Test
    void returnsContextualStore() {
        given(context.writableStore(WritableContractStateStore.class)).willReturn(stateStore);

        assertSame(stateStore, subject.getStore());
    }

    @Test
    void delegatesEntropyToBlockRecordInfo() {
        final var pretendEntropy = Bytes.fromHex("0123456789");
        given(context.blockRecordInfo()).willReturn(blockRecordInfo);
        given(blockRecordInfo.getNMinus3RunningHash()).willReturn(pretendEntropy);
        assertSame(pretendEntropy, subject.entropy());
    }

    @Test
    void returnsZeroEntropyIfNMinus3HashMissing() {
        given(context.blockRecordInfo()).willReturn(blockRecordInfo);
        assertSame(HandleHederaOperations.ZERO_ENTROPY, subject.entropy());
    }

    @Test
    void createsNewSavepointWhenBeginningScope() {
        given(context.savepointStack()).willReturn(savepointStack);

        final var nestedScope = subject.begin();

        assertSame(subject, nestedScope);
        verify(savepointStack).createSavepoint();
    }

    @Test
    void rollsBackSavepointWhenReverting() {
        given(context.savepointStack()).willReturn(savepointStack);

        subject.revert();

        verify(savepointStack).rollback();
    }

    @Test
    void peekNumberUsesContext() {
        given(context.peekAtNewEntityNum()).willReturn(123L);
        assertEquals(123L, subject.peekNextEntityNumber());
    }

    @Test
    void useNumberUsesContext() {
        given(context.newEntityNum()).willReturn(123L);
        assertEquals(123L, subject.useNextEntityNumber());
    }

    @Test
    void commitIsNotImplemented() {
        assertThrows(AssertionError.class, subject::commit);
    }

    @Test
    void lazyCreationCostInGasNotImplemented() {
        assertThrows(AssertionError.class, subject::lazyCreationCostInGas);
    }

    @Test
    void gasPriceInTinybarsNotImplemented() {
        assertEquals(1L, subject.gasPriceInTinybars());
    }

    @Test
    void valueInTinybarsNotImplemented() {
        assertThrows(AssertionError.class, () -> subject.valueInTinybars(1L));
    }

    @Test
    void collectFeeStillTransfersAllToNetworkFunding() {
        given(context.serviceApi(TokenServiceApi.class)).willReturn(tokenServiceApi);

        subject.collectFee(TestHelpers.NON_SYSTEM_ACCOUNT_ID, 123L);

        verify(tokenServiceApi)
                .transferFromTo(
                        TestHelpers.NON_SYSTEM_ACCOUNT_ID,
                        AccountID.newBuilder()
                                .accountNum(DEFAULT_LEDGER_CONFIG.fundingAccount())
                                .build(),
                        123L);
    }

    @Test
    void refundFeeNotImplemented() {
        assertThrows(AssertionError.class, () -> subject.refundFee(AccountID.DEFAULT, 1L));
    }

    @Test
    void chargeStorageRentNotImplemented() {
        assertThrows(AssertionError.class, () -> subject.chargeStorageRent(1L, 2L, true));
    }

    @Test
    void updateStorageMetadataNotImplemented() {
        assertThrows(AssertionError.class, () -> subject.updateStorageMetadata(1L, Bytes.EMPTY, 2));
    }

    @Test
    void createContractNotImplemented() {
        assertThrows(AssertionError.class, () -> subject.createContract(1L, 2L, 3L, Bytes.EMPTY));
    }

    @Test
    void createContractWithBodyNotImplemented() {
        assertThrows(
                AssertionError.class,
                () -> subject.createContract(1L, ContractCreateTransactionBody.DEFAULT, 3L, Bytes.EMPTY));
    }

    @Test
    void deleteAliasedContractNotImplemented() {
        assertThrows(AssertionError.class, () -> subject.deleteAliasedContract(Bytes.EMPTY));
    }

    @Test
    void deleteUnaliasedContractNotImplemented() {
        assertThrows(AssertionError.class, () -> subject.deleteUnaliasedContract(123L));
    }

    @Test
    void getModifiedAccountNumbersNotImplemented() {
        assertThrows(AssertionError.class, subject::getModifiedAccountNumbers);
    }

    @Test
    void createdContractIdsNotImplemented() {
        assertThrows(AssertionError.class, subject::createdContractIds);
    }

    @Test
    void updatedContractNoncesNotImplemented() {
        assertThrows(AssertionError.class, subject::updatedContractNonces);
    }

    @Test
    void getOriginalSlotsUsedNotImplemented() {
        assertThrows(AssertionError.class, () -> subject.getOriginalSlotsUsed(1L));
    }
}
