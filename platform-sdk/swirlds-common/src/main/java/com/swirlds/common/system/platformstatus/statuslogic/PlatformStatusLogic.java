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

package com.swirlds.common.system.platformstatus.statuslogic;

import com.swirlds.common.system.platformstatus.PlatformStatus;
import com.swirlds.common.system.platformstatus.PlatformStatusAction;
import com.swirlds.common.system.platformstatus.PlatformStatusConfig;
import com.swirlds.common.time.Time;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.time.Instant;

/**
 * Interface representing the state machine logic for an individual {@link PlatformStatus}.
 */
@FunctionalInterface
public interface PlatformStatusLogic {
    /**
     * Process a status action.
     * <p>
     * If the input action causes a status transition, then this method will return the new status. Otherwise, it will
     * return the same status as before processing the action.
     *
     * @param action          the status action that has occurred
     * @param statusStartTime the time at which the current status started
     * @param time            a source of time
     * @param config          the platform status config
     * @return the status after processing the action. may be the same status as before processing
     * @throws IllegalArgumentException if the input action is not expected in the current status
     */
    @NonNull
    PlatformStatus processStatusAction(
            @NonNull final PlatformStatusAction action,
            @NonNull final Instant statusStartTime,
            @NonNull final Time time,
            @NonNull final PlatformStatusConfig config);
}
