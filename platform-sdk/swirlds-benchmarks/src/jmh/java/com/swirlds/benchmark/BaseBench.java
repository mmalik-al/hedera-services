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

package com.swirlds.benchmark;

import com.swirlds.benchmark.config.BenchmarkConfig;
import com.swirlds.common.config.export.ConfigExport;
import com.swirlds.common.config.singleton.ConfigurationHolder;
import com.swirlds.common.constructable.ConstructableRegistry;
import com.swirlds.common.constructable.ConstructableRegistryException;
import com.swirlds.common.crypto.config.CryptoConfig;
import com.swirlds.common.io.utility.TemporaryFileBuilder;
import com.swirlds.common.metrics.config.MetricsConfig;
import com.swirlds.config.api.Configuration;
import com.swirlds.config.api.ConfigurationBuilder;
import com.swirlds.config.extensions.sources.LegacyFileConfigSource;
import com.swirlds.merkledb.config.MerkleDbConfig;
import com.swirlds.virtualmap.config.VirtualMapConfig;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.ForkJoinPool;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.openjdk.jmh.annotations.Level;
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.TearDown;
import org.openjdk.jmh.annotations.Timeout;

@State(Scope.Benchmark)
@Timeout(time = Integer.MAX_VALUE)
public abstract class BaseBench {

    private static final Logger logger = LogManager.getLogger(BaseBench.class);

    protected static final String RUN_DELIMITER = "--------------------------------";

    @Param({"100"})
    public int numFiles = 100;

    @Param({"100000"})
    public int numRecords = 100_000;

    @Param({"1000000"})
    public int maxKey = 1_000_000;

    // 8 - VirtualLongKey, 8+ - generic VirtualKey
    @Param({"8"})
    public int keySize = 8;

    @Param({"128"})
    public int recordSize = 128;

    @Param({"32"})
    public int numThreads = 32;

    abstract String benchmarkName();

    private static final int SKEW = 2;
    private static final int RECORD_SIZE_MIN = 8;

    /* Directory for the entire benchmark */
    private static Path benchDir;
    /* Directory for each iteration */
    private Path testDir;
    /* Verify benchmark results */
    protected boolean verify;

    private BenchmarkConfig benchmarkConfig;

    private static BenchmarkConfig loadConfig() throws IOException {
        final Configuration configuration = ConfigurationBuilder.create()
                .withSource(new LegacyFileConfigSource(Path.of(".", "settings.txt")))
                .withConfigDataType(BenchmarkConfig.class)
                .withConfigDataType(VirtualMapConfig.class)
                .withConfigDataType(MerkleDbConfig.class)
                .withConfigDataType(MetricsConfig.class)
                .withConfigDataType(CryptoConfig.class)
                .build();
        ConfigurationHolder.getInstance().setConfiguration(configuration);

        final StringBuilder settingsUsed = new StringBuilder();
        ConfigExport.addConfigContents(configuration, settingsUsed);
        try (OutputStream os = Files.newOutputStream(Path.of(".", "settingsUsed.txt"))) {
            os.write(settingsUsed.toString().getBytes(StandardCharsets.UTF_8));
        }
        return configuration.getConfigData(BenchmarkConfig.class);
    }

    @Setup
    public void setup() throws IOException {
        benchmarkConfig = loadConfig();
        logger.info("Benchmark configuration: {}", benchmarkConfig);
        logger.info("Build: {}", Utils.buildVersion());

        final String data = benchmarkConfig.benchmarkData();
        if (data == null || data.isBlank()) {
            benchDir = Files.createTempDirectory(benchmarkName());
        } else {
            benchDir = Files.createDirectories(Path.of(data).resolve(benchmarkName()));
        }

        TemporaryFileBuilder.overrideTemporaryFileLocation(benchDir.resolve("tmp"));

        try {
            final ConstructableRegistry registry = ConstructableRegistry.getInstance();
            registry.registerConstructables("com.swirlds.virtualmap");
            registry.registerConstructables("com.swirlds.merkledb");
            registry.registerConstructables("com.swirlds.benchmark");
            registry.registerConstructables("com.swirlds.common.crypto");
        } catch (ConstructableRegistryException ex) {
            logger.error("Failed to construct registry", ex);
        }

        verify = benchmarkConfig.verifyResult();

        BenchmarkKey.setKeySize(keySize);

        // recordSize = keySize + valueSize
        BenchmarkValue.setValueSize(Math.max(recordSize - keySize, RECORD_SIZE_MIN));

        if (numThreads <= 0) {
            numThreads = ForkJoinPool.getCommonPoolParallelism();
        }

        // Setup metrics system
        BenchmarkMetrics.start(benchmarkConfig);
    }

    @TearDown
    public void destroy() {
        BenchmarkMetrics.stop();
        if (!benchmarkConfig.saveDataDirectory()) {
            Utils.deleteRecursively(benchDir);
        }
    }

    @Setup(Level.Invocation)
    public void beforeTest() {
        BenchmarkMetrics.reset();
    }

    public void beforeTest(String name) {
        setTestDir(name);
    }

    public static Path getBenchDir() {
        return benchDir;
    }

    public Path getTestDir() {
        return testDir;
    }

    public void setTestDir(String name) {
        testDir = benchDir.resolve(name);
    }

    interface RunnableWithException {
        void run() throws Exception;
    }

    public void afterTest() throws Exception {
        afterTest(false, null);
    }

    public void afterTest(boolean keepTestDir) throws Exception {
        afterTest(keepTestDir, null);
    }

    public void afterTest(RunnableWithException runnable) throws Exception {
        afterTest(false, runnable);
    }

    public void afterTest(boolean keepTestDir, RunnableWithException runnable) throws Exception {
        BenchmarkMetrics.report();
        if (benchmarkConfig.printHistogram()) {
            // Class histogram is interesting before closing
            Utils.printClassHistogram(15);
        }
        if (runnable != null) {
            runnable.run();
        }
        if (!keepTestDir) {
            Utils.deleteRecursively(testDir);
        }
    }

    private long currentKey;
    private long currentRecord;

    protected void resetKeys() {
        currentKey = -1L;
        currentRecord = 0L;
    }

    /**
     * Randomly select next key id in ascending order.
     * numRecords values will be uniformly distributed between 0 and maxKey when SKEW == 1.
     * With larger SKEW, more values will be selected from the lower half of the interval.
     *
     * @return Next key id > lastKey and < maxKey
     */
    protected long nextAscKey() {
        for (; ; ) {
            if (Utils.randomLong(maxKey - ++currentKey) < (numRecords - currentRecord) * SKEW) {
                ++currentRecord;
                return currentKey;
            }
        }
    }

    /**
     * Return next random value id
     *
     * @return Next value id
     */
    protected long nextValue() {
        return Utils.randomLong();
    }

    public BenchmarkConfig getConfig() {
        return benchmarkConfig;
    }
}
