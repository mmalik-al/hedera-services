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

package com.hedera.node.app.service.mono.ledger.accounts.staking;

import static com.hedera.node.app.service.mono.context.properties.PropertyNames.LEDGER_TOTAL_TINY_BAR_FLOAT;
import static com.hedera.node.app.service.mono.context.properties.PropertyNames.STAKING_REWARD_HISTORY_NUM_STORED_PERIODS;
import static com.hedera.node.app.service.mono.ledger.accounts.staking.StakingUtilsTest.buildPendingNodeStakeChanges;
import static com.hedera.node.app.service.mono.state.migration.StakingInfoMapBuilder.buildStakingInfoMap;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.BDDMockito.given;

import com.hedera.node.app.service.mono.context.properties.BootstrapProperties;
import com.hedera.node.app.service.mono.state.adapters.MerkleMapLike;
import com.hedera.node.app.service.mono.state.merkle.MerkleAccount;
import com.hedera.node.app.service.mono.state.merkle.MerkleStakingInfo;
import com.hedera.node.app.service.mono.state.migration.AccountStorageAdapter;
import com.hedera.node.app.service.mono.utils.EntityNum;
import com.hedera.test.factories.accounts.MerkleAccountFactory;
import com.hederahashgraph.api.proto.java.AccountID;
import com.swirlds.common.platform.NodeId;
import com.swirlds.merkle.map.MerkleMap;
import com.swirlds.platform.system.address.AddressBook;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class StakeChangeManagerTest {

    @Mock
    private AddressBook addressBook;

    @Mock
    private StakeInfoManager stakeInfoManager;

    @Mock
    private MerkleMap<EntityNum, MerkleAccount> accounts;

    @Mock
    private BootstrapProperties bootstrapProperties;

    private StakeChangeManager subject;
    private MerkleMap<EntityNum, MerkleStakingInfo> stakingInfo;

    private final EntityNum node0Id = EntityNum.fromLong(0L);

    @BeforeEach
    void setup() {
        stakingInfo = buildsStakingInfoMap();
        subject = new StakeChangeManager(
                stakeInfoManager, () -> AccountStorageAdapter.fromInMemory(MerkleMapLike.from(accounts)));
    }

    @Test
    void ignoresRequestToWithdrawOrAddStakeFromMissingNodeIds() {
        assertDoesNotThrow(() -> subject.awardStake(0L, 100L, false));
        assertDoesNotThrow(() -> subject.withdrawStake(0L, 100L, false));
    }

    @Test
    void withdrawsStakeCorrectly() {
        assertEquals(1000L, stakingInfo.get(node0Id).getStake());
        assertEquals(300L, stakingInfo.get(node0Id).getStakeToReward());
        assertEquals(400L, stakingInfo.get(node0Id).getStakeToNotReward());

        given(stakeInfoManager.mutableStakeInfoFor(0L)).willReturn(stakingInfo.get(node0Id));
        subject.withdrawStake(0L, 100L, false);

        assertEquals(1000L, stakingInfo.get(node0Id).getStake());
        assertEquals(200L, stakingInfo.get(node0Id).getStakeToReward());
        assertEquals(400L, stakingInfo.get(node0Id).getStakeToNotReward());

        subject.withdrawStake(0L, 100L, true);

        assertEquals(1000L, stakingInfo.get(node0Id).getStake());
        assertEquals(200L, stakingInfo.get(node0Id).getStakeToReward());
        assertEquals(300L, stakingInfo.get(node0Id).getStakeToNotReward());
    }

    @Test
    void awardsStakeCorrectly() {
        assertEquals(1000L, stakingInfo.get(node0Id).getStake());
        assertEquals(300L, stakingInfo.get(node0Id).getStakeToReward());
        assertEquals(400L, stakingInfo.get(node0Id).getStakeToNotReward());
        given(stakeInfoManager.mutableStakeInfoFor(0L)).willReturn(stakingInfo.get(node0Id));

        subject.awardStake(0L, 100L, false);

        assertEquals(1000L, stakingInfo.get(node0Id).getStake());
        assertEquals(400L, stakingInfo.get(node0Id).getStakeToReward());
        assertEquals(400L, stakingInfo.get(node0Id).getStakeToNotReward());

        subject.awardStake(0L, 100L, true);

        assertEquals(1000L, stakingInfo.get(node0Id).getStake());
        assertEquals(400L, stakingInfo.get(node0Id).getStakeToReward());
        assertEquals(500L, stakingInfo.get(node0Id).getStakeToNotReward());
    }

    @Test
    void findsOrAddsAccountAsExpected() {
        final var pendingChanges = buildPendingNodeStakeChanges();
        assertEquals(1, pendingChanges.size());

        var placeOfInsertion = subject.findOrAdd(partyId.getAccountNum(), pendingChanges);
        assertEquals(1, placeOfInsertion);
        assertEquals(partyId, pendingChanges.id(1));
        placeOfInsertion = subject.findOrAdd(counterpartyId.getAccountNum(), pendingChanges);
        assertEquals(0, placeOfInsertion);

        assertEquals(2, pendingChanges.size());
    }

    @Test
    void setsStakePeriodStart() {
        final long todayNum = 123456789L;

        final var accountsMap = new MerkleMap<EntityNum, MerkleAccount>();
        accountsMap.put(EntityNum.fromAccountId(counterpartyId), counterparty);
        accountsMap.put(EntityNum.fromAccountId(partyId), party);

        assertEquals(
                -1, accountsMap.get(EntityNum.fromAccountId(counterpartyId)).getStakePeriodStart());
        assertEquals(-1, accountsMap.get(EntityNum.fromAccountId(partyId)).getStakePeriodStart());

        subject = new StakeChangeManager(
                stakeInfoManager, () -> AccountStorageAdapter.fromInMemory(MerkleMapLike.from(accountsMap)));
        subject.initializeAllStakingStartsTo(todayNum);

        assertEquals(todayNum, counterparty.getStakePeriodStart());
        assertEquals(-1, party.getStakePeriodStart());
        assertEquals(
                todayNum,
                accountsMap.get(EntityNum.fromAccountId(counterpartyId)).getStakePeriodStart());
        assertEquals(-1, accountsMap.get(EntityNum.fromAccountId(partyId)).getStakePeriodStart());
    }

    public MerkleMap<EntityNum, MerkleStakingInfo> buildsStakingInfoMap() {
        given(bootstrapProperties.getLongProperty(LEDGER_TOTAL_TINY_BAR_FLOAT)).willReturn(2_000_000_000L);
        given(bootstrapProperties.getIntProperty(STAKING_REWARD_HISTORY_NUM_STORED_PERIODS))
                .willReturn(2);
        given(addressBook.getSize()).willReturn(2);
        given(addressBook.getNodeId(0)).willReturn(new NodeId(0));
        given(addressBook.getNodeId(1)).willReturn(new NodeId(1));

        final var info = buildStakingInfoMap(addressBook, bootstrapProperties);
        info.forEach((a, b) -> {
            b.setStakeToReward(300L);
            b.setStake(1000L);
            b.setStakeToNotReward(400L);
        });
        return info;
    }

    private final long partyBalance = 111L;
    private static final long counterpartyBalance = 555L;
    private final AccountID partyId = AccountID.newBuilder().setAccountNum(123).build();
    private static final AccountID counterpartyId =
            AccountID.newBuilder().setAccountNum(321).build();
    private final MerkleAccount party = MerkleAccountFactory.newAccount()
            .number(EntityNum.fromAccountId(partyId))
            .balance(partyBalance)
            .get();
    private static final MerkleAccount counterparty = MerkleAccountFactory.newAccount()
            .stakedId(-1)
            .number(EntityNum.fromAccountId(counterpartyId))
            .balance(counterpartyBalance)
            .get();
}
