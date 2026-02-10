import { ProviderType } from '../types';

export const getProviderLabel = (type: ProviderType): string => {
    switch (type) {
        case ProviderType.OPENAI_CHAT: return 'OpenAI Chat';
        case ProviderType.OPENAI_RESPONSE: return 'OpenAI Response';
        case ProviderType.ANTHROPIC: return 'Anthropic';
        case ProviderType.GEMINI: return 'Gemini';
        case ProviderType.NEW_API: return 'New API';
        default: return 'Unknown';
    }
};
