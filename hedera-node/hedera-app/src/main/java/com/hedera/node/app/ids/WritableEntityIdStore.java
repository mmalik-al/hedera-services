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

package com.hedera.node.app.ids;

import static java.util.Objects.requireNonNull;

import com.hedera.hapi.node.state.common.EntityNumber;
import com.hedera.node.app.spi.state.WritableSingletonState;
import com.hedera.node.app.spi.state.WritableStates;
import edu.umd.cs.findbugs.annotations.NonNull;

/**
 * A writeable store for entity ids.
 */
public class WritableEntityIdStore {
    /**
     * The underlying data storage class that holds the entity id data.
     */
    private final WritableSingletonState<EntityNumber> entityIdState;

    /**
     * Create a new {@link WritableEntityIdStore} instance.
     *
     * @param states The state to use.
     */
    public WritableEntityIdStore(@NonNull final WritableStates states) {
        requireNonNull(states);
        this.entityIdState = states.getSingleton(EntityIdService.ENTITY_ID_STATE_KEY);
    }

    /**
     * Increments the current entity number in state and returns the new value.
     *
     * @return the next new entity number
     */
    public long incrementAndGet() {
        final EntityNumber oldEntityNum = entityIdState.get();
        long newEntityNum = oldEntityNum == null ? 1 : oldEntityNum.number() + 1;
        entityIdState.put(new EntityNumber(newEntityNum));
        return newEntityNum;
    }
}
