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

package com.swirlds.platform.wiring;

import com.swirlds.common.wiring.transformers.AdvancedTransformation;
import com.swirlds.platform.state.signed.ReservedSignedState;
import edu.umd.cs.findbugs.annotations.NonNull;

/**
 * Manages reservations of a signed state when it needs to be passed to one or more input wires.
 * <p>
 * The contract for managing reservations across vertexes in the wiring is as follows:
 * <ul>
 *     <li>Each vertex, on input, will receive a state reserved for that vertex</li>
 *     <li>The vertex which should either release that state, or return it</li>
 * </ul>
 * The reserver enforces this contract by reserving the state for each input wire, and then releasing the reservation
 * made for the reserver.
 * <p>
 * For each input wire, {@link #transform(ReservedSignedState)} will be called once, reserving the state for that input
 * wire. After a reservation is made for each input wire, {@link #cleanup(ReservedSignedState)} will be called once to
 * release the original reservation.
 *
 * @param name the name of the reserver
 */
public record SignedStateReserver(String name)
        implements AdvancedTransformation<ReservedSignedState, ReservedSignedState> {
    @NonNull
    @Override
    public ReservedSignedState transform(@NonNull final ReservedSignedState reservedSignedState) {
        return reservedSignedState.getAndReserve(name);
    }

    @Override
    public void cleanup(@NonNull final ReservedSignedState reservedSignedState) {
        reservedSignedState.close();
    }

    @NonNull
    @Override
    public String getName() {
        return name;
    }
}
