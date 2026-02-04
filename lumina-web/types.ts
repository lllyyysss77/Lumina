
export enum LoadBalanceMode {
  ROUND_ROBIN = 'Round Robin',
  RANDOM = 'Random',
  FAILOVER = 'Failover',
  WEIGHTED = 'Weighted',
  SAPR = 'SAPR',
}

export enum ProviderType {
  OPENAI_CHAT = 0,
  OPENAI_RESPONSE = 1,
  ANTHROPIC = 2,
  GEMINI = 3,
  NEW_API = 4,
}

export interface Provider {
  id: string;
  name: string;
  type: ProviderType;
  baseUrl: string;
  apiKey: string; // Single API key
  models: string[];
  latency: number; // in ms
  status: 'active' | 'inactive';
  autoSync?: boolean;
}

export interface GroupTarget {
  providerId: string;
  model: string;
}

export interface Group {
  id: string;
  name: string;
  mode: LoadBalanceMode;
  targets: GroupTarget[]; // List of (Provider + Model) targets
  firstTokenTimeout: number;
}

export interface LogEntry {
  id: string;
  timestamp: string;
  method: string;
  path: string;
  status: string; // Changed from number to string to support "SUCCESS" | "FAIL"
  latency: number;
  model: string;
  tokens: number;
  cost: number;
  providerName: string;
}

export interface ModelPrice {
  modelName: string;
  provider: string;
  inputPrice: number;
  outputPrice: number;
  cacheReadPrice?: number | null;
  cacheWritePrice?: number | null;
  contextLimit: number;
  outputLimit: number;
  isReasoning: boolean;
  isToolCall: boolean;
  inputType: string;
  lastUpdatedAt: string;
}

export interface AccessToken {
  id: string;
  name: string;
  token?: string; // Only present on creation response
  maskedToken: string;
  lastUsedAt?: string;
  createdAt: string;
  status: 'active' | 'revoked';
}

export interface DashboardOverview {
    totalRequests: number;
    requestGrowthRate: number;
    totalCost: number;
    costGrowthRate: number;
    avgLatency: number;
    latencyChange: number;
    successRate: number;
    successRateChange: number;
}

export type ViewState = 'dashboard' | 'providers' | 'groups' | 'pricing' | 'logs' | 'settings';

// Auth Types

export interface User {
  username: string;
  token: string;
  expiresIn: number;
}

export interface LoginResponse {
  code: number;
  message: string;
  data: {
    token: string;
    type: string;
    expiresIn: number;
    username: string;
  };
}

// Circuit Breaker Types

export type CircuitState = 'CLOSED' | 'OPEN' | 'HALF_OPEN';

export interface CircuitBreakerStatus {
  providerId: string;
  providerName: string;
  circuitState: CircuitState;
  circuitOpenedAt?: number;
  nextProbeAt?: number;
  openAttempt?: number;
  score: number;
  errorRate: number;
  slowRate: number;
  consecutiveFailures: number;
  totalRequests: number;
  successRequests: number;
  failureRequests: number;
  currentConcurrent: number;
  maxConcurrent: number;
  bulkheadRejectedCount: number;
  probeRemaining?: number;
  halfOpenSuccessCount?: number;
  halfOpenFailureCount?: number;
  manuallyControlled: boolean;
  manualControlReason?: string | null;
}

export interface CircuitBreakerControlRequest {
  providerId: string;
  targetState: CircuitState;
  reason?: string;
  durationMs?: number;
}