/*
 * Copyright (C) 2016-2023 Hedera Hashgraph, LLC
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

package com.swirlds.platform.reconnect;

import com.hedera.pbj.runtime.io.ReadableSequentialData;
import com.hedera.pbj.runtime.io.WritableSequentialData;
import com.swirlds.common.io.streams.SerializableDataInputStream;
import com.swirlds.common.io.streams.SerializableDataOutputStream;
import com.swirlds.common.utility.CommonUtils;
import com.swirlds.merkledb.serialize.ValueSerializer;
import java.io.IOException;
import java.nio.ByteBuffer;

public class TestValueSerializerMerkleDb implements ValueSerializer<TestValue> {

    @Override
    public long getClassId() {
        return 53543454;
    }

    @Override
    public int getVersion() {
        return 1;
    }

    @Override
    public long getCurrentDataVersion() {
        return 1;
    }

    @Override
    public int getSerializedSize() {
        return VARIABLE_DATA_SIZE;
    }

    @Override
    public int getTypicalSerializedSize() {
        return 20; // guesstimation
    }

    @Override
    public void serialize(final TestValue data, final WritableSequentialData out) {
        final String s = data.getValue();
        final byte[] bytes = CommonUtils.getNormalisedStringBytes(s);
        out.writeInt(bytes.length);
        out.writeBytes(bytes);
    }

    @Override
    @Deprecated(forRemoval = true)
    public int serialize(TestValue data, ByteBuffer buffer) throws IOException {
        final String s = data.getValue();
        final byte[] bytes = CommonUtils.getNormalisedStringBytes(s);
        buffer.putInt(bytes.length);
        buffer.put(bytes);
        return Integer.BYTES + bytes.length;
    }

    @Override
    public TestValue deserialize(final ReadableSequentialData in) {
        final int length = in.readInt();
        final byte[] bytes = new byte[length];
        in.readBytes(bytes);
        final String s = CommonUtils.getNormalisedStringFromBytes(bytes);
        return new TestValue(s);
    }

    @Override
    @Deprecated(forRemoval = true)
    public TestValue deserialize(ByteBuffer buffer, long dataVersion) throws IOException {
        final int length = buffer.getInt();
        final byte[] bytes = new byte[length];
        buffer.get(bytes);
        final String s = CommonUtils.getNormalisedStringFromBytes(bytes);
        return new TestValue(s);
    }
}
