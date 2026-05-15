package io.yukti.evaluation.fluency;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FluencyMetricRegistryTest {

    @Test
    void defaultRegistryRegistersAllBuiltInMetrics() {
        var reg = FluencyMetricRegistry.defaultRegistry();
        assertEquals(5, reg.all().size());
        assertTrue(reg.get("flesch_kincaid_grade").isPresent());
        assertTrue(reg.get("flesch_reading_ease").isPresent());
        assertTrue(reg.get("avg_sentence_length").isPresent());
        assertTrue(reg.get("lexical_diversity").isPresent());
        assertTrue(reg.get("word_count").isPresent());
    }

    @Test
    void unknownMetricReturnsEmpty() {
        var reg = FluencyMetricRegistry.defaultRegistry();
        assertTrue(reg.get("nonexistent").isEmpty());
    }
}
