package com.finpay.ledger.service.web;

import com.finpay.common.ai.domain.LlmPort;
import com.finpay.ledger.service.domain.TransactionExplainer;
import com.finpay.ledger.service.infrastructure.ai.LlmTransactionExplainer;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * AI-1 edge: POST /ai/transactions/explain. Transport only — delegates to the
 * {@link TransactionExplainer} use case (Rule 3: no business logic here).
 */
@RestController
@RequestMapping("/ai/transactions")
public class TransactionExplainController {

    private final TransactionExplainer explainer;

    public TransactionExplainController(LlmPort llm) {
        this.explainer = new LlmTransactionExplainer(llm);
    }

    public record ExplainRequest(@NotBlank String event) {}
    public record ExplainResponse(String explanation, boolean fromModel) {}

    @PostMapping("/explain")
    public ExplainResponse explain(@Valid @RequestBody ExplainRequest req) {
        var e = explainer.explain(req.event());
        return new ExplainResponse(e.text(), e.fromModel());
    }
}
