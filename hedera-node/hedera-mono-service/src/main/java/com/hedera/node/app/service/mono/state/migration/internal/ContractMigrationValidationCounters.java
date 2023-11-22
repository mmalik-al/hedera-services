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

package com.hedera.node.app.service.mono.state.migration.internal;

import static java.util.Objects.requireNonNull;

import com.hedera.node.app.service.mono.state.migration.ContractStateMigrator;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentHashMap.KeySetView;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Various counters used to perform final validations on the completed transform.
 * <p>
 * The counters used by the sink process need not actually be atomic at this time, as the sink process is single
 * threaded.  But IMO it is more consistent and thus easier to read to treat _all_ of the counters the same.
 */
@SuppressWarnings("java:S6218") // override equals/hashcode because of arrays: but we never compare these things
public record ContractMigrationValidationCounters(
        @NonNull AtomicInteger slotsSourced,
        @NonNull AtomicInteger slotsSunk,
        @NonNull AtomicReference<KeySetView<Long, Boolean>> contracts,
        @NonNull AtomicInteger nMissingPrevs,
        @NonNull AtomicInteger nMissingNexts,
        @NonNull byte[] runningXorOfLinks) {

    static final int DISTINCT_CONTRACTS_ESTIMATE = 10_000;

    @NonNull
    public static ContractMigrationValidationCounters create() {
        return new ContractMigrationValidationCounters(
                new AtomicInteger(),
                new AtomicInteger(),
                new AtomicReference<>(ConcurrentHashMap.newKeySet(DISTINCT_CONTRACTS_ESTIMATE)),
                new AtomicInteger(),
                new AtomicInteger(),
                new byte[32]);
    }

    public void sourceOne() {
        slotsSourced.incrementAndGet();
    }

    public void sinkOne() {
        slotsSunk.incrementAndGet();
    }

    public void addContract(long cid) {
        contracts.get().add(cid);
    }

    public void addMissingPrev() {
        nMissingPrevs.incrementAndGet();
    }

    public void addMissingNext() {
        nMissingNexts.incrementAndGet();
    }

    public void xorAnotherLink(@NonNull final byte[] link) {
        requireNonNull(link);
        ContractStateMigrator.validateArgument(link.length == 32, "link length must be 32 bytes");
        synchronized (runningXorOfLinks) {
            for (int i = 0; i < 32; i++) {
                runningXorOfLinks[i] ^= link[i];
            }
        }
    }

    public boolean runningXorOfLinksIsZero() {
        byte r = 0;
        synchronized (runningXorOfLinks) {
            for (int i = 0; i < 32; i++) {
                r |= runningXorOfLinks[i];
            }
        }
        return r == 0;
    }
}
