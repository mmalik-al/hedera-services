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

package com.hedera.node.app.service.mono.state.logic;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.BDDMockito.given;

import com.hedera.node.app.service.mono.state.migration.MigrationRecordsManager;
import com.hedera.node.app.service.mono.utils.replay.ReplayAssetRecording;
import java.util.function.BooleanSupplier;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ProcessLogicModuleTest {
    @Mock
    private StandardProcessLogic standardProcessLogic;

    @Mock
    private MigrationRecordsManager migrationRecordsManager;

    @Mock
    private BooleanSupplier isRecordingFacilityMocks;

    @Mock
    private ReplayAssetRecording assetRecording;

    @Test
    void recordTxnsIfRecordingFacilityMocks() {
        given(isRecordingFacilityMocks.getAsBoolean()).willReturn(true);

        final var logic =
                ProcessLogicModule.provideProcessLogic(assetRecording, standardProcessLogic, isRecordingFacilityMocks);

        assertInstanceOf(RecordingProcessLogic.class, logic);
    }

    @Test
    void usesStandardLogicIfNotRecordingFacilityMocks() {
        final var logic =
                ProcessLogicModule.provideProcessLogic(assetRecording, standardProcessLogic, isRecordingFacilityMocks);

        assertInstanceOf(StandardProcessLogic.class, logic);
    }

    @Test
    void usesStandardMigrationManagerIfNotRecordingFacilityMocks() {
        final var manager =
                ProcessLogicModule.provideMigrationRecordsManager(
                        assetRecording, migrationRecordsManager, isRecordingFacilityMocks);

        assertInstanceOf(MigrationRecordsManager.class, manager);
    }
}
