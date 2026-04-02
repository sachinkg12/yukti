package io.yukti.api.handler;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.yukti.api.dto.v1.*;
import io.yukti.api.mapper.OptimizationMapperV1;
import io.yukti.catalog.ClasspathCatalogSource;
import io.yukti.core.api.Catalog;
import io.yukti.core.api.Optimizer;
import io.yukti.core.domain.*;
import io.yukti.core.explainability.NarrativeExplanation;
import io.yukti.core.explainability.Narrator;
import io.yukti.engine.explainability.DeterministicExplanationGeneratorV1;
import io.yukti.engine.explainability.ExplanationServiceV1;
import io.yukti.engine.explainability.LlmClaimGenerator;
import io.yukti.engine.explainability.LlmClaimGeneratorImpl;
import io.yukti.engine.explainability.LlmNarrator;
import io.yukti.engine.explainability.NoOpNarrator;
import io.yukti.engine.explainability.OpenAiLlmProvider;
import io.yukti.engine.explainability.StructuredExplanationBuilder;
import io.yukti.engine.optimizer.OptimizerRegistry;
import io.yukti.engine.parser.DeterministicGoalInterpreter;
import io.yukti.engine.parser.GoalInterpreter;
import io.yukti.engine.parser.GoalInterpretation;
import io.yukti.engine.parser.LlmGoalInterpreter;
import io.yukti.engine.display.AllocationDisplayEnricher;
import io.yukti.engine.parser.PreferenceParserV1;
import io.yukti.engine.valuation.DefaultCppTable;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.stream.Collectors;

/**
 * V1 API handler: /v1/optimize, /v1/catalog/cards, /v1/config/goals.
 * Deterministic, validation, X-Request-Id, ApiErrorResponse.
 */
public final class V1ApiHandler {

    private static final ObjectMapper OM = new ObjectMapper();
    private static final List<String> CATEGORIES = Arrays.stream(Category.values()).map(Enum::name).toList();

    private final Catalog catalog;
    private final Optimizer defaultOptimizer;
    private final OptimizerRegistry registry;
    private final ExplanationServiceV1 explanationService;
    private final PreferenceParserV1 parser;

    private static final String ENV_LLM_CLAIMS_ENABLED = "NARRATION_LLM_ENABLED";
    private static final String ENV_GOAL_LLM_ENABLED = "GOAL_LLM_ENABLED";

    private final GoalInterpreter goalInterpreter;

    public V1ApiHandler() throws Exception {
        catalog = new ClasspathCatalogSource("catalog/catalog-v1.json").load("1.0");
        registry = new OptimizerRegistry();
        defaultOptimizer = registry.select();
        Narrator narrator = createNarrator();
        OpenAiLlmProvider provider = OpenAiLlmProvider.fromEnvOrNull();
        boolean llmClaimsEnabled = "true".equalsIgnoreCase(System.getenv(ENV_LLM_CLAIMS_ENABLED));
        // When LLM is enabled we use only the real provider (no stubs or test doubles).
        LlmClaimGenerator llmClaimGenerator = (llmClaimsEnabled && provider != null)
            ? new LlmClaimGeneratorImpl(provider)
            : null;
        explanationService = new ExplanationServiceV1(
            new DeterministicExplanationGeneratorV1(),
            narrator,
            new StructuredExplanationBuilder(),
            llmClaimGenerator,
            llmClaimsEnabled
        );
        parser = new PreferenceParserV1();
        DeterministicGoalInterpreter deterministicGoalInterpreter = new DeterministicGoalInterpreter(parser);
        boolean goalLlmEnabled = "true".equalsIgnoreCase(System.getenv(ENV_GOAL_LLM_ENABLED));
        goalInterpreter = (goalLlmEnabled && provider != null)
            ? new LlmGoalInterpreter(provider, deterministicGoalInterpreter)
            : deterministicGoalInterpreter;
    }

    /** Uses real LLM when OPENAI_API_KEY is set; otherwise NoOpNarrator. No stubs in production. */
    private static Narrator createNarrator() {
        OpenAiLlmProvider provider = OpenAiLlmProvider.fromEnvOrNull();
        if (provider != null) {
            return new LlmNarrator(provider);
        }
        return new NoOpNarrator();
    }

    public V1ApiHandler(Catalog catalog, Optimizer optimizer, ExplanationServiceV1 explanationService, PreferenceParserV1 parser) {
        this(catalog, optimizer, explanationService, parser, new DeterministicGoalInterpreter(parser));
    }

    public V1ApiHandler(Catalog catalog, Optimizer optimizer, ExplanationServiceV1 explanationService, PreferenceParserV1 parser, GoalInterpreter goalInterpreter) {
        this.catalog = catalog;
        this.defaultOptimizer = optimizer;
        this.registry = new OptimizerRegistry();
        this.explanationService = explanationService;
        this.parser = parser;
        this.goalInterpreter = goalInterpreter;
    }

    /** Returns the set of available optimizer IDs for the /v1/config/optimizers endpoint. */
    public Set<String> availableOptimizerIds() {
        return registry.availableIds();
    }

    public record OptimizeResult(int status, byte[] body) {}

    public OptimizeResult handleOptimize(String body, String requestId) {
        if (requestId == null || requestId.isBlank()) requestId = UUID.randomUUID().toString();

        try {
            var dto = OM.readValue(body, OptimizeRequest.class);
            var errors = validateOptimizeRequest(dto);
            if (!errors.isEmpty()) {
                return new OptimizeResult(400, jsonBytes(new ApiErrorResponse(requestId, "VALIDATION_ERROR",
                    "Validation failed", errors)));
            }

            String catalogVersion = dto.catalogVersion() != null && !dto.catalogVersion().isBlank() ? dto.catalogVersion() : "v1";
            GoalInterpretation goalInterpretation = null;
            if (dto.goalPrompt() != null && !dto.goalPrompt().isBlank()) {
                var explicit = new GoalInterpreter.ExplicitGoalInput(
                    dto.goal() != null ? dto.goal().preferredCurrencies() : null,
                    dto.goal() != null ? dto.goal().cppOverrides() : null
                );
                goalInterpretation = goalInterpreter.interpret(dto.goalPrompt(), explicit);
            }
            OptimizationRequest request = OptimizationMapperV1.toRequest(dto, parser, goalInterpretation);
            Optimizer selectedOptimizer = resolveOptimizer(dto.optimizerId());
            OptimizationResult result = selectedOptimizer.optimize(request, catalog);

            var userGoal = request.getUserGoal();
            NarrativeExplanation explanation = explanationService.explain(
                result, catalogVersion, userGoal.getGoalType(),
                userGoal.getPrimaryCurrency().map(Enum::name).orElse(null));

            String resolvedOptimizerId = selectedOptimizer.id();
            OptimizeResponse.GoalInterpretationDto goalInterpretationDto = OptimizationMapperV1.toGoalInterpretationDto(goalInterpretation);
            List<OptimizeResponse.AllocationEntryDto> allocationDtos = buildAllocationWithEarn(request, result);
            OptimizeResponse response = OptimizationMapperV1.toResponse(
                result, explanation, catalog, requestId, catalogVersion, resolvedOptimizerId, goalInterpretationDto, allocationDtos);

            return new OptimizeResult(200, OM.writeValueAsBytes(response));
        } catch (IllegalArgumentException e) {
            try {
                return new OptimizeResult(400, OM.writeValueAsBytes(new ApiErrorResponse(requestId, "BAD_REQUEST", e.getMessage(), List.of())));
            } catch (Exception ex) { throw new RuntimeException(ex); }
        } catch (Exception e) {
            System.err.println("[V1ApiHandler] INTERNAL_ERROR requestId=" + requestId + " " + e.getClass().getSimpleName() + ": " + e.getMessage());
            e.printStackTrace();
            try {
                return new OptimizeResult(500, OM.writeValueAsBytes(new ApiErrorResponse(requestId, "INTERNAL_ERROR", "Internal error", List.of())));
            } catch (Exception ex) { throw new RuntimeException(ex); }
        }
    }

    public byte[] handleCatalogCards(String catalogVersionParam) {
        String cv = catalogVersionParam != null ? catalogVersionParam : "1.0";
        var cards = catalog.cards();
        var list = new ArrayList<CardsResponse.CardMetadataDto>();
        for (var c : cards) {
            String currency = c.rules().isEmpty() ? "USD_CASH" : c.rules().get(0).currency().getType().name();
            list.add(new CardsResponse.CardMetadataDto(
                c.id(), c.issuer(), c.displayName(), "PERSONAL",
                c.annualFee().getAmount(), currency));
        }
        try {
            return OM.writeValueAsBytes(new CardsResponse(cv, list));
        } catch (Exception e) { throw new RuntimeException(e); }
    }

    public byte[] handleConfigGoals() {
        var table = DefaultCppTable.fallbackDefaults();
        var supportedGoals = List.of(
            new GoalsConfigResponse.SupportedGoalDto("CASHBACK", false, List.of("USD_CASH")),
            new GoalsConfigResponse.SupportedGoalDto("FLEX_POINTS", false, List.of("BANK_UR", "BANK_MR", "BANK_TY", "BANK_C1")),
            new GoalsConfigResponse.SupportedGoalDto("PROGRAM_POINTS", true, List.of("AVIOS"))
        );
        Map<String, BigDecimal> defaultCpp = new LinkedHashMap<>();
        for (RewardCurrencyType t : RewardCurrencyType.values()) {
            BigDecimal v = table.getUsdPerPoint(t);
            if (v.compareTo(BigDecimal.ZERO) > 0) {
                defaultCpp.put(t.name(), v);
            }
        }
        try {
            return OM.writeValueAsBytes(new GoalsConfigResponse(supportedGoals, defaultCpp));
        } catch (Exception e) { throw new RuntimeException(e); }
    }

    private static final List<String> PERIODS = List.of("ANNUAL", "MONTHLY");
    private static final double MAX_ANNUAL_FEE_USD = 5000.0;

    private List<ApiErrorResponse.DetailDto> validateOptimizeRequest(OptimizeRequest dto) {
        var errors = new ArrayList<ApiErrorResponse.DetailDto>();
        if (dto.period() == null || dto.period().isBlank()) {
            errors.add(new ApiErrorResponse.DetailDto("period", "required"));
        } else if (!PERIODS.contains(dto.period().toUpperCase())) {
            errors.add(new ApiErrorResponse.DetailDto("period", "must be ANNUAL or MONTHLY"));
        }
        if (dto.spendByCategoryUsd() == null) {
            errors.add(new ApiErrorResponse.DetailDto("spendByCategoryUsd", "required"));
        } else {
            for (var e : dto.spendByCategoryUsd().entrySet()) {
                if (!CATEGORIES.contains(e.getKey())) {
                    errors.add(new ApiErrorResponse.DetailDto("spendByCategoryUsd." + e.getKey(), "unknown category"));
                } else if (e.getValue() < 0) {
                    errors.add(new ApiErrorResponse.DetailDto("spendByCategoryUsd." + e.getKey(), "must be >= 0"));
                }
            }
        }
        if (dto.goal() == null) {
            errors.add(new ApiErrorResponse.DetailDto("goal", "required"));
            return errors;
        }
        if (dto.goal().goalType() == null || dto.goal().goalType().isBlank()) {
            errors.add(new ApiErrorResponse.DetailDto("goal.goalType", "required"));
        }
        if ("PROGRAM_POINTS".equalsIgnoreCase(dto.goal().goalType())
            && (dto.goal().primaryCurrency() == null || dto.goal().primaryCurrency().isBlank())) {
            errors.add(new ApiErrorResponse.DetailDto("goal.primaryCurrency", "required for PROGRAM_POINTS"));
        }
        if (dto.constraints() == null) {
            errors.add(new ApiErrorResponse.DetailDto("constraints", "required"));
        } else {
            int mc = dto.constraints().maxCards();
            if (mc < 1 || mc > 3) {
                errors.add(new ApiErrorResponse.DetailDto("constraints.maxCards", "must be 1..3"));
            }
            double fee = dto.constraints().maxAnnualFeeUsd();
            if (fee < 0) {
                errors.add(new ApiErrorResponse.DetailDto("constraints.maxAnnualFeeUsd", "must be >= 0"));
            } else if (fee > MAX_ANNUAL_FEE_USD) {
                errors.add(new ApiErrorResponse.DetailDto("constraints.maxAnnualFeeUsd", "must be <= " + (int) MAX_ANNUAL_FEE_USD));
            }
        }
        return errors;
    }

    private List<OptimizeResponse.AllocationEntryDto> buildAllocationWithEarn(
        OptimizationRequest request,
        OptimizationResult result
    ) {
        var enricher = new AllocationDisplayEnricher();
        var enriched = enricher.enrich(
            request.getSpendProfile(),
            result.getAllocation(),
            catalog,
            request.getUserGoal()
        );
        return enriched.stream()
            .map(a -> new OptimizeResponse.AllocationEntryDto(
                a.category(),
                a.cardId(),
                a.earnRatePercent(),
                a.earnValueUsd().doubleValue()
            ))
            .collect(Collectors.toList());
    }

    private Optimizer resolveOptimizer(String optimizerId) {
        if (optimizerId == null || optimizerId.isBlank()) {
            return defaultOptimizer;
        }
        return registry.getOptional(optimizerId)
            .orElseThrow(() -> new IllegalArgumentException(
                "Unknown optimizerId: " + optimizerId + ". Available: " + registry.availableIds()));
    }

    private byte[] jsonBytes(Object obj) throws Exception {
        return OM.writeValueAsBytes(obj);
    }
}
