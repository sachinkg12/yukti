package io.yukti.evaluation.fluency;

import java.util.List;

/** Flesch Kincaid grade level. Lower means easier to read. */
public final class FleschKincaidMetric implements FluencyMetric {

    @Override public String id() { return "flesch_kincaid_grade"; }

    @Override public FluencyScore score(String text) {
        List<String> sentences = TextStats.sentences(text);
        List<String> words = TextStats.words(text);
        if (sentences.isEmpty() || words.isEmpty()) {
            return new FluencyScore(id(), 0.0);
        }
        int totalSyllables = TextStats.totalSyllables(words);
        double wordsPerSentence = (double) words.size() / sentences.size();
        double syllablesPerWord = (double) totalSyllables / words.size();
        double grade = 0.39 * wordsPerSentence + 11.8 * syllablesPerWord - 15.59;
        return new FluencyScore(id(), grade);
    }
}
