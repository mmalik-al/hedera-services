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

package com.hedera.node.app.service.consensus.impl;

import static com.hedera.node.app.service.consensus.impl.ConsensusServiceImpl.TOPICS_KEY;
import static java.util.Objects.requireNonNull;

import com.hedera.hapi.node.base.TopicID;
import com.hedera.hapi.node.state.consensus.Topic;
import com.hedera.node.app.service.consensus.ReadableTopicStore;
import com.hedera.node.app.spi.state.ReadableKVState;
import com.hedera.node.app.spi.state.ReadableStates;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import java.util.Optional;

/**
 * Provides read-only methods for interacting with the underlying data storage mechanisms for
 * working with Topics.
 *
 * <p>This class is not exported from the module. It is an internal implementation detail.
 */
public class ReadableTopicStoreImpl implements ReadableTopicStore {
    /** The underlying data storage class that holds the topic data. */
    private final ReadableKVState<TopicID, Topic> topicState;

    /**
     * Create a new {@link ReadableTopicStoreImpl} instance.
     *
     * @param states The state to use.
     */
    public ReadableTopicStoreImpl(@NonNull final ReadableStates states) {
        requireNonNull(states);

        this.topicState = states.get(TOPICS_KEY);
    }

    /**
     * Returns the topic metadata needed. If the topic doesn't exist returns failureReason. If the
     * topic exists , the failure reason will be null.
     *
     * @param id topic id being looked up
     * @return topic's metadata
     */
    @Nullable
    public Topic getTopic(@NonNull final TopicID id) {
        requireNonNull(id);
        return getTopicLeaf(id).orElse(null);
    }

    @NonNull
    public Optional<Topic> getTopicLeaf(@NonNull final TopicID id) {
        return Optional.ofNullable(topicState.get(id));
    }
}
