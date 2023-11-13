/*
 * Copyright (C) 2022-2023 Hedera Hashgraph, LLC
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

package com.swirlds.platform.util;

import static com.swirlds.logging.legacy.LogMarker.STATE_HASH;

import com.swirlds.common.config.StateConfig;
import com.swirlds.common.threading.framework.QueueThread;
import com.swirlds.common.threading.framework.config.QueueThreadConfiguration;
import com.swirlds.common.threading.manager.ThreadManager;
import com.swirlds.platform.state.State;
import com.swirlds.platform.state.signed.SignedState;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicLong;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.message.Message;
import org.apache.logging.log4j.message.MessageFactory;
import org.apache.logging.log4j.message.ParameterizedMessageFactory;

/**
 * Logger responsible for logging node hashes to the swirlds-hashstream file.
 *
 * <p>HashLogger will queue up tasks to generate and log hashes from the provided signed states on a background thread.
 * The implementation makes use of log4j and will make use of log4j configuration to configure how the log entries are
 * outputted.</p>
 */
public class HashLogger {
    private static final Logger logger = LogManager.getLogger(HashLogger.class);
    private static final MessageFactory MESSAGE_FACTORY = ParameterizedMessageFactory.INSTANCE;
    private static final int LOGGING_QUEUE_CAPACITY = 100;

    private final AtomicLong lastRoundLogged = new AtomicLong(-1);
    private final StateConfig stateConfig;
    private final QueueThread<Runnable> logQueue;
    private final Logger logOutput; // NOSONAR: selected logger to output to.
    private final boolean isEnabled;

    /**
     * Construct a HashLogger.
     *
     * @param threadManager responsible for creating and managing threads
     * @param stateConfig   configuration for the current state.
     */
    public HashLogger(@NonNull final ThreadManager threadManager, @NonNull final StateConfig stateConfig) {
        this(threadManager, stateConfig, logger);
    }

    // Visible for testing
    HashLogger(
            @NonNull final ThreadManager threadManager,
            @NonNull final StateConfig stateConfig,
            @NonNull final Logger logOutput) {
        this.stateConfig = Objects.requireNonNull(stateConfig);
        isEnabled = stateConfig.enableHashStreamLogging();
        logQueue = !isEnabled
                ? null
                : new QueueThreadConfiguration<Runnable>(Objects.requireNonNull(threadManager))
                        .setComponent("logging")
                        .setThreadName("log-hashstream")
                        .setCapacity(LOGGING_QUEUE_CAPACITY)
                        .setHandler(Runnable::run)
                        .build(isEnabled /*start*/);
        this.logOutput = Objects.requireNonNull(logOutput);
    }

    private void log(final SignedState signedState) {
        final long currentRound = signedState.getRound();
        final long prevRound = lastRoundLogged.getAndUpdate(value -> Math.max(value, currentRound));

        if (prevRound >= 0 && currentRound - prevRound > 1) {
            // One or more rounds skipped.
            logOutput.info(
                    STATE_HASH.getMarker(),
                    () -> MESSAGE_FACTORY.newMessage(
                            "*** Several rounds skipped. Round received {}. Previously received {}.",
                            currentRound,
                            prevRound));
        }

        if (currentRound > prevRound) {
            logOutput.info(STATE_HASH.getMarker(), () -> generateLogMessage(signedState));
        }
    }

    /**
     * @return logging queue for unit testing.
     */
    QueueThread<Runnable> queue() {
        return logQueue;
    }

    /**
     * Queues the provided signed state for extracting hashes and logging.
     *
     * @param signedState the signed state to retrieve hash information from and log.
     */
    public void logHashes(final SignedState signedState) {
        if (!isEnabled) {
            return;
        }
        logQueue.offer(() -> log(signedState)); // NOSONAR: silently drop message if unable to queue.
    }

    private Message generateLogMessage(@NonNull final SignedState signedState) {
        final State state = signedState.getState();
        final String platformInfo = state.getInfoString(stateConfig.debugHashDepth());

        return MESSAGE_FACTORY.newMessage(
                """
                        State Info, round = {}:
                        {}""",
                signedState.getRound(),
                platformInfo);
    }
}
