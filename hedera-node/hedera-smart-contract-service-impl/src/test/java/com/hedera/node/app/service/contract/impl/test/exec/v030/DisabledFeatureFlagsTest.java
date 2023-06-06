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

package com.hedera.node.app.service.contract.impl.test.exec.v030;

import static org.junit.jupiter.api.Assertions.*;

import com.hedera.node.app.service.contract.impl.exec.v030.DisabledFeatureFlags;
import org.hyperledger.besu.evm.frame.MessageFrame;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class DisabledFeatureFlagsTest {
    @Mock
    private MessageFrame frame;

    private DisabledFeatureFlags subject = new DisabledFeatureFlags();

    @Test
    void everythingIsDisabled() {
        assertFalse(subject.isImplicitCreationEnabled(frame));
    }
}
