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

package com.swirlds.platform.gossip.shadowgraph;

import static com.swirlds.logging.legacy.LogMarker.SYNC_INFO;
import static com.swirlds.platform.gossip.shadowgraph.SyncUtils.filterLikelyDuplicates;
import static com.swirlds.platform.gossip.shadowgraph.SyncUtils.getMyTipsTheyKnow;
import static com.swirlds.platform.gossip.shadowgraph.SyncUtils.getTheirTipsIHave;
import static com.swirlds.platform.gossip.shadowgraph.SyncUtils.readEventsINeed;
import static com.swirlds.platform.gossip.shadowgraph.SyncUtils.readMyTipsTheyHave;
import static com.swirlds.platform.gossip.shadowgraph.SyncUtils.readTheirTipsAndGenerations;
import static com.swirlds.platform.gossip.shadowgraph.SyncUtils.sendEventsTheyNeed;
import static com.swirlds.platform.gossip.shadowgraph.SyncUtils.writeFirstByte;
import static com.swirlds.platform.gossip.shadowgraph.SyncUtils.writeMyTipsAndGenerations;
import static com.swirlds.platform.gossip.shadowgraph.SyncUtils.writeTheirTipsIHave;

import com.swirlds.base.time.Time;
import com.swirlds.common.context.PlatformContext;
import com.swirlds.common.crypto.Cryptography;
import com.swirlds.common.system.NodeId;
import com.swirlds.common.threading.framework.QueueThread;
import com.swirlds.common.threading.interrupt.InterruptableRunnable;
import com.swirlds.common.threading.pool.ParallelExecutionException;
import com.swirlds.common.threading.pool.ParallelExecutor;
import com.swirlds.platform.consensus.GraphGenerations;
import com.swirlds.platform.event.GossipEvent;
import com.swirlds.platform.gossip.FallenBehindManager;
import com.swirlds.platform.gossip.IntakeEventCounter;
import com.swirlds.platform.gossip.SyncException;
import com.swirlds.platform.gossip.sync.config.SyncConfig;
import com.swirlds.platform.internal.EventImpl;
import com.swirlds.platform.metrics.SyncMetrics;
import com.swirlds.platform.network.Connection;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import java.io.IOException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * The goal of the ShadowGraphSynchronizer is to compare graphs with a remote node, and update them so both sides have
 * the same events in the graph. This process is called a sync.
 * <p>
 * This instance can be called by multiple threads at the same time. To avoid accidental concurrency issues, all the
 * variables in this class are final. The ones that are used for storing information about an ongoing sync are method
 * local.
 */
public class ShadowGraphSynchronizer {

    private static final Logger logger = LogManager.getLogger();

    /**
     * The shadow graph manager to use for this sync
     */
    private final ShadowGraph shadowGraph;
    /**
     * Number of member nodes in the network for this sync
     */
    private final int numberOfNodes;
    /**
     * All sync stats
     */
    private final SyncMetrics syncMetrics;
    /**
     * provides the current consensus instance, a supplier is used because this instance will change after a reconnect,
     * so we have to make sure we always get the latest one
     */
    private final Supplier<GraphGenerations> generationsSupplier;
    /**
     * consumes events received by the peer
     */
    private final Consumer<GossipEvent> eventHandler;
    /**
     * manages sync related decisions
     */
    private final FallenBehindManager fallenBehindManager;

    /**
     * Keeps track of how many events from each peer have been received, but haven't yet made it through the intake
     * pipeline
     */
    private final IntakeEventCounter intakeEventCounter;

    /**
     * executes tasks in parallel
     */
    private final ParallelExecutor executor;
    /**
     * if set to true, send and receive initial negotiation bytes at the start of the sync
     */
    private final boolean sendRecInitBytes;
    /**
     * executed before fetching the tips from the shadowgraph for the second time in phase 3
     */
    private final InterruptableRunnable executePreFetchTips;

    private final Time time;

    /**
     * If true then we do not send all events during a sync that the peer says we need. Instead, we send events that we
     * know are unlikely to be duplicates (e.g. self events), and only send other events if we have had them for a long
     * time and the peer still needs them.
     */
    private final boolean filterLikelyDuplicates;

    /**
     * For events that are neither self events nor ancestors of self events, we must have had this event for at least
     * this amount of time before it is eligible to be sent. Ignored if {@link #filterLikelyDuplicates} is false.
     */
    private final Duration nonAncestorFilterThreshold;

    public ShadowGraphSynchronizer(
            @NonNull final PlatformContext platformContext,
            @NonNull final Time time,
            @NonNull final ShadowGraph shadowGraph,
            final int numberOfNodes,
            @NonNull final SyncMetrics syncMetrics,
            @NonNull final Supplier<GraphGenerations> generationsSupplier,
            @NonNull final QueueThread<GossipEvent> intakeQueue,
            @NonNull final FallenBehindManager fallenBehindManager,
            @NonNull final IntakeEventCounter intakeEventCounter,
            @NonNull final ParallelExecutor executor,
            final boolean sendRecInitBytes,
            @NonNull final InterruptableRunnable executePreFetchTips) {

        Objects.requireNonNull(platformContext);
        Objects.requireNonNull(intakeQueue);

        this.time = Objects.requireNonNull(time);
        this.shadowGraph = Objects.requireNonNull(shadowGraph);
        this.numberOfNodes = numberOfNodes;
        this.syncMetrics = Objects.requireNonNull(syncMetrics);
        this.generationsSupplier = Objects.requireNonNull(generationsSupplier);
        this.fallenBehindManager = Objects.requireNonNull(fallenBehindManager);
        this.intakeEventCounter = Objects.requireNonNull(intakeEventCounter);
        this.executor = Objects.requireNonNull(executor);
        this.sendRecInitBytes = sendRecInitBytes;
        this.executePreFetchTips = Objects.requireNonNull(executePreFetchTips);
        this.eventHandler = buildEventHandler(platformContext, intakeQueue);

        final SyncConfig syncConfig = platformContext.getConfiguration().getConfigData(SyncConfig.class);
        this.filterLikelyDuplicates = syncConfig.filterLikelyDuplicates();
        this.nonAncestorFilterThreshold = syncConfig.nonAncestorFilterThreshold();
    }

    /**
     * Construct the event handler for new events. If configured to do so, this handler will also hash events before
     * passing them down the pipeline.
     *
     * @param platformContext the platform context
     * @param intakeQueue     the event intake queue
     */
    @NonNull
    private Consumer<GossipEvent> buildEventHandler(
            @NonNull final PlatformContext platformContext, @NonNull final QueueThread<GossipEvent> intakeQueue) {

        Objects.requireNonNull(intakeQueue);

        final boolean hashOnGossipThreads = platformContext
                .getConfiguration()
                .getConfigData(SyncConfig.class)
                .hashOnGossipThreads();

        final Consumer<GossipEvent> wrappedPut = event -> {
            try {
                intakeQueue.put(event);
            } catch (final InterruptedException e) {
                // should never happen, and we don't have a simple way of recovering from it
                Thread.currentThread().interrupt();
            }
        };

        if (hashOnGossipThreads) {
            final Cryptography cryptography = platformContext.getCryptography();
            return event -> {
                cryptography.digestSync(event.getHashedData());
                event.buildDescriptor();

                wrappedPut.accept(event);
            };
        } else {
            return wrappedPut;
        }
    }

    /**
     * Executes a sync using the supplied connection.
     *
     * @param platformContext the platform context
     * @param connection      the connection to use
     * @return true if the sync was successful, false if it was aborted
     */
    public boolean synchronize(@NonNull final PlatformContext platformContext, @NonNull final Connection connection)
            throws IOException, ParallelExecutionException, SyncException, InterruptedException {
        logger.info(SYNC_INFO.getMarker(), "{} sync start", connection.getDescription());
        try {
            return reserveSynchronize(platformContext, connection);
        } finally {
            logger.info(SYNC_INFO.getMarker(), "{} sync end", connection.getDescription());
        }
    }

    /**
     * Executes a sync using the supplied connection.
     *
     * @param platformContext the platform context
     * @param connection      the connection to use
     * @return true if the sync was successful, false if it was aborted
     */
    private boolean reserveSynchronize(
            @NonNull final PlatformContext platformContext, @NonNull final Connection connection)
            throws IOException, ParallelExecutionException, SyncException, InterruptedException {

        // accumulates time points for each step in the execution of a single gossip session, used for stats
        // reporting and performance analysis
        final SyncTiming timing = new SyncTiming();
        final List<EventImpl> sendList;
        try (final GenerationReservation reservation = shadowGraph.reserve()) {
            connection.initForSync();

            timing.start();

            if (sendRecInitBytes) {
                writeFirstByte(connection);
            }

            // Step 1: each peer tells the other about its tips and generations

            // the generation we reserved is our minimum round generation
            // the ShadowGraph guarantees it won't be expired until we release it
            final Generations myGenerations = getGenerations(reservation.getGeneration());
            final List<ShadowEvent> myTips = getTips();
            // READ and WRITE generation numbers & tip hashes
            final TheirTipsAndGenerations theirTipsAndGenerations = readWriteParallel(
                    readTheirTipsAndGenerations(connection, numberOfNodes, sendRecInitBytes),
                    writeMyTipsAndGenerations(connection, myGenerations, myTips),
                    connection);
            timing.setTimePoint(1);

            if (theirTipsAndGenerations.isSyncRejected()) {
                logger.info(SYNC_INFO.getMarker(), "{} sync rejected by other", connection.getDescription());
                return false;
            }

            syncMetrics.generations(myGenerations, theirTipsAndGenerations.getGenerations());

            if (fallenBehind(myGenerations, theirTipsAndGenerations.getGenerations(), connection)) {
                // aborting the sync since someone has fallen behind
                return false;
            }

            // events that I know they already have
            final Set<ShadowEvent> eventsTheyHave = new HashSet<>();

            // process the hashes received
            final List<ShadowEvent> theirTips = shadowGraph.shadows(theirTipsAndGenerations.getTips());

            // For each tip they send us, determine if we have that event.
            // For each tip, send true if we have the event and false if we don't.
            final List<Boolean> theirTipsIHave = getTheirTipsIHave(theirTips);

            // Add their tips to the set of events they are known to have
            theirTips.stream().filter(Objects::nonNull).forEach(eventsTheyHave::add);

            // Step 2: each peer tells the other which of the other's tips it already has.

            timing.setTimePoint(2);
            final List<Boolean> theirBooleans = readWriteParallel(
                    readMyTipsTheyHave(connection, myTips.size()),
                    writeTheirTipsIHave(connection, theirTipsIHave),
                    connection);
            timing.setTimePoint(3);

            // Add each tip they know to the known set
            final List<ShadowEvent> knownTips = getMyTipsTheyKnow(connection, myTips, theirBooleans);
            eventsTheyHave.addAll(knownTips);

            // create a send list based on the known set
            sendList = createSendList(
                    connection.getSelfId(), eventsTheyHave, myGenerations, theirTipsAndGenerations.getGenerations());
        }

        final SyncConfig syncConfig = platformContext.getConfiguration().getConfigData(SyncConfig.class);

        return sendAndReceiveEvents(
                connection, timing, sendList, syncConfig.syncKeepalivePeriod(), syncConfig.maxSyncTime());
    }

    @NonNull
    private Generations getGenerations(final long minRoundGen) {
        return new Generations(
                minRoundGen,
                generationsSupplier.get().getMinGenerationNonAncient(),
                generationsSupplier.get().getMaxRoundGeneration());
    }

    @NonNull
    private List<ShadowEvent> getTips() {
        final List<ShadowEvent> myTips = shadowGraph.getTips();
        syncMetrics.updateTipsPerSync(myTips.size());
        syncMetrics.updateMultiTipsPerSync(SyncUtils.computeMultiTipCount(myTips));
        return myTips;
    }

    /**
     * Decide if we have fallen behind with respect to this peer.
     *
     * @param self       our generations
     * @param other      their generations
     * @param connection the connection to use
     * @return true if we have fallen behind, false otherwise
     */
    private boolean fallenBehind(
            @NonNull final Generations self, @NonNull final Generations other, @NonNull final Connection connection) {
        Objects.requireNonNull(self);
        Objects.requireNonNull(other);
        Objects.requireNonNull(connection);

        final SyncFallenBehindStatus status = SyncFallenBehindStatus.getStatus(self, other);
        if (status == SyncFallenBehindStatus.SELF_FALLEN_BEHIND) {
            fallenBehindManager.reportFallenBehind(connection.getOtherId());
        }

        if (status != SyncFallenBehindStatus.NONE_FALLEN_BEHIND) {
            logger.info(SYNC_INFO.getMarker(), "{} aborting sync due to {}", connection.getDescription(), status);
            return true; // abort the sync
        }
        return false;
    }

    /**
     * Create a list of events to send to the peer.
     *
     * @param selfId           the id of this node
     * @param knownSet         the set of events that the peer already has (this is incomplete at this stage and is
     *                         added to during this method)
     * @param myGenerations    the generations of this node
     * @param theirGenerations the generations of the peer
     * @return a list of events to send to the peer
     */
    @NonNull
    private List<EventImpl> createSendList(
            @NonNull final NodeId selfId,
            @NonNull final Set<ShadowEvent> knownSet,
            @NonNull final Generations myGenerations,
            @NonNull final Generations theirGenerations)
            throws InterruptedException {

        Objects.requireNonNull(selfId);
        Objects.requireNonNull(knownSet);
        Objects.requireNonNull(myGenerations);
        Objects.requireNonNull(theirGenerations);

        // add to knownSet all the ancestors of each known event
        final Set<ShadowEvent> knownAncestors = shadowGraph.findAncestors(
                knownSet, SyncUtils.unknownNonAncient(knownSet, myGenerations, theirGenerations));

        // since knownAncestors is a lot bigger than knownSet, it is a lot cheaper to add knownSet to knownAncestors
        // then vice versa
        knownAncestors.addAll(knownSet);

        syncMetrics.knownSetSize(knownAncestors.size());

        // predicate used to search for events to send
        final Predicate<ShadowEvent> knownAncestorsPredicate =
                SyncUtils.unknownNonAncient(knownAncestors, myGenerations, theirGenerations);

        executePreFetchTips.run();
        // in order to get the peer the latest events, we get a new set of tips to search from
        final List<ShadowEvent> myNewTips = shadowGraph.getTips();

        // find all ancestors of tips that are not known
        final List<ShadowEvent> unknownTips =
                myNewTips.stream().filter(knownAncestorsPredicate).collect(Collectors.toList());
        final Set<ShadowEvent> sendSet = shadowGraph.findAncestors(unknownTips, knownAncestorsPredicate);
        // add the tips themselves
        sendSet.addAll(unknownTips);

        final List<EventImpl> eventsTheyMayNeed =
                sendSet.stream().map(ShadowEvent::getEvent).collect(Collectors.toCollection(ArrayList::new));

        final List<EventImpl> sendList;
        if (filterLikelyDuplicates) {
            sendList = filterLikelyDuplicates(
                    shadowGraph, selfId, nonAncestorFilterThreshold, time.now(), eventsTheyMayNeed);
        } else {
            sendList = eventsTheyMayNeed;
        }

        SyncUtils.sort(sendList);

        return sendList;
    }

    /**
     * By this point in time, we have figured out which events we want to send the peer, and the peer has figured out
     * which events it wants to send us. In parallel, send and receive those events.
     *
     * @param connection          the connection to use
     * @param timing              metrics that track sync timing
     * @param sendList            the events to send
     * @param syncKeepAlivePeriod the period at which the reading thread should send keepalive messages
     * @param maxSyncTime         the maximum amount of time to spend syncing with a peer, syncs that take longer than
     *                            this will be aborted
     * @return true if the phase was successful, false if it was aborted
     * @throws ParallelExecutionException if anything goes wrong
     */
    private boolean sendAndReceiveEvents(
            @NonNull final Connection connection,
            @NonNull final SyncTiming timing,
            @NonNull final List<EventImpl> sendList,
            @NonNull final Duration syncKeepAlivePeriod,
            @NonNull final Duration maxSyncTime)
            throws ParallelExecutionException {

        Objects.requireNonNull(connection);
        Objects.requireNonNull(sendList);

        timing.setTimePoint(4);
        // the reading thread uses this to indicate to the writing thread that it is done
        final CountDownLatch eventReadingDone = new CountDownLatch(1);
        // the writer will set it to true if writing is aborted
        final AtomicBoolean writeAborted = new AtomicBoolean(false);
        final Integer eventsRead = readWriteParallel(
                readEventsINeed(
                        connection, eventHandler, syncMetrics, eventReadingDone, intakeEventCounter, maxSyncTime),
                sendEventsTheyNeed(connection, sendList, eventReadingDone, writeAborted, syncKeepAlivePeriod),
                connection);
        if (eventsRead < 0 || writeAborted.get()) {
            // sync was aborted
            logger.info(SYNC_INFO.getMarker(), "{} sync aborted", connection::getDescription);
            return false;
        }
        logger.info(
                SYNC_INFO.getMarker(),
                "{} writing events done, wrote {} events",
                connection::getDescription,
                sendList::size);
        logger.info(
                SYNC_INFO.getMarker(),
                "{} reading events done, read {} events",
                connection.getDescription(),
                eventsRead);

        syncMetrics.syncDone(
                new SyncResult(connection.isOutbound(), connection.getOtherId(), eventsRead, sendList.size()));

        timing.setTimePoint(5);
        syncMetrics.recordSyncTiming(timing, connection);
        return true;
    }

    /**
     * A method to do reads and writes in parallel.
     * <p>
     * It is very important that the read task is executed by the caller thread. The reader thread can always time out,
     * if the writer thread gets blocked by a write method because the buffer is full, the only way to unblock it is to
     * close the connection. So the reader will close the connection and unblock the writer if it times out or if
     * anything goes wrong.
     *
     * @param readTask   read task
     * @param writeTask  write task
     * @param connection the connection to close if anything goes wrong
     * @param <T>        the return type of the read task and this method
     * @return whatever the read task returns
     * @throws ParallelExecutionException thrown if anything goes wrong during these read write operations. the
     *                                    connection will be closed before this exception is thrown
     */
    @Nullable
    private <T> T readWriteParallel(
            @NonNull final Callable<T> readTask,
            @NonNull final Callable<Void> writeTask,
            @NonNull final Connection connection)
            throws ParallelExecutionException {

        Objects.requireNonNull(readTask);
        Objects.requireNonNull(writeTask);
        Objects.requireNonNull(connection);

        return executor.doParallel(readTask, writeTask, connection::disconnect);
    }

    /**
     * Reject a sync
     *
     * @param connection the connection over which the sync was initiated
     * @throws IOException if there are any connection issues
     */
    public void rejectSync(@NonNull final Connection connection) throws IOException {
        Objects.requireNonNull(connection);
        try {
            connection.initForSync();
            SyncUtils.rejectSync(connection, numberOfNodes);
        } finally {
            logger.info(SYNC_INFO.getMarker(), "{} sync rejected by self", connection.getDescription());
        }
    }
}
