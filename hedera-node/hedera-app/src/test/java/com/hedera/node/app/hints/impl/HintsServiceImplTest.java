/*
 * Copyright (C) 2025 Hedera Hashgraph, LLC
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

package com.hedera.node.app.hints.impl;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.hedera.node.app.hints.HintsService;
import com.hedera.node.app.hints.WritableHintsStore;
import com.hedera.node.app.roster.ActiveRosters;
import com.hedera.node.app.spi.AppContext;
import com.hedera.node.config.data.TssConfig;
import com.hedera.pbj.runtime.io.buffer.Bytes;
import com.swirlds.state.lifecycle.SchemaRegistry;
import java.time.Instant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class HintsServiceImplTest {
    private static final Instant CONSENSUS_NOW = Instant.ofEpochSecond(1_234_567L, 890);

    @Mock
    private AppContext appContext;

    @Mock
    private TssConfig tssConfig;

    @Mock
    private ActiveRosters activeRosters;

    @Mock
    private WritableHintsStore hintsStore;

    @Mock
    private SchemaRegistry schemaRegistry;

    private HintsServiceImpl subject;

    @BeforeEach
    void setUp() {
        subject = new HintsServiceImpl(appContext);
    }

    @Test
    void metadataAsExpected() {
        assertEquals(HintsService.NAME, subject.getServiceName());
        assertEquals(HintsService.MIGRATION_ORDER, subject.migrationOrder());
    }

    @Test
    void nothingSupportedExceptRegisteringSchemas() {
        assertThrows(
                UnsupportedOperationException.class,
                () -> subject.reconcile(activeRosters, hintsStore, CONSENSUS_NOW, tssConfig));
        assertThrows(UnsupportedOperationException.class, subject::isReady);
        assertThrows(UnsupportedOperationException.class, subject::currentVerificationKeyOrThrow);
        assertThrows(UnsupportedOperationException.class, () -> subject.signFuture(Bytes.EMPTY));
        assertDoesNotThrow(() -> subject.registerSchemas(schemaRegistry));
    }
}
