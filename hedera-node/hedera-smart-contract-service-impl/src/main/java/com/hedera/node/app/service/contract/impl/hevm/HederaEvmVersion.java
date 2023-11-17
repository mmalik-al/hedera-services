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

package com.hedera.node.app.service.contract.impl.hevm;

import java.util.Map;

public enum HederaEvmVersion {
    VERSION_030("v0.30"),
    VERSION_034("v0.34"),
    VERSION_038("v0.38"),
    VERSION_045("v0.45");

    public static final Map<String, HederaEvmVersion> EVM_VERSIONS = Map.of(
            VERSION_030.key(), VERSION_030,
            VERSION_034.key(), VERSION_034,
            VERSION_038.key(), VERSION_038,
            VERSION_045.key(), VERSION_045);

    HederaEvmVersion(String key) {
        this.key = key;
    }

    private final String key;

    public String key() {
        return key;
    }
}
