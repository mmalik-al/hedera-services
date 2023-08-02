/*
 * Copyright (C) 2021-2023 Hedera Hashgraph, LLC
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

package com.swirlds.merkledb.files;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.swirlds.common.io.utility.TemporaryFileBuilder;
import com.swirlds.merkledb.collections.LongListHeap;
import com.swirlds.test.framework.TestTypeTags;
import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Random;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Stream;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.core.config.Configurator;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Tags;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * Hammer the merging subsystem with as many small merges as possible to try to overwhelm it.
 */
class DataFileCollectionMergeHammerTest {

    @BeforeAll
    public static void setup() {
        Configurator.setRootLevel(Level.WARN);
    }

    @AfterAll
    public static void cleanUp() {
        Configurator.reconfigure();
    }

    @SuppressWarnings("unchecked")
    @ParameterizedTest
    @MethodSource("provideForBenchmark")
    @Tags({@Tag("Speed")})
    void benchmark(int numFiles, int maxEntriesPerFile) throws IOException {
        final Path tempFileDir = TemporaryFileBuilder.buildTemporaryDirectory("DataFileCollectionMergeHammerTest");
        assertDoesNotThrow(() -> {
            final LongListHeap index = new LongListHeap();
            final var serializer = new ExampleFixedSizeDataSerializer();
            final var coll = new DataFileCollection<>(
                    tempFileDir.resolve("benchmark"), "benchmark", serializer, (key, dataLocation, dataValue) -> {});
            final var compactor = new DataFileCompactor(coll);

            final Random rand = new Random(777);
            for (int i = 0; i < numFiles; i++) {
                coll.startWriting();
                final int numRecords = rand.nextInt(maxEntriesPerFile);
                long prevId = 0;
                for (int j = 0; j < numRecords; j++) {
                    final long id = prevId + rand.nextInt((maxEntriesPerFile * 10) - (int) prevId);
                    if (id == prevId) {
                        break;
                    }
                    prevId = id;
                    index.put(id, coll.storeDataItem(new long[] {id, rand.nextLong()}));
                }
                coll.endWriting(index.size() * 2L - 1, index.size() * 2L).setFileCompleted();
            }

            final long start = System.currentTimeMillis();
            final var filesToMerge = (List<DataFileReader<?>>) (Object) coll.getAllCompletedFiles();
            compactor.compactFiles(index, filesToMerge);
            System.out.println(numFiles + " files took " + (System.currentTimeMillis() - start) + "ms");
            index.close();
        });
    }

    private static Stream<Arguments> provideForBenchmark() {
        return Stream.of(
                Arguments.of(2, 100),
                Arguments.of(2, 1000),
                Arguments.of(2, 10_000),
                Arguments.of(2, 100_000),
                Arguments.of(10, 100),
                Arguments.of(10, 1000),
                Arguments.of(10, 10_000),
                Arguments.of(10, 100_000),
                Arguments.of(100, 100),
                Arguments.of(100, 1000),
                Arguments.of(100, 10_000),
                Arguments.of(100, 100_000),
                Arguments.of(1000, 100),
                Arguments.of(1000, 1000),
                Arguments.of(1000, 10_000),
                Arguments.of(1000, 100_000),
                Arguments.of(1000, 1_000_000));
    }

    @SuppressWarnings("unchecked")
    @Test
    @Tags({@Tag(TestTypeTags.HAMMER)})
    void hammer() throws IOException, InterruptedException, ExecutionException {
        final Path tempFileDir = TemporaryFileBuilder.buildTemporaryDirectory("DataFileCollectionMergeHammerTest");
        final LongListHeap index = new LongListHeap();
        final var serializer = new ExampleFixedSizeDataSerializer();
        final var coll = new DataFileCollection<>(
                tempFileDir.resolve("hammer"), "hammer", serializer, (key, dataLocation, dataValue) -> {});
        final var compactor = new DataFileCompactor(coll);

        final Random rand = new Random(777);
        final AtomicBoolean stop = new AtomicBoolean(false);
        ExecutorService writerService = Executors.newSingleThreadExecutor();
        Future<?> writerFuture = writerService.submit(() -> {
            while (!stop.get()) {
                try {
                    coll.startWriting();
                    final int numRecords = rand.nextInt(2500);
                    long prevId = 0;
                    for (int i = 0; i < numRecords; i++) {
                        final long id = prevId + rand.nextInt((10_000) - (int) prevId);
                        if (id == prevId) {
                            break;
                        }
                        prevId = id;
                        index.put(id, coll.storeDataItem(new long[] {id, rand.nextLong()}));
                    }
                    coll.endWriting(index.size() * 2L - 1, index.size() * 2L).setFileCompleted();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        });

        ExecutorService compactorService = Executors.newSingleThreadExecutor();
        Future<?> compactorFuture = compactorService.submit(() -> {
            while (!stop.get()) {
                try {
                    final List<DataFileReader<?>> filesToMerge =
                            (List<DataFileReader<?>>) (Object) coll.getAllCompletedFiles();
                    if (filesToMerge.size() > compactor.getMinNumberOfFilesToMerge()) {
                        System.out.println(filesToMerge.size());
                    }
                    if (filesToMerge.size() > 10000) {
                        stop.set(true);
                    }
                    compactor.compactFiles(index, filesToMerge);
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        });

        for (int i = 0; i < 100; i++) {
            System.out.println("Iteration " + i);
            compactor.pauseCompaction();
            SECONDS.sleep(3);
            compactor.resumeCompaction();
            SECONDS.sleep(1);
        }
        stop.set(true);
        compactorFuture.get();
        writerFuture.get();
        final var filesToMerge = coll.getAllCompletedFiles();
        assertTrue(filesToMerge.size() < 10000, "Too many files! We didn't keep up");
        coll.close();
        index.close();
    }
}
