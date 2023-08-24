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

package com.hedera.node.app.throttle;

import com.hedera.node.app.throttle.impl.NetworkUtilizationManagerImpl;
import dagger.Binds;
import dagger.Module;
import dagger.Provides;
import edu.umd.cs.findbugs.annotations.NonNull;
import javax.inject.Singleton;

@Module
public interface ThrottleInjectionModule {
    @Binds
    @Singleton
    ThrottleAccumulator bindThrottleAccumulator(ThrottleAccumulatorImpl throttleAccumulator);

    /** Provides an implementation of the {@link com.hedera.node.app.throttle.NetworkUtilizationManager}. */
    @Provides
    @Singleton
    public static NetworkUtilizationManager provideNetworkUtilizationManager(
            @NonNull final HandleThrottleAccumulator handleThrottling) {
        return new NetworkUtilizationManagerImpl(handleThrottling);
    }
}
