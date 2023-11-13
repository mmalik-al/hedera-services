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

package com.swirlds.merkledb;

import static com.swirlds.common.threading.manager.AdHocThreadManager.getStaticThreadManager;
import static com.swirlds.common.units.UnitConstants.BYTES_TO_BITS;
import static com.swirlds.logging.legacy.LogMarker.ERROR;
import static com.swirlds.logging.legacy.LogMarker.EXCEPTION;
import static com.swirlds.logging.legacy.LogMarker.MERKLE_DB;
import static com.swirlds.merkledb.KeyRange.INVALID_KEY_RANGE;
import static com.swirlds.merkledb.MerkleDb.MERKLEDB_COMPONENT;
import static java.util.Objects.requireNonNull;

import com.swirlds.base.utility.ToStringBuilder;
import com.swirlds.common.config.singleton.ConfigurationHolder;
import com.swirlds.common.crypto.DigestType;
import com.swirlds.common.crypto.Hash;
import com.swirlds.common.metrics.Metrics;
import com.swirlds.common.threading.framework.config.ThreadConfiguration;
import com.swirlds.common.units.UnitConstants;
import com.swirlds.merkledb.collections.HashListByteBuffer;
import com.swirlds.merkledb.collections.LongList;
import com.swirlds.merkledb.collections.LongListDisk;
import com.swirlds.merkledb.collections.LongListOffHeap;
import com.swirlds.merkledb.collections.OffHeapUser;
import com.swirlds.merkledb.config.MerkleDbConfig;
import com.swirlds.merkledb.files.DataFileCollection;
import com.swirlds.merkledb.files.DataFileCompactor;
import com.swirlds.merkledb.files.DataFileReader;
import com.swirlds.merkledb.files.MemoryIndexDiskKeyValueStore;
import com.swirlds.merkledb.files.VirtualHashRecordSerializer;
import com.swirlds.merkledb.files.VirtualLeafRecordSerializer;
import com.swirlds.merkledb.files.hashmap.Bucket;
import com.swirlds.merkledb.files.hashmap.HalfDiskHashMap;
import com.swirlds.merkledb.files.hashmap.HalfDiskVirtualKeySet;
import com.swirlds.merkledb.files.hashmap.VirtualKeySetSerializer;
import com.swirlds.merkledb.serialize.KeyIndexType;
import com.swirlds.merkledb.serialize.KeySerializer;
import com.swirlds.virtualmap.VirtualKey;
import com.swirlds.virtualmap.VirtualLongKey;
import com.swirlds.virtualmap.VirtualValue;
import com.swirlds.virtualmap.datasource.VirtualDataSource;
import com.swirlds.virtualmap.datasource.VirtualHashRecord;
import com.swirlds.virtualmap.datasource.VirtualKeySet;
import com.swirlds.virtualmap.datasource.VirtualLeafRecord;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Comparator;
import java.util.Objects;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.LongAdder;
import java.util.stream.Stream;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public final class MerkleDbDataSource<K extends VirtualKey, V extends VirtualValue> implements VirtualDataSource<K, V> {

    private static final Logger logger = LogManager.getLogger(MerkleDbDataSource.class);

    /**
     * Since {@code com.swirlds.platform.Browser} populates settings, and it is loaded before any
     * application classes that might instantiate a data source, the {@link ConfigurationHolder}
     * holder will have been configured by the time this static initializer runs.
     */
    private static final MerkleDbConfig config = ConfigurationHolder.getConfigData(MerkleDbConfig.class);

    /** Count of open database instances */
    private static final LongAdder COUNT_OF_OPEN_DATABASES = new LongAdder();

    /** The version number for format of current data files */
    private static class MetadataFileFormatVersion {
        public static final int ORIGINAL = 1;
        public static final int KEYRANGE_ONLY = 2;
    }

    /** Virtual database instance that hosts this data source. */
    private final MerkleDb database;

    /** Table name. Used as a subdir name in the database directory */
    private final String tableName;

    /** Table ID used by this data source in its database instance. */
    private final int tableId;

    /**
     * Table config, includes key and value serializers as well as a few other non-global params.
     */
    private final MerkleDbTableConfig<K, V> tableConfig;

    /** We have an optimized mode when the keys can be represented by a single long */
    private final boolean isLongKeyMode;

    /**
     * In memory off-heap store for path to disk location, this is used for internal hashes store.
     */
    private final LongList pathToDiskLocationInternalNodes;

    /** In memory off-heap store for path to disk location, this is used by leave store. */
    private final LongList pathToDiskLocationLeafNodes;

    /**
     * In memory off-heap store for node hashes. This data is never stored on disk so on load from disk, this
     * will be empty. That should cause all internal node hashes to have to be computed on the first round
     * which will be expensive.
     */
    private final HashListByteBuffer hashStoreRam;

    /**
     * On disk store for node hashes. Can be null if all hashes are being stored in ram by setting
     * tableConfig.hashesRamToDiskThreshold to Long.MAX_VALUE.
     */
    private final MemoryIndexDiskKeyValueStore<VirtualHashRecord> hashStoreDisk;

    /** True when hashesRamToDiskThreshold is less than Long.MAX_VALUE */
    private final boolean hasDiskStoreForHashes;

    /**
     * In memory off-heap store for key to path map, this is used when isLongKeyMode=true and keys
     * are longs
     */
    private final LongList longKeyToPath;

    /**
     * Mixed disk and off-heap memory store for key to path map, this is used if
     * isLongKeyMode=false, and we have complex keys.
     */
    private final HalfDiskHashMap<K> objectKeyToPath;

    /** Mixed disk and off-heap memory store for path to leaf key and value */
    private final MemoryIndexDiskKeyValueStore<VirtualLeafRecord<K, V>> pathToKeyValue;

    /**
     * Cache size for reading virtual leaf records. Initialized in data source creation time from
     * MerkleDb settings. If the value is zero, leaf records cache isn't used.
     */
    private final int leafRecordCacheSize;

    /**
     * Virtual leaf records cache. It's a simple array indexed by leaf keys % cache size. Cache
     * eviction is not needed, as array size is fixed and can be configured in MerkleDb settings.
     * Index conflicts are resolved in a very straightforward way: whatever entry is read last, it's
     * put to the cache.
     */
    @SuppressWarnings("rawtypes")
    private final VirtualLeafRecord[] leafRecordCache;

    /** Thread pool storing internal records */
    private final ExecutorService storeInternalExecutor;

    /** Thread pool storing key-to-path mappings */
    private final ExecutorService storeKeyToPathExecutor;

    /** Thread pool creating snapshots, it is unbounded in threads, but we use at most 7 */
    private final ExecutorService snapshotExecutor;

    /** Flag for if a snapshot is in progress */
    private final AtomicBoolean snapshotInProgress = new AtomicBoolean(false);

    /** The range of valid leaf paths for data currently stored by this data source. */
    private volatile KeyRange validLeafPathRange = INVALID_KEY_RANGE;

    /** Paths to all database files and directories */
    private final MerkleDbPaths dbPaths;

    private final AtomicBoolean closed = new AtomicBoolean(false);

    /** Runs compactions for the storages of this data source */
    final MerkleDbCompactionCoordinator compactionCoordinator;

    private MerkleDbStatisticsUpdater statisticsUpdater;

    public MerkleDbDataSource(
            final MerkleDb database,
            final String tableName,
            final int tableId,
            final MerkleDbTableConfig<K, V> tableConfig,
            final boolean compactionEnabled)
            throws IOException {
        this.database = database;
        this.tableName = tableName;
        this.tableId = tableId;
        this.tableConfig = tableConfig;

        // create thread group with label
        final ThreadGroup threadGroup = new ThreadGroup("MerkleDb-" + tableName);
        // create thread pool storing internal records
        storeInternalExecutor = Executors.newSingleThreadExecutor(new ThreadConfiguration(getStaticThreadManager())
                .setComponent(MERKLEDB_COMPONENT)
                .setThreadGroup(threadGroup)
                .setThreadName("Store Internal Records")
                .setExceptionHandler((t, ex) ->
                        logger.error(EXCEPTION.getMarker(), "[{}] Uncaught exception during storing", tableName, ex))
                .buildFactory());
        // create thread pool storing key-to-path mappings
        storeKeyToPathExecutor = Executors.newSingleThreadExecutor(new ThreadConfiguration(getStaticThreadManager())
                .setComponent(MERKLEDB_COMPONENT)
                .setThreadGroup(threadGroup)
                .setThreadName("Store Key to Path")
                .setExceptionHandler((t, ex) -> logger.error(
                        EXCEPTION.getMarker(), "[{}] Uncaught exception during storing" + " keys", tableName, ex))
                .buildFactory());
        // thread pool creating snapshots, it is unbounded in threads, but we use at most 7
        snapshotExecutor = Executors.newCachedThreadPool(new ThreadConfiguration(getStaticThreadManager())
                .setComponent(MERKLEDB_COMPONENT)
                .setThreadGroup(threadGroup)
                .setThreadName("Snapshot")
                .setExceptionHandler(
                        (t, ex) -> logger.error(EXCEPTION.getMarker(), "Uncaught exception during snapshots", ex))
                .buildFactory());

        final Path storageDir = database.getTableDir(tableName, tableId);
        dbPaths = new MerkleDbPaths(storageDir);

        // check if we are loading an existing database or creating a new one
        if (Files.exists(storageDir)) {
            // read metadata
            if (Files.exists(dbPaths.metadataFile)) {
                loadMetadata(dbPaths.metadataFile);
            } else {
                logger.info(
                        MERKLE_DB.getMarker(),
                        "[{}] Loading existing set of data files but no metadata file was found in" + " [{}]",
                        tableName,
                        storageDir.toAbsolutePath());
                throw new IOException("Can not load an existing MerkleDbDataSource from ["
                        + storageDir.toAbsolutePath()
                        + "] because metadata file is missing");
            }
        } else {
            Files.createDirectories(storageDir);
        }

        // data item serializers for internal/leaf file collections
        final VirtualHashRecordSerializer virtualHashRecordSerializer = new VirtualHashRecordSerializer();
        final VirtualLeafRecordSerializer<K, V> leafRecordSerializer = new VirtualLeafRecordSerializer<>(tableConfig);

        // create path to disk location index
        final boolean forceIndexRebuilding = config.indexRebuildingEnforced();
        if (tableConfig.isPreferDiskBasedIndices()) {
            pathToDiskLocationInternalNodes = new LongListDisk(dbPaths.pathToDiskLocationInternalNodesFile);
        } else if (Files.exists(dbPaths.pathToDiskLocationInternalNodesFile) && !forceIndexRebuilding) {
            pathToDiskLocationInternalNodes = new LongListOffHeap(dbPaths.pathToDiskLocationInternalNodesFile);
        } else {
            pathToDiskLocationInternalNodes = new LongListOffHeap();
        }
        // path to disk location index, leaf nodes
        if (tableConfig.isPreferDiskBasedIndices()) {
            pathToDiskLocationLeafNodes = new LongListDisk(dbPaths.pathToDiskLocationLeafNodesFile);
        } else if (Files.exists(dbPaths.pathToDiskLocationLeafNodesFile) && !forceIndexRebuilding) {
            pathToDiskLocationLeafNodes = new LongListOffHeap(dbPaths.pathToDiskLocationLeafNodesFile);
        } else {
            pathToDiskLocationLeafNodes = new LongListOffHeap(config.reservedBufferLengthForLeafList());
        }

        // internal node hashes store, RAM
        if (tableConfig.getHashesRamToDiskThreshold() > 0) {
            if (Files.exists(dbPaths.hashStoreRamFile)) {
                hashStoreRam = new HashListByteBuffer(dbPaths.hashStoreRamFile);
            } else {
                hashStoreRam = new HashListByteBuffer();
            }
        } else {
            hashStoreRam = null;
        }

        statisticsUpdater = new MerkleDbStatisticsUpdater(this);

        final Runnable updateTotalStatsFunction = () -> {
            statisticsUpdater.updateStoreFileStats();
            statisticsUpdater.updateOffHeapStats();
        };

        // internal node hashes store, on disk
        hasDiskStoreForHashes = tableConfig.getHashesRamToDiskThreshold() < Long.MAX_VALUE;
        final DataFileCompactor hashStoreDiskFileCompactor;
        if (hasDiskStoreForHashes) {
            String storeName = tableName + "_internalhashes";
            hashStoreDisk = new MemoryIndexDiskKeyValueStore<>(
                    dbPaths.hashStoreDiskDirectory,
                    storeName,
                    tableName + ":internalHashes",
                    virtualHashRecordSerializer,
                    null,
                    pathToDiskLocationInternalNodes);
            hashStoreDiskFileCompactor = new DataFileCompactor(
                    storeName,
                    hashStoreDisk.getFileCollection(),
                    pathToDiskLocationInternalNodes,
                    statisticsUpdater::setHashesStoreCompactionTimeMs,
                    statisticsUpdater::setHashesStoreCompactionSavedSpaceMb,
                    updateTotalStatsFunction);
        } else {
            hashStoreDisk = null;
            hashStoreDiskFileCompactor = null;
        }

        final DataFileCompactor objectKeyToPathFileCompactor;
        // key to path store
        final DataFileCollection.LoadedDataCallback loadedDataCallback;
        if (tableConfig.getKeySerializer().getIndexType() == KeyIndexType.SEQUENTIAL_INCREMENTING_LONGS) {
            isLongKeyMode = true;
            objectKeyToPath = null;
            objectKeyToPathFileCompactor = null;
            if (Files.exists(dbPaths.longKeyToPathFile)) {
                longKeyToPath = new LongListOffHeap(dbPaths.longKeyToPathFile);
                // we do not need callback longKeyToPath was written to disk, so we can load it
                // directly
                loadedDataCallback = null;
            } else {
                longKeyToPath = new LongListOffHeap();
                loadedDataCallback = (path, dataLocation, keyValueData) -> {
                    // read key from keyValueData, as we are in isLongKeyMode mode then
                    // the key is a single long
                    final long key = keyValueData.getLong(0);
                    // update index
                    longKeyToPath.put(key, path);
                };
            }
        } else {
            isLongKeyMode = false;
            longKeyToPath = null;
            String storeName = tableName + "_objectkeytopath";
            objectKeyToPath = new HalfDiskHashMap<>(
                    tableConfig.getMaxNumberOfKeys(),
                    tableConfig.getKeySerializer(),
                    dbPaths.objectKeyToPathDirectory,
                    storeName,
                    tableName + ":objectKeyToPath",
                    tableConfig.isPreferDiskBasedIndices());
            objectKeyToPathFileCompactor = new DataFileCompactor(
                    storeName,
                    objectKeyToPath.getFileCollection(),
                    objectKeyToPath.getBucketIndexToBucketLocation(),
                    statisticsUpdater::setLeafKeysStoreCompactionTimeMs,
                    statisticsUpdater::setLeafKeysStoreCompactionSavedSpaceMb,
                    updateTotalStatsFunction);
            objectKeyToPath.printStats();
            // we do not need callback as HalfDiskHashMap loads its own data from disk
            loadedDataCallback = null;
        }

        // Create path to key/value store, this will create new or load if files exist
        final String storeName = tableName + "_pathtohashkeyvalue";
        pathToKeyValue = new MemoryIndexDiskKeyValueStore<>(
                dbPaths.pathToKeyValueDirectory,
                storeName,
                tableName + ":pathToHashKeyValue",
                leafRecordSerializer,
                loadedDataCallback,
                pathToDiskLocationLeafNodes);
        final DataFileCompactor pathToKeyValueFileCompactor = new DataFileCompactor(
                storeName,
                pathToKeyValue.getFileCollection(),
                pathToDiskLocationLeafNodes,
                statisticsUpdater::setLeavesStoreCompactionTimeMs,
                statisticsUpdater::setLeavesStoreCompactionSavedSpaceMb,
                updateTotalStatsFunction);

        // Leaf records cache
        leafRecordCacheSize = config.leafRecordCacheSize();
        leafRecordCache = (leafRecordCacheSize > 0) ? new VirtualLeafRecord[leafRecordCacheSize] : null;

        // Update count of open databases
        COUNT_OF_OPEN_DATABASES.increment();

        logger.info(
                MERKLE_DB.getMarker(),
                "Created MerkleDB [{}] with store path '{}', maxNumKeys = {}, hash RAM/disk cutoff" + " = {}",
                tableName,
                storageDir,
                tableConfig.getMaxNumberOfKeys(),
                tableConfig.getHashesRamToDiskThreshold());

        compactionCoordinator = new MerkleDbCompactionCoordinator(
                tableName, objectKeyToPathFileCompactor, hashStoreDiskFileCompactor, pathToKeyValueFileCompactor);

        if (compactionEnabled) {
            enableBackgroundCompaction();
        }
    }

    /**
     * Enables background compaction process.
     */
    @Override
    public void enableBackgroundCompaction() {
        compactionCoordinator.enableBackgroundCompaction();
    }

    /** Stop background compaction process, interrupting the current compaction if one is happening.
     * This will not corrupt the database but will leave files around.*/
    @Override
    public void stopAndDisableBackgroundCompaction() {
        compactionCoordinator.stopAndDisableBackgroundCompaction();
    }

    /** {@inheritDoc} */
    @SuppressWarnings("unchecked")
    @Override
    public VirtualKeySet<K> buildKeySet() {
        final KeySerializer<K> keySerializer =
                isLongKeyMode ? (KeySerializer<K>) new VirtualKeySetSerializer() : objectKeyToPath.getKeySerializer();
        return new HalfDiskVirtualKeySet<>(
                keySerializer,
                config.keySetBloomFilterHashCount(),
                config.keySetBloomFilterSizeInBytes() * BYTES_TO_BITS,
                config.keySetHalfDiskHashMapSize(),
                config.keySetHalfDiskHashMapBuffer());
    }

    /**
     * Get the count of open database instances. This is databases that have been opened but not yet
     * closed.
     *
     * @return Count of open databases.
     */
    static long getCountOfOpenDatabases() {
        return COUNT_OF_OPEN_DATABASES.sum();
    }

    /** Get the most recent first leaf path */
    public long getFirstLeafPath() {
        return validLeafPathRange.getMinValidKey();
    }

    /** Get the most recent last leaf path */
    public long getLastLeafPath() {
        return validLeafPathRange.getMaxValidKey();
    }

    /**
     * Pauses compaction of all data file collections used by this data source. It may not stop compaction
     * immediately, but as soon as compaction process needs to update data source state, which is
     * critical for snapshots (e.g. update an index), it will be stopped until {@link
     * #resumeCompaction()}} is called.
     */
    void pauseCompaction() throws IOException {
        compactionCoordinator.pauseCompaction();
    }

    /** Resumes previously stopped data file collection merging. */
    void resumeCompaction() throws IOException {
        compactionCoordinator.resumeCompaction();
    }

    /**
     * Save a batch of data to data store.
     * <p>
     * If you call this method where not all data is provided to cover the change in
     * firstLeafPath and lastLeafPath, then any reads after this call may return rubbish or throw
     * obscure exceptions for any internals or leaves that have not been written. For example, if
     * you were to grow the tree by more than 2x, and then called this method in batches, be aware
     * that if you were to query for some record between batches that hadn't yet been saved, you
     * will encounter problems.
     *
     * @param firstLeafPath the tree path for first leaf
     * @param lastLeafPath the tree path for last leaf
     * @param hashRecordsToUpdate stream of records with hashes to update, it is assumed this is sorted by
     *     path and each path only appears once.
     * @param leafRecordsToAddOrUpdate stream of new leaf nodes and updated leaf nodes
     * @param leafRecordsToDelete stream of new leaf nodes to delete, The leaf record's key and path
     *     have to be populated, all other data can be null.
     * @throws IOException If there was a problem saving changes to data source
     */
    @Override
    public void saveRecords(
            final long firstLeafPath,
            final long lastLeafPath,
            final Stream<VirtualHashRecord> hashRecordsToUpdate,
            final Stream<VirtualLeafRecord<K, V>> leafRecordsToAddOrUpdate,
            final Stream<VirtualLeafRecord<K, V>> leafRecordsToDelete)
            throws IOException {
        try {
            validLeafPathRange = new KeyRange(firstLeafPath, lastLeafPath);
            final CountDownLatch countDownLatch = new CountDownLatch(lastLeafPath > 0 ? 1 : 0);

            // might as well write to the 3 data stores in parallel, so lets fork 2 threads for the easy stuff
            if (lastLeafPath > 0) {
                storeInternalExecutor.execute(() -> {
                    try {
                        writeHashes(lastLeafPath, hashRecordsToUpdate);
                    } catch (final IOException e) {
                        logger.error(ERROR.getMarker(), "[{}] Failed to store internal records", tableName, e);
                        throw new UncheckedIOException(e);
                    } finally {
                        countDownLatch.countDown();
                    }
                });
            }

            // we might as well do this in the archive thread rather than leaving it waiting
            writeLeavesToPathToKeyValue(firstLeafPath, lastLeafPath, leafRecordsToAddOrUpdate, leafRecordsToDelete);
            // wait for the other threads in the rare case they are not finished yet. We need to
            // have all writing
            // done before we return as when we return the state version we are writing is deleted
            // from the cache and
            // the flood gates are opened for reads through to the data we have written here.
            try {
                countDownLatch.await();
            } catch (final InterruptedException e) {
                logger.warn(
                        EXCEPTION.getMarker(),
                        "[{}] Interrupted while waiting on internal record storage",
                        tableName,
                        e);
                Thread.currentThread().interrupt();
            }
        } finally {
            // Report total size on disk as sum of all store files. All metadata and other helper files
            // are considered small enough to be ignored. If/when we decide to use on-disk long lists
            // for indices, they should be added here
            statisticsUpdater.updateStoreFileStats();
            // update off-heap stats
            statisticsUpdater.updateOffHeapStats();
        }
    }

    /**
     * Load a leaf record by key
     *
     * @param key they to the leaf to load record for
     * @return loaded record or null if not found
     * @throws IOException If there was a problem reading record from db
     */
    @SuppressWarnings("unchecked")
    @Override
    public VirtualLeafRecord<K, V> loadLeafRecord(final K key) throws IOException {
        requireNonNull(key);

        final long path;
        VirtualLeafRecord<K, V> cached = null;
        int cacheIndex = -1;
        if (leafRecordCache != null) {
            cacheIndex = Math.abs(key.hashCode() % leafRecordCacheSize);
            // No synchronization is needed here. Java guarantees (JLS 17.7) that reference writes
            // are atomic, so we will never get corrupted objects from the array. The object may
            // be overwritten in the cache in a different thread in parallel, but it isn't a
            // problem as cached entry key is checked below anyway
            cached = leafRecordCache[cacheIndex];
        }
        // If an entry is found in the cache, and entry key is the one requested
        if ((cached != null) && key.equals(cached.getKey())) {
            // Some cache entries contain just key and path, but no value. If the value is there,
            // just return the cached entry. If not, at least make use of the path
            if (cached.getValue() != null) {
                // A copy is returned to ensure cached value immutability.
                return cached.copy();
            }
            // Note that the path may be INVALID_PATH here, this is perfectly legal
            path = cached.getPath();
        } else {
            // Cache miss
            cached = null;
            statisticsUpdater.countLeafKeyReads();
            path = isLongKeyMode
                    ? longKeyToPath.get(((VirtualLongKey) key).getKeyAsLong(), INVALID_PATH)
                    : objectKeyToPath.get(key, INVALID_PATH);
        }

        // If the key didn't map to anything, we just return null
        if (path == INVALID_PATH) {
            // Cache the result if not already cached
            if (leafRecordCache != null && cached == null) {
                leafRecordCache[cacheIndex] = new VirtualLeafRecord<K, V>(path, key, null);
            }
            return null;
        }

        // If the key returns a value from the map, but it lies outside the first/last
        // leaf path, then return null. This can happen if the map contains old keys
        // that haven't been removed.
        if (!validLeafPathRange.withinRange(path)) {
            return null;
        }

        statisticsUpdater.countLeafReads();
        // Go ahead and lookup the value.
        VirtualLeafRecord<K, V> leafRecord = pathToKeyValue.get(path);

        // FUTURE WORK: once the reconnect key leak bug is fixed, this block should be removed
        if (!leafRecord.getKey().equals(key)) {
            if (config.reconnectKeyLeakMitigationEnabled()) {
                logger.warn(MERKLE_DB.getMarker(), "leaked key {} encountered, mitigation is enabled", key);
                return null;
            } else {
                logger.error(
                        EXCEPTION.getMarker(),
                        "leaked key {} encountered, mitigation is disabled, expect problems",
                        key);
            }
        }

        if (leafRecordCache != null) {
            // No synchronization is needed here, see the comment above
            // A copy is returned to ensure cached value immutability.
            leafRecordCache[cacheIndex] = leafRecord;
            leafRecord = leafRecord.copy();
        }

        return leafRecord;
    }

    /**
     * Load a leaf record by path
     *
     * @param path the path for the leaf we are loading
     * @return loaded record or null if not found
     * @throws IOException If there was a problem reading record from db
     */
    @Override
    public VirtualLeafRecord<K, V> loadLeafRecord(final long path) throws IOException {
        final KeyRange leafPathRange = validLeafPathRange;
        if (!leafPathRange.withinRange(path)) {
            throw new IllegalArgumentException("path (" + path + ") is not valid; must be in range " + leafPathRange);
        }
        statisticsUpdater.countLeafReads();
        return pathToKeyValue.get(path);
    }

    /**
     * Find the path of the given key
     *
     * @param key the key for a path
     * @return the path or INVALID_PATH if not stored
     * @throws IOException If there was a problem locating the key
     */
    @SuppressWarnings("unchecked")
    @Override
    public long findKey(final K key) throws IOException {
        requireNonNull(key);

        // Check the cache first
        int cacheIndex = -1;
        if (leafRecordCache != null) {
            cacheIndex = Math.abs(key.hashCode() % leafRecordCacheSize);
            // No synchronization is needed here. See the comment in loadLeafRecord(key) above
            final VirtualLeafRecord<K, V> cached = leafRecordCache[cacheIndex];
            if (cached != null && key.equals(cached.getKey())) {
                // Cached path may be a valid path or INVALID_PATH, both are legal here
                return cached.getPath();
            }
        }

        statisticsUpdater.countLeafKeyReads();
        final long path = isLongKeyMode
                ? longKeyToPath.get(((VirtualLongKey) key).getKeyAsLong(), INVALID_PATH)
                : objectKeyToPath.get(key, INVALID_PATH);

        if (leafRecordCache != null) {
            // Path may be INVALID_PATH here. Still needs to be cached (negative result)
            leafRecordCache[cacheIndex] = new VirtualLeafRecord<K, V>(path, key, null);
        }

        return path;
    }

    /**
     * Load hash for a leaf node with given path
     *
     * @param path the path to get hash for
     * @return loaded hash or null if hash is not stored
     * @throws IOException if there was a problem loading hash
     */
    @Override
    public Hash loadHash(final long path) throws IOException {
        if (path < 0) {
            throw new IllegalArgumentException("path is less than 0");
        }

        // It is possible that the caller will ask for an internal node that the database doesn't
        // know about. This can happen if some leaves have been added to the tree, but we haven't
        // hashed yet, so the cache doesn't have any internal records for it, and somebody
        // tries to iterate over the nodes in the tree.
        long lastLeaf = validLeafPathRange.getMaxValidKey();
        if (path > lastLeaf) {
            return null;
        }

        final Hash hash;
        if (path < tableConfig.getHashesRamToDiskThreshold()) {
            hash = hashStoreRam.get(path);
            // Should count hash reads here, too?
        } else {
            final VirtualHashRecord rec = hashStoreDisk.get(path);
            hash = (rec != null) ? rec.hash() : null;
            statisticsUpdater.countHashReads();
        }

        return hash;
    }

    /** Wait for any merges to finish, then close all data stores and free all resources. */
    @Override
    public void close() throws IOException {
        if (!closed.getAndSet(true)) {
            try {
                // stop merging and shutdown the datasource compactor
                compactionCoordinator.stopAndDisableBackgroundCompaction();
                // shut down all executors
                shutdownThreadsAndWait(storeInternalExecutor, storeKeyToPathExecutor, snapshotExecutor);
            } finally {
                try {
                    // close all closable data stores
                    logger.info(MERKLE_DB.getMarker(), "Closing Data Source [{}]", tableName);
                    if (hashStoreRam != null) {
                        hashStoreRam.close();
                    }
                    if (hashStoreDisk != null) {
                        hashStoreDisk.close();
                    }
                    pathToDiskLocationInternalNodes.close();
                    pathToDiskLocationLeafNodes.close();
                    if (longKeyToPath != null) {
                        longKeyToPath.close();
                    }
                    if (objectKeyToPath != null) {
                        objectKeyToPath.close();
                    }
                    pathToKeyValue.close();
                    // Store metadata
                    saveMetadata(dbPaths.metadataFile);
                } catch (final Exception e) {
                    logger.warn(EXCEPTION.getMarker(), "Exception while closing Data Source [{}]", tableName);
                } catch (final Error t) {
                    logger.error(EXCEPTION.getMarker(), "Error while closing Data Source [{}]", tableName);
                    throw t;
                } finally {
                    // updated count of open databases
                    COUNT_OF_OPEN_DATABASES.decrement();
                    // Notify the database
                    database.closeDataSource(this);
                }
            }
        }
    }

    /**
     * Write a snapshot of the current state of the database at this moment in time. This will block
     * till the snapshot is completely created.
     * <p>
     *
     * <b> Only one snapshot can happen at a time, this will throw an IllegalStateException if
     * another snapshot is currently happening. </b>
     * <p>
     * <b> IMPORTANT, after this is completed the caller owns the directory. It is responsible
     * for deleting it when it is no longer needed. </b>
     *
     * @param snapshotDirectory Directory to put snapshot into, it will be created if it doesn't
     *     exist.
     * @throws IOException If there was a problem writing the current database out to the given
     *     directory
     * @throws IllegalStateException If there is already a snapshot happening
     */
    @SuppressWarnings("ConstantConditions")
    @Override
    public void snapshot(final Path snapshotDirectory) throws IOException, IllegalStateException {
        // check if another snapshot was running
        final boolean aSnapshotWasInProgress = snapshotInProgress.getAndSet(true);
        if (aSnapshotWasInProgress) {
            throw new IllegalStateException("Tried to start a snapshot when one was already in progress");
        }
        try {
            // start timing snapshot
            final long START = System.currentTimeMillis();
            // create snapshot dir if it doesn't exist
            Files.createDirectories(snapshotDirectory);
            final MerkleDbPaths snapshotDbPaths = new MerkleDbPaths(snapshotDirectory);
            // main snapshotting process in multiple-threads
            try {
                final CountDownLatch countDownLatch = new CountDownLatch(8);
                // write all data stores
                runWithSnapshotExecutor(true, countDownLatch, "pathToDiskLocationInternalNodes", () -> {
                    pathToDiskLocationInternalNodes.writeToFile(snapshotDbPaths.pathToDiskLocationInternalNodesFile);
                    return true;
                });
                runWithSnapshotExecutor(true, countDownLatch, "pathToDiskLocationLeafNodes", () -> {
                    pathToDiskLocationLeafNodes.writeToFile(snapshotDbPaths.pathToDiskLocationLeafNodesFile);
                    return true;
                });
                runWithSnapshotExecutor(hashStoreRam != null, countDownLatch, "internalHashStoreRam", () -> {
                    hashStoreRam.writeToFile(snapshotDbPaths.hashStoreRamFile);
                    return true;
                });
                runWithSnapshotExecutor(hashStoreDisk != null, countDownLatch, "internalHashStoreDisk", () -> {
                    hashStoreDisk.snapshot(snapshotDbPaths.hashStoreDiskDirectory);
                    return true;
                });
                runWithSnapshotExecutor(longKeyToPath != null, countDownLatch, "longKeyToPath", () -> {
                    longKeyToPath.writeToFile(snapshotDbPaths.longKeyToPathFile);
                    return true;
                });
                runWithSnapshotExecutor(objectKeyToPath != null, countDownLatch, "objectKeyToPath", () -> {
                    objectKeyToPath.snapshot(snapshotDbPaths.objectKeyToPathDirectory);
                    return true;
                });
                runWithSnapshotExecutor(true, countDownLatch, "pathToKeyValue", () -> {
                    pathToKeyValue.snapshot(snapshotDbPaths.pathToKeyValueDirectory);
                    return true;
                });
                runWithSnapshotExecutor(true, countDownLatch, "metadata", () -> {
                    saveMetadata(snapshotDbPaths.metadataFile);
                    return true;
                });
                // wait for the others to finish
                countDownLatch.await();
            } catch (final InterruptedException e) {
                logger.error(
                        EXCEPTION.getMarker(),
                        "[{}] InterruptedException from waiting for countDownLatch in snapshot",
                        tableName,
                        e);
                Thread.currentThread().interrupt();
            }
            logger.info(
                    MERKLE_DB.getMarker(),
                    "[{}] Snapshot all finished in {} seconds",
                    tableName,
                    (System.currentTimeMillis() - START) * UnitConstants.MILLISECONDS_TO_SECONDS);
        } finally {
            snapshotInProgress.set(false);
        }
    }

    @Override
    public long estimatedSize(final long dirtyInternals, final long dirtyLeaves) {
        // Deleted leaves count is ignored, as deleted leaves aren't flushed to data source
        final long estimatedInternalsSize = dirtyInternals
                * (Long.BYTES // path
                        + DigestType.SHA_384.digestLength()); // hash
        final long estimatedLeavesSize = dirtyLeaves
                * (Long.BYTES // path
                        + DigestType.SHA_384.digestLength() // hash
                        + tableConfig.getKeySerializer().getTypicalSerializedSize() // key
                        + tableConfig.getValueSerializer().getTypicalSerializedSize()); // value
        return estimatedInternalsSize + estimatedLeavesSize;
    }

    /** toString for debugging */
    @Override
    public String toString() {
        return new ToStringBuilder(this)
                .append("maxNumberOfKeys", tableConfig.getMaxNumberOfKeys())
                .append("preferDiskBasedIndexes", tableConfig.isPreferDiskBasedIndices())
                .append("isLongKeyMode", isLongKeyMode)
                .append("pathToDiskLocationInternalNodes.size", pathToDiskLocationInternalNodes.size())
                .append("pathToDiskLocationLeafNodes.size", pathToDiskLocationLeafNodes.size())
                .append("hashesRamToDiskThreshold", tableConfig.getHashesRamToDiskThreshold())
                .append("hashStoreRam.size", hashStoreRam == null ? null : hashStoreRam.size())
                .append("hashStoreDisk", hashStoreDisk)
                .append("hasDiskStoreForHashes", hasDiskStoreForHashes)
                .append("longKeyToPath.size", longKeyToPath == null ? null : longKeyToPath.size())
                .append("objectKeyToPath", objectKeyToPath)
                .append("pathToKeyValue", pathToKeyValue)
                .append("snapshotInProgress", snapshotInProgress.get())
                .toString();
    }

    /**
     * Database instance that hosts this data source.
     *
     * @return Virtual database instance
     */
    public MerkleDb getDatabase() {
        return database;
    }

    /**
     * Table ID for this data source in its virtual database instance.
     *
     * @return Table ID
     */
    public int getTableId() {
        return tableId;
    }

    /**
     * Table name for this data source in its virtual database instance.
     *
     * @return Table name
     */
    public String getTableName() {
        return tableName;
    }

    /**
     * Table config for this data source. Includes key and value serializers, internal nodes
     * RAM/disk threshold, etc.
     *
     * @return Table config
     */
    public MerkleDbTableConfig<K, V> getTableConfig() {
        return tableConfig;
    }

    // For testing purpose
    Path getStorageDir() {
        return dbPaths.storageDir;
    }

    // For testing purpose
    long getMaxNumberOfKeys() {
        return tableConfig.getMaxNumberOfKeys();
    }

    // For testing purpose
    long getHashesRamToDiskThreshold() {
        return tableConfig.getHashesRamToDiskThreshold();
    }

    // For testing purpose
    boolean isPreferDiskBasedIndexes() {
        return tableConfig.isPreferDiskBasedIndices();
    }

    // For testing purpose
    boolean isCompactionEnabled() {
        return compactionCoordinator.isCompactionEnabled();
    }

    private void saveMetadata(final Path targetFile) throws IOException {
        final KeyRange leafRange = validLeafPathRange;
        try (final DataOutputStream metaOut = new DataOutputStream(
                Files.newOutputStream(targetFile, StandardOpenOption.CREATE, StandardOpenOption.WRITE))) {
            metaOut.writeInt(MetadataFileFormatVersion.KEYRANGE_ONLY); // serialization version
            metaOut.writeLong(leafRange.getMinValidKey());
            metaOut.writeLong(leafRange.getMaxValidKey());
            metaOut.flush();
        }
    }

    private void loadMetadata(final Path sourceFile) throws IOException {
        try (final DataInputStream metaIn = new DataInputStream(Files.newInputStream(sourceFile))) {
            final int fileVersion = metaIn.readInt();
            if (fileVersion == MetadataFileFormatVersion.ORIGINAL) {
                metaIn.readLong(); // skip hashesRamToDiskThreshold
            } else if (fileVersion != MetadataFileFormatVersion.KEYRANGE_ONLY) {
                throw new IOException(
                        "Tried to read a file with incompatible file format version [" + fileVersion + "].");
            }
            validLeafPathRange = new KeyRange(metaIn.readLong(), metaIn.readLong());
        }
    }

    /** {@inheritDoc} */
    @Override
    public void registerMetrics(final Metrics metrics) {
        statisticsUpdater.registerMetrics(metrics);
    }

    /** {@inheritDoc} */
    @Override
    public void copyStatisticsFrom(final VirtualDataSource<K, V> that) {
        if (!(that instanceof MerkleDbDataSource<?, ?> thatDataSource)) {
            logger.warn(MERKLE_DB.getMarker(), "Can only copy statistics from MerkleDbDataSource");
            return;
        }
        statisticsUpdater = thatDataSource.statisticsUpdater;
    }

    // ==================================================================================================================
    // private methods

    /**
     * Shutdown threads if they are running and wait for them to finish
     *
     * @param executors array of threads to shut down.
     * @throws IOException if there was a problem or timeout shutting down threads.
     */
    private void shutdownThreadsAndWait(final ExecutorService... executors) throws IOException {
        try {
            // shutdown threads
            for (final ExecutorService executor : executors) {
                if (!executor.isShutdown()) {
                    executor.shutdown();
                    final boolean finishedWithoutTimeout = executor.awaitTermination(5, TimeUnit.MINUTES);
                    if (!finishedWithoutTimeout) {
                        throw new IOException("Timeout while waiting for executor service to finish.");
                    }
                }
            }
        } catch (final InterruptedException e) {
            logger.warn(EXCEPTION.getMarker(), "[{}] Interrupted while waiting on executors to shutdown", tableName, e);
            Thread.currentThread().interrupt();
            throw new IOException("Interrupted while waiting for shutdown to finish.", e);
        }
    }

    /**
     * Run a runnable on background thread using snapshot ExecutorService, counting down latch when
     * done.
     *
     * @param shouldRun when true, run runnable otherwise just countdown latch
     * @param countDownLatch latch to count down when done
     * @param taskName the name of the task for logging
     * @param runnable the code to run
     */
    private void runWithSnapshotExecutor(
            final boolean shouldRun,
            final CountDownLatch countDownLatch,
            final String taskName,
            final Callable<Object> runnable) {
        if (shouldRun) {
            snapshotExecutor.submit(() -> {
                final long START = System.currentTimeMillis();
                try {
                    runnable.call();
                    logger.trace(
                            MERKLE_DB.getMarker(),
                            "[{}] Snapshot {} complete in {} seconds",
                            tableName,
                            taskName,
                            (System.currentTimeMillis() - START) * UnitConstants.MILLISECONDS_TO_SECONDS);
                    return true; // turns this into a callable, so it can throw checked
                    // exceptions
                } finally {
                    countDownLatch.countDown();
                }
            });
        } else {
            countDownLatch.countDown();
        }
    }

    /**
     * Write all hashes to hashStore
     */
    private void writeHashes(final long maxValidPath, final Stream<VirtualHashRecord> dirtyHashes) throws IOException {
        if ((dirtyHashes == null) || (maxValidPath <= 0)) {
            // nothing to do
            return;
        }

        if (hasDiskStoreForHashes) {
            hashStoreDisk.startWriting(0, maxValidPath);
        }

        dirtyHashes.forEach(rec -> {
            statisticsUpdater.countFlushHashesWritten();
            if (rec.path() < tableConfig.getHashesRamToDiskThreshold()) {
                hashStoreRam.put(rec.path(), rec.hash());
            } else {
                try {
                    hashStoreDisk.put(rec.path(), rec);
                } catch (final IOException e) {
                    logger.error(EXCEPTION.getMarker(), "[{}] IOException writing internal records", tableName, e);
                    throw new UncheckedIOException(e);
                }
            }
        });

        if (hasDiskStoreForHashes) {
            final DataFileReader<VirtualHashRecord> newHashesFile = hashStoreDisk.endWriting();
            statisticsUpdater.setFlushHashesStoreFileSize(newHashesFile);
            compactionCoordinator.compactDiskStoreForHashesAsync();
        }
    }

    /** Write all the given leaf records to pathToKeyValue */
    private void writeLeavesToPathToKeyValue(
            final long firstLeafPath,
            final long lastLeafPath,
            final Stream<VirtualLeafRecord<K, V>> dirtyLeaves,
            final Stream<VirtualLeafRecord<K, V>> deletedLeaves)
            throws IOException {
        if ((dirtyLeaves == null) || (firstLeafPath <= 0)) {
            // nothing to do
            return;
        }

        // start writing
        pathToKeyValue.startWriting(firstLeafPath, lastLeafPath);
        if (!isLongKeyMode) {
            objectKeyToPath.startWriting();
        }

        // iterate over leaf records
        dirtyLeaves.sorted(Comparator.comparingLong(VirtualLeafRecord::getPath)).forEachOrdered(leafRecord -> {
            if (isLongKeyMode) {
                longKeyToPath.put(((VirtualLongKey) leafRecord.getKey()).getKeyAsLong(), leafRecord.getPath());
            } else {
                objectKeyToPath.put(leafRecord.getKey(), leafRecord.getPath());
            }
            statisticsUpdater.countFlushLeafKeysWritten();

            // update pathToKeyValue
            try {
                pathToKeyValue.put(leafRecord.getPath(), leafRecord);
            } catch (final IOException e) {
                logger.error(EXCEPTION.getMarker(), "[{}] IOException writing to pathToKeyValue", tableName, e);
                throw new UncheckedIOException(e);
            }
            statisticsUpdater.countFlushLeavesWritten();

            // cache the record
            invalidateReadCache(leafRecord.getKey());
        });

        // iterate over leaf records to delete
        deletedLeaves.forEach(leafRecord -> {
            // update objectKeyToPath
            if (isLongKeyMode) {
                longKeyToPath.put(((VirtualLongKey) leafRecord.getKey()).getKeyAsLong(), INVALID_PATH);
            } else {
                objectKeyToPath.delete(leafRecord.getKey());
            }
            statisticsUpdater.countFlushLeavesDeleted();

            // delete from pathToKeyValue, we don't need to explicitly delete leaves as
            // they will be deleted on
            // next merge based on range of valid leaf paths. If a leaf at path X is deleted
            // then a new leaf is
            // inserted at path X then the record is just updated to new leaf's data.

            // delete the record from the cache
            invalidateReadCache(leafRecord.getKey());
        });

        // end writing
        final DataFileReader<VirtualLeafRecord<K, V>> pathToKeyValueReader = pathToKeyValue.endWriting();
        statisticsUpdater.setFlushLeavesStoreFileSize(pathToKeyValueReader);
        compactionCoordinator.compactPathToKeyValueAsync();
        if (!isLongKeyMode) {
            final DataFileReader<Bucket<K>> objectKeyToPathReader = objectKeyToPath.endWriting();
            statisticsUpdater.setFlushLeafKeysStoreFileSize(objectKeyToPathReader);
            compactionCoordinator.compactDiskStoreForObjectKeyToPathAsync();
        }
    }

    /**
     * Invalidates the given key in virtual leaf record cache, if the cache is enabled.
     * <p>
     * If the key is deleted, it's still updated in the cache. It means no record with the given
     * key exists in the data source, so further lookups for the key are skipped.
     * <p>
     * Cache index is calculated as the key's hash code % cache size. The cache is only updated,
     * if the current record at this index has the given key. If the key is different, no update is
     * performed.
     *
     * @param key Virtual leaf record key
     */
    @SuppressWarnings("unchecked")
    private void invalidateReadCache(final K key) {
        if (leafRecordCache == null) {
            return;
        }
        final int cacheIndex = Math.abs(key.hashCode() % leafRecordCacheSize);
        final VirtualLeafRecord<K, V> cached = leafRecordCache[cacheIndex];
        if ((cached != null) && key.equals(cached.getKey())) {
            leafRecordCache[cacheIndex] = null;
        }
    }

    FileStatisticAware getHashStoreDisk() {
        return hashStoreDisk;
    }

    FileStatisticAware getObjectKeyToPath() {
        return objectKeyToPath;
    }

    FileStatisticAware getPathToKeyValue() {
        return pathToKeyValue;
    }

    MerkleDbCompactionCoordinator getCompactionCoordinator() {
        return compactionCoordinator;
    }

    OffHeapUser getHashStoreRam() {
        return hashStoreRam;
    }

    LongList getLongKeyToPath() {
        return longKeyToPath;
    }

    LongList getPathToDiskLocationInternalNodes() {
        return pathToDiskLocationInternalNodes;
    }

    LongList getPathToDiskLocationLeafNodes() {
        return pathToDiskLocationLeafNodes;
    }

    /**
     * Used for tests.
     *
     * @return true if we are in "long key" mode.
     */
    boolean isLongKeyMode() {
        return isLongKeyMode;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public int hashCode() {
        return Objects.hash(database, tableId);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean equals(Object o) {
        if (!(o instanceof MerkleDbDataSource<?, ?> other)) {
            return false;
        }
        return Objects.equals(database, other.database) && Objects.equals(tableId, other.tableId);
    }
}
