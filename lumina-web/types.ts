
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
  requestModel: string;
  actualModel: string;
  tokens: number;
  cost: number;
  providerName: string;
}

export interface ModelPrice {
  id?: number;
  modelName: string;
  displayName?: string;
  provider: string;
  family?: string;
  inputPrice: number;
  outputPrice: number;
  cacheReadPrice?: number | null;
  cacheWritePrice?: number | null;
  contextLimit: number;
  outputLimit: number;
  inputLimit?: number | null;
  isReasoning: boolean;
  isToolCall: boolean;
  isAttachment?: boolean;
  isStructuredOutput?: boolean;
  isTemperature?: boolean;
  isOpenWeights?: boolean;
  inputType: string;
  outputType?: string;
  knowledgeCutoff?: string;
  releaseDate?: string;
  isActive?: boolean;
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
  expiredAt?: number | null; // Unix timestamp in seconds, null = never expires
  maxAmount?: number | null; // USD spending limit, null = unlimited
  totalRequests?: number;
  successRequests?: number;
  totalInputTokens?: number;
  totalOutputTokens?: number;
  totalCost?: number;
}

export interface DashboardOverview {
    totalRequests: number;
    requestGrowthRate: number;
    totalTokens: number;
    tokenGrowthRate: number;
    totalCost: number;
    costGrowthRate: number;
    avgLatency: number;
    latencyChange: number;
    successRate: number;
    successRateChange: number;
}

export type ViewState = 'dashboard' | 'providers' | 'groups' | 'pricing' | 'logs' | 'tokens' | 'settings';

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
  modelName?: string;
  stateSinceAt?: number;
  stateExplanation?: string;
  lastStateChangeReason?: string | null;
  lastFailureType?: string | null;
  circuitState: CircuitState;
  circuitOpenedAt?: number;
  nextProbeAt?: number;
  openAttempt?: number;
  score: number;
  latencyEmaMs?: number;
  successRateEma?: number;
  errorRate: number;
  slowRate: number;
  windowTotalCount?: number;
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
  manualControlledAt?: number;
  manualControlOperator?: string | null;
  manualControlReason?: string | null;
  effectiveConfigSource?: string;
  effectiveGroupNames?: string[];
  mixedConfig?: boolean;
  effectiveConfig?: {
    minCalls: number;
    errorRateThreshold: number;
    consecutiveFailureThreshold: number;
    slowCallThresholdMs: number;
    slowRateThreshold: number;
    permittedCallsInHalfOpen: number;
    halfOpenSuccessThreshold: number;
    halfOpenFailureThreshold: number;
    halfOpenMaxDurationMs: number;
    openBaseMs: number;
    openMaxMs: number;
    backoffMultiplier: number;
    jitterRatio: number;
    maxFailoverAttempts: number;
    maxConcurrentRequestsPerProvider: number;
    sourceLevel: string;
  };
}

export interface CircuitBreakerControlRequest {
  providerId: string;
  targetState: CircuitState;
  reason?: string;
  durationMs?: number;
}

export interface CircuitBreakerRecentEvent {
  action: string;
  providerId: string;
  providerName: string;
  modelName?: string;
  fromState: string;
  toState: string;
  reason?: string | null;
  operator?: string | null;
  timestamp: string;
}
