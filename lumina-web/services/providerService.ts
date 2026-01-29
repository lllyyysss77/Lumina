import { api } from '../utils/request';
import { Provider, ProviderType } from '../types';

interface ProviderDTO {
  id: number;
  name: string;
  type: number;
  isEnabled: boolean;
  baseUrl: string;
  modelName: string;
  autoSync: boolean;
  apiKey?: string;
  createdAt?: string;
  updatedAt?: string;
}

export interface ProviderPageResponse {
  records: Provider[];
  total: number;
  size: number;
  current: number;
  pages: number;
}

export const providerService = {
  // Fetch simple list (backward compatibility for Groups)
  async getList(current = 1, size = 100): Promise<Provider[]> {
    const data = await this.getPage(current, size);
    return data.records;
  },

  // Fetch paginated list with metadata
  async getPage(current = 1, size = 10): Promise<ProviderPageResponse> {
    const response = await api.get<any>('/providers/page', { params: { current, size } });
    
    if (response.code === 200 && response.data) {
       const records = (response.data.records || []).map((item: any) => ({
        id: String(item.id),
        name: item.name,
        type: item.type as ProviderType,
        baseUrl: item.baseUrl,
        apiKey: item.apiKey || '', 
        models: item.modelName ? item.modelName.split(',') : [],
        latency: 0, 
        status: item.isEnabled ? 'active' : 'inactive',
        autoSync: item.autoSync
      }));

      return {
        records,
        total: response.data.total || 0,
        size: response.data.size || size,
        current: response.data.current || current,
        pages: response.data.pages || 0
      };
    }

    return {
      records: [],
      total: 0,
      size,
      current,
      pages: 0
    };
  },

  // Create a new provider
  async create(provider: Partial<Provider>): Promise<any> {
    const data = {
      name: provider.name,
      type: provider.type,
      isEnabled: provider.status === 'active' ? 1 : 0,
      baseUrl: provider.baseUrl,
      modelName: provider.models?.join(','),
      autoSync: provider.autoSync ? 1 : 0,
      apiKey: provider.apiKey // Send as string
    };
    return api.post('/providers', data);
  },

  // Update an existing provider
  async update(id: string, provider: Partial<Provider>): Promise<any> {
    const data = {
      name: provider.name,
      type: provider.type,
      isEnabled: provider.status === 'active' ? 1 : 0,
      baseUrl: provider.baseUrl,
      modelName: provider.models?.join(','),
      autoSync: provider.autoSync ? 1 : 0,
      apiKey: provider.apiKey // Send as string
    };
    return api.put(`/providers/${id}`, data);
  },

  // Delete a provider
  async delete(id: string): Promise<any> {
    return api.delete(`/providers/${id}`);
  },

  // Sync models from provider
  async syncModels(baseUrl: string, apiKey: string): Promise<string[]> {
    const response = await api.post<any>('/providers/models', { baseUrl, apiKey });
    if (response.code === 200 && Array.isArray(response.data)) {
      return response.data;
    }
    throw new Error(response.message || 'Failed to sync models');
  }
};