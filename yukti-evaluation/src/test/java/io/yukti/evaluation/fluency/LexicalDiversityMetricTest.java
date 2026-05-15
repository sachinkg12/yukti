package io.yukti.evaluation.fluency;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class LexicalDiversityMetricTest {

    @Test
    void allUniqueGivesOne() {
        var m = new LexicalDiversityMetric();
        assertEquals(1.0, m.score("alpha beta gamma delta").value(), 1e-9);
    }

    @Test
    void allSameGivesInverseLength() {
        var m = new LexicalDiversityMetric();
        assertEquals(0.25, m.score("alpha alpha alpha alpha").value(), 1e-9);
    }

    @Test
    void caseInsensitive() {
        var m = new LexicalDiversityMetric();
        assertEquals(0.5, m.score("Alpha alpha BETA beta").value(), 1e-9);
    }
}
