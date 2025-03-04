/*
 * Copyright (C) 2024 Hedera Hashgraph, LLC
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

package com.hedera.node.app.service.schedule.impl.schemas;

import static com.hedera.node.app.service.schedule.impl.schemas.V0490ScheduleSchema.SCHEDULES_BY_EQUALITY_KEY;
import static com.hedera.node.app.service.schedule.impl.schemas.V0490ScheduleSchema.SCHEDULES_BY_EXPIRY_SEC_KEY;
import static com.hedera.node.app.service.schedule.impl.schemas.V0490ScheduleSchema.SCHEDULES_BY_ID_KEY;
import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.mockito.Mockito.mock;

import com.hedera.hapi.node.base.ScheduleID;
import com.hedera.hapi.node.base.SemanticVersion;
import com.hedera.hapi.node.state.primitives.ProtoBytes;
import com.hedera.hapi.node.state.primitives.ProtoLong;
import com.hedera.hapi.node.state.schedule.Schedule;
import com.hedera.hapi.node.state.schedule.ScheduleList;
import com.swirlds.state.lifecycle.MigrationContext;
import com.swirlds.state.lifecycle.StateDefinition;
import com.swirlds.state.spi.WritableStates;
import com.swirlds.state.test.fixtures.MapWritableKVState;
import com.swirlds.state.test.fixtures.MapWritableStates;
import java.util.Comparator;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.BDDMockito;

class V0490ScheduleSchemaTest {

    private V0490ScheduleSchema subject;

    @BeforeEach
    void setUp() {
        subject = new V0490ScheduleSchema();
    }

    @Test
    void constructorHappyPath() {
        // Instance created in setup
        Assertions.assertThat(subject.getVersion())
                .isEqualTo(
                        SemanticVersion.newBuilder().major(0).minor(49).patch(0).build());
    }

    @Test
    void statesToCreateIsCorrect() {
        var sortedResult = subject.statesToCreate().stream()
                .sorted(Comparator.comparing(StateDefinition::stateKey))
                .toList();

        final var stateDef1 = sortedResult.getFirst();
        Assertions.assertThat(stateDef1.stateKey()).isEqualTo(SCHEDULES_BY_EQUALITY_KEY);
        Assertions.assertThat(stateDef1.keyCodec()).isEqualTo(ProtoBytes.PROTOBUF);
        Assertions.assertThat(stateDef1.valueCodec()).isEqualTo(ScheduleList.PROTOBUF);
        final var stateDef2 = sortedResult.get(1);
        Assertions.assertThat(stateDef2.stateKey()).isEqualTo(SCHEDULES_BY_EXPIRY_SEC_KEY);
        Assertions.assertThat(stateDef2.keyCodec()).isEqualTo(ProtoLong.PROTOBUF);
        Assertions.assertThat(stateDef2.valueCodec()).isEqualTo(ScheduleList.PROTOBUF);
        final var stateDef3 = sortedResult.get(2);
        Assertions.assertThat(stateDef3.stateKey()).isEqualTo(SCHEDULES_BY_ID_KEY);
        Assertions.assertThat(stateDef3.keyCodec()).isEqualTo(ScheduleID.PROTOBUF);
        Assertions.assertThat(stateDef3.valueCodec()).isEqualTo(Schedule.PROTOBUF);
    }

    @Test
    void statesToRemoveIsEmpty() {
        Assertions.assertThat(subject.statesToRemove()).isEmpty();
    }

    @SuppressWarnings("DataFlowIssue")
    @Test
    void restartNullArgThrows() {
        Assertions.assertThatThrownBy(() -> subject.restart(null)).isInstanceOf(NullPointerException.class);
    }

    @Test
    void restartHappyPath() {
        Assertions.assertThatNoException().isThrownBy(() -> subject.restart(mock(MigrationContext.class)));
    }

    private WritableStates newEmptySchedulesWritableStates() {
        final var writableStates = MapWritableStates.builder()
                .state(new MapWritableKVState<>(SCHEDULES_BY_ID_KEY))
                .state(new MapWritableKVState<>(SCHEDULES_BY_EQUALITY_KEY))
                .state(new MapWritableKVState<>(SCHEDULES_BY_EXPIRY_SEC_KEY))
                .build();
        verifyEmptyById(writableStates);
        verifyEmptyByExpiry(writableStates);
        verifyEmptyByEquality(writableStates);

        return writableStates;
    }

    private MigrationContext newMockCtx(final WritableStates ws) {
        final var ctx = mock(MigrationContext.class);
        BDDMockito.given(ctx.newStates()).willReturn(ws);
        return ctx;
    }

    private void verifyEmptyById(final WritableStates actual) {
        verifyEmptyScheduleState(SCHEDULES_BY_ID_KEY, actual);
    }

    private void verifyEmptyByExpiry(final WritableStates actual) {
        verifyEmptyScheduleState(SCHEDULES_BY_EXPIRY_SEC_KEY, actual);
    }

    private void verifyEmptyByEquality(final WritableStates actual) {
        verifyEmptyScheduleState(SCHEDULES_BY_EQUALITY_KEY, actual);
    }

    private void verifyEmptyScheduleState(final String scheduleStateKey, final WritableStates actual) {
        assertThat(actual.get(scheduleStateKey).size()).isZero();
    }

    private void verifyNonEmptyById(final WritableStates actual) {
        verifyNonEmptyScheduleState(SCHEDULES_BY_ID_KEY, actual);
    }

    private void verifyNonEmptyByExpiry(final WritableStates actual) {
        verifyNonEmptyScheduleState(SCHEDULES_BY_EXPIRY_SEC_KEY, actual);
    }

    private void verifyNonEmptyByEquality(final WritableStates actual) {
        verifyNonEmptyScheduleState(SCHEDULES_BY_EQUALITY_KEY, actual);
    }

    private void verifyNonEmptyScheduleState(final String scheduleStateKey, final WritableStates actual) {
        // Note: we're not worried so much about a correct entity migration; just that the entity migration happened
        final var scheduleKey = actual.get(scheduleStateKey).keys().next();
        assertThat(scheduleKey).isNotNull();
        final var scheduleVal = actual.get(scheduleStateKey).get(scheduleKey);
        assertThat(scheduleVal).isNotNull();
        assertThat(actual.get(scheduleStateKey).size()).isEqualTo(1);
    }
}
