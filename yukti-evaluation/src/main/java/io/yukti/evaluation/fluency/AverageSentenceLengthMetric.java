package io.yukti.evaluation.fluency;

import java.util.List;

/** Average words per sentence. */
public final class AverageSentenceLengthMetric implements FluencyMetric {

    @Override public String id() { return "avg_sentence_length"; }

    @Override public FluencyScore score(String text) {
        List<String> sentences = TextStats.sentences(text);
        List<String> words = TextStats.words(text);
        if (sentences.isEmpty()) return new FluencyScore(id(), 0.0);
        return new FluencyScore(id(), (double) words.size() / sentences.size());
    }
}
