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

package com.hedera.node.app.service.token;

import com.hedera.node.app.spi.service.Service;
import edu.umd.cs.findbugs.annotations.NonNull;

/**
 * Implements the HAPI <a href="https://github.com/hashgraph/hedera-protobufs/blob/main/services/token_service.proto">Token
 * Service</a>.
 */
public interface TokenService extends Service {

    String NAME = "TokenService";

    @NonNull
    @Override
    default String getServiceName() {
        return NAME;
    }
}
