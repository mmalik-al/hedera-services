/*
 * Copyright (C) 2020-2024 Hedera Hashgraph, LLC
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

package com.swirlds.common.test.merkle;

import static com.swirlds.common.merkle.hash.MerkleHashChecker.checkHashAndLog;
import static com.swirlds.common.merkle.hash.MerkleHashChecker.getNodesWithInvalidHashes;
import static java.lang.System.identityHashCode;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.swirlds.common.crypto.Hash;
import com.swirlds.common.merkle.MerkleInternal;
import com.swirlds.common.merkle.MerkleNode;
import com.swirlds.common.merkle.crypto.MerkleCryptoFactory;
import com.swirlds.common.merkle.crypto.MerkleCryptography;
import com.swirlds.common.merkle.utility.DebugIterationEndpoint;
import com.swirlds.common.merkle.utility.MerkleTreeVisualizer;
import com.swirlds.common.test.fixtures.RandomUtils;
import com.swirlds.common.test.merkle.dummy.DummyMerkleInternal;
import com.swirlds.common.test.merkle.dummy.DummyMerkleInternal2;
import com.swirlds.common.test.merkle.dummy.DummyMerkleLeaf;
import com.swirlds.common.test.merkle.dummy.DummyMerkleNode;
import com.swirlds.common.test.merkle.dummy.SelfHashingDummyMerkleLeaf;
import com.swirlds.common.test.merkle.util.MerkleTestUtils;
import com.swirlds.test.framework.TestComponentTags;
import com.swirlds.test.framework.TestQualifierTags;
import com.swirlds.test.framework.TestTypeTags;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for merkle tree hashing
 */
@DisplayName("Merkle Hash Tests")
class MerkleHashTests {

    private static MerkleCryptography cryptography;

    @BeforeAll
    public static void setup() {
        cryptography = MerkleCryptoFactory.getInstance();
    }

    /**
     * Two merkle trees with the same topology should have the same hash.
     * Two different merkle trees will have the same hash when pigs fly.
     */
    @Test
    @Tag(TestTypeTags.FUNCTIONAL)
    @Tag(TestComponentTags.MERKLE)
    @DisplayName("Test Deterministic Hashing")
    void testDeterministicHashing() {
        final List<DummyMerkleNode> listI = MerkleTestUtils.buildTreeList();
        final List<DummyMerkleNode> listJ = MerkleTestUtils.buildTreeList();

        for (int i = 0; i < listI.size(); i++) {
            for (int j = 0; j < listJ.size(); j++) {
                final MerkleNode nodeI = listI.get(i);
                final MerkleNode nodeJ = listJ.get(j);
                if (nodeI == null || nodeJ == null) {
                    // Hashes do not support null trees
                    continue;
                }

                if (i == j) {
                    assertEquals(cryptography.digestTreeSync(nodeI), cryptography.digestTreeSync(nodeJ));
                } else {
                    assertNotEquals(cryptography.digestTreeSync(nodeI), cryptography.digestTreeSync(nodeJ));
                }
            }
        }
    }

    /**
     * Verify that the hash generated by an asynchronous hasher matches that of a synchronous hasher
     */
    @Test
    @Tag(TestTypeTags.FUNCTIONAL)
    @Tag(TestComponentTags.MERKLE)
    @DisplayName("Test Asynchronous Hashing")
    void testAsynchronousHashing() throws InterruptedException, ExecutionException {
        final List<DummyMerkleNode> listI = MerkleTestUtils.buildTreeList();
        final List<DummyMerkleNode> listJ = MerkleTestUtils.buildTreeList();

        for (int i = 0; i < listI.size(); i++) {
            for (int j = 0; j < listJ.size(); j++) {
                final DummyMerkleNode nodeI = listI.get(i);
                final DummyMerkleNode nodeJ = listJ.get(j);
                if (nodeI == null || nodeJ == null) {
                    // Null can not be hashed
                    continue;
                }

                if (i == j) {
                    assertEquals(
                            cryptography.digestTreeSync(nodeI),
                            cryptography.digestTreeAsync(nodeJ).get());
                } else {
                    assertNotEquals(
                            cryptography.digestTreeSync(nodeI),
                            cryptography.digestTreeAsync(nodeJ).get());
                }
            }
        }
    }

    @Test
    @Tag(TestTypeTags.FUNCTIONAL)
    @Tag(TestComponentTags.MERKLE)
    @Tag(TestQualifierTags.AT_SCALE)
    @Tag(TestQualifierTags.TIME_CONSUMING)
    @DisplayName("Test Asynchronous Hashing Large Random Tree")
    void testAsynchronousHashingLargeRandomTree() throws InterruptedException, ExecutionException {
        final DummyMerkleNode tree1 = MerkleTestUtils.generateRandomTree(0, 2, 1, 1, 0, 3, 1, 0.08);
        final DummyMerkleNode tree2 = MerkleTestUtils.generateRandomTree(0, 2, 1, 1, 0, 3, 1, 0.08);

        // For the sake of sanity, make sure the base trees are equivalent
        assertTrue(MerkleTestUtils.areTreesEqual(tree1, tree2), "trees should be equal");

        MerkleTestUtils.printTreeStats(tree1);

        assertEquals(
                cryptography.digestTreeSync(tree1),
                cryptography.digestTreeAsync(tree2).get());
    }

    /**
     * Verify that each node is only hashed once.
     */
    @Test
    @Tag(TestTypeTags.FUNCTIONAL)
    @Tag(TestComponentTags.MERKLE)
    @DisplayName("Double Hashing Test")
    void doubleHashingTest() {
        final MerkleNode tree = MerkleTestUtils.buildLessSimpleTree();
        final MerkleNode subtree = MerkleTestUtils.buildLessSimpleTree();

        // Hash the subtree. This subtree should not allow itself to be hashed twice.
        cryptography.digestTreeSync(subtree);

        final Map<Integer, Hash> hashes = new HashMap<>();
        subtree.forEachNode((node) -> {
            if (node != null) {
                hashes.put(identityHashCode(node), node.getHash());
            }
        });

        // Add the subtree
        final MerkleInternal treeRoot = tree.cast();
        treeRoot.setChild(treeRoot.getNumberOfChildren(), subtree);

        // Hash the tree. Should not need to hash the already hashed subtree.
        cryptography.digestTreeSync(tree);

        // If a node is rehashed then the hash will be an equivalent but distinct object
        tree.forEachNode((node) -> {
            final int identityHashCode = identityHashCode(node);
            if (node != null && hashes.containsKey(identityHashCode)) {
                assertSame(hashes.get(identityHashCode), node.getHash(), "hash should not be a different object");
            }
        });
    }

    /**
     * Utility function that writes a tree's hash to a file.
     */
    void writeTreeToFile(final MerkleNode tree, final String filePath) throws IOException {
        final FileOutputStream stream = new FileOutputStream(filePath);
        stream.write(cryptography.digestTreeSync(tree).getValue());
        stream.close();
    }

    /**
     * This test verifies that two MerkleInternal nodes with different types
     * but the same children hash to different values.
     */
    @Test
    @Tag(TestTypeTags.FUNCTIONAL)
    @Tag(TestComponentTags.MERKLE)
    @DisplayName("Internal Nodes With Different Types")
    void internalNodesWithDifferentTypes() {

        final DummyMerkleInternal node1 = new DummyMerkleInternal();
        final DummyMerkleInternal2 node2 = new DummyMerkleInternal2();

        // Compare two nodes without leaves
        cryptography.digestTreeSync(node1);
        cryptography.digestTreeSync(node2);
        assertNotEquals(node1.getHash(), node2.getHash());

        // Compare two nodes with leaves
        final DummyMerkleLeaf A = new DummyMerkleLeaf("A");
        final DummyMerkleLeaf B = new DummyMerkleLeaf("B");
        final DummyMerkleLeaf C = new DummyMerkleLeaf("C");

        node1.setHash(null);
        node2.setHash(null);

        node1.setChild(0, A);
        node2.setChild(0, A);
        node1.setChild(1, B);
        node2.setChild(1, B);
        node1.setChild(2, C);
        node2.setChild(2, C);

        cryptography.digestTreeSync(node1);
        cryptography.digestTreeSync(node2);
        assertNotEquals(node1.getHash(), node2.getHash());
        assertNotEquals(node1.getHash(), node2.getHash());
    }

    @Test
    @Tag(TestTypeTags.FUNCTIONAL)
    @Tag(TestComponentTags.MERKLE)
    @DisplayName("Test Merkle Hash Checker")
    void testMerkleHashChecker() {
        final DummyMerkleNode tree = MerkleTestUtils.buildLessSimpleTreeExtended();
        cryptography.digestTreeSync(tree);

        final MerkleInternal root = tree.cast();

        // modify the hash of an internal node to something random
        final DummyMerkleNode mod1 = root.getChild(1);
        mod1.setHash(RandomUtils.randomHash());

        // modify the data of a leaf without changing the hash
        final DummyMerkleLeaf mod2 = root.getChild(2).asInternal().getChild(0);
        mod2.setValue("D*");

        // set the hash of one of the nodes to null
        final MerkleNode mod3 = root.getChild(2).asInternal().getChild(1);
        mod3.setHash(null);

        // check the hashes and add the mismatch to the list
        final List<MerkleNode> mismatch = getNodesWithInvalidHashes(root);

        // assert it works
        assertEquals(4, mismatch.size(), "3 nodes plus the root have invalid hash");
        assertSame(mod1, mismatch.get(0), "mod1 has a random hash");
        assertSame(mod2, mismatch.get(1), "mod2 changed its value");
        assertSame(mod3, mismatch.get(2), "mod3 set its hash to null");
        assertSame(root, mismatch.get(3), "root's hash is invalid due to invalid children's hash");

        assertFalse(checkHashAndLog(root, "unit test", 3), "hash should be invalid");
    }

    @Test
    @Tag(TestTypeTags.FUNCTIONAL)
    @Tag(TestComponentTags.MERKLE)
    @DisplayName("Hash Tree With Self Hashing Node")
    void hashTreeWithSelfHashingNode() {
        final DummyMerkleNode tree = MerkleTestUtils.buildLessSimpleTreeExtended();
        tree.asInternal().setChild(3, new SelfHashingDummyMerkleLeaf("asdf"));
        cryptography.digestTreeSync(tree);
        tree.forEachNode((node) -> assertNotNull(node.getHash(), "all nodes should be hashed"));

        final DummyMerkleNode tree2 = MerkleTestUtils.buildLessSimpleTreeExtended();
        final SelfHashingDummyMerkleLeaf leaf = new SelfHashingDummyMerkleLeaf("asdf");
        leaf.setReturnNullForHash(true);
        tree2.asInternal().setChild(3, leaf);

        assertThrows(
                UnsupportedOperationException.class,
                () -> cryptography.digestTreeSync(tree2),
                "if a self hashing node returns null then we should fail");
    }

    /**
     * This test validates that
     * {@link MerkleTreeVisualizer} does
     * not print information for too many nodes.
     */
    @Test
    @Tag(TestTypeTags.FUNCTIONAL)
    @Tag(TestComponentTags.MERKLE)
    @DisplayName("generateHashDebugString() Test")
    void generateHashDebugStringTest() throws ExecutionException, InterruptedException {

        final MerkleInternal tree = MerkleTestUtils.buildLessSimpleTreeExtended();
        tree.setChild(3, new NoTraversalDummyMerkleInternal());
        tree.getChild(3).asInternal().setChild(0, new DummyMerkleInternal("should not appear"));

        MerkleCryptoFactory.getInstance().digestTreeAsync(tree).get();

        final String debugString = new MerkleTreeVisualizer(tree).setDepth(2).render();

        // 8 nodes are at depth 2 or higher (in buildLessSimpleTreeExtended) plus the additional node of type
        // NoTraversalDummyMerkleInternal. The child of NoTraversalDummyMerkleInternal is at depth 2, but should
        // be ignored due to the @DebugIterationEndpoint annotation.
        final int expectedNodeCount = 9;

        final String[] lines = debugString.split("\n");

        System.out.println(debugString);
        assertEquals(expectedNodeCount, lines.length, "number of nodes should match expected");
    }

    @Test
    @Tag(TestTypeTags.FUNCTIONAL)
    @Tag(TestComponentTags.MERKLE)
    @DisplayName("Exception Is Rethrown Test")
    void exceptionIsRethrownTest() {

        final MerkleInternal tree = MerkleTestUtils.buildLessSimpleTreeExtended();
        ((DummyMerkleLeaf) tree.getChild(0)).setThrowWhenHashed(true);

        // This should not throw and should complete in a reasonable amount of time.
        final Future<Hash> future = MerkleCryptoFactory.getInstance().digestTreeAsync(tree);

        // This should throw the internal exception that was encountered
        assertThrows(ExecutionException.class, future::get, "expected hashing to fail");
    }

    /**
     * This internal node implementation is marked with an annotation that prevents the debug hash string method
     * from iterating to its children.
     */
    @DebugIterationEndpoint
    private static class NoTraversalDummyMerkleInternal extends DummyMerkleInternal {}
}
