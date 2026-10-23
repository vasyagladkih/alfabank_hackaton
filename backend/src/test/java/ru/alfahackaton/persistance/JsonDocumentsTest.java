package ru.alfahackaton.persistance;

import org.junit.jupiter.api.Test;
import ru.alfahackaton.persistance.JsonDocuments;

import static org.junit.jupiter.api.Assertions.*;

class JsonDocumentsTest {
    @Test
    void canonicalHashIsStableAcrossObjectOrderAndDecimalFormatting() {
        String first = JsonDocuments.canonicalize("{\"z\":1.50,\"a\":{\"missing\":null,\"b\":2}}");
        String second = JsonDocuments.canonicalize("{ \"a\":{\"b\":2.0,\"missing\":null}, \"z\":1.5 }");
        assertEquals("{\"a\":{\"b\":2,\"missing\":null},\"z\":1.5}", first);
        assertEquals(first, second);
        assertEquals(JsonDocuments.sha256(first), JsonDocuments.sha256(second));
    }

    @Test
    void evidenceMustBeAnObjectAndCannotHideDuplicateKeys() {
        assertThrows(IllegalArgumentException.class, () -> JsonDocuments.canonicalize("[]"));
        assertThrows(IllegalArgumentException.class, () -> JsonDocuments.canonicalize("{\"a\":1,\"a\":2}"));
    }
}
