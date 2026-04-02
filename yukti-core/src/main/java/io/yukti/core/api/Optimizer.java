package io.yukti.core.api;

import io.yukti.core.domain.OptimizationRequest;
import io.yukti.core.domain.OptimizationResult;

/**
 * Portfolio optimizer. Extensible: greedy, ILP, etc.
 */
public interface Optimizer {
    String id();
    OptimizationResult optimize(OptimizationRequest request, Catalog catalog);
}
