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

package com.hedera.node.app.service.mono.state.migration;

import static com.hedera.node.app.service.mono.utils.MiscUtils.withLoggedDuration;
import static com.swirlds.common.threading.manager.AdHocThreadManager.getStaticThreadManager;
import static java.lang.Thread.currentThread;
import static java.util.Objects.requireNonNull;

import com.hedera.hapi.node.state.contract.SlotKey;
import com.hedera.hapi.node.state.contract.SlotValue;
import com.hedera.node.app.service.mono.state.adapters.VirtualMapLike;
import com.hedera.node.app.service.mono.state.migration.internal.ContractMigrationValidationCounters;
import com.hedera.node.app.service.mono.state.virtual.ContractKey;
import com.hedera.node.app.service.mono.state.virtual.IterableContractValue;
import com.hedera.node.app.service.mono.utils.NonAtomicReference;
import com.hedera.node.app.spi.state.WritableKVState;
import com.hedera.pbj.runtime.io.buffer.Bytes;
import com.swirlds.common.threading.interrupt.InterruptableConsumer;
import com.swirlds.common.threading.interrupt.InterruptableSupplier;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.stream.Collectors;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Migrate mono service's contract store (contract slots, i.e. per-contract K/V pairs) to modular service's contract store.
 */
public class ContractStateMigrator {
    private static final Logger log = LogManager.getLogger(ContractStateMigrator.class);

    /**
     * The actual migration routine, called by the thing that migrates all the stores
     *
     * Adds the migrated slots to `toState`.  Caller is responsible for supplying the
     * state to write to ... and for committing it after the transform is done.
     */
    public static void migrateFromContractStoreVirtualMap(
            @NonNull final VirtualMapLike<ContractKey, IterableContractValue> fromState,
            @NonNull final WritableKVState<SlotKey, SlotValue> toState) {
        requireNonNull(fromState);
        requireNonNull(toState);

        final var validationsFailed = new ArrayList<String>();
        final var migrator = new ContractStateMigrator(fromState, toState);
        migrator.doit(validationsFailed);
        if (!validationsFailed.isEmpty()) {
            final var formattedFailedValidations =
                    validationsFailed.stream().collect(Collectors.joining("\n   ***", "   ***", "\n"));
            log.error("{}: transform validations failed:\n{}", LOG_CAPTION, formattedFailedValidations);
            throw new BrokenTransformationException(LOG_CAPTION + ": transformation didn't complete successfully");
        }
    }

    static final int THREAD_COUNT = 8; // TODO: is there a configuration option good for this?
    static final int MAXIMUM_SLOTS_IN_FLIGHT = 1_000_000; // This holds both mainnet and testnet (at this time, 2023-11)
    static final String LOG_CAPTION = "contract-store mono-to-modular migration";

    final VirtualMapLike<ContractKey, IterableContractValue> fromState;
    final WritableKVState<SlotKey, SlotValue> toState;

    boolean doValidation;
    final ContractMigrationValidationCounters counters;

    ContractStateMigrator(
            @NonNull final VirtualMapLike<ContractKey, IterableContractValue> fromState,
            @NonNull WritableKVState<SlotKey, SlotValue> toState) {
        requireNonNull(fromState);
        requireNonNull(toState);
        this.fromState = fromState;
        this.toState = toState;
        this.doValidation = true;
        this.counters = ContractMigrationValidationCounters.create();
    }

    void setDoingValidation(final boolean doValidation) {
        this.doValidation = doValidation;
    }

    /**
     * Do the transform from mono-service's contract store to modular-service's contract store, and do some
     * post-transforms sanity checking.
     */
    void doit(@NonNull final List<String> validationsFailed) {

        final var completedProcesses = new NonAtomicReference<EnumSet<CompletedProcesses>>();

        withLoggedDuration(() -> completedProcesses.set(transformStore()), log, LOG_CAPTION + ": complete transform");

        requireNonNull(completedProcesses.get(), "must have valid completed processes set at this point");

        if (doValidation) {
            validationsFailed.addAll(validateTransform(completedProcesses.get()));
        }
    }

    /**
     * The intermediate representation of a contract slot (K/V pair, plus prev/next linked list references).
     */
    @SuppressWarnings("java:S6218") // should overload equals/hashcode  - but will never test for equality or hash this
    private record ContractSlotLocal(
            long contractId, @NonNull int[] key, @NonNull byte[] value, int[] prev, int[] next) {

        public static final ContractSlotLocal SENTINEL =
                new ContractSlotLocal(0L, new int[8], new byte[32], null, null);

        private ContractSlotLocal {
            requireNonNull(key);
            requireNonNull(value);
            validateArgument(key.length == 8, "wrong length key");
            validateArgument(value.length == 32, "wrong length value");
            if (prev != null) validateArgument(prev.length == 8, "wrong length prev link");
            if (next != null) validateArgument(next.length == 8, "wrong length next link");
        }

        public ContractSlotLocal(@NonNull final ContractKey k, @NonNull final IterableContractValue v) {
            this(k.getContractId(), k.getKey(), v.getValue(), v.getExplicitPrevKey(), v.getExplicitNextKey());
        }
    }

    /** Indicates that the source or sink process processed all slots */
    enum CompletedProcesses {
        SOURCING,
        SINKING
    }

    /**
     *  Operates the source and sink processes to transform the mono-state ("from") into the modular-state ("to").
     *  */
    @NonNull
    EnumSet<CompletedProcesses> transformStore() {

        final var slotQueue = new ArrayBlockingQueue<ContractSlotLocal>(MAXIMUM_SLOTS_IN_FLIGHT);

        // Sinking and sourcing happen concurrently.  (Though sourcing is multithreaded, sinking is all on one thread.)
        // Consider: For debugging, don't create (and start) `processSlotQueue` until after sourcing is complete. Just
        // accumulate everything in the fromStore in the queue before sinking anything.

        CompletableFuture<Void> processSlotQueue =
                CompletableFuture.runAsync(() -> iterateOverQueuedSlots(slotQueue::take));

        final var completedTasks = EnumSet.noneOf(CompletedProcesses.class);

        boolean didCompleteSourcing = iterateOverCurrentData(slotQueue::put);

        if (didCompleteSourcing) completedTasks.add(CompletedProcesses.SOURCING);

        boolean didCompleteSinking = true;
        try {
            processSlotQueue.join();
        } catch (CompletionException cex) {
            final var ex = cex.getCause();
            log.error(LOG_CAPTION + ": interrupt when sinking slots", ex);
            didCompleteSinking = false;
        }
        if (didCompleteSinking) completedTasks.add(CompletedProcesses.SINKING);

        return completedTasks;
    }

    /**
     * Pull slots off the queue and add each one immediately to the modular-service state.  This is "sinking" the slots.
     *
     * This is single-threaded.  (But does operate concurrently with sourcing.)
     */
    @SuppressWarnings("java:S2142") // must rethrow InterruptedException - Sonar doesn't understand wrapping it to throw
    void iterateOverQueuedSlots(@NonNull final InterruptableSupplier<ContractSlotLocal> slotSource) {
        requireNonNull(slotSource);

        while (true) {
            final ContractSlotLocal slot;
            try {
                slot = slotSource.get();
            } catch (InterruptedException e) {
                throw new CompletionException(e);
            }
            if (slot == ContractSlotLocal.SENTINEL) break;

            final var key = SlotKey.newBuilder()
                    .contractNumber(slot.contractId())
                    .key(bytesFromInts(slot.key()))
                    .build();
            final var value = SlotValue.newBuilder()
                    .value(Bytes.wrap(slot.value()))
                    .previousKey(bytesFromInts(slot.prev()))
                    .nextKey(bytesFromInts((slot.next())))
                    .build();
            toState.put(key, value);

            // Some counts to provide some validation
            if (doValidation) {
                counters.sinkOne();
                counters.addContract(key.contractNumber());
                if (value.previousKey() == Bytes.EMPTY) counters.addMissingPrev();
                else counters.xorAnotherLink(value.previousKey().toByteArray());
                if (value.nextKey() == Bytes.EMPTY) counters.addMissingNext();
                else counters.xorAnotherLink(value.nextKey().toByteArray());
            }
        }
    }

    /**
     * Iterate over the incoming mono-service state, pushing slots (key + value) into the queue.  This is "sourcing"
     * the slots.
     *
     * This iteration operates with multiple threads simultaneously.
     */
    boolean iterateOverCurrentData(@NonNull final InterruptableConsumer<ContractSlotLocal> slotSink) {
        boolean didRunToCompletion = true;
        try {
            fromState.extractVirtualMapData(
                    getStaticThreadManager(),
                    entry -> {
                        final var contractKey = entry.left();
                        final var iterableContractValue = entry.right();
                        slotSink.accept(new ContractSlotLocal(contractKey, iterableContractValue));

                        // Some counts to provide some validation
                        if (doValidation) {
                            counters.sourceOne();
                        }
                    },
                    THREAD_COUNT);
            slotSink.accept(ContractSlotLocal.SENTINEL);
        } catch (final InterruptedException ex) {
            currentThread().interrupt();
            didRunToCompletion = false;
        }
        return didRunToCompletion;
    }

    @NonNull
    List<String> validateTransform(@NonNull final EnumSet<CompletedProcesses> completedProcesses) {
        requireNonNull(completedProcesses);

        final var validationsFailed = new ArrayList<String>();

        // All that follows is validation that all processing completed and sanity checks pass
        if (doValidation) {
            if (completedProcesses.size()
                    != EnumSet.allOf(CompletedProcesses.class).size()) {
                if (!completedProcesses.contains(CompletedProcesses.SOURCING))
                    validationsFailed.add("Sourcing process didn't finish");
                if (!completedProcesses.contains(CompletedProcesses.SINKING))
                    validationsFailed.add("Sinking process didn't finish");
            }

            final var fromSize = fromState.size();
            if (fromSize != counters.slotsSourced().get()
                    || fromSize != counters.slotsSunk().get()
                    || fromSize != toState.size()) {
                validationsFailed.add(
                        "counters of slots processed don't match: %d source size, %d #slots sourced, %d slots sunk, %d final destination size"
                                .formatted(
                                        fromSize,
                                        counters.slotsSourced().get(),
                                        counters.slotsSunk().get(),
                                        toState.size()));
            }

            final var nContracts = counters.contracts().get().size();
            if (nContracts != counters.nMissingPrevs().get()
                    || nContracts != counters.nMissingNexts().get()) {
                validationsFailed.add(
                        "distinct contracts doesn't match #null prev links and/or #null next links: %d contract, %d null prevs, %d null nexts"
                                .formatted(
                                        nContracts,
                                        counters.nMissingPrevs().get(),
                                        counters.nMissingNexts().get()));
            }

            if (!counters.runningXorOfLinksIsZero()) {
                validationsFailed.add("prev/next links (over all contracts) aren't properly paired");
            }
        }

        return validationsFailed;
    }

    //    @NonNull
    //    static WritableKVState<SlotKey, SlotValue> getNewContractStore() {
    //        return new InMemoryWritableKVState<SlotKey, SlotValue>();
    //    }

    /** Convert int[] to byte[] and then to Bytes. If argument is null or 0-length then return `Bytes.EMPTY`. */
    static Bytes bytesFromInts(final int[] ints) {
        if (ints == null) return Bytes.EMPTY;
        if (ints.length == 0) return Bytes.EMPTY;

        // N.B.: `ByteBuffer.allocate` creates the right `byte[]`.  `asIntBuffer` will share it, so no extra copy
        // there.  Finally, `Bytes.wrap` will wrap it - and end up owning it once `buf` goes out of scope - so no extra
        // copy there either.
        final ByteBuffer buf = ByteBuffer.allocate(32);
        buf.asIntBuffer().put(ints);
        return Bytes.wrap(buf.array());
    }

    /** Validate an argument by throwing `IllegalArgumentException` if the test fails */
    public static void validateArgument(final boolean b, @NonNull final String msg) {
        if (!b) throw new IllegalArgumentException(msg);
    }

    static class BrokenTransformationException extends RuntimeException {

        public BrokenTransformationException(String message) {
            super(message);
        }
    }
}
