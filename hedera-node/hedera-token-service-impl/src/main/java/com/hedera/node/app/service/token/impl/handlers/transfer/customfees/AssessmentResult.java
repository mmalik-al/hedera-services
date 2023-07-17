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

package com.hedera.node.app.service.token.impl.handlers.transfer.customfees;

import static java.util.Collections.emptyList;

import com.hedera.hapi.node.base.AccountAmount;
import com.hedera.hapi.node.base.AccountID;
import com.hedera.hapi.node.base.TokenID;
import com.hedera.hapi.node.base.TokenTransferList;
import com.hedera.hapi.node.transaction.AssessedCustomFee;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.apache.commons.lang3.tuple.Pair;

public class AssessmentResult {
    private Map<TokenID, Map<AccountID, Long>> htsAdjustments;
    // two maps to aggregate all custom fee balance changes. These two maps are used
    // to construct a transaction body that needs to be assessed again for custom fees
    private Map<AccountID, Long> hbarAdjustments;
    private Set<Pair<AccountID, TokenID>> royaltiesPaid;
    private Map<TokenID, Map<AccountID, Long>> immutableInputTokenAdjustments;
    private Map<TokenID, Map<AccountID, Long>> mutableInputTokenAdjustments;
    private Map<AccountID, Long> inputHbarAdjustments;
    /* And for each "assessable change" that can be charged a custom fee, delegate to our
    fee assessor to update the balance changes with the custom fee. */
    private List<AssessedCustomFee> assessedCustomFees;

    public AssessmentResult(
            final List<TokenTransferList> inputTokenTransfers, final List<AccountAmount> inputHbarTransfers) {
        mutableInputTokenAdjustments = buildFungibleTokenTransferMap(inputTokenTransfers);
        immutableInputTokenAdjustments = Collections.unmodifiableMap(mutableInputTokenAdjustments.entrySet().stream()
                .collect(Collectors.toMap(Map.Entry::getKey, entry -> Map.copyOf(entry.getValue()))));

        inputHbarAdjustments = buildHbarTransferMap(inputHbarTransfers);

        htsAdjustments = new HashMap<>();
        hbarAdjustments = new HashMap<>();
        royaltiesPaid = new HashSet<>();
        assessedCustomFees = new ArrayList<>();
    }

    public Map<TokenID, Map<AccountID, Long>> getImmutableInputTokenAdjustments() {
        return immutableInputTokenAdjustments;
    }

    public Map<TokenID, Map<AccountID, Long>> getMutableInputTokenAdjustments() {
        return mutableInputTokenAdjustments;
    }

    public Map<AccountID, Long> getHbarAdjustments() {
        return hbarAdjustments;
    }

    public Map<TokenID, Map<AccountID, Long>> getHtsAdjustments() {
        return htsAdjustments;
    }

    public List<AssessedCustomFee> getAssessedCustomFees() {
        return assessedCustomFees;
    }

    public void addAssessedCustomFee(final AssessedCustomFee assessedCustomFee) {
        assessedCustomFees.add(assessedCustomFee);
    }

    public Set<Pair<AccountID, TokenID>> getRoyaltiesPaid() {
        return royaltiesPaid;
    }

    public void setRoyaltiesPaid(final Set<Pair<AccountID, TokenID>> royaltiesPaid) {
        this.royaltiesPaid = royaltiesPaid;
    }

    public void addToRoyaltiesPaid(final Pair<AccountID, TokenID> paid) {
        royaltiesPaid.add(paid);
    }

    public Map<AccountID, Long> getInputHbarAdjustments() {
        return inputHbarAdjustments;
    }

    private Map<TokenID, Map<AccountID, Long>> buildFungibleTokenTransferMap(
            final List<TokenTransferList> tokenTransfers) {
        final var fungibleTransfersMap = new HashMap<TokenID, Map<AccountID, Long>>();
        for (final var xfer : tokenTransfers) {
            final var tokenId = xfer.token();
            final var fungibleTokenTransfers = xfer.transfersOrElse(emptyList());
            if (fungibleTokenTransfers.isEmpty()) {
                continue;
            }
            final var tokenTransferMap = new HashMap<AccountID, Long>();
            for (final var aa : fungibleTokenTransfers) {
                tokenTransferMap.put(aa.accountID(), aa.amount());
            }
            if (!tokenTransferMap.isEmpty()) {
                fungibleTransfersMap.put(tokenId, tokenTransferMap);
            }
        }
        return fungibleTransfersMap;
    }

    private Map<AccountID, Long> buildHbarTransferMap(@NonNull final List<AccountAmount> hbarTransfers) {
        final var adjustments = new HashMap<AccountID, Long>();
        for (final var aa : hbarTransfers) {
            adjustments.put(aa.accountID(), aa.amount());
        }
        return adjustments;
    }

    public boolean haveAssessedChanges() {
        return !assessedCustomFees.isEmpty() || !hbarAdjustments.isEmpty() || !htsAdjustments.isEmpty();
    }
}
