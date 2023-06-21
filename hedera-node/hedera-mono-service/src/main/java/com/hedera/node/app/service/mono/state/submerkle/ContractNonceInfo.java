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

package com.hedera.node.app.service.mono.state.submerkle;

import static com.hedera.node.app.service.mono.state.serdes.IoUtils.readNullableSerializable;
import static com.hedera.node.app.service.mono.state.serdes.IoUtils.writeNullableSerializable;

import com.google.common.base.MoreObjects;
import com.swirlds.common.io.SelfSerializable;
import com.swirlds.common.io.streams.SerializableDataInputStream;
import com.swirlds.common.io.streams.SerializableDataOutputStream;
import java.io.IOException;

public class ContractNonceInfo implements SelfSerializable {

    static final int MERKLE_VERSION = 1;

    static final long RUNTIME_CONSTRUCTABLE_ID = 0x18ec32eaf9371551L;

    private EntityId contractId;
    private long nonce;

    public ContractNonceInfo() {}

    public ContractNonceInfo(final EntityId contractId, final long nonce) {
        this.contractId = contractId;
        this.nonce = nonce;
    }

    @Override
    public void deserialize(final SerializableDataInputStream in, final int version) throws IOException {
        nonce = in.readLong();
        contractId = readNullableSerializable(in);
    }

    @Override
    public void serialize(final SerializableDataOutputStream out) throws IOException {
        out.writeLong(nonce);
        writeNullableSerializable(contractId, out);
    }

    @Override
    public long getClassId() {
        return RUNTIME_CONSTRUCTABLE_ID;
    }

    @Override
    public int getVersion() {
        return MERKLE_VERSION;
    }

    @Override
    public String toString() {
        return MoreObjects.toStringHelper(this)
                .add("contractId", contractId)
                .add("nonce", nonce)
                .toString();
    }

    public EntityId getContractId() {
        return contractId;
    }

    public long getNonce() {
        return nonce;
    }

    public com.hederahashgraph.api.proto.java.ContractNonceInfo toGrpc() {
        final var grpc = com.hederahashgraph.api.proto.java.ContractNonceInfo.newBuilder();
        if (contractId != null) {
            grpc.setContractId(contractId.toGrpcContractId());
        }
        grpc.setNonce(nonce);
        return grpc.build();
    }
}
