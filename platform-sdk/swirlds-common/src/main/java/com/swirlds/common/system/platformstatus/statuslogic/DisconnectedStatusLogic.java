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
 * Placeholder class representing the state machine logic for the {@link PlatformStatus#DISCONNECTED DISCONNECTED}
 * status
 * <p>
 * Will be removed once the status is removed
 */
@Deprecated(forRemoval = true)
public class DisconnectedStatusLogic implements PlatformStatusLogic {
    @NonNull
    @Override
    public PlatformStatus processStatusAction(
            @NonNull PlatformStatusAction action,
            @NonNull Instant statusStartTime,
            @NonNull Time time,
            @NonNull PlatformStatusConfig config) {

        throw new IllegalArgumentException("Unexpected action `%s` while in status `DISCONNECTED`".formatted(action));
    }
}
