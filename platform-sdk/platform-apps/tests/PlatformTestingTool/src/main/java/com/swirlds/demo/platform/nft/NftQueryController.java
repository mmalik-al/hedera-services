/*
 * Copyright (C) 2022-2023 Hedera Hashgraph, LLC
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

package com.swirlds.demo.platform.nft;

import static com.swirlds.common.metrics.FloatFormats.FORMAT_9_6;
import static com.swirlds.logging.LogMarker.EXCEPTION;

import com.swirlds.common.metrics.SpeedometerMetric;
import com.swirlds.common.metrics.SpeedometerMetric.Config;
import com.swirlds.common.metrics.config.MetricsConfig;
import com.swirlds.common.system.Platform;
import com.swirlds.common.utility.AutoCloseableWrapper;
import com.swirlds.common.utility.throttle.Throttle;
import com.swirlds.config.api.Configuration;
import com.swirlds.demo.platform.PlatformTestingToolState;
import com.swirlds.demo.platform.nft.config.NftConfig;
import com.swirlds.demo.platform.nft.config.NftQueryConfig;
import com.swirlds.demo.platform.nft.config.NftQueryType;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * This object manages queries on old versions of the state for NFTs.
 */
public class NftQueryController {

    private static final Logger logger = LogManager.getLogger(NftQueryController.class);

    private SpeedometerMetric nftQueriesAnsweredPerSecond;

    private Platform platform;
    private int numberOfThreads;
    private Throttle throttle;
    private NftQueryType queryType;

    /**
     * Create a new query controller.
     *
     * @param nftConfig
     * 		configuration for an NFT test
     * @param platform
     * 		the current platform
     */
    public NftQueryController(final NftConfig nftConfig, final Platform platform) {
        if (nftConfig == null) {
            return;
        }

        final NftQueryConfig queryConfig = nftConfig.getQueryConfig();
        if (queryConfig == null) {
            return;
        }

        this.platform = platform;
        this.throttle = new Throttle(queryConfig.getQps());
        this.numberOfThreads = queryConfig.getNumberOfThreads();
        this.queryType = queryConfig.getQueryType();

        initStats();
    }

    private void initStats() {
        final Configuration configuration = platform.getContext().getConfiguration();
        final MetricsConfig metricsConfig = configuration.getConfigData(MetricsConfig.class);
        nftQueriesAnsweredPerSecond = this.platform
                .getContext()
                .getMetrics()
                .getOrCreate(new Config(metricsConfig, "NFT", "nftQueriesAnsweredPerSecond")
                        .withDescription("number of NFT queries have been answered per second")
                        .withFormat(FORMAT_9_6));

        NftSimpleQuerier.registerStats(this.platform);
    }

    /**
     * Start some threads that will query about NFTs in the background.
     */
    public void launch() {
        if (queryType == null) {
            return;
        }

        final ExecutorService queryThreadPool = Executors.newFixedThreadPool(this.numberOfThreads);
        for (int index = 0; index < this.numberOfThreads; index++) {
            queryThreadPool.execute(this::execute);
        }
    }

    private void execute() {
        //noinspection InfiniteLoopStatement
        while (true) {
            while (!this.throttle.allow()) {
                Thread.onSpinWait();
            }

            try (final AutoCloseableWrapper<PlatformTestingToolState> stateWrapper =
                    platform.getLatestImmutableState("NFTQueryController.execute()")) {
                final PlatformTestingToolState state = stateWrapper.get();
                if (state == null) {
                    continue;
                }

                try {
                    this.queryType.execute(state, this.nftQueriesAnsweredPerSecond);
                } catch (final Exception ex) {
                    logger.error(EXCEPTION.getMarker(), "Failed to run query {}", this.queryType, ex);
                }
            }
        }
    }
}
