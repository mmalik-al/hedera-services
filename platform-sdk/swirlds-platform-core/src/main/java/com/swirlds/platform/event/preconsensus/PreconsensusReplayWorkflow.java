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

package com.swirlds.platform.event.preconsensus;

import static com.swirlds.common.formatting.StringFormattingUtils.commaSeparatedNumber;
import static com.swirlds.common.system.PlatformStatus.READY;
import static com.swirlds.common.system.PlatformStatus.REPLAYING_EVENTS;
import static com.swirlds.common.system.PlatformStatus.STARTING_UP;
import static com.swirlds.common.units.TimeUnit.UNIT_MILLISECONDS;
import static com.swirlds.logging.LogMarker.EXCEPTION;
import static com.swirlds.logging.LogMarker.PLATFORM_STATUS;
import static com.swirlds.logging.LogMarker.STARTUP;

import com.swirlds.common.context.PlatformContext;
import com.swirlds.common.formatting.UnitFormatter;
import com.swirlds.common.io.IOIterator;
import com.swirlds.common.system.PlatformStatus;
import com.swirlds.common.threading.framework.QueueThread;
import com.swirlds.common.threading.manager.ThreadManager;
import com.swirlds.common.time.Time;
import com.swirlds.logging.payloads.PlatformStatusPayload;
import com.swirlds.platform.components.EventIntake;
import com.swirlds.platform.components.state.StateManagementComponent;
import com.swirlds.platform.event.EventIntakeTask;
import com.swirlds.platform.event.GossipEvent;
import com.swirlds.platform.eventhandling.ConsensusRoundHandler;
import com.swirlds.platform.gossip.shadowgraph.ShadowGraph;
import com.swirlds.platform.internal.EventImpl;
import com.swirlds.platform.state.signed.ReservedSignedState;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

// TODO test

/**
 * This class encapsulates the logic for replaying preconsensus events at boot up time.
 */
public final class PreconsensusReplayWorkflow {

    private static final Logger logger = LogManager.getLogger(PreconsensusReplayWorkflow.class);

    private PreconsensusReplayWorkflow() {}

    /**
     * Replays preconsensus events from disk.
     *
     * @param platformContext                    the platform context for this node
     * @param threadManager                      the thread manager for this node
     * @param preConsensusEventFileManager       manages the preconsensus event files on disk
     * @param preConsensusEventWriter            writes preconsensus events to disk
     * @param shadowGraph                        the shadow graph, used by gossip to track the current events
     * @param eventIntake                        the event intake component where events from the stream are fed
     * @param intakeQueue                        the queue thread for the event intake component
     * @param consensusRoundHandler              the object responsible for applying transactions to consensus rounds
     * @param stateManagementComponent           manages various copies of the state
     * @param currentPlatformStatus              a pointer to the current platform status
     * @param initialMinimumGenerationNonAncient the minimum generation of events to replay
     */
    public static void replayPreconsensusEvents(
            @NonNull final PlatformContext platformContext,
            @NonNull final ThreadManager threadManager,
            @NonNull final Time time,
            @NonNull final PreConsensusEventFileManager preConsensusEventFileManager,
            @NonNull final PreConsensusEventWriter preConsensusEventWriter,
            @NonNull final ShadowGraph shadowGraph,
            @NonNull final EventIntake eventIntake,
            @NonNull final QueueThread<EventIntakeTask> intakeQueue,
            @NonNull final ConsensusRoundHandler consensusRoundHandler,
            @NonNull final StateManagementComponent stateManagementComponent,
            @NonNull final AtomicReference<PlatformStatus> currentPlatformStatus,
            final long initialMinimumGenerationNonAncient) {

        Objects.requireNonNull(platformContext);
        Objects.requireNonNull(time);
        Objects.requireNonNull(preConsensusEventFileManager);
        Objects.requireNonNull(preConsensusEventWriter);
        Objects.requireNonNull(shadowGraph);
        Objects.requireNonNull(eventIntake);
        Objects.requireNonNull(intakeQueue);
        Objects.requireNonNull(consensusRoundHandler);
        Objects.requireNonNull(stateManagementComponent);
        Objects.requireNonNull(currentPlatformStatus);

        setupReplayStatus(currentPlatformStatus);

        logger.info(
                STARTUP.getMarker(),
                "replaying preconsensus event stream starting at generation {}",
                initialMinimumGenerationNonAncient);

        try {
            final Instant start = time.now();

            final IOIterator<EventImpl> iterator =
                    preConsensusEventFileManager.getEventIterator(initialMinimumGenerationNonAncient);

            final EventReplayPipeline eventReplayPipeline =
                    new EventReplayPipeline(platformContext, threadManager, iterator, (EventImpl e) -> {
                        if (shadowGraph.isHashInGraph(e.getBaseEventHashedData().getHash())) {
                            // the shadowgraph doesn't deal with duplicate events well, filter them out here
                            return;
                        }
                        eventIntake.addUnlinkedEvent(new GossipEvent(e.getHashedData(), e.getUnhashedData()));
                    });
            eventReplayPipeline.replayEvents();

            waitForReplayToComplete(intakeQueue, consensusRoundHandler);

            final Instant finish = time.now();
            final Duration elapsed = Duration.between(start, finish);

            logReplayInfo(
                    stateManagementComponent,
                    eventReplayPipeline.getEventCount(),
                    eventReplayPipeline.getTransactionCount(),
                    elapsed);

            preConsensusEventWriter.beginStreamingNewEvents();

        } catch (final InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("interrupted while replaying preconsensus event stream", e);
        }

        setupEndOfReplayStatus(currentPlatformStatus);
    }

    /**
     * Update the platform status for PCES replay.
     */
    private static void setupReplayStatus(@NonNull final AtomicReference<PlatformStatus> currentPlatformStatus) {
        // Sanity check for platform status can be removed after we clean up platform status management
        if (currentPlatformStatus.get() != STARTING_UP) {
            throw new IllegalStateException(
                    "Platform status should be STARTING_UP, current status is " + currentPlatformStatus.get());
        }

        currentPlatformStatus.set(REPLAYING_EVENTS);
        logger.info(PLATFORM_STATUS.getMarker(), () -> new PlatformStatusPayload(
                        "Platform status changed.", STARTING_UP.name(), REPLAYING_EVENTS.name())
                .toString());
    }

    /**
     * Update the platform status to indicate that PCES replay has completed.\
     */
    private static void setupEndOfReplayStatus(@NonNull final AtomicReference<PlatformStatus> currentPlatformStatus) {
        // Sanity check for platform status can be removed after we clean up platform status management
        if (currentPlatformStatus.get() != REPLAYING_EVENTS) {
            throw new IllegalStateException(
                    "Platform status should be REPLAYING_EVENTS, current status is " + currentPlatformStatus.get());
        }
        currentPlatformStatus.set(READY);
        logger.info(PLATFORM_STATUS.getMarker(), () -> new PlatformStatusPayload(
                        "Platform status changed.", REPLAYING_EVENTS.name(), READY.name())
                .toString());
    }

    /**
     * Wait for all events to be replied. Some of this work happens on asynchronous threads, so we need to wait for them
     * to complete even after we exhaust all available events from the stream.
     */
    private static void waitForReplayToComplete(
            @NonNull final QueueThread<EventIntakeTask> intakeQueue,
            @NonNull final ConsensusRoundHandler consensusRoundHandler)
            throws InterruptedException {

        // Wait until all events from the preconsensus event stream have been fully ingested.
        intakeQueue.waitUntilNotBusy();

        // Wait until all rounds from the preconsensus event stream have been fully processed.
        consensusRoundHandler.waitUntilNotBusy();

        // TODO are there other queues we need to wait on? (Talk to Lazar)
    }

    /**
     * Write information about the replay to disk.
     */
    private static void logReplayInfo(
            @NonNull final StateManagementComponent stateManagementComponent,
            final long eventCount,
            final long transactionCount,
            @NonNull final Duration elapsedTime) {

        try (final ReservedSignedState latestConsensusRound =
                stateManagementComponent.getLatestImmutableState("SwirldsPlatform.replayPreconsensusEventStream()")) {

            if (latestConsensusRound.isNull()) {
                logger.info(
                        STARTUP.getMarker(),
                        "Replayed {} preconsensus events. No rounds reached consensus.",
                        commaSeparatedNumber(eventCount));
                return;
            }

            final Instant firstTimestamp = stateManagementComponent.getFirstStateTimestamp();
            final long firstRound = stateManagementComponent.getFirstStateRound();

            if (firstTimestamp == null) {
                // This should be impossible. If we have a state, we should have a timestamp.
                logger.error(
                        EXCEPTION.getMarker(),
                        "Replayed {} preconsensus events. "
                                + "First state timestamp is null, which should not be possible if a "
                                + "round has reached consensus",
                        commaSeparatedNumber(eventCount));
                return;
            }

            final long latestRound = latestConsensusRound.get().getRound();
            final long elapsedRounds = latestRound - firstRound;

            final Instant latestRoundTimestamp = latestConsensusRound.get().getConsensusTimestamp();
            final Duration elapsedConsensusTime = Duration.between(firstTimestamp, latestRoundTimestamp);

            logger.info(
                    STARTUP.getMarker(),
                    "replayed {} preconsensus events. These events contained {} transactions. "
                            + "{} rounds reached consensus spanning {} of consensus time. The latest "
                            + "round to reach consensus is round {}. Replay took {}.",
                    commaSeparatedNumber(eventCount),
                    commaSeparatedNumber(transactionCount),
                    commaSeparatedNumber(elapsedRounds),
                    new UnitFormatter(elapsedConsensusTime.toMillis(), UNIT_MILLISECONDS).setAbbreviate(false),
                    commaSeparatedNumber(latestRound),
                    new UnitFormatter(elapsedTime.toMillis(), UNIT_MILLISECONDS).setAbbreviate(false));
        }
    }
}
