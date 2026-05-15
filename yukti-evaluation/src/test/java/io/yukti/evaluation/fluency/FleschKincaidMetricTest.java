package io.yukti.evaluation.fluency;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FleschKincaidMetricTest {

    @Test
    void emptyTextReturnsZero() {
        var m = new FleschKincaidMetric();
        assertEquals(0.0, m.score("").value());
        assertEquals(0.0, m.score("   ").value());
    }

    @Test
    void simpleSentenceReturnsLowGrade() {
        var m = new FleschKincaidMetric();
        double grade = m.score("The cat sat on the mat.").value();
        assertTrue(grade < 5.0, "expected low grade level for simple text, got " + grade);
    }

    @Test
    void complexSentenceReturnsHigherGrade() {
        var m = new FleschKincaidMetric();
        double simple = m.score("The cat ate fish. The dog ran fast. The bird flew home.").value();
        double complex_ = m.score("The optimization framework demonstrates sophisticated combinatorial reasoning.").value();
        assertTrue(complex_ > simple, "complex should score higher than simple");
    }
}
