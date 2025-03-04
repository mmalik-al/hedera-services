/*
 * Copyright (C) 2025 Hedera Hashgraph, LLC
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

package com.hedera.node.app.history.impl;

import static org.junit.jupiter.api.Assertions.*;

import com.hedera.hapi.node.state.roster.Roster;
import com.hedera.pbj.runtime.io.buffer.Bytes;
import java.util.Map;
import org.junit.jupiter.api.Test;

class HistoryLibraryCodecTest {
    private final HistoryLibraryCodec subject = new HistoryLibraryCodec();

    @Test
    void nothingSupportedYet() {
        assertThrows(UnsupportedOperationException.class, () -> subject.encodeHistory(Bytes.EMPTY, Bytes.EMPTY));
        assertThrows(UnsupportedOperationException.class, () -> subject.encodeAddressBook(Roster.DEFAULT, Map.of()));
    }
}
