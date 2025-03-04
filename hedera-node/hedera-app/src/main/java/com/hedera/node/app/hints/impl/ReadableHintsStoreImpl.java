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

import static java.util.Objects.requireNonNull;

import com.hedera.node.app.hints.ReadableHintsStore;
import com.hedera.pbj.runtime.io.buffer.Bytes;
import com.swirlds.state.spi.ReadableStates;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;

/**
 * Placeholder implementation of the {@link ReadableHintsStore}.
 */
public class ReadableHintsStoreImpl implements ReadableHintsStore {
    public ReadableHintsStoreImpl(@NonNull final ReadableStates states) {
        requireNonNull(states);
    }

    @Override
    public @Nullable Bytes getVerificationKeyFor(@NonNull final Bytes rosterHash) {
        requireNonNull(rosterHash);
        throw new UnsupportedOperationException();
    }
}
