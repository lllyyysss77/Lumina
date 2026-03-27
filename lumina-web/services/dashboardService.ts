import { api } from '../utils/request';
import { CircuitBreakerStatus, DashboardOverview } from '../types';

export interface TrafficDataPoint {
  hour: number;
  requestCount: number;
  timestamp: number;
}

export interface ModelTokenUsageData {
  modelName: string;
  inputTokens: number;
  outputTokens: number;
  totalTokens: number;
  requestCount: number;
  percentage: number;
}

export interface ProviderStats {
  rank: number;
  providerId: number;
  providerName: string;
  callCount: number;
  estimatedCost: number;
  avgLatency: number;
  successRate: number;
}

export interface ObservabilityOverview {
  providersTracked: number;
  openCircuits: number;
  halfOpenCircuits: number;
  bulkheadRejections: number;
  logDroppedTotal: number;
  logQueueSize: number;
  cacheHitRate: number;
  failoverSwitches: number;
  failoverTerminations: number;
  failoverDepthAvg: number;
}

export interface ObservabilitySelection {
  saprSelections: number;
  roundRobinSelections: number;
  fallbackToRoundRobin: number;
  skippedExcluded: number;
  skippedCircuitOpen: number;
  skippedCircuitHalfOpen: number;
  bulkheadRejectedNonStream: number;
  bulkheadRejectedStream: number;
  failoverAttemptsNonStream: number;
  failoverAttemptsStream: number;
}

export interface ObservabilityLogPipeline {
  queueSize: number;
  droppedTotal: number;
  avgBatchSize: number;
  avgFlushMs: number;
}

export interface ObservabilityCacheMetric {
  cache: string;
  hits: number;
  misses: number;
  expired: number;
  loads: number;
  hitRate: number;
  avgLoadMs: number;
}

export interface DashboardObservability {
  overview: ObservabilityOverview;
  selection: ObservabilitySelection;
  logPipeline: ObservabilityLogPipeline;
  caches: ObservabilityCacheMetric[];
  providers: CircuitBreakerStatus[];
}

export const dashboardService = {
  async getOverview(): Promise<DashboardOverview> {
    const response = await api.get<any>('/dashboard/overview');
    if (response.code === 200 && response.data) {
      return response.data;
    }
    // Return safe default if request fails
    return {
        totalRequests: 0,
        requestGrowthRate: 0,
        totalTokens: 0,
        tokenGrowthRate: 0,
        totalCost: 0,
        costGrowthRate: 0,
        avgLatency: 0,
        latencyChange: 0,
        successRate: 0,
        successRateChange: 0
    };
  },

  async getTraffic(): Promise<TrafficDataPoint[]> {
    const response = await api.get<any>('/dashboard/traffic');
    if (response.code === 200 && Array.isArray(response.data)) {
      return response.data;
    }
    return [];
  },

  async getModelTokenUsage(): Promise<ModelTokenUsageData[]> {
    const response = await api.get<any>('/dashboard/model-token-usage');
    if (response.code === 200 && Array.isArray(response.data)) {
      return response.data;
    }
    return [];
  },

  async getProviderStats(): Promise<ProviderStats[]> {
    const response = await api.get<any>('/dashboard/provider-stats');
    if (response.code === 200 && Array.isArray(response.data)) {
      return response.data;
    }
    return [];
  },

  async getObservability(): Promise<DashboardObservability> {
    const response = await api.get<any>('/dashboard/observability');
    if (response.code === 200 && response.data) {
      return response.data as DashboardObservability;
    }
    return {
      overview: {
        providersTracked: 0,
        openCircuits: 0,
        halfOpenCircuits: 0,
        bulkheadRejections: 0,
        logDroppedTotal: 0,
        logQueueSize: 0,
        cacheHitRate: 0,
        failoverSwitches: 0,
        failoverTerminations: 0,
        failoverDepthAvg: 0,
      },
      selection: {
        saprSelections: 0,
        roundRobinSelections: 0,
        fallbackToRoundRobin: 0,
        skippedExcluded: 0,
        skippedCircuitOpen: 0,
        skippedCircuitHalfOpen: 0,
        bulkheadRejectedNonStream: 0,
        bulkheadRejectedStream: 0,
        failoverAttemptsNonStream: 0,
        failoverAttemptsStream: 0,
      },
      logPipeline: {
        queueSize: 0,
        droppedTotal: 0,
        avgBatchSize: 0,
        avgFlushMs: 0,
      },
      caches: [],
      providers: [],
    };
  }
};
