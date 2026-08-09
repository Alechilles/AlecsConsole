package com.alechilles.alecsconsole;

import com.hypixel.hytale.codec.ExtraInfo;
import com.hypixel.hytale.codec.util.RawJsonReader;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ConsoleEventDataTest {
    @Test
    void decodesDynamicControlValuesAndRejectsMalformedRowSequences() throws Exception {
        String json = "{\"Action\":\"SourceChanged\",\"@Value\":\"SERVER\","
                + "\"@CatalogId\":\"CURRENT_SERVER:server:test.log\",\"RowSequence\":\"not-a-number\"}";

        ConsoleEventData data = ConsoleEventData.CODEC.decodeJson(
                new RawJsonReader(json.toCharArray()), ExtraInfo.THREAD_LOCAL.get());

        assertEquals(ConsoleEventData.Action.SourceChanged, data.action());
        assertEquals("SERVER", data.value());
        assertEquals("CURRENT_SERVER:server:test.log", data.catalogId());
        assertEquals(null, data.parsedRowSequence());
    }
}
