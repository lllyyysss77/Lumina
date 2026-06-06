package com.lumina.converter;

public enum ProtocolType {
    OPENAI_CHAT(0),
    OPENAI_RESPONSES(1),
    ANTHROPIC(2),
    GEMINI(3),
    OPENAI_IMAGES(4);

    private final int code;

    ProtocolType(int code) {
        this.code = code;
    }

    public int getCode() {
        return code;
    }

    public static ProtocolType fromCode(Integer code) {
        if (code == null) return OPENAI_CHAT;
        for (ProtocolType type : values()) {
            if (type.code == code) return type;
        }
        return OPENAI_CHAT;
    }

    public static ProtocolType fromRequestType(String requestType) {
        return switch (requestType) {
            case "openai_chat_completions" -> OPENAI_CHAT;
            case "openai_responses" -> OPENAI_RESPONSES;
            case "openai_images_generations" -> OPENAI_IMAGES;
            case "anthropic_messages" -> ANTHROPIC;
            case "gemini_models" -> GEMINI;
            default -> OPENAI_CHAT;
        };
    }

    public String toRequestType() {
        return switch (this) {
            case OPENAI_CHAT -> "openai_chat_completions";
            case OPENAI_RESPONSES -> "openai_responses";
            case OPENAI_IMAGES -> "openai_images_generations";
            case ANTHROPIC -> "anthropic_messages";
            case GEMINI -> "gemini_models";
            default -> "openai_chat_completions";
        };
    }
}
