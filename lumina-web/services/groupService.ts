import { api } from '../utils/request';
import { Group, LoadBalanceMode, GroupTarget } from '../types';

// Backend DTO interfaces
interface GroupItemDTO {
  id?: number;
  groupId?: number;
  providerId: number;
  providerName?: string;
  modelName: string;
  priority?: number;
  weight?: number;
}

interface GroupDTO {
  id: number;
  name: string;
  balanceMode: number;
  groupItems: GroupItemDTO[];
  createdAt?: string;
  updatedAt?: string;
  handler?: any;
  firstTokenTimeout?: number;
}

// Mapping Helpers
const MODE_MAP_TO_FRONTEND: Record<number, LoadBalanceMode> = {
  1: LoadBalanceMode.ROUND_ROBIN,
  2: LoadBalanceMode.RANDOM,
  3: LoadBalanceMode.WEIGHTED,
  4: LoadBalanceMode.FAILOVER,
  5: LoadBalanceMode.SAPR,
};

const MODE_MAP_TO_BACKEND: Record<string, number> = {
  [LoadBalanceMode.ROUND_ROBIN]: 1,
  [LoadBalanceMode.RANDOM]: 2,
  [LoadBalanceMode.WEIGHTED]: 3,
  [LoadBalanceMode.FAILOVER]: 4,
  [LoadBalanceMode.SAPR]: 5,
};

export interface GroupPageResponse {
  records: Group[];
  total: number;
  size: number;
  current: number;
  pages: number;
}

export const groupService = {
  // Fetch paginated list of groups with metadata
  async getPage(current = 1, size = 10): Promise<GroupPageResponse> {
    const response = await api.get<any>('/groups/page', { params: { current, size } });
    
    if (response.code === 200 && response.data) {
      const records = (response.data.records || []).map((item: GroupDTO) => ({
        id: String(item.id),
        name: item.name,
        mode: MODE_MAP_TO_FRONTEND[item.balanceMode] || LoadBalanceMode.ROUND_ROBIN,
        targets: (item.groupItems || []).map((gi: GroupItemDTO) => ({
          providerId: String(gi.providerId),
          model: gi.modelName,
        })),
        firstTokenTimeout: item.firstTokenTimeout || 3000, 
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

  // Backward compatibility: Fetch simple list
  async getList(current = 1, size = 100): Promise<Group[]> {
    const data = await this.getPage(current, size);
    return data.records;
  },

  // Create a new group
  async create(group: Partial<Group>): Promise<any> {
    const data = {
      name: group.name,
      // Default to SAPR (5)
      balanceMode: MODE_MAP_TO_BACKEND[group.mode || LoadBalanceMode.SAPR] || 5,
      firstTokenTimeout: group.firstTokenTimeout,
      groupItems: group.targets?.map(t => ({
        providerId: parseInt(t.providerId, 10),
        modelName: t.model,
        weight: 1, // Default weight
        priority: 0 // Default priority
      })) || []
    };
    return api.post('/groups', data);
  },

  // Update an existing group
  async update(id: string, group: Partial<Group>): Promise<any> {
    const data = {
      name: group.name,
      balanceMode: MODE_MAP_TO_BACKEND[group.mode || LoadBalanceMode.SAPR] || 5,
      firstTokenTimeout: group.firstTokenTimeout,
      groupItems: group.targets?.map(t => ({
        providerId: parseInt(t.providerId, 10),
        modelName: t.model,
        weight: 1,
        priority: 0
      })) || []
    };
    return api.put(`/groups/${id}`, data);
  },

  // Delete a group
  async delete(id: string): Promise<any> {
    return api.delete(`/groups/${id}`);
  }
};