package com.swirlds.platform.roster;

import static com.swirlds.common.test.fixtures.RandomUtils.getRandomPrintSeed;
import static com.swirlds.common.test.fixtures.RandomUtils.randomString;
import static java.util.Collections.shuffle;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.swirlds.common.context.PlatformContext;
import com.swirlds.common.system.NodeId;
import com.swirlds.common.system.address.Address;
import com.swirlds.common.system.address.AddressBook;
import com.swirlds.common.test.fixtures.RandomAddressBookGenerator;
import com.swirlds.test.framework.context.TestPlatformContextBuilder;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;
import org.junit.jupiter.api.Test;

class RosterDiffGeneratorTests {

    /**
     * There should be sane behavior when the roster does not change.
     */
    @Test
    void noChangesTest() {
        final Random random = getRandomPrintSeed();

        final PlatformContext platformContext = TestPlatformContextBuilder.create().build();
        final RosterDiffGenerator generator = new RosterDiffGenerator(platformContext);

        final AddressBook roster = new RandomAddressBookGenerator(random).build();
        platformContext.getCryptography().digestSync(roster);

        // First round added should yield a null diff
        assertNull(generator.generateDiff(new UpdatedRoster(0, roster)));

        for (int i = 1; i < 100; i++) {
            final UpdatedRoster newRoster = new UpdatedRoster(i, roster);
            final RosterDiff diff = generator.generateDiff(newRoster);

            assertNotNull(diff);

            assertSame(newRoster, diff.newRoster());

            assertTrue(diff.rosterIsIdentical());
            assertFalse(diff.membershipChanged());
            assertFalse(diff.consensusWeightChanged());
            assertTrue(diff.addedNodes().isEmpty());
            assertTrue(diff.removedNodes().isEmpty());
            assertTrue(diff.modifiedNodes().isEmpty());
        }
    }

    @Test
    void randomChangesTest() {
        final Random random = getRandomPrintSeed(0); // TODO

        final PlatformContext platformContext = TestPlatformContextBuilder.create().build();
        final RosterDiffGenerator generator = new RosterDiffGenerator(platformContext);

        AddressBook previousRoster = new RandomAddressBookGenerator(random).setSize(32).build();
        assertNull(generator.generateDiff(new UpdatedRoster(0, previousRoster)));

        for (int round = 1; round < 100; round++) { // TODO can we use more rounds?

            final AddressBook newRoster = previousRoster.copy();

            final int addedNodeCount;
            if (random.nextDouble() < 1.0 / 3.0) {
                // 1/3 chance of adding a few nodes
                addedNodeCount = random.nextInt(1, 3);
            } else {
                addedNodeCount = 0;
            }

            final int removedNodeCount;
            if (random.nextDouble() < 1.0 / 3.0) {
                // 1/3 chance of removing up to 3 nodes
                final int newRosterSize = Math.max(1, previousRoster.getSize() - random.nextInt(1, 3));
                removedNodeCount = previousRoster.getSize() - newRosterSize;
            } else {
                removedNodeCount = 0;
            }

            final int modifiedNodeCount;
            if (random.nextDouble() < 1.0 / 3.0) {
                // 1/3 chance of modifying a few of the remaining nodes
                final int remainingNodes = previousRoster.getSize() - removedNodeCount;
                modifiedNodeCount = Math.min(remainingNodes, random.nextInt(1, 3));
            } else {
                modifiedNodeCount = 0;
            }

            // First, randomly remove nodes.
            final List<NodeId> currentNodes = new ArrayList<>(newRoster.getNodeIdSet());
            shuffle(currentNodes, random);
            final Set<NodeId> removedNodes = new HashSet<>();
            for (int i = 0; i < removedNodeCount; i++) {
                final NodeId nodeToRemove = currentNodes.get(i);
                removedNodes.add(nodeToRemove);
                newRoster.remove(nodeToRemove);
            }

            // Next, randomly modify remaining nodes.
            final boolean modifyConsensusWeight = random.nextDouble() < 1.0 / 3.0; // 1/3 chance of modifying weight
            final List<NodeId> remainingNodes = new ArrayList<>(newRoster.getNodeIdSet());
            shuffle(remainingNodes, random);
            final Set<NodeId> modifiedNodes = new HashSet<>();
            for (int i = 0; i < modifiedNodeCount; i++) {
                final NodeId nodeToModify = remainingNodes.get(i);
                modifiedNodes.add(nodeToModify);
                final Address address = newRoster.getAddress(nodeToModify);

                Address newAddress = address.copySetMemo(randomString(random, 32));
                if (modifyConsensusWeight) {
                    newAddress = newAddress.copySetWeight(newAddress.getWeight() + 1);
                }

                newRoster.add(newAddress);
            }

            // Finally, randomly add nodes.
            final Set<NodeId> addedNodes = new HashSet<>();
            for (int i = 0; i < addedNodeCount; i++) {
                final NodeId nodeToAdd = newRoster.getNextNodeId();
                addedNodes.add(nodeToAdd);

                final Address address = RandomAddressBookGenerator.addressWithRandomData(
                        random,
                        nodeToAdd,
                        random.nextLong(1, 100_000_000));

                newRoster.add(address);
            }

            final boolean membershipChanged = removedNodeCount != 0 || addedNodeCount != 0;
            final boolean consensusWeightChanged =
                    membershipChanged || (modifiedNodeCount != 0 && modifyConsensusWeight);
            final boolean rosterIsIdentical = !membershipChanged && !consensusWeightChanged && modifiedNodeCount == 0;

            platformContext.getCryptography().digestSync(newRoster);

            final UpdatedRoster updatedRoster = new UpdatedRoster(round, newRoster);
            final RosterDiff diff = generator.generateDiff(updatedRoster);

            assertNotNull(diff);
            assertSame(updatedRoster, diff.newRoster());

            assertEquals(rosterIsIdentical, diff.rosterIsIdentical());
            assertEquals(membershipChanged, diff.membershipChanged());
            assertEquals(consensusWeightChanged, diff.consensusWeightChanged());
            assertEquals(addedNodes, new HashSet<>(diff.addedNodes()));
            assertEquals(removedNodes, new HashSet<>(diff.removedNodes()));
            assertEquals(modifiedNodes, new HashSet<>(diff.modifiedNodes()));
        }
    }

}
