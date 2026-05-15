package io.yukti.evaluation.judge;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JsonObjectExtractorTest {

    @Test
    void cleanSingleObject() {
        var got = JsonObjectExtractor.firstObject("{\"winner\": \"A\"}");
        assertTrue(got.isPresent());
        assertEquals("{\"winner\": \"A\"}", got.get());
    }

    @Test
    void objectAfterProse() {
        var got = JsonObjectExtractor.firstObject("Here is my answer: {\"winner\":\"A\"} thanks.");
        assertTrue(got.isPresent());
        assertEquals("{\"winner\":\"A\"}", got.get());
    }

    @Test
    void firstOfMultipleObjects() {
        // Old greedy regex would have captured both objects as one invalid string.
        var got = JsonObjectExtractor.firstObject("{\"winner\":\"A\"} let me reconsider {\"winner\":\"B\"}");
        assertTrue(got.isPresent());
        assertEquals("{\"winner\":\"A\"}", got.get(), "should return the first complete object only");
    }

    @Test
    void nestedObjectHandledCorrectly() {
        var input = "{\"winner\":\"A\",\"meta\":{\"nested\":true},\"confidence\":0.9}";
        var got = JsonObjectExtractor.firstObject(input);
        assertTrue(got.isPresent());
        assertEquals(input, got.get());
    }

    @Test
    void bracesInsideStringIgnored() {
        // The } in the rationale string should not close the JSON
        var input = "{\"winner\":\"A\",\"rationale\":\"Note: {} is empty\"}";
        var got = JsonObjectExtractor.firstObject(input);
        assertTrue(got.isPresent());
        assertEquals(input, got.get());
    }

    @Test
    void escapedQuotesInString() {
        var input = "{\"winner\":\"A\",\"rationale\":\"He said \\\"hi}\\\".\"}";
        var got = JsonObjectExtractor.firstObject(input);
        assertTrue(got.isPresent());
        assertEquals(input, got.get());
    }

    @Test
    void markdownCodeFence() {
        var got = JsonObjectExtractor.firstObject("```json\n{\"winner\":\"A\"}\n```");
        assertTrue(got.isPresent());
        assertEquals("{\"winner\":\"A\"}", got.get());
    }

    @Test
    void noObjectReturnsEmpty() {
        assertFalse(JsonObjectExtractor.firstObject("no braces here").isPresent());
        assertFalse(JsonObjectExtractor.firstObject("").isPresent());
        assertFalse(JsonObjectExtractor.firstObject(null).isPresent());
    }

    @Test
    void unclosedObjectReturnsEmpty() {
        assertFalse(JsonObjectExtractor.firstObject("{\"winner\":\"A\"").isPresent());
    }
}
