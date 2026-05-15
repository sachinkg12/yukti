package io.yukti.evaluation.fluency;

import java.util.List;

/** Flesch Reading Ease score. Higher means easier to read (0 to 100 typical range). */
public final class FleschReadingEaseMetric implements FluencyMetric {

    @Override public String id() { return "flesch_reading_ease"; }

    @Override public FluencyScore score(String text) {
        List<String> sentences = TextStats.sentences(text);
        List<String> words = TextStats.words(text);
        if (sentences.isEmpty() || words.isEmpty()) {
            return new FluencyScore(id(), 0.0);
        }
        int totalSyllables = TextStats.totalSyllables(words);
        double wordsPerSentence = (double) words.size() / sentences.size();
        double syllablesPerWord = (double) totalSyllables / words.size();
        double score = 206.835 - 1.015 * wordsPerSentence - 84.6 * syllablesPerWord;
        return new FluencyScore(id(), score);
    }
}
