package com.lumina.converter;

public enum ProtocolType {
    OPENAI_CHAT(0),
    OPENAI_RESPONSES(1),
    ANTHROPIC(2),
    GEMINI(3),
    NEW_API(4);

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
            case "anthropic_messages" -> ANTHROPIC;
            case "gemini_models" -> GEMINI;
            default -> OPENAI_CHAT;
        };
    }

    public String toRequestType() {
        return switch (this) {
            case OPENAI_CHAT -> "openai_chat_completions";
            case OPENAI_RESPONSES -> "openai_responses";
            case ANTHROPIC -> "anthropic_messages";
            case GEMINI -> "gemini_models";
            case NEW_API -> "openai_chat_completions";
        };
    }
}
