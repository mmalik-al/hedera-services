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

package com.swirlds.platform.state.editor;

import com.swirlds.cli.utility.SubcommandOf;
import com.swirlds.common.merkle.MerkleNode;
import com.swirlds.common.merkle.utility.MerkleTreeVisualizer;
import picocli.CommandLine;

@CommandLine.Command(
        name = "ls",
        mixinStandardHelpOptions = true,
        description = "Print information about nodes at this position in the state.")
@SubcommandOf(StateEditorRoot.class)
public class StateEditorLs extends StateEditorOperation {

    private String path = "";
    private int depth = 10;
    private boolean verbose = false;

    @CommandLine.Parameters(arity = "0..1", description = "The route to show.")
    private void setPath(final String path) {
        this.path = path;
    }

    @CommandLine.Option(
            names = {"-d", "--depth"},
            description = "The maximum depth to show, relative to the current working route.")
    private void setDepth(final int depth) {
        this.depth = depth;
    }

    @CommandLine.Option(
            names = {"-v", "--verbose"},
            description = "Enable verbose mode, where we don't ignore nodes inside the map classes.")
    private void setVerbose(final boolean verbose) {
        this.verbose = verbose;
    }

    /**
     * {@inheritDoc}
     */
    @SuppressWarnings("java:S106")
    @Override
    public void run() {
        final MerkleNode node = getStateEditor().getRelativeNode(path);
        final MerkleTreeVisualizer visualizer = new MerkleTreeVisualizer(node)
                .setDepth(depth)
                .setIgnoreDepthAnnotations(verbose)
                .setHashLength(8)
                .setUseColors(true);
        System.out.println("\n" + visualizer);
    }
}
