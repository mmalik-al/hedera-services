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

package com.swirlds.common.wiring;

import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import java.util.Objects;
import java.util.function.Consumer;

/**
 * An object that can insert work to be handled on a wire.
 *
 * @param <T> the type of object that the work handles
 */
public class WireChannel<T> {

    private final Wire wire;
    private Consumer<Object> handler;

    /**
     * Constructor.
     *
     * @param wire the wire to insert data into
     */
    WireChannel(@NonNull final Wire wire) {
        this.wire = Objects.requireNonNull(wire);
    }

    /**
     * Bind this inserter to a handler. A handler must be bound to this inserter prior to inserting data via any
     * method.
     *
     * @param handler the handler to bind to this inserter
     * @return this
     * @throws IllegalStateException if a handler is already bound and this method is called a second time
     */
    @SuppressWarnings("unchecked")
    @NonNull
    public WireChannel<T> bind(@NonNull final Consumer<T> handler) {
        if (this.handler != null) {
            throw new IllegalStateException("Handler already bound");
        }
        this.handler = (Consumer<Object>) Objects.requireNonNull(handler);

        return this;
    }

    /**
     * Add a task to the wire. May block if back pressure is enabled. Similar to {@link #interruptablePut(Object)}
     * except that it cannot be interrupted and can block forever if backpressure is enabled.
     *
     * @param data the data to be processed by the wire
     */
    public void put(@Nullable final T data) {
        wire.put(handler, data);
    }

    /**
     * Add a task to the wire. May block if back pressure is enabled. If backpressure is enabled and being applied, this
     * method can be interrupted.
     *
     * @param data the data to be processed by the wire
     * @throws InterruptedException if the thread is interrupted while waiting for capacity to become available
     */
    public void interruptablePut(@Nullable final T data) throws InterruptedException {
        wire.interruptablePut(handler, data);
    }

    /**
     * Add a task to the wire. If backpressure is enabled and there is not immediately capacity available, this method
     * will not accept the data.
     *
     * @param data the data to be processed by the wire
     * @return true if the data was accepted, false otherwise
     */
    public boolean offer(@Nullable final T data) {
        return wire.offer(handler, data);
    }

    /**
     * Inject data into the wire, doing so even if it causes the number of unprocessed tasks to exceed the capacity
     * specified by configured back pressure. If backpressure is disabled, this operation is logically equivalent to
     * {@link #put(Object)}.
     *
     * @param data the data to be processed by the wire
     */
    public void inject(@Nullable final T data) {
        wire.inject(handler, data);
    }
}
