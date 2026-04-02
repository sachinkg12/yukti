package io.yukti.engine.explainability;

import io.yukti.core.domain.Category;
import io.yukti.core.domain.EvidenceBlock;
import io.yukti.core.domain.ObjectiveBreakdown;
import io.yukti.core.domain.OptimizationResult;
import io.yukti.explain.core.evidence.graph.EvidenceEdge;
import io.yukti.explain.core.evidence.graph.EvidenceEdgeType;
import io.yukti.explain.core.evidence.graph.EvidenceGraph;
import io.yukti.explain.core.evidence.graph.EvidenceIdHelper;
import io.yukti.explain.core.evidence.graph.EvidenceNode;

import java.math.BigDecimal;
import java.util.*;
import java.util.regex.Pattern;

/**
 * Domain adapter: builds an EvidenceGraph from an OptimizationResult.
 * Deterministic: same result → same graph and digest.
 */
public final class EvidenceGraphBuilder {

    private static final String ROOT_ID = EvidenceGraph.rootEvidenceId();
    private static final Pattern NUMBER_IN_CONTENT = Pattern.compile("\\d+\\.?\\d*");

    /**
     * Build graph from optimization result: one node per evidence block (plus root), edges (SUPPORTS from root;
     * DEPENDS_ON from CAP_HIT to WINNER_BY_CATEGORY for same category), allowedEntities and allowedNumbers, digest.
     */
    public EvidenceGraph build(OptimizationResult result) {
        List<EvidenceNode> nodes = new ArrayList<>();
        List<EvidenceEdge> edges = new ArrayList<>();
        Set<String> entities = new HashSet<>();
        Set<String> numbers = new HashSet<>();

        // Root node (virtual)
        nodes.add(new EvidenceNode(ROOT_ID, "RESULT", "", "", ""));

        // Entities from portfolio and allocation
        result.getPortfolioIds().forEach(entities::add);
        result.getAllocation().values().forEach(entities::add);
        for (Category c : result.getAllocation().keySet()) {
            entities.add(c.name());
        }

        // Numbers from breakdown
        ObjectiveBreakdown b = result.getBreakdown();
        addBreakdownNumbers(b, numbers);

        // One node per evidence block; edges root --SUPPORTS--> node; CAP_HIT --DEPENDS_ON--> winner for same cat
        List<EvidenceBlock> blocks = result.getEvidenceBlocks();
        Map<String, String> winnerByCategory = new HashMap<>(); // category -> evidenceId of WINNER_BY_CATEGORY node
        for (EvidenceBlock eb : blocks) {
            String eid = EvidenceIdHelper.compute(eb.getType(), eb.getCardId(), eb.getCategory(), eb.getContent());
            nodes.add(new EvidenceNode(eid, eb.getType(), eb.getCardId(), eb.getCategory(), eb.getContent()));
            entities.add(eb.getCardId());
            if (eb.getCategory() != null && !eb.getCategory().isEmpty()) {
                entities.add(eb.getCategory());
            }
            // allowedNumbers: paper defines as structured fields + breakdown; EvidenceBlock has no structured numeric fields yet, so we add from content for compatibility. TODO: derive from structured evidence when available.
            addNumbersFromContent(eb.getContent(), numbers);

            edges.add(new EvidenceEdge(ROOT_ID, eid, EvidenceEdgeType.SUPPORTS));

            if ("WINNER_BY_CATEGORY".equals(eb.getType()) && eb.getCategory() != null && !eb.getCategory().isEmpty()) {
                winnerByCategory.put(eb.getCategory(), eid);
            }
        }
        for (EvidenceBlock eb : blocks) {
            if ("CAP_HIT".equals(eb.getType()) && eb.getCategory() != null && !eb.getCategory().isEmpty()) {
                String winnerId = winnerByCategory.get(eb.getCategory());
                if (winnerId != null) {
                    String capId = EvidenceIdHelper.compute(eb.getType(), eb.getCardId(), eb.getCategory(), eb.getContent());
                    edges.add(new EvidenceEdge(capId, winnerId, EvidenceEdgeType.DEPENDS_ON));
                }
            }
        }

        String digest = computeDigest(nodes, edges);
        return new EvidenceGraph(
            nodes,
            edges,
            entities,
            numbers,
            digest
        );
    }

    private static void addBreakdownNumbers(ObjectiveBreakdown b, Set<String> numbers) {
        addNumber(numbers, b.getEarnValue().getAmount());
        addNumber(numbers, b.getCreditsValue().getAmount());
        addNumber(numbers, b.getFees().getAmount());
        addNumber(numbers, b.getNet().getAmount());
    }

    /** Add both plain and stripTrailingZeros forms so claim verifier matches generator output. */
    private static void addNumber(Set<String> numbers, BigDecimal value) {
        if (value == null) return;
        numbers.add(value.toPlainString());
        numbers.add(value.stripTrailingZeros().toPlainString());
    }

    private static void addNumbersFromContent(String content, Set<String> numbers) {
        if (content == null) return;
        var matcher = NUMBER_IN_CONTENT.matcher(content);
        while (matcher.find()) {
            numbers.add(matcher.group());
        }
    }

    private static String computeDigest(List<EvidenceNode> nodes, List<EvidenceEdge> edges) {
        List<EvidenceNode> sortedNodes = new ArrayList<>(nodes);
        sortedNodes.sort(Comparator.comparing(EvidenceNode::evidenceId));
        List<EvidenceEdge> sortedEdges = new ArrayList<>(edges);
        sortedEdges.sort(Comparator
            .comparing(EvidenceEdge::fromEvidenceId)
            .thenComparing(EvidenceEdge::toEvidenceId)
            .thenComparing(e -> e.edgeType().name()));
        StringBuilder sb = new StringBuilder();
        // Digest uses canonical structured fields only (paper-aligned); content is excluded.
        for (EvidenceNode n : sortedNodes) {
            sb.append("N\t").append(n.evidenceId()).append("\t").append(n.type()).append("\t")
                .append(n.cardId()).append("\t").append(n.category()).append("\n");
        }
        for (EvidenceEdge e : sortedEdges) {
            sb.append("E\t").append(e.fromEvidenceId()).append("\t").append(e.toEvidenceId()).append("\t")
                .append(e.edgeType().name()).append("\n");
        }
        return EvidenceIdHelper.sha256Hex(sb.toString());
    }
}
