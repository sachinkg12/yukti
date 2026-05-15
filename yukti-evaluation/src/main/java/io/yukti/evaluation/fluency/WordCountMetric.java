package io.yukti.evaluation.fluency;

/** Word count. Useful for normalizing other metrics. */
public final class WordCountMetric implements FluencyMetric {

    @Override public String id() { return "word_count"; }

    @Override public FluencyScore score(String text) {
        return new FluencyScore(id(), TextStats.words(text).size());
    }
}
