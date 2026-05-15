package io.yukti.evaluation.fluency;

import java.util.List;

/** Type token ratio. Unique words divided by total words. */
public final class LexicalDiversityMetric implements FluencyMetric {

    @Override public String id() { return "lexical_diversity"; }

    @Override public FluencyScore score(String text) {
        List<String> words = TextStats.words(text);
        if (words.isEmpty()) return new FluencyScore(id(), 0.0);
        long unique = TextStats.uniqueLowercase(words).count();
        return new FluencyScore(id(), (double) unique / words.size());
    }
}
