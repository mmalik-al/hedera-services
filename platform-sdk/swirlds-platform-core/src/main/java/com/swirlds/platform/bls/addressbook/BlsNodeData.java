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

package com.swirlds.platform.bls.addressbook;

import com.swirlds.platform.bls.crypto.BlsPublicKey;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/** Class containing data relevant to BLS, for an individual node */
public class BlsNodeData {
    /** The number of shares the node has */
    private int shareCount;

    /**
     * The IBE public key of the node
     * <p>
     * Will be null if the CRS protocol hasn't been completed yet
     */
    @Nullable
    private BlsPublicKey ibePublicKey;

    /** List of shareIds that are owned by the node */
    @NonNull
    private List<Integer> shareIds;

    /**
     * Constructor
     *
     * @param stake        the consensus stake the node holds
     * @param ibePublicKey the node's IBE public key
     */
    public BlsNodeData(final long stake, @Nullable final BlsPublicKey ibePublicKey) {
        if (stake > Integer.MAX_VALUE || stake < 0) {
            throw new IllegalArgumentException(String.format("Invalid stake value: [%s]", stake));
        }

        this.shareCount = (int) stake;
        this.ibePublicKey = ibePublicKey;
        this.shareIds = new ArrayList<>();
    }

    /**
     * Copy constructor
     *
     * @param otherNodeData the object being copied
     */
    public BlsNodeData(@NonNull final BlsNodeData otherNodeData) {
        Objects.requireNonNull(otherNodeData, "otherNodeData must not be null");

        this.shareCount = otherNodeData.shareCount;
        this.shareIds = new ArrayList<>(otherNodeData.shareIds);

        if (otherNodeData.ibePublicKey == null) {
            this.ibePublicKey = null;
        } else {
            this.ibePublicKey = new BlsPublicKey(otherNodeData.ibePublicKey);
        }
    }

    /**
     * Sets the {@link #shareIds} list
     *
     * @param shareIds the new shareId list
     */
    public void setShareIds(@NonNull final List<Integer> shareIds) {
        Objects.requireNonNull(shareIds, "shareIds must not be null");

        this.shareIds = Collections.unmodifiableList(shareIds);
    }

    /**
     * Gets the number of shares the node owns
     *
     * @return the node's {@link #shareCount}
     */
    public int getShareCount() {
        return shareCount;
    }

    /**
     * Sets a new stake value for the node
     *
     * @param stake the new stake value
     */
    public void setStake(final long stake) {
        if (stake > Integer.MAX_VALUE || stake < 0) {
            throw new IllegalArgumentException(String.format("Invalid stake value: [%s]", stake));
        }

        this.shareCount = (int) stake;
    }

    /**
     * Gets the node's IBE public key
     *
     * @return the {@link #ibePublicKey}
     */
    @Nullable
    public BlsPublicKey getIbePublicKey() {
        return ibePublicKey;
    }

    /**
     * Gets an unmodifiable list of the shareIds owned by the node
     *
     * @return an unmodifiable view of {@link #shareIds}
     */
    @NonNull
    public List<Integer> getShareIds() {
        return shareIds;
    }

    /**
     * Sets the public key of the node. Allows genesis nodes to add IBE public keys after CRS generation, and before the
     * first keying
     *
     * <p>Throws an {@link IllegalStateException} if the node already has a public key
     *
     * @param publicKey the new public key
     */
    public void setIbePublicKey(@NonNull final BlsPublicKey publicKey) {
        Objects.requireNonNull(publicKey, "publicKey must not be null");

        if (ibePublicKey != null) {
            throw new IllegalStateException("Cannot overwrite existing IBE public key");
        }

        ibePublicKey = publicKey;
    }
}
