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

package com.swirlds.platform.bls.message;

import com.swirlds.common.system.NodeId;
import edu.umd.cs.findbugs.annotations.NonNull;

/** An interface representing a BlsProtocolMessage */
public interface BlsProtocolMessage {
    /**
     * Gets the message senderId
     *
     * @return the message senderId
     */
    @NonNull
    NodeId getSenderId();
}
