import { api } from '../utils/request';
import { AccessToken } from '../types';

interface ApiKeyDTO {
  id: number;
  name: string;
  apiKey: string;
  isEnabled: boolean;
  expiredAt: number | null;
  maxAmount: number | null;
  createdAt: string;
  updatedAt: string;
}

interface ApiKeyUsageDTO {
  id: number;
  name: string;
  apiKey: string;
  isEnabled: boolean;
  expiredAt: number | null;
  maxAmount: number | null;
  totalRequests: number;
  successRequests: number;
  totalInputTokens: number;
  totalOutputTokens: number;
  totalCost: number;
}

export const tokenService = {
  // Fetch list of tokens with usage stats
  async getList(): Promise<AccessToken[]> {
    const response = await api.get<any>('/api-keys/usage');

    if (response.code === 200) {
      let items: ApiKeyUsageDTO[] = [];

      if (Array.isArray(response.data)) {
        items = response.data;
      } else if (response.data) {
        items = [response.data];
      }

      return items.map((item) => ({
        id: String(item.id),
        name: item.name,
        token: item.apiKey,
        maskedToken: item.apiKey
          ? `${item.apiKey.substring(0, 3)}...${item.apiKey.substring(item.apiKey.length - 4)}`
          : '******',
        createdAt: '',
        status: item.isEnabled ? 'active' as const : 'revoked' as const,
        expiredAt: item.expiredAt,
        maxAmount: item.maxAmount,
        totalRequests: item.totalRequests,
        successRequests: item.successRequests,
        totalInputTokens: item.totalInputTokens,
        totalOutputTokens: item.totalOutputTokens,
        totalCost: item.totalCost,
      }));
    }
    return [];
  },

  // Create a new token
  async create(name: string): Promise<AccessToken> {
    const response = await api.post<any>('/api-keys/generate', { name });

    if (response.code === 200 && response.data) {
      const item = response.data as ApiKeyDTO;
      return {
        id: String(item.id),
        name: item.name,
        token: item.apiKey, // Expose full token on creation
        maskedToken: item.apiKey
          ? `${item.apiKey.substring(0, 3)}...${item.apiKey.substring(item.apiKey.length - 4)}`
          : '******',
        createdAt: item.createdAt,
        status: item.isEnabled ? 'active' : 'revoked',
        expiredAt: item.expiredAt,
        maxAmount: item.maxAmount,
      };
    }
    throw new Error(response.message || 'Failed to create token');
  },

  // Toggle enable/disable a token
  async toggle(id: string): Promise<AccessToken> {
    const response = await api.put<any>(`/api-keys/${id}/toggle`);
    if (response.code === 200 && response.data) {
      const item = response.data as ApiKeyDTO;
      return {
        id: String(item.id),
        name: item.name,
        token: item.apiKey,
        maskedToken: item.apiKey
          ? `${item.apiKey.substring(0, 3)}...${item.apiKey.substring(item.apiKey.length - 4)}`
          : '******',
        createdAt: item.createdAt,
        status: item.isEnabled ? 'active' : 'revoked',
        expiredAt: item.expiredAt,
        maxAmount: item.maxAmount,
      };
    }
    throw new Error(response.message || 'Failed to toggle token');
  },

  // Update token spending quota. null means unlimited.
  async updateQuota(id: string, maxAmount: number | null): Promise<AccessToken> {
    const response = await api.put<any>(`/api-keys/${id}/quota`, { maxAmount });
    if (response.code === 200 && response.data) {
      const item = response.data as ApiKeyDTO;
      return {
        id: String(item.id),
        name: item.name,
        token: item.apiKey,
        maskedToken: item.apiKey
          ? `${item.apiKey.substring(0, 3)}...${item.apiKey.substring(item.apiKey.length - 4)}`
          : '******',
        createdAt: item.createdAt,
        status: item.isEnabled ? 'active' : 'revoked',
        expiredAt: item.expiredAt,
        maxAmount: item.maxAmount,
      };
    }
    throw new Error(response.message || 'Failed to update token quota');
  },

  // Delete/Revoke a token
  async delete(id: string): Promise<void> {
    const response = await api.delete<any>(`/api-keys/${id}`);
    if (response.code !== 200) {
        throw new Error(response.message || 'Failed to delete token');
    }
  }
};
