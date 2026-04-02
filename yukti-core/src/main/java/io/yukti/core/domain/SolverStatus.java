package io.yukti.core.domain;

/**
 * Status of the optimization solver after solving.
 *
 * <p>Tracks whether the result is solver-certified optimal, a feasible sub-optimal
 * solution, or a fallback to a heuristic due to solver failure.
 */
public enum SolverStatus {
    /** Solver found and certified an optimal solution. */
    OPTIMAL,

    /** Solver found a feasible but not provably optimal solution (e.g., time limit). */
    FEASIBLE,

    /** Problem is infeasible — no valid portfolio satisfies all constraints. */
    INFEASIBLE,

    /** Problem is unbounded (should not occur with budget constraints). */
    UNBOUNDED,

    /** Solver hit time limit without finding any feasible solution. */
    TIME_LIMIT,

    /** MILP solver failed; result produced by greedy fallback. */
    FALLBACK_GREEDY,

    /** Solver has not been run (e.g., empty input). */
    NOT_SOLVED
}
