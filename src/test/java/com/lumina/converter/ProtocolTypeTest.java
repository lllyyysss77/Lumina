package com.lumina.converter;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ProtocolTypeTest {

    @Test
    void mapsOpenAiImagesGenerationsRequestType() {
        ProtocolType type = ProtocolType.fromRequestType("openai_images_generations");

        assertEquals(ProtocolType.OPENAI_IMAGES, type);
        assertEquals(4, type.getCode());
        assertEquals("openai_images_generations", type.toRequestType());
    }
}
