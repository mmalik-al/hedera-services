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

package com.swirlds.platform.wiring;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.Mockito.mock;

import com.swirlds.common.context.PlatformContext;
import com.swirlds.common.test.fixtures.platform.TestPlatformContextBuilder;
import com.swirlds.platform.StateSigner;
import com.swirlds.platform.builder.PlatformBuildingBlocks;
import com.swirlds.platform.builder.PlatformComponentBuilder;
import com.swirlds.platform.components.AppNotifier;
import com.swirlds.platform.components.EventWindowManager;
import com.swirlds.platform.components.SavedStateController;
import com.swirlds.platform.components.appcomm.LatestCompleteStateNotifier;
import com.swirlds.platform.components.consensus.ConsensusEngine;
import com.swirlds.platform.event.creation.EventCreationManager;
import com.swirlds.platform.event.deduplication.EventDeduplicator;
import com.swirlds.platform.event.hashing.EventHasher;
import com.swirlds.platform.event.linking.InOrderLinker;
import com.swirlds.platform.event.orphan.OrphanBuffer;
import com.swirlds.platform.event.preconsensus.PcesReplayer;
import com.swirlds.platform.event.preconsensus.PcesSequencer;
import com.swirlds.platform.event.preconsensus.PcesWriter;
import com.swirlds.platform.event.preconsensus.durability.RoundDurabilityBuffer;
import com.swirlds.platform.event.runninghash.RunningEventHasher;
import com.swirlds.platform.event.signing.SelfEventSigner;
import com.swirlds.platform.event.stream.ConsensusEventStream;
import com.swirlds.platform.event.validation.EventSignatureValidator;
import com.swirlds.platform.event.validation.InternalEventValidator;
import com.swirlds.platform.eventhandling.ConsensusRoundHandler;
import com.swirlds.platform.eventhandling.TransactionPrehandler;
import com.swirlds.platform.gossip.shadowgraph.Shadowgraph;
import com.swirlds.platform.publisher.PlatformPublisher;
import com.swirlds.platform.state.iss.IssDetector;
import com.swirlds.platform.state.iss.IssHandler;
import com.swirlds.platform.state.nexus.LatestCompleteStateNexus;
import com.swirlds.platform.state.nexus.SignedStateNexus;
import com.swirlds.platform.state.signed.SignedStateFileManager;
import com.swirlds.platform.state.signed.SignedStateHasher;
import com.swirlds.platform.state.signed.StateGarbageCollector;
import com.swirlds.platform.state.signed.StateSignatureCollector;
import com.swirlds.platform.system.events.BirthRoundMigrationShim;
import com.swirlds.platform.util.HashLogger;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link PlatformWiring}
 */
class PlatformWiringTests {
    @Test
    @DisplayName("Assert that all input wires are bound to something")
    void testBindings() {
        final PlatformContext platformContext =
                TestPlatformContextBuilder.create().build();

        final PlatformWiring wiring = new PlatformWiring(platformContext, true, true);

        final PlatformComponentBuilder componentBuilder =
                new PlatformComponentBuilder(mock(PlatformBuildingBlocks.class));

        componentBuilder
                .withEventHasher(mock(EventHasher.class))
                .withInternalEventValidator(mock(InternalEventValidator.class))
                .withEventDeduplicator(mock(EventDeduplicator.class))
                .withEventSignatureValidator(mock(EventSignatureValidator.class))
                .withStateGarbageCollector(mock(StateGarbageCollector.class))
                .withSelfEventSigner(mock(SelfEventSigner.class))
                .withOrphanBuffer(mock(OrphanBuffer.class))
                .withRunningEventHasher(mock(RunningEventHasher.class))
                .withEventCreationManager(mock(EventCreationManager.class))
                .withInOrderLinker(mock(InOrderLinker.class))
                .withConsensusEngine(mock(ConsensusEngine.class))
                .withConsensusEventStream(mock(ConsensusEventStream.class))
                .withPcesSequencer(mock(PcesSequencer.class))
                .withRoundDurabilityBuffer(mock(RoundDurabilityBuffer.class))
                .withTransactionPrehandler(mock(TransactionPrehandler.class));

        wiring.bind(
                componentBuilder,
                mock(SignedStateFileManager.class),
                mock(StateSigner.class),
                mock(PcesReplayer.class),
                mock(PcesWriter.class),
                mock(Shadowgraph.class),
                mock(StateSignatureCollector.class),
                mock(EventWindowManager.class),
                mock(ConsensusRoundHandler.class),
                mock(IssDetector.class),
                mock(IssHandler.class),
                mock(HashLogger.class),
                mock(BirthRoundMigrationShim.class),
                mock(LatestCompleteStateNotifier.class),
                mock(SignedStateNexus.class),
                mock(LatestCompleteStateNexus.class),
                mock(SavedStateController.class),
                mock(SignedStateHasher.class),
                mock(AppNotifier.class),
                mock(PlatformPublisher.class));

        assertFalse(wiring.getModel().checkForUnboundInputWires());
    }
}
