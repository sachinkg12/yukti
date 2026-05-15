package io.yukti.evaluation.fluency;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Registry of fluency metrics. Open Closed: add a new metric by registering it.
 */
public final class FluencyMetricRegistry {

    private final Map<String, FluencyMetric> byId;

    public FluencyMetricRegistry(List<FluencyMetric> metrics) {
        this.byId = new LinkedHashMap<>();
        for (FluencyMetric m : metrics) {
            this.byId.put(m.id(), m);
        }
    }

    public static FluencyMetricRegistry defaultRegistry() {
        return new FluencyMetricRegistry(List.of(
            new FleschKincaidMetric(),
            new FleschReadingEaseMetric(),
            new AverageSentenceLengthMetric(),
            new LexicalDiversityMetric(),
            new WordCountMetric()
        ));
    }

    public Optional<FluencyMetric> get(String id) {
        return Optional.ofNullable(byId.get(id));
    }

    public List<FluencyMetric> all() {
        return List.copyOf(byId.values());
    }
}
