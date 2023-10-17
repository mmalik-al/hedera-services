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

import com.swirlds.base.time.Time;
import com.swirlds.common.metrics.Metrics;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import java.util.function.Consumer;

/**
 * Wires two or more components together.
 */
public abstract class Wire {

    /**
     * Get a new wire builder.
     *
     * @param name the name of the wire. Used for metrics and debugging. Must be unique (not enforced by framework).
     *             Must only contain alphanumeric characters, underscores, and hyphens (enforced by framework).
     * @return a new wire builder
     */
    public static WireBuilder builder(@NonNull final String name) {
        return new WireBuilder(name);
    }

    /**
     * Get a new wire metrics builder. Can be passed to {@link WireBuilder#withMetricsBuilder(WireMetricsBuilder)} to
     * add metrics to the wire.
     *
     * @param metrics the metrics framework
     * @param time    provides wall clock time
     * @return a new wire metrics builder
     */
    public static WireMetricsBuilder metricsBuilder(@NonNull final Metrics metrics, @NonNull final Time time) {
        return new WireMetricsBuilder(metrics, time);
    }

    /**
     * Get the name of the wire.
     *
     * @return the name of the wire
     */
    @NonNull
    public abstract String getName();

    /**
     * Get the number of unprocessed tasks. Returns -1 if this wire is not monitoring the number of unprocessed tasks.
     * Wires do not track the number of unprocessed tasks by default. To enable tracking, enable
     * {@link WireMetricsBuilder#withScheduledTaskCountMetricEnabled(boolean)} or set a capacity that is not unlimited
     * via {@link WireBuilder#withScheduledTaskCapacity(long)}.
     *
     * <p>
     * TODO this is wonky API, since a counter is not always enabled
     */
    public abstract long getUnprocessedTaskCount();

    /**
     * Get a wire channel for this wire. In order to use this channel, a handler must be bound via
     * {@link WireChannel#bind(Consumer)}.
     *
     * @param <T> the type of data handled by the handler
     * @return the channel
     */
    @NonNull
    public final <T> WireChannel<T> createChannel() {
        return new WireChannel<>(this);
    }

    /**
     * Get a wire channel for this wire. In order to use this channel, a handler must be bound via
     * {@link WireChannel#bind(Consumer)}.
     *
     * @param clazz the type of data handled by the handler, convenience argument for scenarios where the compiler can't
     *              figure out generic types
     * @param <T>   the type of data handled by the handler
     * @return the channel
     */
    @NonNull
    public final <T> WireChannel<T> createChannel(@NonNull final Class<T> clazz) {
        return new WireChannel<>(this);
    }

    /**
     * Flush all data in the wire. Blocks until all data currently in flight has been processed.
     * <p>
     * Note: not all wire implementations support flushing. Currently, only wires with
     * {@link WireBuilder#withConcurrency(boolean)} set to false support flushing. Calling this method on a wire that
     * does not support flushing will result in an error being written to the log (no exception is thrown).
     */
    public abstract void flush();

    /**
     * Flush all data in the wire. Blocks until all data currently in flight has been processed.
     *
     * <p>
     * Note: not all wire implementations support flushing. Currently, only wires with
     * {@link WireBuilder#withConcurrency(boolean)} set to false support flushing. Calling this method on a wire that
     * does not support flushing will result in an error being written to the log (no exception is thrown).
     *
     * @throws InterruptedException if the thread is interrupted while waiting for all data to be processed
     */
    public abstract void interruptableFlush() throws InterruptedException;

    /**
     * Add a task to the wire. May block if back pressure is enabled. Similar to
     * {@link #interruptablePut(Consumer, Object)} except that it cannot be interrupted and can block forever if
     * backpressure is enabled.
     *
     * @param handler handles the provided data
     * @param data    the data to be processed by the wire
     */
    protected abstract void put(@NonNull Consumer<Object> handler, @Nullable Object data);

    /**
     * Add a task to the wire. May block if back pressure is enabled. If backpressure is enabled and being applied, this
     * method can be interrupted.
     *
     * @param data the data to be processed by the wire
     * @throws InterruptedException if the thread is interrupted while waiting for capacity to become available
     */
    protected abstract void interruptablePut(@NonNull Consumer<Object> handler, @Nullable Object data)
            throws InterruptedException;

    /**
     * Add a task to the wire. If backpressure is enabled and there is not immediately capacity available, this method
     * will not accept the data.
     *
     * @param data the data to be processed by the wire
     * @return true if the data was accepted, false otherwise
     */
    protected abstract boolean offer(@NonNull Consumer<Object> handler, @Nullable Object data);

    /**
     * Inject data into the wire, doing so even if it causes the number of unprocessed tasks to exceed the capacity
     * specified by configured back pressure. If backpressure is disabled, this operation is logically equivalent to
     * {@link #put(Consumer, Object)}.
     *
     * @param data the data to be processed by the wire
     */
    protected abstract void inject(@NonNull Consumer<Object> handler, @Nullable Object data);
}
