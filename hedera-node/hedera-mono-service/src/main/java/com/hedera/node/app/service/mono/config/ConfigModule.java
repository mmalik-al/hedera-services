/*
 * Copyright (C) 2020-2023 Hedera Hashgraph, LLC
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

package com.hedera.node.app.service.mono.config;

import com.hedera.node.app.service.mono.utils.MiscUtils;
import com.hedera.node.app.service.mono.utils.replay.IsFacilityRecordingOn;
import com.hedera.node.app.spi.numbers.HederaAccountNumbers;
import com.hedera.node.app.spi.numbers.HederaFileNumbers;
import dagger.Binds;
import dagger.Module;
import dagger.Provides;

import javax.inject.Singleton;
import java.util.function.BooleanSupplier;

@Module
public interface ConfigModule {
    @Binds
    HederaFileNumbers bindFileNumbers(FileNumbers fileNumbers);

    @Binds
    HederaAccountNumbers bindAccountNumbers(AccountNumbers accountNumbers);

    @Provides
    @Singleton
    @IsFacilityRecordingOn
    static BooleanSupplier provideIsFacilityRecordingOn() {
        return MiscUtils::isFacilityRecordingOn;
    }
}
