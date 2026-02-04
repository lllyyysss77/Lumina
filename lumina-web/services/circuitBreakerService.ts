import { api } from '../utils/request';
import { CircuitBreakerStatus, CircuitBreakerControlRequest } from '../types';

export const circuitBreakerService = {
  // Get status list for all providers
  async getList(): Promise<CircuitBreakerStatus[]> {
    const response = await api.get<any>('/circuit-breaker/list');
    // Direct array response based on API docs
    if (Array.isArray(response)) {
      return response;
    }
    // Fallback if wrapped in data object
    if (response && Array.isArray((response as any).data)) {
        return (response as any).data;
    }
    return [];
  },

  // Get single provider status
  async getStatus(providerId: string): Promise<CircuitBreakerStatus> {
    const encodedId = encodeURIComponent(providerId);
    return api.get<CircuitBreakerStatus>(`/circuit-breaker/status/${encodedId}`);
  },

  // Manually control circuit breaker
  async control(data: CircuitBreakerControlRequest, operator: string): Promise<CircuitBreakerStatus> {
    return api.post<CircuitBreakerStatus>('/circuit-breaker/control', data, {
      headers: {
        'X-Operator': operator
      }
    });
  },

  // Release manual control
  async release(providerId: string): Promise<CircuitBreakerStatus> {
    const encodedId = encodeURIComponent(providerId);
    return api.post<CircuitBreakerStatus>(`/circuit-breaker/release/${encodedId}`);
  }
};