/*
 * Copyright (C) 2020-2023 Hedera Hashgraph, LLC
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

package com.hedera.services.bdd.spec;

import static com.hedera.services.bdd.spec.HapiPropertySource.asAccount;
import static com.hedera.services.bdd.spec.HapiPropertySource.asSources;
import static com.hedera.services.bdd.spec.HapiPropertySource.inPriorityOrder;
import static com.hedera.services.bdd.spec.HapiSpec.CostSnapshotMode;
import static com.hedera.services.bdd.spec.keys.KeyFactory.KeyType;
import static com.hedera.services.bdd.spec.transactions.TxnUtils.bytecodePath;
import static java.util.stream.Collectors.toList;
import static java.util.stream.Collectors.toSet;

import com.hedera.services.bdd.spec.keys.SigControl;
import com.hedera.services.bdd.spec.props.JutilPropertySource;
import com.hedera.services.bdd.spec.props.MapPropertySource;
import com.hedera.services.bdd.spec.props.NodeConnectInfo;
import com.hedera.services.bdd.spec.transactions.HapiTxnOp;
import com.hedera.services.bdd.spec.utilops.records.AutoSnapshotRecordSource;
import com.hederahashgraph.api.proto.java.*;
import java.security.SecureRandom;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.apache.commons.lang3.StringUtils;

public class HapiSpecSetup {
    private final SecureRandom r = new SecureRandom();

    private static final HapiPropertySource defaultNodeProps;

    static {
        defaultNodeProps = new JutilPropertySource("bootstrap.properties");
    }

    public static HapiPropertySource getDefaultNodeProps() {
        return defaultNodeProps;
    }

    private Set<ResponseCodeEnum> streamlinedIngestChecks = null;
    private HapiPropertySource ciPropertiesMap = null;
    private static HapiPropertySource DEFAULT_PROPERTY_SOURCE = null;
    private static final HapiPropertySource BASE_DEFAULT_PROPERTY_SOURCE = JutilPropertySource.getDefaultInstance();

    public static final HapiPropertySource getDefaultPropertySource() {
        if (DEFAULT_PROPERTY_SOURCE == null) {
            String globals = System.getProperty("global.property.overrides");
            globals = (globals == null) ? "" : globals;
            String[] sources = globals.length() > 0 ? globals.split(",") : new String[0];
            DEFAULT_PROPERTY_SOURCE =
                    inPriorityOrder(asSources(Stream.of(Stream.of(sources), Stream.of(BASE_DEFAULT_PROPERTY_SOURCE))
                            .flatMap(Function.identity())
                            .toArray(n -> new Object[n])));
        }
        return DEFAULT_PROPERTY_SOURCE;
    }

    private static final HapiSpecSetup DEFAULT_INSTANCE;

    static {
        DEFAULT_INSTANCE = new HapiSpecSetup(getDefaultPropertySource());
    }

    public static HapiSpecSetup getDefaultInstance() {
        return DEFAULT_INSTANCE;
    }

    private HapiPropertySource props;

    public enum NodeSelection {
        FIXED,
        RANDOM
    }

    public enum TlsConfig {
        ON,
        OFF,
        ALTERNATE
    }

    public enum TxnProtoStructure {
        NEW,
        OLD,
        ALTERNATE
    }

    public HapiSpecSetup(HapiPropertySource props) {
        this.props = props;
    }

    /**
     * Add new properties that would merge with existing ones, if a property already exist then
     * override it with new value
     *
     * @param props A map of new properties
     */
    public void addOverrides(final Map<String, Object> props) {
        this.props = HapiPropertySource.inPriorityOrder(new MapPropertySource(props), this.props);
    }

    public String defaultRecordLoc() {
        return props.get("recordStream.path");
    }

    public FileID addressBookId() {
        return props.getFile("address.book.id");
    }

    public String addressBookName() {
        return props.get("address.book.name");
    }

    public AccountID addressBookControl() {
        return props.getAccount("address.book.controlAccount.id");
    }

    public String addressBookControlName() {
        return props.get("address.book.controlAccount.name");
    }

    public FileID apiPermissionsId() {
        return props.getFile("api.permissions.id");
    }

    public String apiPermissionsFile() {
        return props.get("api.permissions.name");
    }

    public FileID appPropertiesId() {
        return props.getFile("app.properties.id");
    }

    public String appPropertiesFile() {
        return props.get("app.properties.name");
    }

    public Boolean clientFeeScheduleFromDisk() {
        return props.getBoolean("client.feeSchedule.fromDisk");
    }

    public String clientFeeSchedulePath() {
        return props.get("client.feeSchedule.path");
    }

    public Boolean clientExchangeRatesFromDisk() {
        return props.getBoolean("client.exchangeRates.fromDisk");
    }

    public String clientExchangeRatesPath() {
        return props.get("client.exchangeRates.path");
    }

    public String costSnapshotDir() {
        return props.get("cost.snapshot.dir");
    }

    public CostSnapshotMode costSnapshotMode() {
        return props.getCostSnapshotMode("cost.snapshot.mode");
    }

    public HapiPropertySource ciPropertiesMap() {
        if (null == ciPropertiesMap) {
            ciPropertiesMap = MapPropertySource.parsedFromCommaDelimited(props.get("ci.properties.map"));
        }
        return ciPropertiesMap;
    }

    public Set<HederaFunctionality> txnTypesToSchedule() {
        final var commaDelimited = props.get("spec.autoScheduledTxns");
        return commaDelimited.isBlank()
                ? Collections.emptySet()
                : Arrays.stream(commaDelimited.split(","))
                        .map(HederaFunctionality::valueOf)
                        .collect(toSet());
    }

    public Duration defaultAutoRenewPeriod() {
        return props.getDurationFromSecs("default.autorenew.secs");
    }

    public long defaultBalance() {
        return props.getLong("default.balance.tinyBars");
    }

    public long defaultCallGas() {
        return props.getLong("default.call.gas");
    }

    public String defaultConsensusMessage() {
        return props.get("default.consensus.message");
    }

    public long defaultContractBalance() {
        return props.getLong("default.contract.balance.tinyBars");
    }

    public String defaultContractPath() {
        return bytecodePath(props.get("default.contract.bytecode"));
    }

    public long defaultCreateGas() {
        return props.getLong("default.create.gas");
    }

    public long defaultExpirationSecs() {
        return props.getLong("default.expiration.secs");
    }

    public long defaultFee() {
        return props.getLong("default.fee");
    }

    public byte[] defaultFileContents() {
        return props.getBytes("default.file.contents");
    }

    public SigControl.KeyAlgo defaultKeyAlgo() {
        return props.getKeyAlgorithm("default.keyAlgorithm");
    }

    public KeyType defaultKeyType() {
        return props.getKeyType("default.keyType");
    }

    public int defaultListN() {
        return props.getInteger("default.listKey.N");
    }

    public long defaultMaxLocalCallRetBytes() {
        return props.getLong("default.max.localCall.retBytes");
    }

    public String defaultMemo() {
        return props.get("default.memo");
    }

    public HapiSpec.UTF8Mode isMemoUTF8() {
        return props.getUTF8Mode("default.useMemoUTF8");
    }

    public String defaultUTF8memo() {
        return props.get("default.memoUtf8Charset");
    }

    public AccountID defaultNode() {
        return props.getAccount("default.node");
    }

    public String defaultNodeName() {
        return props.get("default.node.name");
    }

    public long defaultNodePaymentTinyBars() {
        return props.getLong("default.nodePayment.tinyBars");
    }

    public String defaultPayerMnemonic() {
        return props.get("default.payer.mnemonic");
    }

    public String defaultPayerMnemonicFile() {
        return props.get("default.payer.mnemonicFile");
    }

    public String defaultPayerPemKeyLoc() {
        return props.get("default.payer.pemKeyLoc");
    }

    public String defaultPayerPemKeyPassphrase() {
        return props.get("default.payer.pemKeyPassphrase");
    }

    public AccountID defaultPayer() {
        return props.getAccount("default.payer");
    }

    public String defaultPayerKey() {
        return props.get("default.payer.key");
    }

    public String defaultPayerName() {
        return props.get("default.payer.name");
    }

    public AccountID defaultProxy() {
        return props.getAccount("default.proxy");
    }

    public long defaultQueueSaturationMs() {
        return props.getLong("default.queueSaturation.ms");
    }

    public RealmID defaultRealm() {
        return props.getRealm("default.realm");
    }

    /**
     * Returns whether a {@link HapiSpec} should automatically take and fuzzy-match snapshots of the record stream.
     *
     * @return whether a {@link HapiSpec} should automatically take and fuzzy-match snapshots of the record stream
     */
    public boolean autoSnapshotManagement() {
        return props.getBoolean("recordStream.autoSnapshotManagement");
    }

    /**
     * Returns the record stream source for the {@link HapiSpec} to use when automatically taking snapshots
     * with {@code recordStream.autoSnapshotManagement=true}.
     *
     * @return the record stream source for the {@link HapiSpec} to use when automatically taking snapshots
     */
    public AutoSnapshotRecordSource autoSnapshotTarget() {
        return props.getAutoSnapshotRecordSource("recordStream.autoSnapshotTarget");
    }

    /**
     * Returns the record stream source for the {@link HapiSpec} to use when automatically matching snapshots
     * with {@code recordStream.autoMatchTarget=true}.
     *
     * @return the record stream source for the {@link HapiSpec} to use when automatically matching snapshots
     */
    public AutoSnapshotRecordSource autoMatchTarget() {
        return props.getAutoSnapshotRecordSource("recordStream.autoMatchTarget");
    }

    public boolean defaultReceiverSigRequired() {
        return props.getBoolean("default.receiverSigRequired");
    }

    public ShardID defaultShard() {
        return props.getShard("default.shard");
    }

    public int defaultThresholdM() {
        return props.getInteger("default.thresholdKey.M");
    }

    public int defaultThresholdN() {
        return props.getInteger("default.thresholdKey.N");
    }

    public long defaultThroughputObsExpiryMs() {
        return props.getLong("default.throughputObs.expiry.ms");
    }

    public long defaultThroughputObsSleepMs() {
        return props.getLong("default.throughputObs.sleep.ms");
    }

    public String defaultTokenSymbol() {
        return props.get("default.token.symbol");
    }

    public String defaultTokenName() {
        return props.get("default.token.name");
    }

    public long defaultTokenInitialSupply() {
        return props.getLong("default.token.initialSupply");
    }

    public int defaultTokenDecimals() {
        return props.getInteger("default.token.decimals");
    }

    public int defaultTopicRunningHashVersion() {
        return props.getInteger("default.topic.runningHash.version");
    }

    public AccountID defaultTransfer() {
        return props.getAccount("default.transfer");
    }

    public String defaultTransferName() {
        return props.get("default.transfer.name");
    }

    public Duration defaultValidDuration() {
        return props.getDurationFromSecs("default.validDuration.secs");
    }

    public FileID exchangeRatesId() {
        return props.getFile("exchange.rates.id");
    }

    public String exchangeRatesName() {
        return props.get("exchange.rates.name");
    }

    public AccountID exchangeRatesControl() {
        return props.getAccount("exchange.rates.controlAccount.id");
    }

    public String exchangeRatesControlName() {
        return props.get("exchange.rates.controlAccount.name");
    }

    public HapiSpec.SpecStatus expectedFinalStatus() {
        return props.getSpecStatus("expected.final.status");
    }

    public AccountID feeScheduleControl() {
        return props.getAccount("fee.schedule.controlAccount.id");
    }

    public String feeScheduleControlName() {
        return props.get("fee.schedule.controlAccount.name");
    }

    public long feeScheduleFetchFee() {
        return props.getLong("fee.schedule.fetch.fee");
    }

    public FileID feeScheduleId() {
        return props.getFile("fee.schedule.id");
    }

    public String feeScheduleName() {
        return props.get("fee.schedule.name");
    }

    public int feesTokenTransferUsageMultiplier() {
        return props.getInteger("fees.tokenTransferUsageMultiplier");
    }

    public Boolean useFixedFee() {
        return props.getBoolean("fees.useFixedOffer");
    }

    public long fixedFee() {
        return props.getLong("fees.fixedOffer");
    }

    public String freezeAdminName() {
        return props.get("freeze.admin.name");
    }

    public AccountID freezeAdminId() {
        return props.getAccount("freeze.admin.id");
    }

    public FileID updateFeatureId() {
        return props.getFile("update.feature.id");
    }

    public String updateFeatureName() {
        return props.get("update.feature.name");
    }

    public AccountID fundingAccount() {
        return props.getAccount("funding.account");
    }

    public String fundingAccountName() {
        return props.get("funding.account.name");
    }

    public AccountID genesisAccount() {
        return props.getAccount("genesis.account");
    }

    public String genesisAccountName() {
        return props.get("genesis.account.name");
    }

    public ContractID invalidContract() {
        return props.getContract("invalid.contract");
    }

    public String invalidContractName() {
        return props.get("invalid.contract.name");
    }

    public Boolean measureConsensusLatency() {
        return props.getBoolean("measure.consensus.latency");
    }

    public Boolean suppressUnrecoverableNetworkFailures() {
        return props.getBoolean("warnings.suppressUnrecoverableNetworkFailures");
    }

    public FileID nodeDetailsId() {
        return props.getFile("node.details.id");
    }

    public String nodeDetailsName() {
        return props.get("node.details.name");
    }

    public List<NodeConnectInfo> nodes() {
        NodeConnectInfo.NEXT_DEFAULT_ACCOUNT_NUM = 3;
        return Stream.of(props.get("nodes").split(","))
                .map(NodeConnectInfo::new)
                .collect(toList());
    }

    public NodeSelection nodeSelector() {
        return props.getNodeSelector("node.selector");
    }

    public Integer numOpFinisherThreads() {
        return props.getInteger("num.opFinisher.threads");
    }

    public String persistentEntitiesDir() {
        return props.get("persistentEntities.dir.path");
    }

    public boolean requiresPersistentEntities() {
        return StringUtils.isNotEmpty(persistentEntitiesDir());
    }

    public boolean updateManifestsForCreatedPersistentEntities() {
        return props.getBoolean("persistentEntities.updateCreatedManifests");
    }

    public Integer port() {
        return props.getInteger("port");
    }

    public boolean statusDeferredResolvesDoAsync() {
        return props.getBoolean("status.deferredResolves.doAsync");
    }

    public long statusPreResolvePauseMs() {
        return props.getLong("status.preResolve.pause.ms");
    }

    public long statusWaitSleepMs() {
        return props.getLong("status.wait.sleep.ms");
    }

    public long statusWaitTimeoutMs() {
        return props.getLong("status.wait.timeout.ms");
    }

    public AccountID nodeRewardAccount() {
        return asAccount("0.0.801");
    }

    public AccountID stakingRewardAccount() {
        return asAccount("0.0.800");
    }

    public AccountID feeCollectorAccount() {
        return asAccount("0.0.802");
    }

    public String nodeRewardAccountName() {
        return "NODE_REWARD";
    }

    public String stakingRewardAccountName() {
        return "STAKING_REWARD";
    }

    public String feeCollectorAccountName() {
        return "FEE_COLLECTOR";
    }

    public FileID throttleDefinitionsId() {
        return props.getFile("throttle.definitions.id");
    }

    public String throttleDefinitionsName() {
        return props.get("throttle.definitions.name");
    }

    public TlsConfig tls() {
        return props.getTlsConfig("tls");
    }

    public boolean getConfigTLS() {
        boolean useTls = false;
        switch (this.tls()) {
            case ON:
                useTls = Boolean.TRUE;
                break;
            case OFF:
                useTls = Boolean.FALSE;
                break;
            case ALTERNATE:
                useTls = r.nextBoolean();
        }
        return useTls;
    }

    TxnProtoStructure txnProtoStructure() {
        var protoStructure = props.getTxnConfig("txn.proto.structure");
        if (TxnProtoStructure.ALTERNATE == protoStructure) {
            if (r.nextBoolean()) {
                return TxnProtoStructure.NEW;
            } else {
                return TxnProtoStructure.OLD;
            }
        }
        return protoStructure;
    }

    public long txnStartOffsetSecs() {
        return props.getLong("txn.start.offset.secs");
    }

    public AccountID strongControlAccount() {
        return props.getAccount("strong.control.account");
    }

    public String strongControlName() {
        return props.get("strong.control.name");
    }

    public AccountID systemDeleteAdmin() {
        return props.getAccount("systemDeleteAdmin.account");
    }

    public String systemDeleteAdminName() {
        return props.get("systemDeleteAdmin.name");
    }

    public AccountID systemUndeleteAdmin() {
        return props.getAccount("systemUndeleteAdmin.account");
    }

    public String systemUndeleteAdminName() {
        return props.get("systemUndeleteAdmin.name");
    }

    /**
     * Stream the set of HAPI operations that should be submitted to workflow port 60211/60212.
     * This code is needed to test each operation through the new workflow code.
     *
     * @return set of hapi operations
     */
    public Set<HederaFunctionality> workflowOperations() {
        final var workflowOps = props.get("client.workflow.operations");
        if (workflowOps.isEmpty()) {
            return Collections.emptySet();
        }
        return Stream.of(workflowOps.split(","))
                .map(HederaFunctionality::valueOf)
                .collect(toSet());
    }

    /**
     * Returns the set of response codes that should be always be enforced on ingest. When
     * {@link HapiTxnOp#hasPrecheck(ResponseCodeEnum)} is given a response code <i>not</i> in
     * this set, it will automatically accept {@code OK} in its place, but switch the expected
     * consensus status to that response code.
     *
     * <p>That is, for a non-streamlined status like {@link ResponseCodeEnum#INVALID_ACCOUNT_AMOUNTS},
     * {@code hasPrecheck(INVALID_ACCOUNT_AMOUNTS)} is equivalent to,
     * <pre>{@code
     *     cryptoTransfer(...)
     *         .hasPrecheckFrom(OK, INVALID_ACCOUNT_AMOUNTS)
     *         .hasKnownStatus(INVALID_ACCOUNT_AMOUNTS)
     * }</pre>
     *
     * @return the set of response codes that should be always be enforced on ingest
     */
    public Set<ResponseCodeEnum> streamlinedIngestChecks() {
        if (streamlinedIngestChecks == null) {
            final var nominal = props.get("spec.streamlinedIngestChecks");
            streamlinedIngestChecks = EnumSet.copyOf(
                    nominal.isEmpty()
                            ? Collections.emptySet()
                            : Stream.of(nominal.split(","))
                                    .map(ResponseCodeEnum::valueOf)
                                    .collect(Collectors.toSet()));
        }
        return streamlinedIngestChecks;
    }
}
