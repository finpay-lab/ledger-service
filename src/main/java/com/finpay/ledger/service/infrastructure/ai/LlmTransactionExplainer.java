package com.finpay.ledger.service.infrastructure.ai;

import com.finpay.common.ai.domain.LlmPort;
import com.finpay.ledger.service.domain.TransactionExplainer;

/**
 * AI-1 implementation: builds a tight prompt from the event envelope and asks
 * the shared {@link LlmPort} for a plain-language explanation. The port is
 * BYOK + off-mode safe, so this never throws and degrades to a labeled
 * stand-in when no LLM backend is configured (FP-65).
 */
public final class LlmTransactionExplainer implements TransactionExplainer {

    private final LlmPort llm;

    public LlmTransactionExplainer(LlmPort llm) {
        this.llm = llm;
    }

    @Override
    public Explanation explain(String eventJson) {
        String system = "You are a concise financial explainer for a banking ledger. "
                + "Explain the transaction in 1-2 plain sentences for a customer. No advice.";
        String user = "Event: " + (eventJson == null ? "{}" : eventJson);
        var result = llm.complete(system, user);
        return new Explanation(result.text(), result.fromModel());
    }
}
