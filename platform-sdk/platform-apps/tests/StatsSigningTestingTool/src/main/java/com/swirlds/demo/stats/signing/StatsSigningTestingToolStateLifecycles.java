/*
 * Copyright (C) 2025 Hedera Hashgraph, LLC
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

package com.swirlds.demo.stats.signing;

import static com.swirlds.common.utility.CommonUtils.hex;
import static com.swirlds.logging.legacy.LogMarker.EXCEPTION;
import static com.swirlds.logging.legacy.LogMarker.TESTING_EXCEPTIONS_ACCEPTABLE_RECONNECT;

import com.hedera.hapi.platform.event.StateSignatureTransaction;
import com.swirlds.common.context.PlatformContext;
import com.swirlds.common.crypto.CryptographyHolder;
import com.swirlds.common.crypto.TransactionSignature;
import com.swirlds.common.crypto.VerificationStatus;
import com.swirlds.platform.components.transaction.system.ScopedSystemTransaction;
import com.swirlds.platform.state.StateLifecycles;
import com.swirlds.platform.system.InitTrigger;
import com.swirlds.platform.system.Platform;
import com.swirlds.platform.system.Round;
import com.swirlds.platform.system.SoftwareVersion;
import com.swirlds.platform.system.address.AddressBook;
import com.swirlds.platform.system.events.Event;
import com.swirlds.platform.system.transaction.ConsensusTransaction;
import com.swirlds.platform.system.transaction.Transaction;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.function.Consumer;
import java.util.function.Supplier;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class StatsSigningTestingToolStateLifecycles implements StateLifecycles<StatsSigningTestingToolState> {

    /**
     * use this for all logging, as controlled by the optional data/log4j2.xml file
     */
    private static final Logger logger = LogManager.getLogger(StatsSigningTestingToolStateLifecycles.class);

    /** if true, artificially take {@link #HANDLE_MICROS} to handle each consensus transaction */
    private static final boolean SYNTHETIC_HANDLE_TIME = false;

    /** the number of microseconds to wait before returning from the handle method */
    private static final int HANDLE_MICROS = 100;

    private final Supplier<SttTransactionPool> transactionPoolSupplier;

    public StatsSigningTestingToolStateLifecycles(Supplier<SttTransactionPool> transactionPoolSupplier) {
        this.transactionPoolSupplier = transactionPoolSupplier;
    }

    @Override
    public void onStateInitialized(
            @NonNull StatsSigningTestingToolState state,
            @NonNull Platform platform,
            @NonNull InitTrigger trigger,
            @Nullable SoftwareVersion previousVersion) {}

    @Override
    public void onPreHandle(
            @NonNull Event event,
            @NonNull StatsSigningTestingToolState state,
            @NonNull Consumer<ScopedSystemTransaction<StateSignatureTransaction>> stateSignatureTransactionCallback) {
        final SttTransactionPool sttTransactionPool = transactionPoolSupplier.get();
        if (sttTransactionPool != null) {
            event.forEachTransaction(transaction -> {
                if (transaction.isSystem()) {
                    return;
                }
                final TransactionSignature transactionSignature =
                        sttTransactionPool.expandSignatures(transaction.getApplicationTransaction());
                if (transactionSignature != null) {
                    transaction.setMetadata(transactionSignature);
                    CryptographyHolder.get().verifySync(List.of(transactionSignature));
                }
            });
        }
    }

    @Override
    public void onHandleConsensusRound(
            @NonNull Round round,
            @NonNull StatsSigningTestingToolState state,
            @NonNull Consumer<ScopedSystemTransaction<StateSignatureTransaction>> stateSignatureTransactionCallback) {
        state.throwIfImmutable();
        round.forEachTransaction(v -> handleTransaction(v, state));
    }

    private void handleTransaction(final ConsensusTransaction trans, final StatsSigningTestingToolState state) {
        if (trans.isSystem()) {
            return;
        }
        final TransactionSignature s = trans.getMetadata();

        if (s != null && validateSignature(s, trans) && s.getSignatureStatus() != VerificationStatus.VALID) {
            logger.error(
                    EXCEPTION.getMarker(),
                    "Invalid Transaction Signature [ transactionId = {}, status = {}, signatureType = {},"
                            + " publicKey = {}, signature = {}, data = {} ]",
                    TransactionCodec.txId(trans.getApplicationTransaction()),
                    s.getSignatureStatus(),
                    s.getSignatureType(),
                    hex(Arrays.copyOfRange(
                            s.getContentsDirect(),
                            s.getPublicKeyOffset(),
                            s.getPublicKeyOffset() + s.getPublicKeyLength())),
                    hex(Arrays.copyOfRange(
                            s.getContentsDirect(),
                            s.getSignatureOffset(),
                            s.getSignatureOffset() + s.getSignatureLength())),
                    hex(Arrays.copyOfRange(
                            s.getContentsDirect(), s.getMessageOffset(), s.getMessageOffset() + s.getMessageLength())));
        }

        state.incrementRunningSum(TransactionCodec.txId(trans.getApplicationTransaction()));

        maybeDelay();
    }

    private void maybeDelay() {
        if (SYNTHETIC_HANDLE_TIME) {
            final long start = System.nanoTime();
            while (System.nanoTime() - start < (HANDLE_MICROS * 1_000)) {
                // busy wait
            }
        }
    }

    private boolean validateSignature(final TransactionSignature signature, final Transaction transaction) {
        try {
            final Future<Void> future = signature.waitForFuture();
            // Block & Ignore the Void return
            future.get();
            return true;
        } catch (final InterruptedException e) {
            logger.info(
                    TESTING_EXCEPTIONS_ACCEPTABLE_RECONNECT.getMarker(),
                    "handleTransaction Interrupted. This should happen only during a reconnect");
            Thread.currentThread().interrupt();
        } catch (final ExecutionException e) {
            logger.error(
                    EXCEPTION.getMarker(),
                    "error while validating transaction signature for transaction {}",
                    transaction,
                    e);
        }
        return false;
    }

    @Override
    public void onSealConsensusRound(@NonNull Round round, @NonNull StatsSigningTestingToolState state) {}

    @Override
    public void onUpdateWeight(
            @NonNull StatsSigningTestingToolState state,
            @NonNull AddressBook configAddressBook,
            @NonNull PlatformContext context) {}

    @Override
    public void onNewRecoveredState(@NonNull StatsSigningTestingToolState recoveredState) {}
}
