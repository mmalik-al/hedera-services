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

package com.hedera.node.app.fixtures.workflows.handle.verifier;

import static com.hedera.node.app.signature.impl.SignatureVerificationImpl.failedVerification;
import static com.hedera.node.app.signature.impl.SignatureVerificationImpl.passedVerification;
import static java.util.Collections.emptyList;
import static java.util.Objects.requireNonNull;
import static java.util.concurrent.CompletableFuture.completedFuture;

import com.hedera.hapi.node.base.Key;
import com.hedera.hapi.node.base.KeyList;
import com.hedera.node.app.fixtures.workflows.prehandle.FakeSignatureVerificationFuture;
import com.hedera.node.app.signature.CompoundSignatureVerificationFuture;
import com.hedera.node.app.signature.KeyVerifier;
import com.hedera.node.app.signature.SignatureVerificationFuture;
import com.hedera.node.app.signature.impl.SignatureVerificationImpl;
import com.hedera.node.app.spi.fixtures.Scenarios;
import com.hedera.node.app.spi.signatures.SignatureVerification;
import com.hedera.node.app.spi.signatures.VerificationAssistant;
import com.hedera.node.config.data.HederaConfig;
import com.hedera.pbj.runtime.io.buffer.Bytes;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Supplier;

public class FakeKeyVerifier implements KeyVerifier {
    private final Map<Key, SignatureVerificationFuture> keyVerifications = new HashMap<>() {
        {
            put(
                    Scenarios.ALICE.keyInfo().publicKey(),
                    new FakeSignatureVerificationFuture(new SignatureVerificationImpl(
                            Scenarios.ALICE.keyInfo().publicKey(),
                            Scenarios.ALICE.account().alias(),
                            true)));
        }
    };
    private final long timeout;

    public FakeKeyVerifier(@NonNull final HederaConfig config) {
        timeout = requireNonNull(config, "config must not be null").workflowVerificationTimeoutMS();
    }

    @NonNull
    @Override
    public SignatureVerification verificationFor(@NonNull Key key) {
        requireNonNull(key, "key must not be null");
        // FUTURE: Cache the results of this method, if it is usually called several times
        return resolveFuture(verificationFutureFor(key), () -> failedVerification(key));
    }

    @NonNull
    @Override
    public SignatureVerification verificationFor(@NonNull Key key, @NonNull VerificationAssistant callback) {
        requireNonNull(key, "key must not be null");
        requireNonNull(callback, "callback must not be null");

        return switch (key.key().kind()) {
            case ED25519, ECDSA_SECP256K1 -> {
                final var result = resolveFuture(keyVerifications.get(key), () -> failedVerification(key));
                yield callback.test(key, result) ? passedVerification(key) : failedVerification(key);
            }
            case KEY_LIST -> {
                final var keys = key.keyListOrThrow().keysOrElse(emptyList());
                var failed = keys.isEmpty();
                for (final var childKey : keys) {
                    failed |= verificationFor(childKey, callback).failed();
                }
                yield failed ? failedVerification(key) : passedVerification(key);
            }
            case THRESHOLD_KEY -> {
                final var thresholdKey = key.thresholdKeyOrThrow();
                final var keyList = thresholdKey.keysOrElse(KeyList.DEFAULT);
                final var keys = keyList.keysOrElse(emptyList());
                final var threshold = thresholdKey.threshold();
                final var clampedThreshold = Math.max(1, Math.min(threshold, keys.size()));
                var passed = 0;
                for (final var childKey : keys) {
                    if (verificationFor(childKey, callback).passed()) {
                        passed++;
                    }
                }
                yield passed >= clampedThreshold ? passedVerification(key) : failedVerification(key);
            }
            case CONTRACT_ID, DELEGATABLE_CONTRACT_ID, ECDSA_384, RSA_3072, UNSET -> {
                final var failure = failedVerification(key);
                yield callback.test(key, failure) ? passedVerification(key) : failure;
            }
        };
    }

    @NonNull
    @Override
    public SignatureVerification verificationFor(@NonNull Bytes evmAlias) {
        requireNonNull(evmAlias, "evmAlias must not be null");
        // FUTURE: Cache the results of this method, if it is usually called several times
        if (evmAlias.length() == 20) {
            for (final var result : keyVerifications.values()) {
                final var account = result.evmAlias();
                if (account != null && evmAlias.matchesPrefix(account)) {
                    return resolveFuture(result, () -> failedVerification(evmAlias));
                }
            }
        }
        return failedVerification(evmAlias);
    }

    @Override
    public int numSignaturesVerified() {
        return keyVerifications.size();
    }

    @NonNull
    private Future<SignatureVerification> verificationFutureFor(@NonNull final Key key) {
        return switch (key.key().kind()) {
            case ED25519, ECDSA_SECP256K1 -> {
                final var result = keyVerifications.get(key);
                yield result == null ? completedFuture(failedVerification(key)) : result;
            }
            case KEY_LIST -> {
                final var keys = key.keyListOrThrow().keysOrElse(emptyList());
                yield verificationFutureFor(key, keys, 0);
            }
            case THRESHOLD_KEY -> {
                final var thresholdKey = key.thresholdKeyOrThrow();
                final var keyList = thresholdKey.keysOrElse(KeyList.DEFAULT);
                final var keys = keyList.keysOrElse(emptyList());
                final var threshold = thresholdKey.threshold();
                final var clampedThreshold = Math.max(1, Math.min(threshold, keys.size()));
                yield verificationFutureFor(key, keys, keys.size() - clampedThreshold);
            }
            case CONTRACT_ID, DELEGATABLE_CONTRACT_ID, ECDSA_384, RSA_3072, UNSET -> completedFuture(
                    failedVerification(key));
        };
    }

    @NonNull
    private Future<SignatureVerification> verificationFutureFor(
            @NonNull final Key key, @NonNull final List<Key> keys, final int numCanFail) {
        // If there are no keys, then we always fail. There must be at least one key in a key list or threshold key
        // for it to be a valid key and to pass any form of verification.
        if (keys.isEmpty() || numCanFail < 0) {
            return completedFuture(failedVerification(key));
        }
        final var futures = keys.stream().map(this::verificationFutureFor).toList();
        return new CompoundSignatureVerificationFuture(key, null, futures, numCanFail);
    }

    @NonNull
    private SignatureVerification resolveFuture(
            @Nullable final Future<SignatureVerification> future,
            @NonNull final Supplier<SignatureVerification> fallback) {
        if (future == null) {
            return fallback.get();
        }
        try {
            return future.get(timeout, TimeUnit.MILLISECONDS);
        } catch (final InterruptedException | ExecutionException | TimeoutException e) {
            Thread.currentThread().interrupt();
        }
        return fallback.get();
    }
}
