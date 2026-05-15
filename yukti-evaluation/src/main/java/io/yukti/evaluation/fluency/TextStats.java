package io.yukti.evaluation.fluency;

import java.util.Arrays;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * Static text statistics shared by fluency metrics.
 * Kept package private style as a single class so each metric stays small.
 */
final class TextStats {

    private static final Pattern SENTENCE_SPLIT = Pattern.compile("(?<=[.!?])\\s+");
    private static final Pattern WORD_SPLIT = Pattern.compile("\\s+");
    private static final Pattern VOWEL_GROUP = Pattern.compile("[aeiouy]+");

    private TextStats() {}

    static List<String> sentences(String text) {
        if (text == null || text.isBlank()) return List.of();
        return Arrays.stream(SENTENCE_SPLIT.split(text.trim()))
            .filter(s -> !s.isBlank())
            .toList();
    }

    static List<String> words(String text) {
        if (text == null || text.isBlank()) return List.of();
        return Arrays.stream(WORD_SPLIT.split(text.trim()))
            .map(w -> w.replaceAll("[^A-Za-z']", ""))
            .filter(w -> !w.isEmpty())
            .toList();
    }

    static int syllables(String word) {
        if (word == null || word.isEmpty()) return 0;
        String w = word.toLowerCase().replaceAll("[^a-z]", "");
        if (w.isEmpty()) return 0;
        int count = 0;
        var matcher = VOWEL_GROUP.matcher(w);
        while (matcher.find()) count++;
        if (w.endsWith("e") && count > 1) count--;
        return Math.max(count, 1);
    }

    static int totalSyllables(List<String> words) {
        return words.stream().mapToInt(TextStats::syllables).sum();
    }

    static Stream<String> uniqueLowercase(List<String> words) {
        return words.stream().map(String::toLowerCase).distinct();
    }
}
