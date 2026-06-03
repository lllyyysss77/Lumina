package com.lumina.util;

import com.lumina.entity.LlmModel;

import java.math.BigDecimal;
import java.math.RoundingMode;

public final class CostCalculator {

    private CostCalculator() {
    }

    public static BigDecimal calculate(LlmModel model, String requestType, Integer inputTokens, Integer outputTokens,
                                       Integer cacheReadTokens, Integer cacheCreationTokens) {
        if (model == null) {
            return BigDecimal.ZERO.setScale(4, RoundingMode.HALF_UP);
        }

        int input = positiveTokens(inputTokens);
        int output = positiveTokens(outputTokens);
        int cacheRead = positiveTokens(cacheReadTokens);
        int cacheCreation = positiveTokens(cacheCreationTokens);
        int regularInput = regularInputTokens(requestType, input, cacheRead, cacheCreation);

        BigDecimal inputPrice = priceOrZero(model.getInputPrice());
        BigDecimal outputPrice = priceOrZero(model.getOutputPrice());
        BigDecimal cacheReadPrice = model.getCacheReadPrice() != null ? model.getCacheReadPrice() : inputPrice;
        BigDecimal cacheWritePrice = model.getCacheWritePrice() != null ? model.getCacheWritePrice() : inputPrice;

        return tokenCost(inputPrice, regularInput)
                .add(tokenCost(outputPrice, output))
                .add(tokenCost(cacheReadPrice, cacheRead))
                .add(tokenCost(cacheWritePrice, cacheCreation))
                .setScale(4, RoundingMode.HALF_UP);
    }

    private static int regularInputTokens(String requestType, int inputTokens, int cacheReadTokens, int cacheCreationTokens) {
        if (!cacheTokensIncludedInInput(requestType)) {
            return inputTokens;
        }
        return Math.max(0, inputTokens - cacheReadTokens - cacheCreationTokens);
    }

    private static boolean cacheTokensIncludedInInput(String requestType) {
        return "openai_chat_completions".equals(requestType)
                || "openai_responses".equals(requestType)
                || "gemini_models".equals(requestType);
    }

    private static int positiveTokens(Integer tokens) {
        return tokens != null && tokens > 0 ? tokens : 0;
    }

    private static BigDecimal priceOrZero(BigDecimal price) {
        return price != null ? price : BigDecimal.ZERO;
    }

    private static BigDecimal tokenCost(BigDecimal pricePerMillionTokens, int tokens) {
        if (tokens <= 0 || pricePerMillionTokens.signum() == 0) {
            return BigDecimal.ZERO;
        }
        return pricePerMillionTokens
                .multiply(BigDecimal.valueOf(tokens))
                .divide(BigDecimal.valueOf(1_000_000), 6, RoundingMode.HALF_UP);
    }
}
