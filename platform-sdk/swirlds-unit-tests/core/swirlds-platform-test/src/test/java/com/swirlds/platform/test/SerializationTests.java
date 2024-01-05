/*
 * Copyright (C) 2020-2024 Hedera Hashgraph, LLC
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

package com.swirlds.platform.test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.params.provider.Arguments.arguments;

import com.swirlds.common.constructable.ConstructableRegistry;
import com.swirlds.common.constructable.ConstructableRegistryException;
import com.swirlds.common.io.SelfSerializable;
import com.swirlds.common.platform.NodeId;
import com.swirlds.common.test.fixtures.TransactionUtils;
import com.swirlds.common.test.fixtures.io.SerializationUtils;
import com.swirlds.platform.test.fixtures.event.RandomEventUtils;
import com.swirlds.test.framework.TestTypeTags;
import com.swirlds.test.framework.config.TestConfigBuilder;
import java.io.IOException;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

public class SerializationTests {

    @BeforeAll
    public static void setup() throws ConstructableRegistryException {
        new TestConfigBuilder().withValue("transactionMaxBytes", 1_000_000).getOrCreateConfig();

        ConstructableRegistry.getInstance().registerConstructables("com.swirlds");
    }

    @ParameterizedTest
    @Tag(TestTypeTags.FUNCTIONAL)
    @DisplayName("Serialize then deserialize SelfSerializable class")
    @MethodSource("selfSerializableProvider")
    public <T extends SelfSerializable> void serializeDeserializeTest(T generated) throws IOException {
        T serDes = SerializationUtils.serializeDeserialize(generated);
        assertEquals(generated, serDes);
    }

    static Stream<Arguments> selfSerializableProvider() {
        return Stream.of(arguments(
                RandomEventUtils.randomEventHashedData(
                        68164523688792345L,
                        new NodeId(0),
                        RandomEventUtils.DEFAULT_FIRST_EVENT_TIME_CREATED,
                        TransactionUtils.randomSwirldTransactions(1234321, 10),
                        null,
                        null),
                RandomEventUtils.randomEventHashedData(
                        68164523688792345L,
                        new NodeId(0),
                        RandomEventUtils.DEFAULT_FIRST_EVENT_TIME_CREATED,
                        null,
                        null,
                        null)));
    }
}
