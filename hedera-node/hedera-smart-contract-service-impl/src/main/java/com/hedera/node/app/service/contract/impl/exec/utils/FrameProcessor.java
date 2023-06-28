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

package com.hedera.node.app.service.contract.impl.exec.utils;

import static com.hedera.node.app.service.contract.impl.exec.utils.FrameUtils.contractsConfigOf;
import static com.hedera.node.app.service.contract.impl.hevm.HederaEvmTransactionResult.failureFrom;
import static com.hedera.node.app.service.contract.impl.hevm.HederaEvmTransactionResult.successFrom;
import static com.hedera.node.app.service.contract.impl.utils.ConversionUtils.*;
import static java.util.Objects.requireNonNull;
import static org.hyperledger.besu.evm.frame.MessageFrame.State.COMPLETED_SUCCESS;

import com.hedera.node.app.service.contract.impl.exec.gas.CustomGasCalculator;
import com.hedera.node.app.service.contract.impl.exec.processors.CustomMessageCallProcessor;
import com.hedera.node.app.service.contract.impl.hevm.HederaEvmTransactionResult;
import com.hedera.node.app.service.contract.impl.hevm.HederaTracer;
import com.hedera.node.app.service.contract.impl.state.ProxyWorldUpdater;
import edu.umd.cs.findbugs.annotations.NonNull;
import javax.inject.Inject;
import javax.inject.Singleton;
import org.hyperledger.besu.evm.frame.MessageFrame;
import org.hyperledger.besu.evm.processor.ContractCreationProcessor;

@Singleton
public class FrameProcessor {
    private final CustomGasCalculator gasCalculator;

    @Inject
    public FrameProcessor(@NonNull final CustomGasCalculator gasCalculator) {
        this.gasCalculator = gasCalculator;
    }

    public HederaEvmTransactionResult process(
            final long gasLimit,
            @NonNull final MessageFrame frame,
            @NonNull final HederaTracer tracer,
            @NonNull final CustomMessageCallProcessor messageCall,
            @NonNull final ContractCreationProcessor contractCreation) {
        requireAllNonNull(frame, tracer, messageCall, contractCreation);

        // We compute the Hedera id up front because the called contract could
        // selfdestruct,preventing us from looking up its contract id later on
        final var recipientAddress = frame.getRecipientAddress();
        final var recipientEvmAddress = asEvmContractId(recipientAddress);
        final var recipientId = isLongZero(recipientAddress)
                ? asNumberedContractId(recipientAddress)
                : ((ProxyWorldUpdater) frame.getWorldUpdater()).getHederaContractId(recipientAddress);

        // Now process the transaction implied by the frame
        tracer.initProcess(frame);
        final var stack = frame.getMessageFrameStack();
        stack.addFirst(frame);
        while (!stack.isEmpty()) {
            process(stack.peekFirst(), tracer, messageCall, contractCreation);
        }
        tracer.finalizeProcess(frame);

        // And package up its result
        final var gasUsed = effectiveGasUsed(gasLimit, frame);
        if (frame.getState() == COMPLETED_SUCCESS) {
            return successFrom(gasUsed, recipientId, recipientEvmAddress, frame);
        } else {
            return failureFrom(gasUsed, frame);
        }
    }

    private void requireAllNonNull(
            @NonNull final MessageFrame frame,
            @NonNull final HederaTracer tracer,
            @NonNull final CustomMessageCallProcessor messageCall,
            @NonNull final ContractCreationProcessor contractCreation) {
        requireNonNull(frame);
        requireNonNull(tracer);
        requireNonNull(messageCall);
        requireNonNull(contractCreation);
    }

    private void process(
            @NonNull final MessageFrame frame,
            @NonNull final HederaTracer tracer,
            @NonNull final CustomMessageCallProcessor messageCall,
            @NonNull final ContractCreationProcessor contractCreation) {
        final var executor =
                switch (frame.getType()) {
                    case MESSAGE_CALL -> messageCall;
                    case CONTRACT_CREATION -> contractCreation;
                };
        executor.process(frame, tracer);
    }

    private long effectiveGasUsed(final long gasLimit, @NonNull final MessageFrame frame) {
        var nominalUsed = gasLimit - frame.getRemainingGas();
        final var selfDestructRefund = gasCalculator.getSelfDestructRefundAmount()
                * Math.min(frame.getSelfDestructs().size(), nominalUsed / gasCalculator.getMaxRefundQuotient());
        nominalUsed -= (selfDestructRefund + frame.getGasRefund());
        final var maxRefundPercent = contractsConfigOf(frame).maxRefundPercentOfGasLimit();
        return Math.max(nominalUsed, gasLimit - gasLimit * maxRefundPercent / 100);
    }
}
