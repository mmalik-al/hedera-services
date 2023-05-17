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

package com.hedera.node.app.service.contract.impl.state;

import static java.util.Objects.requireNonNull;

import com.hedera.node.app.spi.meta.bni.Scope;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import java.util.Collection;
import java.util.Optional;
import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.datatypes.Wei;
import org.hyperledger.besu.evm.account.Account;
import org.hyperledger.besu.evm.account.EvmAccount;
import org.hyperledger.besu.evm.worldstate.WorldUpdater;

public class ProxyWorldUpdater implements WorldUpdater {
    /**
     * The scope in which this {@code ProxyWorldUpdater} operates; stored in case we need to
     * create a "stacked" updater in a child scope via {@link #updater()}.
     */
    private final Scope scope;
    /**
     * The factory used to create new {@link EvmFrameState} instances; used once in the
     * constructor, and then again in {@link #updater()} if that is called.
     */
    private final EvmFrameStateFactory evmFrameStateFactory;
    /**
     * The {@link EvmFrameState} managing this {@code ProxyWorldUpdater}'s state.
     */
    private final EvmFrameState evmFrameState;

    /**
     * If our {@code CreateOperation}s used the addresses prescribed by the {@code CREATE} and
     * {@code CREATE2} specs, they would (clearly) not need Hedera state; and would not need to
     * call into the frame's {@link ProxyWorldUpdater}. Similarly, if a {@link ProxyWorldUpdater}
     * did not need any frame context to create a new account, it would not need a call from
     * the {@code CreateOperation}.
     *
     * <p>Both hypotheticals are false:
     * <ul>
     *     <li>The {@code CreateOperation} needs to call into the {@link ProxyWorldUpdater}
     *     because our {@code CREATE} address derives from the next Hedera entity number.</li>
     *     <li>To correctly create an account, the {@link ProxyWorldUpdater} must know the
     *     recipient address of the parent frame, as any children created in this frame
     *     will "inherit" many of their Hedera properties from the recipient.</li>
     * </ul>
     *
     * <p>So we need a little scratchpad to facilitate this data exchange with any create
     * operations executing in this {@link ProxyWorldUpdater}'s frame.
     */
    @Nullable
    private PendingCreation pendingCreation;

    public ProxyWorldUpdater(@NonNull final Scope scope, @NonNull final EvmFrameStateFactory evmFrameStateFactory) {
        this.scope = requireNonNull(scope);
        this.evmFrameStateFactory = requireNonNull(evmFrameStateFactory);
        this.evmFrameState = evmFrameStateFactory.createWithin(scope);
    }

    @Override
    public @Nullable Account get(@NonNull final Address address) {
        throw new AssertionError("Not implemented");
    }

    @Override
    public EvmAccount getAccount(@NonNull final Address address) {
        throw new AssertionError("Not implemented");
    }

    @Override
    public EvmAccount createAccount(@NonNull final Address address, final long nonce, @NonNull final Wei balance) {
        throw new AssertionError("Not implemented");
    }

    @Override
    public void deleteAccount(@NonNull final Address address) {
        throw new AssertionError("Not implemented");
    }

    @Override
    public void revert() {
        throw new AssertionError("Not implemented");
    }

    @Override
    public void commit() {
        throw new AssertionError("Not implemented");
    }

    @Override
    public @NonNull Optional<WorldUpdater> parentUpdater() {
        throw new AssertionError("Not implemented");
    }

    @Override
    public @NonNull WorldUpdater updater() {
        throw new AssertionError("Not implemented");
    }

    @Override
    public @NonNull Collection<? extends Account> getTouchedAccounts() {
        throw new AssertionError("Not implemented");
    }

    @Override
    public @NonNull Collection<Address> getDeletedAccountAddresses() {
        throw new AssertionError("Not implemented");
    }
}
