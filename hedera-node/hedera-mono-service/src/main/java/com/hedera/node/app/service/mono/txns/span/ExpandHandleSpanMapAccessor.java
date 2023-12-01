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

package com.hedera.node.app.service.mono.txns.span;

import com.hedera.node.app.hapi.fees.usage.crypto.CryptoApproveAllowanceMeta;
import com.hedera.node.app.hapi.fees.usage.crypto.CryptoCreateMeta;
import com.hedera.node.app.hapi.fees.usage.crypto.CryptoDeleteAllowanceMeta;
import com.hedera.node.app.hapi.fees.usage.crypto.CryptoUpdateMeta;
import com.hedera.node.app.hapi.fees.usage.token.meta.TokenFeeScheduleUpdateMeta;
import com.hedera.node.app.hapi.fees.usage.token.meta.TokenBurnMeta;
import com.hedera.node.app.hapi.fees.usage.token.meta.TokenCreateMeta;
import com.hedera.node.app.hapi.fees.usage.token.meta.TokenFreezeMeta;
import com.hedera.node.app.hapi.fees.usage.token.meta.TokenPauseMeta;
import com.hedera.node.app.hapi.fees.usage.token.meta.TokenUnfreezeMeta;
import com.hedera.node.app.hapi.fees.usage.token.meta.TokenUnpauseMeta;
import com.hedera.node.app.hapi.fees.usage.token.meta.TokenWipeMeta;
import com.hedera.node.app.hapi.fees.usage.util.UtilPrngMeta;
import com.hedera.node.app.hapi.utils.ethereum.EthTxData;
import com.hedera.node.app.hapi.utils.ethereum.EthTxSigs;
import com.hedera.node.app.service.mono.grpc.marshalling.ImpliedTransfers;
import com.hedera.node.app.service.mono.utils.accessors.TxnAccessor;
import com.hederahashgraph.api.proto.java.TransactionBody;
import java.util.Map;
import javax.inject.Inject;
import javax.inject.Singleton;

/** Minimal helper class for getting/setting entries in a span map. */
@Singleton
public class ExpandHandleSpanMapAccessor {
    private static final String IMPLIED_TRANSFERS_KEY = "impliedTransfers";
    private static final String FEE_SCHEDULE_UPDATE_META_KEY = "feeScheduleUpdateMeta";
    private static final String TOKEN_CREATE_META_KEY = "tokenCreateMeta";
    private static final String TOKEN_BURN_META_KEY = "tokenBurnMeta";
    private static final String TOKEN_WIPE_META_KEY = "tokenWipeMeta";
    private static final String TOKEN_FREEZE_META_KEY = "tokenFreezeMeta";
    private static final String TOKEN_UNFREEZE_META_KEY = "tokenUnfreezeMeta";
    private static final String TOKEN_PAUSE_META_KEY = "tokenPauseMeta";
    private static final String TOKEN_UNPAUSE_META_KEY = "tokenUnpauseMeta";
    private static final String CRYPTO_CREATE_META_KEY = "cryptoCreateMeta";
    private static final String CRYPTO_UPDATE_META_KEY = "cryptoUpdateMeta";
    private static final String CRYPTO_APPROVE_META_KEY = "cryptoApproveMeta";
    private static final String CRYPTO_DELETE_ALLOWANCE_META_KEY = "cryptoDeleteAllowanceMeta";
    private static final String ETH_TX_DATA_META_KEY = "ethTxDataMeta";
    private static final String ETH_TX_SIGS_META_KEY = "ethTxSigsMeta";
    private static final String ETH_TX_BODY_META_KEY = "ethTxBodyMeta";
    private static final String ETH_TX_EXPANSION_KEY = "ethTxExpansion";
    private static final String UTIL_PRNG_META_KEY = "utilPrngMeta";

    @Inject
    public ExpandHandleSpanMapAccessor() {
        // Default constructor
    }

    public void setFeeScheduleUpdateMeta(
            final TxnAccessor accessor, final TokenFeeScheduleUpdateMeta tokenFeeScheduleUpdateMeta) {
        accessor.getSpanMap().put(FEE_SCHEDULE_UPDATE_META_KEY, tokenFeeScheduleUpdateMeta);
    }

    public TokenFeeScheduleUpdateMeta getFeeScheduleUpdateMeta(final TxnAccessor accessor) {
        return (TokenFeeScheduleUpdateMeta) accessor.getSpanMap().get(FEE_SCHEDULE_UPDATE_META_KEY);
    }

    public void setImpliedTransfers(final TxnAccessor accessor, final ImpliedTransfers impliedTransfers) {
        accessor.getSpanMap().put(IMPLIED_TRANSFERS_KEY, impliedTransfers);
    }

    public ImpliedTransfers getImpliedTransfers(final TxnAccessor accessor) {
        return (ImpliedTransfers) accessor.getSpanMap().get(IMPLIED_TRANSFERS_KEY);
    }

    public void setTokenCreateMeta(final TxnAccessor accessor, final TokenCreateMeta tokenCreateMeta) {
        accessor.getSpanMap().put(TOKEN_CREATE_META_KEY, tokenCreateMeta);
    }

    public TokenCreateMeta getTokenCreateMeta(final TxnAccessor accessor) {
        return (TokenCreateMeta) accessor.getSpanMap().get(TOKEN_CREATE_META_KEY);
    }

    public void setTokenBurnMeta(final TxnAccessor accessor, final TokenBurnMeta tokenBurnMeta) {
        accessor.getSpanMap().put(TOKEN_BURN_META_KEY, tokenBurnMeta);
    }

    public TokenBurnMeta getTokenBurnMeta(final TxnAccessor accessor) {
        return (TokenBurnMeta) accessor.getSpanMap().get(TOKEN_BURN_META_KEY);
    }

    public void setTokenWipeMeta(final TxnAccessor accessor, final TokenWipeMeta tokenWipeMeta) {
        accessor.getSpanMap().put(TOKEN_WIPE_META_KEY, tokenWipeMeta);
    }

    public TokenWipeMeta getTokenWipeMeta(final TxnAccessor accessor) {
        return (TokenWipeMeta) accessor.getSpanMap().get(TOKEN_WIPE_META_KEY);
    }

    public void setTokenFreezeMeta(final TxnAccessor accessor, final TokenFreezeMeta tokenFreezeMeta) {
        accessor.getSpanMap().put(TOKEN_FREEZE_META_KEY, tokenFreezeMeta);
    }

    public TokenFreezeMeta getTokenFreezeMeta(final TxnAccessor accessor) {
        return (TokenFreezeMeta) accessor.getSpanMap().get(TOKEN_FREEZE_META_KEY);
    }

    public void setTokenUnfreezeMeta(final TxnAccessor accessor, final TokenUnfreezeMeta tokenUnfreezeMeta) {
        accessor.getSpanMap().put(TOKEN_UNFREEZE_META_KEY, tokenUnfreezeMeta);
    }

    public TokenUnfreezeMeta getTokenUnfreezeMeta(final TxnAccessor accessor) {
        return (TokenUnfreezeMeta) accessor.getSpanMap().get(TOKEN_UNFREEZE_META_KEY);
    }

    public void setTokenPauseMeta(final TxnAccessor accessor, final TokenPauseMeta tokenPauseMeta) {
        accessor.getSpanMap().put(TOKEN_PAUSE_META_KEY, tokenPauseMeta);
    }

    public TokenPauseMeta getTokenPauseMeta(final TxnAccessor accessor) {
        return (TokenPauseMeta) accessor.getSpanMap().get(TOKEN_PAUSE_META_KEY);
    }

    public void setTokenUnpauseMeta(final TxnAccessor accessor, final TokenUnpauseMeta tokenUnpauseMeta) {
        accessor.getSpanMap().put(TOKEN_UNPAUSE_META_KEY, tokenUnpauseMeta);
    }

    public TokenUnpauseMeta getTokenUnpauseMeta(final TxnAccessor accessor) {
        return (TokenUnpauseMeta) accessor.getSpanMap().get(TOKEN_UNPAUSE_META_KEY);
    }

    public void setCryptoCreateMeta(final TxnAccessor accessor, final CryptoCreateMeta cryptoCreateMeta) {
        accessor.getSpanMap().put(CRYPTO_CREATE_META_KEY, cryptoCreateMeta);
    }

    public CryptoCreateMeta getCryptoCreateMeta(final TxnAccessor accessor) {
        return (CryptoCreateMeta) accessor.getSpanMap().get(CRYPTO_CREATE_META_KEY);
    }

    public void setCryptoUpdate(final TxnAccessor accessor, final CryptoUpdateMeta cryptoUpdateMeta) {
        accessor.getSpanMap().put(CRYPTO_UPDATE_META_KEY, cryptoUpdateMeta);
    }

    public CryptoUpdateMeta getCryptoUpdateMeta(final TxnAccessor accessor) {
        return (CryptoUpdateMeta) accessor.getSpanMap().get(CRYPTO_UPDATE_META_KEY);
    }

    public void setCryptoApproveMeta(final TxnAccessor accessor, final CryptoApproveAllowanceMeta cryptoApproveMeta) {
        accessor.getSpanMap().put(CRYPTO_APPROVE_META_KEY, cryptoApproveMeta);
    }

    public CryptoApproveAllowanceMeta getCryptoApproveMeta(final TxnAccessor accessor) {
        return (CryptoApproveAllowanceMeta) accessor.getSpanMap().get(CRYPTO_APPROVE_META_KEY);
    }

    public void setCryptoDeleteAllowanceMeta(
            final TxnAccessor accessor, final CryptoDeleteAllowanceMeta cryptoDeleteAllowanceMeta) {
        accessor.getSpanMap().put(CRYPTO_DELETE_ALLOWANCE_META_KEY, cryptoDeleteAllowanceMeta);
    }

    public CryptoDeleteAllowanceMeta getCryptoDeleteAllowanceMeta(final TxnAccessor accessor) {
        return (CryptoDeleteAllowanceMeta) accessor.getSpanMap().get(CRYPTO_DELETE_ALLOWANCE_META_KEY);
    }

    public void setEthTxDataMeta(final TxnAccessor accessor, final EthTxData ethTxData) {
        accessor.getSpanMap().put(ETH_TX_DATA_META_KEY, ethTxData);
    }

    public void setEthTxDataMeta(final Map<String, Object> spanMap, final EthTxData ethTxData) {
        spanMap.put(ETH_TX_DATA_META_KEY, ethTxData);
    }

    public EthTxData getEthTxDataMeta(final TxnAccessor accessor) {
        return (EthTxData) accessor.getSpanMap().get(ETH_TX_DATA_META_KEY);
    }

    public EthTxData getEthTxDataMeta(final Map<String, Object> spanMap) {
        return (EthTxData) spanMap.get(ETH_TX_DATA_META_KEY);
    }

    public void setEthTxSigsMeta(final TxnAccessor accessor, final EthTxSigs ethTxSigs) {
        accessor.getSpanMap().put(ETH_TX_SIGS_META_KEY, ethTxSigs);
    }

    public void setEthTxSigsMeta(final Map<String, Object> spanMap, final EthTxSigs ethTxSigs) {
        spanMap.put(ETH_TX_SIGS_META_KEY, ethTxSigs);
    }

    public EthTxSigs getEthTxSigsMeta(final TxnAccessor accessor) {
        return (EthTxSigs) accessor.getSpanMap().get(ETH_TX_SIGS_META_KEY);
    }

    public void setEthTxBodyMeta(final TxnAccessor accessor, final TransactionBody txBody) {
        accessor.getSpanMap().put(ETH_TX_BODY_META_KEY, txBody);
    }

    public void setEthTxBodyMeta(final Map<String, Object> spanMap, final TransactionBody txBody) {
        spanMap.put(ETH_TX_BODY_META_KEY, txBody);
    }

    public TransactionBody getEthTxBodyMeta(final TxnAccessor accessor) {
        return (TransactionBody) accessor.getSpanMap().get(ETH_TX_BODY_META_KEY);
    }

    public void setEthTxExpansion(final TxnAccessor accessor, final EthTxExpansion expansion) {
        accessor.getSpanMap().put(ETH_TX_EXPANSION_KEY, expansion);
    }

    public void setEthTxExpansion(final Map<String, Object> spanMap, final EthTxExpansion expansion) {
        spanMap.put(ETH_TX_EXPANSION_KEY, expansion);
    }

    public EthTxExpansion getEthTxExpansion(final TxnAccessor accessor) {
        return (EthTxExpansion) accessor.getSpanMap().get(ETH_TX_EXPANSION_KEY);
    }

    public UtilPrngMeta getUtilPrngMeta(final TxnAccessor accessor) {
        return (UtilPrngMeta) accessor.getSpanMap().get(UTIL_PRNG_META_KEY);
    }

    public void setUtilPrngMeta(final TxnAccessor accessor, final UtilPrngMeta utilPrngMeta) {
        accessor.getSpanMap().put(UTIL_PRNG_META_KEY, utilPrngMeta);
    }
}
