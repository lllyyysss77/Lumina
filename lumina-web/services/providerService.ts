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
  // Fetch paginated list of providers
  async getList(): Promise<Provider[]> {
    const response = await api.get<any>('/providers');
    
    if (response.code === 200 && response.data && Array.isArray(response.data)) {
      return response.data.map((item: any) => ({
        id: String(item.id),
        name: item.name,
        type: item.type as ProviderType,
        baseUrl: item.baseUrl,
        // Map directly to string, default to empty
        apiKey: item.apiKey || '', 
        models: item.modelName ? item.modelName.split(',') : [],
        latency: 0, // Latency is not provided in the basic CRUD API
        status: item.isEnabled ? 'active' : 'inactive',
        autoSync: item.autoSync
      }));
    }
    return [];
  },

  // Fetch paginated list of providers
  async getPage(current = 1, size = 6): Promise<ProviderPageResponse> {
    const response = await api.get<any>('/providers/page', { params: { current, size } });

    if (response.code === 200 && response.data && Array.isArray(response.data.records)) {
      const records = response.data.records.map((item: any) => ({
        id: String(item.id),
        name: item.name,
        type: item.type as ProviderType,
        baseUrl: item.baseUrl,
        // Map directly to string, default to empty
        apiKey: item.apiKey || '',
        models: item.modelName ? item.modelName.split(',') : [],
        latency: 0, // Latency is not provided in the basic CRUD API
        status: item.isEnabled ? 'active' : 'inactive',
        autoSync: item.autoSync
      }));

      return {
        records,
        total: response.data.total,
        size: response.data.size,
        current: response.data.current,
        pages: response.data.pages
      };
    }
    return {
      records: [],
      total: 0,
      size: size,
      current: 1,
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