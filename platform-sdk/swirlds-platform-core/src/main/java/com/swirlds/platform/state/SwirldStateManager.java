/*
 * Copyright (C) 2024-2025 Hedera Hashgraph, LLC
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

package com.swirlds.platform.state;

import static com.swirlds.platform.components.transaction.system.SystemTransactionExtractionUtils.extractFromRound;
import static com.swirlds.platform.state.SwirldStateManagerUtils.fastCopy;
import static java.util.Objects.requireNonNull;

import com.hedera.hapi.node.state.roster.Roster;
import com.hedera.hapi.platform.event.StateSignatureTransaction;
import com.swirlds.common.context.PlatformContext;
import com.swirlds.common.platform.NodeId;
import com.swirlds.platform.FreezePeriodChecker;
import com.swirlds.platform.components.transaction.system.ScopedSystemTransaction;
import com.swirlds.platform.internal.ConsensusRound;
import com.swirlds.platform.metrics.StateMetrics;
import com.swirlds.platform.state.signed.SignedState;
import com.swirlds.platform.system.Round;
import com.swirlds.platform.system.SoftwareVersion;
import com.swirlds.platform.system.status.StatusActionSubmitter;
import com.swirlds.platform.uptime.UptimeTracker;
import com.swirlds.state.merkle.MerkleStateRoot;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

/**
 * Manages all interactions with the state object required by {@link StateLifecycles}.
 */
public class SwirldStateManager implements FreezePeriodChecker {

    /**
     * Stats relevant to the state operations.
     */
    private final StateMetrics stats;

    /**
     * reference to the state that reflects all known consensus transactions
     */
    private final AtomicReference<PlatformMerkleStateRoot> stateRef = new AtomicReference<>();

    /**
     * The most recent immutable state. No value until the first fast copy is created.
     */
    private final AtomicReference<PlatformMerkleStateRoot> latestImmutableState = new AtomicReference<>();

    /**
     * Handle transactions by applying them to a state
     */
    private final TransactionHandler transactionHandler;

    /**
     * Tracks and reports node uptime.
     */
    private final UptimeTracker uptimeTracker;

    /**
     * The current software version.
     */
    private final SoftwareVersion softwareVersion;

    private final StateLifecycles<PlatformMerkleStateRoot> stateLifecycles;

    /**
     * Constructor.
     *
     * @param platformContext       the platform context
     * @param roster                the current roster
     * @param selfId                this node's id
     * @param statusActionSubmitter enables submitting platform status actions
     * @param softwareVersion       the current software version
     * @param stateLifecycles       the state lifecycles
     */
    public SwirldStateManager(
            @NonNull final PlatformContext platformContext,
            @NonNull final Roster roster,
            @NonNull final NodeId selfId,
            @NonNull final StatusActionSubmitter statusActionSubmitter,
            @NonNull final SoftwareVersion softwareVersion,
            @NonNull final StateLifecycles<PlatformMerkleStateRoot> stateLifecycles) {
        requireNonNull(platformContext);
        requireNonNull(roster);
        requireNonNull(selfId);
        requireNonNull(stateLifecycles);

        this.stateLifecycles = stateLifecycles;
        this.stats = new StateMetrics(platformContext.getMetrics());
        requireNonNull(statusActionSubmitter);
        this.softwareVersion = requireNonNull(softwareVersion);
        this.transactionHandler = new TransactionHandler(selfId, stats);
        this.uptimeTracker =
                new UptimeTracker(platformContext, roster, statusActionSubmitter, selfId, platformContext.getTime());
    }

    /**
     * Set the initial eventHandler for the platform. This method should only be called once.
     *
     * @param state the initial state
     */
    public void setInitialState(@NonNull final PlatformMerkleStateRoot state) {
        requireNonNull(state);

        state.throwIfDestroyed("state must not be destroyed");
        state.throwIfImmutable("state must be mutable");

        if (stateRef.get() != null) {
            throw new IllegalStateException(
                    "Attempt to set initial eventHandler when there is already a eventHandler reference.");
        }

        // Create a fast copy so there is always an immutable eventHandler to
        // invoke handleTransaction on for pre-consensus transactions
        fastCopyAndUpdateRefs(state);
    }

    /**
     * Handles the events in a consensus round. Implementations are responsible for invoking
     * {@link StateLifecycles#onHandleConsensusRound(Round, MerkleStateRoot, Consumer)} .
     *
     * @param round the round to handle
     */
    public List<ScopedSystemTransaction<StateSignatureTransaction>> handleConsensusRound(final ConsensusRound round) {
        final PlatformMerkleStateRoot state = stateRef.get();

        uptimeTracker.handleRound(round);
        transactionHandler.handleRound(round, stateLifecycles, state);

        // TODO update this logic to return the transactions from the callback consumer passed in
        // stateLifecycles.onHandleConsensusRound, when it is implemented
        return extractFromRound(round, StateSignatureTransaction.class);
    }

    /**
     * Seals the platform's state changes for the given round.
     * @param round the round to seal
     */
    public void sealConsensusRound(@NonNull final Round round) {
        requireNonNull(round);
        final PlatformMerkleStateRoot state = stateRef.get();
        stateLifecycles.onSealConsensusRound(round, state);
    }

    /**
     * Returns the consensus state. The consensus state could become immutable at any time. Modifications must not be
     * made to the returned state.
     */
    public PlatformMerkleStateRoot getConsensusState() {
        return stateRef.get();
    }

    /**
     * Invoked when a signed state is about to be created for the current freeze period.
     * <p>
     * Invoked only by the consensus handling thread, so there is no chance of the state being modified by a concurrent
     * thread.
     * </p>
     */
    public void savedStateInFreezePeriod() {
        // set current DualState's lastFrozenTime to be current freezeTime
        stateRef.get()
                .getWritablePlatformState()
                .setLastFrozenTime(stateRef.get().getReadablePlatformState().getFreezeTime());
    }

    /**
     * Loads all necessary data from the {@code reservedSignedState}.
     *
     * @param signedState the signed state to load
     */
    public void loadFromSignedState(@NonNull final SignedState signedState) {
        PlatformMerkleStateRoot state = signedState.getState();

        state.throwIfDestroyed("state must not be destroyed");
        state.throwIfImmutable("state must be mutable");

        fastCopyAndUpdateRefs(state);
    }

    private void fastCopyAndUpdateRefs(final PlatformMerkleStateRoot state) {
        PlatformMerkleStateRoot newState = fastCopy(state, stats, softwareVersion);

        // Set latest immutable first to prevent the newly immutable stateRoot from being deleted between setting the
        // stateRef and the latestImmutableState
        setLatestImmutableState(state);
        setState(newState);
    }

    /**
     * Sets the consensus state to the state provided. Must be mutable and have a reference count of at least 1.
     *
     * @param state a new mutable state
     */
    private void setState(final PlatformMerkleStateRoot state) {
        final var currVal = stateRef.get();
        if (currVal != null) {
            currVal.release();
        }
        // Do not increment the reference count because the state provided already has a reference count of at least
        // one to represent this reference and to prevent it from being deleted before this reference is set.
        stateRef.set(state);
    }

    private void setLatestImmutableState(final PlatformMerkleStateRoot immutableState) {
        final PlatformMerkleStateRoot currVal = latestImmutableState.get();
        if (currVal != null) {
            currVal.release();
        }
        immutableState.reserve();
        latestImmutableState.set(immutableState);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean isInFreezePeriod(final Instant timestamp) {
        final PlatformStateAccessor platformState = getConsensusState().getReadablePlatformState();
        return SwirldStateManagerUtils.isInFreezePeriod(
                timestamp, platformState.getFreezeTime(), platformState.getLastFrozenTime());
    }

    /**
     * <p>Updates the state to a fast copy of itself and returns a reference to the previous state to be used for
     * signing. The reference count of the previous state returned by this is incremented to prevent it from being
     * garbage collected until it is put in a signed state, so callers are responsible for decrementing the reference
     * count when it is no longer needed.</p>
     *
     * <p>Consensus event handling will block until this method returns. Pre-consensus
     * event handling may or may not be blocked depending on the implementation.</p>
     *
     * @return a copy of the state to use for the next signed state
     * @see PlatformMerkleStateRoot#copy()
     */
    public PlatformMerkleStateRoot getStateForSigning() {
        fastCopyAndUpdateRefs(stateRef.get());
        return latestImmutableState.get();
    }
}
