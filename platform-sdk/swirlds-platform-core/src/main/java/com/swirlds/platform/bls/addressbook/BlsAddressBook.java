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

import com.swirlds.common.system.NodeId;
import com.swirlds.platform.bls.crypto.BlsPublicKey;
import com.swirlds.platform.bls.crypto.PublicKeyShares;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeSet;

/**
 * Class representing an address book. Will be conceptually merged into the existing platform address book
 */
public class BlsAddressBook {

    /** Mapping from nodeId to a data object describing the node */
    @NonNull
    private final Map<NodeId, BlsNodeData> nodeDataMap;

    /** A sorted set of nodeIds */
    @NonNull
    private final SortedSet<NodeId> sortedNodeIds;

    /** Total number of shares belonging to all nodes in the address book */
    private int totalShares;

    /** The {@link PublicKeyShares} object, representing the public keys of all existing shares */
    @NonNull
    private PublicKeyShares publicKeyShares;

    /** True if nodes have been added or removed since calling {@link #recomputeTransientData()} */
    private boolean dirty;

    /** Constructor */
    public BlsAddressBook() {
        this.nodeDataMap = new HashMap<>();
        this.totalShares = 0;
        this.sortedNodeIds = new TreeSet<>();
        this.publicKeyShares = new PublicKeyShares();
        this.dirty = true;
    }

    /**
     * Copy constructor
     *
     * @param otherAddressBook the address book being copied
     */
    public BlsAddressBook(@NonNull final BlsAddressBook otherAddressBook) {
        Objects.requireNonNull(otherAddressBook, "otherAddressBook must not be null");

        final Map<NodeId, BlsNodeData> copiedNodeDataMap = new HashMap<>();

        for (final Map.Entry<NodeId, BlsNodeData> nodeDataEntry : otherAddressBook.nodeDataMap.entrySet()) {
            copiedNodeDataMap.put(nodeDataEntry.getKey(), new BlsNodeData(nodeDataEntry.getValue()));
        }

        this.nodeDataMap = copiedNodeDataMap;
        this.sortedNodeIds = new TreeSet<>(otherAddressBook.sortedNodeIds);
        this.totalShares = otherAddressBook.getTotalShares();
        this.publicKeyShares = new PublicKeyShares(otherAddressBook.publicKeyShares);
        this.dirty = otherAddressBook.dirty;
    }

    /**
     * Gets the sorted list of nodeIds in the address book
     *
     * @return a sorted list of node ids
     */
    @NonNull
    public SortedSet<NodeId> getSortedNodeIds() {
        return Collections.unmodifiableSortedSet(sortedNodeIds);
    }

    /**
     * Gets the number of shares belonging to a node. A node's share count is equal to its stake
     *
     * @param nodeId which node's share count is being requested
     * @return the corresponding share count
     */
    public int getNodeShareCount(@NonNull final NodeId nodeId) {
        return getNodeData(nodeId).getShareCount();
    }

    /**
     * Gets the total number of shares held by all nodes in the address book
     *
     * @return the value of {@link #totalShares}
     */
    public int getTotalShares() {
        checkDirty("getTotalShares");

        return totalShares;
    }

    /**
     * Gets the public IBE key associated with a node id
     *
     * @param nodeId which node's public key is being requested
     * @return the corresponding IBE public key
     */
    @Nullable
    public BlsPublicKey getIbePublicKey(@NonNull final NodeId nodeId) {
        return getNodeData(nodeId).getIbePublicKey();
    }

    /**
     * Recomputes values that are derived from the existing composition of the address book
     *
     * <p>Sets {@link #dirty} to false after doing calculations
     */
    public void recomputeTransientData() {
        totalShares = 0;

        int shareId = 1;
        for (final NodeId nodeId : sortedNodeIds) {
            final BlsNodeData nodeData = getNodeData(nodeId);

            final int nodeShareCount = nodeData.getShareCount();
            totalShares = Math.addExact(totalShares, nodeShareCount);

            final List<Integer> nodeShareIds = new ArrayList<>();

            for (int shareCount = 0; shareCount < nodeShareCount; ++shareCount) {
                nodeShareIds.add(shareId);
                ++shareId;
            }

            nodeData.setShareIds(nodeShareIds);
        }

        dirty = false;
    }

    /**
     * Create a new entry in the address book
     *
     * @param nodeId       node id of the new node
     * @param stake        consensus stake belonging to the new node
     * @param ibePublicKey IBE public key of the new node
     */
    public void addNode(@NonNull final NodeId nodeId, final long stake, @Nullable final BlsPublicKey ibePublicKey) {
        Objects.requireNonNull(nodeId, "nodeId must not be null");

        nodeDataMap.put(nodeId, new BlsNodeData(stake, ibePublicKey));
        sortedNodeIds.add(nodeId);

        dirty = true;
    }

    /**
     * Removes a node from the address book
     *
     * @param nodeId the id of the node to remove
     */
    public void removeNode(@NonNull final NodeId nodeId) {
        Objects.requireNonNull(nodeId, "nodeId must not be null");

        nodeDataMap.remove(nodeId);
        sortedNodeIds.remove(nodeId);

        dirty = true;
    }

    /**
     * Sets the {@link PublicKeyShares} of the address book
     *
     * @param publicKeyShares the new public key shares
     */
    public void setPublicKeyShares(@NonNull final PublicKeyShares publicKeyShares) {
        this.publicKeyShares = Objects.requireNonNull(publicKeyShares, "publicKeyShares must not be null");
    }

    /**
     * Gets the {@link PublicKeyShares} of the address book
     *
     * @return the public key shares
     */
    @NonNull
    public PublicKeyShares getPublicKeyShares() {
        return publicKeyShares;
    }

    /**
     * Checks if a node is in this address book
     *
     * @param nodeId the id of the node to check
     * @return true if the node is in this address book, otherwise false
     */
    public boolean containsNode(@NonNull final NodeId nodeId) {
        Objects.requireNonNull(nodeId, "nodeId must not be null");

        return nodeDataMap.containsKey(nodeId);
    }

    /**
     * Gets a list of share ids that belong to a single node
     *
     * @param nodeId the id of the node to get share ids for
     * @return a list of share ids that belong to nodeId
     */
    @NonNull
    public List<Integer> getNodeShareIds(@NonNull final NodeId nodeId) {
        checkDirty("getNodeShareIds");

        return getNodeData(nodeId).getShareIds();
    }

    /**
     * Gets the combined share count of a set of nodes
     *
     * @param nodeSet the set of nodes to get the combined share count of
     * @return the combined share count of the set of nodes
     */
    public int getCombinedShares(@NonNull final Set<NodeId> nodeSet) {
        Objects.requireNonNull(nodeSet, "nodeSet must not be null");

        int combinedShares = 0;

        for (final NodeId nodeId : nodeSet) {
            if (containsNode(nodeId)) {
                // we don't have to worry about overflow, since we check elsewhere that even the
                // total share count won't exceed integer bounds
                combinedShares += getNodeShareCount(nodeId);
            }
        }

        return combinedShares;
    }

    /**
     * Gets the number of shares owned by nodes that haven't been disqualified
     *
     * @param maliciousNodes the set of malicious nodes
     * @param offlineNodes   the set of offline nodes
     * @return the combined share count of nodes in neither malicious nor offline sets
     */
    public int getNonDisqualifiedShareCount(
            @NonNull final Set<NodeId> maliciousNodes, @NonNull final Set<NodeId> offlineNodes) {
        return getTotalShares() - getCombinedShares(maliciousNodes) - getCombinedShares(offlineNodes);
    }

    /**
     * Gets the minimum share count which satisfies a given threshold
     *
     * @param threshold the threshold the returned share count satisfies
     * @return the number of shares which satisfy the given threshold
     */
    public int getSharesSatisfyingThreshold(@Nullable final Threshold threshold) {
        if (sortedNodeIds.isEmpty()) {
            throw new IllegalStateException("Cannot determine threshold if address book contains no nodes");
        }

        // don't enforce a threshold
        if (threshold == null) {
            return 0;
        }

        // cannot overflow, since the value returned by getMinSatisfyingValue will always be <= the
        // whole value, which in this case is an int
        return (int) threshold.getMinValueMeetingThreshold(getTotalShares());
    }

    /**
     * Sets the IBE public key of a node. Serves only to allow genesis nodes to add their public keys after CRS
     * generation, and before initial key generation
     *
     * @param nodeId       the id of the node to set the public key for
     * @param ibePublicKey the node's new public key
     */
    public void setIbePublicKey(@NonNull final NodeId nodeId, @NonNull final BlsPublicKey ibePublicKey) {
        final BlsNodeData nodeData = getNodeData(nodeId);

        if (nodeData.getIbePublicKey() != null) {
            throw new IllegalStateException("Cannot overwrite existing IBE public key");
        }

        nodeData.setIbePublicKey(ibePublicKey);
    }

    /**
     * Sets a new stake value for a node
     *
     * @param nodeId   the id of the node to set the stake of
     * @param newStake the new consensus stake value for the node
     */
    public void setNodeStake(@NonNull final NodeId nodeId, final long newStake) {
        getNodeData(nodeId).setStake(newStake);

        dirty = true;
    }

    /**
     * Checks if the address book is dirty before performing an operation that requires a clean address book
     *
     * @param attemptedOperation the operation that is being attempted, for logging's sake in case address book is
     *                           dirty
     */
    private void checkDirty(@NonNull final String attemptedOperation) {
        Objects.requireNonNull(attemptedOperation, "attemptedOperation string must not be null");

        if (!dirty) {
            return;
        }

        throw new IllegalStateException("Cannot " + attemptedOperation + ", since address book is dirty");
    }

    /**
     * Safely gets the node data for a given node id that is known to exist in the address book
     *
     * @param nodeId the node to get the BlsNodeData for
     * @return the BlsNodeData for the given node id
     */
    @NonNull
    private BlsNodeData getNodeData(@NonNull final NodeId nodeId) {
        Objects.requireNonNull(nodeId, "nodeId must not be null");

        return Objects.requireNonNull(
                nodeDataMap.get(nodeId), String.format("nodeId %s is not in the address book", nodeId));
    }
}
