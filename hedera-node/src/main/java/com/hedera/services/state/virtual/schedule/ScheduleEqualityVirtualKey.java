/*-
 * ‌
 * Hedera Services Node
 * ​
 * Copyright (C) 2018 - 2022 Hedera Hashgraph, LLC
 * ​
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
 * ‍
 */

package com.hedera.services.state.virtual.schedule;

import java.io.IOException;
import java.nio.ByteBuffer;

import com.hedera.services.utils.MiscUtils;
import com.swirlds.common.io.streams.SerializableDataInputStream;
import com.swirlds.common.io.streams.SerializableDataOutputStream;
import com.swirlds.virtualmap.VirtualLongKey;

/**
 * A {@link com.swirlds.virtualmap.VirtualKey} for a {@link ScheduleVirtualValue#equalityCheckKey()}.
 */
public final class ScheduleEqualityVirtualKey implements VirtualLongKey {
	private static final long CLASS_ID = 0xcd76f4fba3967595L;
	static final int BYTES_IN_SERIALIZED_FORM = 8;

	private static final class ClassVersion {
		public static final int ORIGINAL = 1;
	}

	private long value;

	public ScheduleEqualityVirtualKey() {
		this(-1);
	}

	/**
	 * @param value the long value of an {@link EntityNum}
	 */
	public ScheduleEqualityVirtualKey(final long value) {
		this.value = value;
	}


	public static int sizeInBytes() {
		return BYTES_IN_SERIALIZED_FORM;
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public long getKeyAsLong() {
		return value;
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public long getClassId() {
		return CLASS_ID;
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public int getVersion() {
		return ClassVersion.ORIGINAL;
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public void serialize(final SerializableDataOutputStream out) throws IOException {
		out.writeLong(value);
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public void deserialize(final SerializableDataInputStream in, final int version) throws IOException {
		value = in.readLong();
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public void serialize(final ByteBuffer buffer) throws IOException {
		buffer.putLong(value);
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public void deserialize(final ByteBuffer buffer, final int version) throws IOException {
		value = buffer.getLong();
	}
	/**
	 * {@inheritDoc}
	 */
	@Override
	public boolean equals(final Object o) {
		if (this == o) {
			return true;
		}

		if (o == null || getClass() != o.getClass()) {
			return false;
		}

		final ScheduleEqualityVirtualKey that = (ScheduleEqualityVirtualKey) o;

		return value == that.value;
	}

	/**
	 * Verifies if the content from {@code buffer} is equal to the content of this instance.
	 *
	 * @param buffer
	 * 		The buffer with data to be compared with this class.
	 * @param version
	 * 		The version of the data inside the given {@code buffer}.
	 * @return {@code true} if the content from the buffer has the same data as this instance.
	 *        {@code false}, otherwise.
	 * @throws IOException
	 */
	public boolean equals(final ByteBuffer buffer, final int version) throws IOException {
		return buffer.getLong() == this.value;
	}


	/**
	 * {@inheritDoc}
	 */
	@Override
	public int hashCode() {
		return (int) MiscUtils.perm64(value);
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public String toString() {
		return "ScheduleEqualityVirtualKey{" +
				"value=" + value +
				'}';
	}
}
