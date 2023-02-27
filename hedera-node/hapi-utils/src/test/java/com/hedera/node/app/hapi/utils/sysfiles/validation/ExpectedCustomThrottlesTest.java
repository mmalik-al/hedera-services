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

package com.hedera.node.app.hapi.utils.sysfiles.validation;

import static com.hedera.node.app.hapi.utils.sysfiles.validation.ExpectedCustomThrottles.ACTIVE_OPS;
import static com.hederahashgraph.api.proto.java.HederaFunctionality.ConsensusCreateTopic;
import static com.hederahashgraph.api.proto.java.HederaFunctionality.ConsensusDeleteTopic;
import static com.hederahashgraph.api.proto.java.HederaFunctionality.ConsensusGetTopicInfo;
import static com.hederahashgraph.api.proto.java.HederaFunctionality.ConsensusSubmitMessage;
import static com.hederahashgraph.api.proto.java.HederaFunctionality.ConsensusUpdateTopic;
import static com.hederahashgraph.api.proto.java.HederaFunctionality.ContractCallLocal;
import static com.hederahashgraph.api.proto.java.HederaFunctionality.ContractCreate;
import static com.hederahashgraph.api.proto.java.HederaFunctionality.ContractDelete;
import static com.hederahashgraph.api.proto.java.HederaFunctionality.ContractGetBytecode;
import static com.hederahashgraph.api.proto.java.HederaFunctionality.ContractGetInfo;
import static com.hederahashgraph.api.proto.java.HederaFunctionality.ContractGetRecords;
import static com.hederahashgraph.api.proto.java.HederaFunctionality.ContractUpdate;
import static com.hederahashgraph.api.proto.java.HederaFunctionality.CryptoApproveAllowance;
import static com.hederahashgraph.api.proto.java.HederaFunctionality.CryptoCreate;
import static com.hederahashgraph.api.proto.java.HederaFunctionality.CryptoDelete;
import static com.hederahashgraph.api.proto.java.HederaFunctionality.CryptoDeleteAllowance;
import static com.hederahashgraph.api.proto.java.HederaFunctionality.CryptoGetAccountBalance;
import static com.hederahashgraph.api.proto.java.HederaFunctionality.CryptoGetAccountRecords;
import static com.hederahashgraph.api.proto.java.HederaFunctionality.CryptoGetInfo;
import static com.hederahashgraph.api.proto.java.HederaFunctionality.CryptoTransfer;
import static com.hederahashgraph.api.proto.java.HederaFunctionality.CryptoUpdate;
import static com.hederahashgraph.api.proto.java.HederaFunctionality.EthereumTransaction;
import static com.hederahashgraph.api.proto.java.HederaFunctionality.FileAppend;
import static com.hederahashgraph.api.proto.java.HederaFunctionality.FileCreate;
import static com.hederahashgraph.api.proto.java.HederaFunctionality.FileDelete;
import static com.hederahashgraph.api.proto.java.HederaFunctionality.FileGetContents;
import static com.hederahashgraph.api.proto.java.HederaFunctionality.FileGetInfo;
import static com.hederahashgraph.api.proto.java.HederaFunctionality.FileUpdate;
import static com.hederahashgraph.api.proto.java.HederaFunctionality.GetVersionInfo;
import static com.hederahashgraph.api.proto.java.HederaFunctionality.ScheduleCreate;
import static com.hederahashgraph.api.proto.java.HederaFunctionality.ScheduleDelete;
import static com.hederahashgraph.api.proto.java.HederaFunctionality.ScheduleGetInfo;
import static com.hederahashgraph.api.proto.java.HederaFunctionality.ScheduleSign;
import static com.hederahashgraph.api.proto.java.HederaFunctionality.TokenAccountWipe;
import static com.hederahashgraph.api.proto.java.HederaFunctionality.TokenAssociateToAccount;
import static com.hederahashgraph.api.proto.java.HederaFunctionality.TokenBurn;
import static com.hederahashgraph.api.proto.java.HederaFunctionality.TokenCreate;
import static com.hederahashgraph.api.proto.java.HederaFunctionality.TokenDelete;
import static com.hederahashgraph.api.proto.java.HederaFunctionality.TokenDissociateFromAccount;
import static com.hederahashgraph.api.proto.java.HederaFunctionality.TokenFeeScheduleUpdate;
import static com.hederahashgraph.api.proto.java.HederaFunctionality.TokenFreezeAccount;
import static com.hederahashgraph.api.proto.java.HederaFunctionality.TokenGetAccountNftInfos;
import static com.hederahashgraph.api.proto.java.HederaFunctionality.TokenGetInfo;
import static com.hederahashgraph.api.proto.java.HederaFunctionality.TokenGetNftInfo;
import static com.hederahashgraph.api.proto.java.HederaFunctionality.TokenGetNftInfos;
import static com.hederahashgraph.api.proto.java.HederaFunctionality.TokenGrantKycToAccount;
import static com.hederahashgraph.api.proto.java.HederaFunctionality.TokenMint;
import static com.hederahashgraph.api.proto.java.HederaFunctionality.TokenPause;
import static com.hederahashgraph.api.proto.java.HederaFunctionality.TokenRevokeKycFromAccount;
import static com.hederahashgraph.api.proto.java.HederaFunctionality.TokenUnfreezeAccount;
import static com.hederahashgraph.api.proto.java.HederaFunctionality.TokenUnpause;
import static com.hederahashgraph.api.proto.java.HederaFunctionality.TokenUpdate;
import static com.hederahashgraph.api.proto.java.HederaFunctionality.TransactionGetReceipt;
import static com.hederahashgraph.api.proto.java.HederaFunctionality.TransactionGetRecord;
import static com.hederahashgraph.api.proto.java.HederaFunctionality.UtilPrng;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class ExpectedCustomThrottlesTest {
    @Test
    void releaseTwentyHasExpected() {
        assertEquals(56, ACTIVE_OPS.size());

        assertTrue(ACTIVE_OPS.contains(CryptoCreate), "Missing CryptoCreate!");
        assertTrue(ACTIVE_OPS.contains(CryptoTransfer), "Missing CryptoTransfer!");
        assertTrue(ACTIVE_OPS.contains(CryptoUpdate), "Missing CryptoUpdate!");
        assertTrue(ACTIVE_OPS.contains(CryptoDelete), "Missing CryptoDelete!");
        assertTrue(ACTIVE_OPS.contains(CryptoApproveAllowance), "Missing CryptoApproveAllowance!");
        assertTrue(ACTIVE_OPS.contains(CryptoDeleteAllowance), "Missing CryptoDeleteAllowance!");
        assertTrue(ACTIVE_OPS.contains(FileCreate), "Missing FileCreate!");
        assertTrue(ACTIVE_OPS.contains(FileUpdate), "Missing FileUpdate!");
        assertTrue(ACTIVE_OPS.contains(FileDelete), "Missing FileDelete!");
        assertTrue(ACTIVE_OPS.contains(FileAppend), "Missing FileAppend!");
        assertTrue(ACTIVE_OPS.contains(ContractCreate), "Missing ContractCreate!");
        assertTrue(ACTIVE_OPS.contains(ContractUpdate), "Missing ContractUpdate!");
        assertTrue(ACTIVE_OPS.contains(ContractCreate), "Missing ContractCreate!");
        assertTrue(ACTIVE_OPS.contains(ContractDelete), "Missing ContractDelete!");
        assertTrue(ACTIVE_OPS.contains(ConsensusCreateTopic), "Missing ConsensusCreateTopic!");
        assertTrue(ACTIVE_OPS.contains(ConsensusUpdateTopic), "Missing ConsensusUpdateTopic!");
        assertTrue(ACTIVE_OPS.contains(ConsensusDeleteTopic), "Missing ConsensusDeleteTopic!");
        assertTrue(ACTIVE_OPS.contains(ConsensusSubmitMessage), "Missing ConsensusSubmitMessage!");
        assertTrue(ACTIVE_OPS.contains(TokenCreate), "Missing TokenCreate!");
        assertTrue(ACTIVE_OPS.contains(TokenFreezeAccount), "Missing TokenFreezeAccount!");
        assertTrue(ACTIVE_OPS.contains(TokenGetNftInfo), "Missing TokenGetNftInfo!");
        assertTrue(ACTIVE_OPS.contains(TokenGetAccountNftInfos), "Missing TokenGetAccountNftInfos!");
        assertTrue(ACTIVE_OPS.contains(TokenGetNftInfos), "Missing TokenGetNftInfos!");
        assertTrue(ACTIVE_OPS.contains(TokenUnfreezeAccount), "Missing TokenUnfreezeAccount!");
        assertTrue(ACTIVE_OPS.contains(TokenPause), "Missing TokenPause!");
        assertTrue(ACTIVE_OPS.contains(TokenUnpause), "Missing TokenUnpause!");
        assertTrue(ACTIVE_OPS.contains(TokenGrantKycToAccount), "Missing TokenGrantKycToAccount!");
        assertTrue(ACTIVE_OPS.contains(TokenRevokeKycFromAccount), "Missing TokenRevokeKycFromAccount!");
        assertTrue(ACTIVE_OPS.contains(TokenDelete), "Missing TokenDelete!");
        assertTrue(ACTIVE_OPS.contains(TokenMint), "Missing TokenMint!");
        assertTrue(ACTIVE_OPS.contains(TokenBurn), "Missing TokenBurn!");
        assertTrue(ACTIVE_OPS.contains(TokenAccountWipe), "Missing TokenAccountWipe!");
        assertTrue(ACTIVE_OPS.contains(TokenUpdate), "Missing TokenUpdate!");
        assertTrue(ACTIVE_OPS.contains(TokenAssociateToAccount), "Missing TokenAssociateToAccount!");
        assertTrue(ACTIVE_OPS.contains(TokenDissociateFromAccount), "Missing TokenDissociateFromAccount!");
        assertTrue(ACTIVE_OPS.contains(TokenFeeScheduleUpdate), "Missing TokenFeeScheduleUpdate!");
        assertTrue(ACTIVE_OPS.contains(ScheduleCreate), "Missing ScheduleCreate!");
        assertTrue(ACTIVE_OPS.contains(ScheduleSign), "Missing ScheduleSign!");
        assertTrue(ACTIVE_OPS.contains(ScheduleDelete), "Missing ScheduleDelete!");
        assertTrue(ACTIVE_OPS.contains(ConsensusGetTopicInfo), "Missing ConsensusGetTopicInfo!");
        assertTrue(ACTIVE_OPS.contains(ContractCallLocal), "Missing ContractCallLocal!");
        assertTrue(ACTIVE_OPS.contains(ContractGetInfo), "Missing ContractGetInfo!");
        assertTrue(ACTIVE_OPS.contains(ContractGetBytecode), "Missing ContractGetBytecode!");
        assertTrue(ACTIVE_OPS.contains(ContractGetRecords), "Missing ContractGetRecords!");
        assertTrue(ACTIVE_OPS.contains(CryptoGetAccountBalance), "Missing CryptoGetAccountBalance!");
        assertTrue(ACTIVE_OPS.contains(CryptoGetAccountRecords), "Missing CryptoGetAccountRecords!");
        assertTrue(ACTIVE_OPS.contains(CryptoGetInfo), "Missing CryptoGetInfo!");
        assertTrue(ACTIVE_OPS.contains(FileGetContents), "Missing FileGetContents!");
        assertTrue(ACTIVE_OPS.contains(FileGetInfo), "Missing FileGetInfo!");
        assertTrue(ACTIVE_OPS.contains(TransactionGetReceipt), "Missing TransactionGetReceipt!");
        assertTrue(ACTIVE_OPS.contains(TransactionGetRecord), "Missing TransactionGetRecord!");
        assertTrue(ACTIVE_OPS.contains(GetVersionInfo), "Missing GetVersionInfo!");
        assertTrue(ACTIVE_OPS.contains(TokenGetInfo), "Missing TokenGetInfo!");
        assertTrue(ACTIVE_OPS.contains(ScheduleGetInfo), "Missing ScheduleGetInfo!");
        assertTrue(ACTIVE_OPS.contains(EthereumTransaction), "Missing EthereumTransaction!");
        assertTrue(ACTIVE_OPS.contains(UtilPrng), "Missing UtilPrng!");
    }
}
