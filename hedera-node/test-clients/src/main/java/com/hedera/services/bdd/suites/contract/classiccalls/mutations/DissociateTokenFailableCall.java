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

package com.hedera.services.bdd.suites.contract.classiccalls.mutations;

import static com.hedera.services.bdd.spec.HapiPropertySource.idAsHeadlongAddress;
import static com.hedera.services.bdd.suites.contract.classiccalls.ClassicFailureMode.INVALID_ACCOUNT_ID_FAILURE;
import static com.hedera.services.bdd.suites.contract.classiccalls.ClassicFailureMode.INVALID_TOKEN_ID_FAILURE;
import static com.hedera.services.bdd.suites.contract.classiccalls.ClassicInventory.ALICE;
import static com.hedera.services.bdd.suites.contract.classiccalls.ClassicInventory.INVALID_ACCOUNT_ADDRESS;
import static com.hedera.services.bdd.suites.contract.classiccalls.ClassicInventory.INVALID_TOKEN_ADDRESS;
import static com.hedera.services.bdd.suites.contract.classiccalls.ClassicInventory.VALID_NON_FUNGIBLE_TOKEN_IDS;

import com.esaulpaugh.headlong.abi.Function;
import com.hedera.services.bdd.spec.HapiSpec;
import com.hedera.services.bdd.suites.contract.classiccalls.AbstractFailableNonStaticCall;
import com.hedera.services.bdd.suites.contract.classiccalls.ClassicFailureMode;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.EnumSet;

public class DissociateTokenFailableCall extends AbstractFailableNonStaticCall {
    private static final Function SIGNATURE = new Function("dissociateToken(address,address)", "(int64)");

    public DissociateTokenFailableCall() {
        super(EnumSet.of(INVALID_TOKEN_ID_FAILURE, INVALID_ACCOUNT_ID_FAILURE));
    }

    @Override
    public String name() {
        return "dissociateToken";
    }

    @Override
    public byte[] encodedCall(@NonNull final ClassicFailureMode mode, @NonNull final HapiSpec spec) {
        throwIfUnsupported(mode);
        final var validAccountAddress = idAsHeadlongAddress(spec.registry().getAccountID(ALICE));
        final var validTokenAddress = idAsHeadlongAddress(spec.registry().getTokenID(VALID_NON_FUNGIBLE_TOKEN_IDS[0]));
        if (mode == INVALID_TOKEN_ID_FAILURE) {
            return SIGNATURE
                    .encodeCallWithArgs(validAccountAddress, INVALID_TOKEN_ADDRESS)
                    .array();
        } else {
            // Must be INVALID_ACCOUNT_ID_FAILURE
            return SIGNATURE
                    .encodeCallWithArgs(INVALID_ACCOUNT_ADDRESS, validTokenAddress)
                    .array();
        }
    }
}
