import React, { useEffect, useState } from 'react';
import {
  AreaChart,
  Area,
  XAxis,
  YAxis,
  CartesianGrid,
  Tooltip,
  ResponsiveContainer,
  BarChart,
  Bar,
  Cell,
} from 'recharts';
import {
  Activity,
  ArrowDownRight,
  ArrowUpRight,
  BarChart3,
  Clock,
  Coins,
  Database,
  Gauge,
  Server,
  ShieldAlert,
  Sigma,
  Zap,
} from 'lucide-react';
import { useLanguage } from './LanguageContext';
import {
  DashboardObservability,
  dashboardService,
  HealthHeatmapData,
  ObservabilityCacheMetric,
  ProviderStats,
} from '../services/dashboardService';
import { CircuitBreakerStatus, DashboardOverview } from '../types';
import { DashboardSkeleton } from './Skeletons';
import { SlideInItem } from './Animations';
import { Pagination } from './Pagination';
import { HealthHeatmap } from './HealthHeatmap';

const StatCard: React.FC<{
  title: string;
  value: string;
  trend: string;
  trendDirection: 'up' | 'down';
  trendPositive: boolean;
  icon: React.ElementType;
}> = ({ title, value, trend, trendDirection, trendPositive, icon: Icon }) => (
  <div className="bg-white dark:bg-[#1a1a1a] rounded-2xl p-6 shadow-card border border-gray-200/60 dark:border-gray-800 hover:shadow-soft transition-all duration-300 group">
    <div className="flex items-center justify-between mb-4">
      <div className="p-2.5 rounded-xl bg-gray-50 dark:bg-gray-800 text-gray-900 dark:text-gray-100 group-hover:scale-105 transition-transform duration-300">
        <Icon size={20} strokeWidth={2} />
      </div>
      <div
        className={`flex items-center px-2 py-1 rounded-lg text-xs font-semibold ${
          trendPositive
            ? 'bg-green-50 text-green-700 dark:bg-green-900/20 dark:text-green-400'
            : 'bg-red-50 text-red-700 dark:bg-red-900/20 dark:text-red-400'
        }`}
      >
        {trendDirection === 'up' ? <ArrowUpRight size={12} className="mr-1" /> : <ArrowDownRight size={12} className="mr-1" />}
        {trend}
      </div>
    </div>
    <h3 className="text-gray-500 dark:text-gray-400 text-xs font-medium uppercase tracking-wide">{title}</h3>
    <p className="text-2xl font-bold text-gray-900 dark:text-white mt-1 tracking-tight">{value}</p>
  </div>
);

const RuntimeCard: React.FC<{
  title: string;
  value: string;
  detail: string;
  icon: React.ElementType;
}> = ({ title, value, detail, icon: Icon }) => (
  <div className="bg-white dark:bg-[#1a1a1a] rounded-2xl p-5 shadow-card border border-gray-200/60 dark:border-gray-800">
    <div className="flex items-start justify-between gap-3">
      <div>
        <p className="text-[11px] font-semibold uppercase tracking-[0.18em] text-gray-400 dark:text-gray-500">{title}</p>
        <p className="mt-2 text-2xl font-bold tracking-tight text-gray-900 dark:text-white">{value}</p>
        <p className="mt-2 text-sm text-gray-500 dark:text-gray-400">{detail}</p>
      </div>
      <div className="rounded-xl border border-gray-200/70 dark:border-gray-700 bg-gray-50 dark:bg-gray-800/70 p-2.5 text-gray-600 dark:text-gray-300">
        <Icon size={18} />
      </div>
    </div>
  </div>
);

type RankingMetric = 'calls' | 'cost' | 'latency' | 'successRate';

const formatPercent = (value: number) => `${value.toFixed(1)}%`;

const formatNumber = (value: number) => value.toLocaleString();

const formatTokenCount = (value: number) => {
  if (value >= 1_000_000) {
    return `${(value / 1_000_000).toFixed(value >= 10_000_000 ? 0 : 1)}M`;
  }
  if (value >= 1_000) {
    return `${(value / 1_000).toFixed(value >= 10_000 ? 0 : 1)}K`;
  }
  return `${value}`;
};

const formatCircuitState = (status: CircuitBreakerStatus, t: (key: string) => string) => {
  switch (status.circuitState) {
    case 'OPEN':
      return {
        label: t('settings.circuitBreaker.states.OPEN'),
        className: 'bg-red-50 text-red-700 dark:bg-red-900/20 dark:text-red-300',
      };
    case 'HALF_OPEN':
      return {
        label: t('settings.circuitBreaker.states.HALF_OPEN'),
        className: 'bg-amber-50 text-amber-700 dark:bg-amber-900/20 dark:text-amber-300',
      };
    default:
      return {
        label: t('settings.circuitBreaker.states.CLOSED'),
        className: 'bg-emerald-50 text-emerald-700 dark:bg-emerald-900/20 dark:text-emerald-300',
      };
  }
};

const formatCacheLabel = (cacheName: string, t: (key: string) => string) => {
  const key = `dashboard.observability.cache.names.${cacheName}`;
  const translated = t(key);
  return translated === key ? cacheName : translated;
};

const providerSuccessRate = (provider: CircuitBreakerStatus) => {
  if (!provider.totalRequests) {
    return 0;
  }
  return (provider.successRequests / provider.totalRequests) * 100;
};

const formatTimestamp = (timestamp?: number) => {
  if (!timestamp) {
    return '-';
  }
  return new Date(timestamp).toLocaleTimeString([], { hour: '2-digit', minute: '2-digit', second: '2-digit' });
};

export const Dashboard: React.FC = () => {
  const { t } = useLanguage();
  const [metric, setMetric] = useState<RankingMetric>('calls');
  const [overview, setOverview] = useState<DashboardOverview | null>(null);
  const [trafficData, setTrafficData] = useState<{ time: string; requests: number }[]>([]);
  const [modelUsageData, setModelUsageData] = useState<{ name: string; tokens: number }[]>([]);
  const [providerRanking, setProviderRanking] = useState<ProviderStats[]>([]);
  const [observability, setObservability] = useState<DashboardObservability | null>(null);
  const [healthHeatmap, setHealthHeatmap] = useState<HealthHeatmapData | null>(null);
  const [lastRuntimeRefresh, setLastRuntimeRefresh] = useState<string>('--:--:--');
  const [isRefreshingRuntime, setIsRefreshingRuntime] = useState<boolean>(false);
  const [runtimePagination, setRuntimePagination] = useState({
    current: 1,
    size: 8,
  });
  const [runtimeFilters, setRuntimeFilters] = useState({
    provider: 'ALL',
    model: 'ALL',
    state: 'ALL',
  });
  const [isLoading, setIsLoading] = useState(true);

  useEffect(() => {
    let active = true;

    const fetchAllData = async () => {
      setIsLoading(true);
      try {
        const [overviewData, trafficRaw, modelUsageRaw, providerStatsRaw, observabilityRaw, heatmapRaw] = await Promise.all([
          dashboardService.getOverview(),
          dashboardService.getTraffic(),
          dashboardService.getModelTokenUsage(),
          dashboardService.getProviderStats(),
          dashboardService.getObservability(),
          dashboardService.getHealthHeatmap(7),
        ]);

        if (!active) {
          return;
        }

        setOverview(overviewData);
        setObservability(observabilityRaw);
        setHealthHeatmap(heatmapRaw);
        setLastRuntimeRefresh(
          new Date().toLocaleTimeString([], { hour: '2-digit', minute: '2-digit', second: '2-digit' }),
        );

        if (trafficRaw.length > 0) {
          setTrafficData(
            trafficRaw
              .sort((a, b) => a.timestamp - b.timestamp)
              .map((item) => ({
                time: `${item.hour.toString().padStart(2, '0')}:00`,
                requests: item.requestCount,
              })),
          );
        } else {
          setTrafficData([]);
        }

        if (modelUsageRaw.length > 0) {
          setModelUsageData(
            modelUsageRaw.map((item) => ({
              name: item.modelName,
              tokens: item.totalTokens,
            })),
          );
        } else {
          setModelUsageData([]);
        }

        setProviderRanking(providerStatsRaw || []);
      } catch (error) {
        console.error('Failed to load dashboard data', error);
      } finally {
        if (active) {
          setIsLoading(false);
        }
      }
    };

    const refreshRuntimeData = async () => {
      setIsRefreshingRuntime(true);
      try {
        const runtime = await dashboardService.getObservability();
        if (!active) {
          return;
        }
        setObservability(runtime);
        setLastRuntimeRefresh(
          new Date().toLocaleTimeString([], { hour: '2-digit', minute: '2-digit', second: '2-digit' }),
        );
      } catch (error) {
        console.error('Failed to refresh observability data', error);
      } finally {
        if (active) setIsRefreshingRuntime(false);
      }
    };

    fetchAllData();
    const intervalId = window.setInterval(refreshRuntimeData, 10000);

    return () => {
      active = false;
      window.clearInterval(intervalId);
    };
  }, []);

  const getSortedData = () => {
    return [...providerRanking].sort((a, b) => {
      switch (metric) {
        case 'calls':
          return b.callCount - a.callCount;
        case 'cost':
          return b.estimatedCost - a.estimatedCost;
        case 'latency':
          return a.avgLatency - b.avgLatency;
        case 'successRate':
          return b.successRate - a.successRate;
        default:
          return 0;
      }
    });
  };

  const getRankingRowKey = (provider: ProviderStats, index: number) => {
    const providerIdentity = provider.providerId ?? 'unknown';
    return `${metric}-${providerIdentity}-${provider.providerName}-${index}`;
  };

  const formatRankingValue = (item: ProviderStats) => {
    switch (metric) {
      case 'calls':
        return item.callCount.toLocaleString();
      case 'cost':
        return `$${item.estimatedCost.toFixed(4)}`;
      case 'latency':
        return `${item.avgLatency.toFixed(0)}ms`;
      case 'successRate':
        return `${item.successRate.toFixed(1)}%`;
      default:
        return '';
    }
  };

  const getProgressBarColor = (item: ProviderStats) => {
    if (metric === 'successRate') {
      if (item.successRate >= 99) return 'bg-emerald-400';
      if (item.successRate >= 95) return 'bg-amber-400';
      return 'bg-red-400';
    }
    if (metric === 'latency') {
      if (item.avgLatency <= 1000) return 'bg-emerald-400';
      if (item.avgLatency <= 3000) return 'bg-amber-400';
      return 'bg-red-400';
    }
    return 'bg-gray-400 dark:bg-gray-500';
  };

  const getProgressBarWidth = (item: ProviderStats, maxVal: number) => {
    let val = 0;
    if (metric === 'calls') val = item.callCount;
    if (metric === 'cost') val = item.estimatedCost;
    if (metric === 'latency') val = item.avgLatency;
    if (metric === 'successRate') val = item.successRate;
    return `${maxVal > 0 ? (val / maxVal) * 100 : 0}%`;
  };

  const getRankingStatusInfo = (item: ProviderStats, currentMetric: RankingMetric) => {
    if (currentMetric === 'calls') {
      if (item.callCount < 50) {
        return {
          label: t('dashboard.ranking.status.observation'),
          className: 'bg-gray-100 dark:bg-gray-800 text-gray-600 dark:text-gray-300',
        };
      }
      if (item.callCount <= 1000) {
        return {
          label: t('dashboard.ranking.status.normal'),
          className: 'bg-blue-50 dark:bg-blue-900/30 text-blue-700 dark:text-blue-300',
        };
      }
      return {
        label: t('dashboard.ranking.status.active'),
        className: 'bg-green-50 dark:bg-green-900/30 text-green-700 dark:text-green-300',
      };
    }

    if (currentMetric === 'cost') {
      return null;
    }

    if (currentMetric === 'latency') {
      if (item.avgLatency <= 15000) {
        return {
          label: t('dashboard.ranking.status.excellent'),
          className: 'bg-emerald-50 dark:bg-emerald-900/30 text-emerald-700 dark:text-emerald-300',
        };
      }
      if (item.avgLatency <= 35000) {
        return {
          label: t('dashboard.ranking.status.normal'),
          className: 'bg-amber-50 dark:bg-amber-900/30 text-amber-700 dark:text-amber-300',
        };
      }
      return {
        label: t('dashboard.ranking.status.slow'),
        className: 'bg-red-50 dark:bg-red-900/30 text-red-700 dark:text-red-300',
      };
    }

    if (item.callCount === 0) {
      return {
        label: t('dashboard.ranking.status.observation'),
        className: 'bg-gray-100 dark:bg-gray-800 text-gray-600 dark:text-gray-300',
      };
    }
    if (item.successRate >= 99) {
      return {
        label: t('dashboard.ranking.status.excellent'),
        className: 'bg-emerald-50 dark:bg-emerald-900/30 text-emerald-700 dark:text-emerald-300',
      };
    }
    if (item.successRate >= 95) {
      return {
        label: t('dashboard.ranking.status.normal'),
        className: 'bg-blue-50 dark:bg-blue-900/30 text-blue-700 dark:text-blue-300',
      };
    }
    return {
      label: t('dashboard.ranking.status.abnormal'),
      className: 'bg-red-50 dark:bg-red-900/30 text-red-700 dark:text-red-300',
    };
  };

  const sortedData = getSortedData();
  const maxValue = Math.max(
    ...providerRanking.map((provider) => {
      if (metric === 'calls') return provider.callCount;
      if (metric === 'cost') return provider.estimatedCost;
      if (metric === 'latency') return provider.avgLatency;
      return 100;
    }),
    1,
  );

  const metrics: RankingMetric[] = ['calls', 'cost', 'latency', 'successRate'];

  const safeOverview = overview || {
    totalRequests: 0,
    requestGrowthRate: 0,
    totalTokens: 0,
    tokenGrowthRate: 0,
    totalCost: 0,
    costGrowthRate: 0,
    avgLatency: 0,
    latencyChange: 0,
    successRate: 0,
    successRateChange: 0,
    cacheHitCount: 0,
    cacheHitRate: 0,
    cacheReadTokens: 0,
  };

  const runtime = observability || {
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

  const providerFilterOptions = Array.from(
    new Set(runtime.providers.map((provider) => provider.providerName || provider.providerId).filter(Boolean)),
  ).sort((a, b) => a.localeCompare(b));
  const modelFilterOptions = Array.from(
    new Set(runtime.providers.map((provider) => provider.modelName).filter((value): value is string => Boolean(value))),
  ).sort((a, b) => a.localeCompare(b));
  const filteredRuntimeProviders = runtime.providers.filter((provider) => {
    const providerLabel = provider.providerName || provider.providerId;
    const providerMatch = runtimeFilters.provider === 'ALL' || providerLabel === runtimeFilters.provider;
    const modelMatch = runtimeFilters.model === 'ALL' || provider.modelName === runtimeFilters.model;
    const stateMatch = runtimeFilters.state === 'ALL' || provider.circuitState === runtimeFilters.state;
    return providerMatch && modelMatch && stateMatch;
  });
  const sortedRuntimeProviders = [...filteredRuntimeProviders].sort((a, b) => {
    if (b.totalRequests !== a.totalRequests) {
      return b.totalRequests - a.totalRequests;
    }
    return b.successRequests - a.successRequests;
  });
  const runtimeTotalPages = Math.max(1, Math.ceil(sortedRuntimeProviders.length / runtimePagination.size));
  const runtimeCurrentPage = Math.min(runtimePagination.current, runtimeTotalPages);
  const pagedRuntimeProviders = sortedRuntimeProviders.slice(
    (runtimeCurrentPage - 1) * runtimePagination.size,
    runtimeCurrentPage * runtimePagination.size,
  );

  useEffect(() => {
    if (runtimePagination.current !== runtimeCurrentPage) {
      setRuntimePagination((prev) => ({
        ...prev,
        current: runtimeCurrentPage,
      }));
    }
  }, [runtimeCurrentPage, runtimePagination.current]);

  const handleRuntimePageChange = (page: number, size: number) => {
    setRuntimePagination({
      current: page,
      size,
    });
  };
  const handleRuntimeFilterChange = (key: 'provider' | 'model' | 'state', value: string) => {
    setRuntimeFilters((prev) => ({
      ...prev,
      [key]: value,
    }));
    setRuntimePagination((prev) => ({
      ...prev,
      current: 1,
    }));
  };

  if (isLoading) {
    return <DashboardSkeleton />;
  }

  return (
    <div className="space-y-8">
      <SlideInItem>
        <div className="mb-8">
          <h1 className="text-3xl font-bold text-gray-900 dark:text-white tracking-tight">{t('dashboard.title')}</h1>
          <p className="text-gray-500 dark:text-gray-400 mt-1">{t('dashboard.subtitle')}</p>
        </div>
      </SlideInItem>

      <div className="grid grid-cols-1 md:grid-cols-2 xl:grid-cols-6 gap-4">
        {[
          {
            title: t('dashboard.totalRequests'),
            value: safeOverview.totalRequests.toLocaleString(),
            trend: `${safeOverview.requestGrowthRate.toFixed(1)}%`,
            trendDirection: safeOverview.requestGrowthRate >= 0 ? ('up' as const) : ('down' as const),
            trendPositive: safeOverview.requestGrowthRate >= 0,
            icon: Zap,
          },
          {
            title: t('dashboard.totalTokens'),
            value: formatTokenCount(safeOverview.totalTokens),
            trend: `${safeOverview.tokenGrowthRate.toFixed(1)}%`,
            trendDirection: safeOverview.tokenGrowthRate >= 0 ? ('up' as const) : ('down' as const),
            trendPositive: safeOverview.tokenGrowthRate >= 0,
            icon: Sigma,
          },
          {
            title: t('dashboard.totalCost'),
            value: `$${safeOverview.totalCost.toFixed(4)}`,
            trend: `${safeOverview.costGrowthRate.toFixed(1)}%`,
            trendDirection: safeOverview.costGrowthRate >= 0 ? ('up' as const) : ('down' as const),
            trendPositive: true,
            icon: Coins,
          },
          {
            title: t('dashboard.avgLatency'),
            value: `${safeOverview.avgLatency.toFixed(0)}ms`,
            trend: `${Math.abs(safeOverview.latencyChange).toFixed(0)}ms`,
            trendDirection: safeOverview.latencyChange >= 0 ? ('up' as const) : ('down' as const),
            trendPositive: safeOverview.latencyChange <= 0,
            icon: Clock,
          },
          {
            title: t('dashboard.successRate'),
            value: `${safeOverview.successRate.toFixed(1)}%`,
            trend: `${safeOverview.successRateChange.toFixed(1)}%`,
            trendDirection: safeOverview.successRateChange >= 0 ? ('up' as const) : ('down' as const),
            trendPositive: safeOverview.successRateChange >= 0,
            icon: Activity,
          },
          {
            title: t('dashboard.cacheHitRate'),
            value: `${safeOverview.cacheHitRate.toFixed(1)}%`,
            trend: formatTokenCount(safeOverview.cacheReadTokens),
            trendDirection: safeOverview.cacheHitRate > 0 ? ('up' as const) : ('down' as const),
            trendPositive: safeOverview.cacheHitRate > 0,
            icon: Database,
          },
        ].map((item, index) => (
          <SlideInItem key={index} index={index} delay={index * 50}>
            <StatCard {...item} />
          </SlideInItem>
        ))}
      </div>

      {healthHeatmap && healthHeatmap.cells.length > 0 && (
        <SlideInItem delay={140} className="bg-white dark:bg-[#1a1a1a] rounded-2xl shadow-card border border-gray-200/60 dark:border-gray-800 p-6">
          <HealthHeatmap
            cells={healthHeatmap.cells}
            days={healthHeatmap.days}
            overallSuccessRate={healthHeatmap.overallSuccessRate}
          />
        </SlideInItem>
      )}

      <SlideInItem delay={160} className="bg-white dark:bg-[#1a1a1a] rounded-2xl shadow-card border border-gray-200/60 dark:border-gray-800 p-6">
        <div className="flex flex-col gap-4 lg:flex-row lg:items-end lg:justify-between mb-6">
          <div>
            <h2 className="text-base font-semibold text-gray-900 dark:text-white">{t('dashboard.observability.title')}</h2>
            <p className="text-sm text-gray-500 dark:text-gray-400 mt-1">{t('dashboard.observability.subtitle')}</p>
          </div>
          <div className="inline-flex items-center gap-2 text-xs font-medium text-gray-500 dark:text-gray-400 rounded-full border border-gray-200 dark:border-gray-700 px-3 py-1.5 bg-gray-50/50 dark:bg-gray-800/30">
            <div className="relative flex h-2 w-2">
              {isRefreshingRuntime && (
                <span className="animate-ping absolute inline-flex h-full w-full rounded-full bg-emerald-400 opacity-75"></span>
              )}
              <span className={`relative inline-flex rounded-full h-2 w-2 ${isRefreshingRuntime ? 'bg-emerald-500' : 'bg-gray-300 dark:bg-gray-600'}`}></span>
            </div>
            {t('dashboard.observability.autoRefresh', { seconds: 10, time: lastRuntimeRefresh })}
          </div>
        </div>
        <div className="grid grid-cols-1 md:grid-cols-2 xl:grid-cols-5 gap-4">
          <RuntimeCard
            title={t('dashboard.observability.cards.cacheHitRate')}
            value={formatPercent(runtime.overview.cacheHitRate)}
            detail={`${formatNumber(runtime.overview.providersTracked)} ${t('dashboard.observability.cards.providersTrackedSuffix')}`}
            icon={Database}
          />
          <RuntimeCard
            title={t('dashboard.observability.cards.openCircuits')}
            value={`${runtime.overview.openCircuits} / ${runtime.overview.halfOpenCircuits}`}
            detail={t('dashboard.observability.cards.openCircuitsDetail')}
            icon={ShieldAlert}
          />
          <RuntimeCard
            title={t('dashboard.observability.cards.failoverSwitches')}
            value={formatNumber(runtime.overview.failoverSwitches)}
            detail={`${runtime.overview.failoverTerminations} ${t('dashboard.observability.cards.failoverTerminationsSuffix')}`}
            icon={Activity}
          />
          <RuntimeCard
            title={t('dashboard.observability.cards.bulkheadRejections')}
            value={formatNumber(runtime.overview.bulkheadRejections)}
            detail={t('dashboard.observability.cards.bulkheadRejectionsDetail')}
            icon={Gauge}
          />
          <RuntimeCard
            title={t('dashboard.observability.cards.logQueue')}
            value={`${runtime.logPipeline.queueSize} / ${runtime.overview.logDroppedTotal}`}
            detail={`${runtime.logPipeline.avgFlushMs.toFixed(1)}ms · ${runtime.logPipeline.avgBatchSize.toFixed(1)}`}
            icon={Server}
          />
        </div>
      </SlideInItem>

      <SlideInItem delay={220} className="grid grid-cols-1 xl:grid-cols-3 gap-6">
        <div className="xl:col-span-2 bg-white dark:bg-[#1a1a1a] p-6 rounded-2xl shadow-card border border-gray-200/60 dark:border-gray-800">
          <h3 className="text-base font-semibold text-gray-900 dark:text-white mb-5 flex items-center gap-2">
            <Database size={18} className="text-gray-400" />
            {t('dashboard.observability.cache.title')}
          </h3>
          <div className="overflow-x-auto">
            <table className="w-full text-sm">
              <thead className="text-xs uppercase tracking-wider text-gray-500 dark:text-gray-400">
                <tr>
                  <th className="text-left py-3">{t('dashboard.observability.cache.columns.name')}</th>
                  <th className="text-right py-3">{t('dashboard.observability.cache.columns.hitRate')}</th>
                  <th className="text-right py-3">{t('dashboard.observability.cache.columns.lookups')}</th>
                  <th className="text-right py-3">{t('dashboard.observability.cache.columns.loads')}</th>
                  <th className="text-right py-3">{t('dashboard.observability.cache.columns.avgLoadMs')}</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-gray-100 dark:divide-gray-800">
                {runtime.caches.map((cache: ObservabilityCacheMetric) => (
                  <tr key={cache.cache}>
                    <td className="py-4 font-medium text-gray-900 dark:text-white">{formatCacheLabel(cache.cache, t)}</td>
                    <td className="py-4 text-right text-gray-700 dark:text-gray-300">{formatPercent(cache.hitRate)}</td>
                    <td className="py-4 text-right text-gray-500 dark:text-gray-400">
                      {formatNumber(cache.hits + cache.misses + cache.expired)}
                    </td>
                    <td className="py-4 text-right text-gray-500 dark:text-gray-400">{formatNumber(cache.loads)}</td>
                    <td className="py-4 text-right text-gray-500 dark:text-gray-400">{cache.avgLoadMs.toFixed(1)}ms</td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        </div>

        <div className="space-y-6">
          <div className="bg-white dark:bg-[#1a1a1a] p-6 rounded-2xl shadow-card border border-gray-200/60 dark:border-gray-800">
            <h3 className="text-base font-semibold text-gray-900 dark:text-white mb-4">{t('dashboard.observability.routing.title')}</h3>
            <div className="space-y-3 text-sm">
              {[
                {
                  label: t('dashboard.observability.routing.items.saprSelections'),
                  value: runtime.selection.saprSelections,
                },
                {
                  label: t('dashboard.observability.routing.items.roundRobinSelections'),
                  value: runtime.selection.roundRobinSelections,
                },
                {
                  label: t('dashboard.observability.routing.items.fallbackToRoundRobin'),
                  value: runtime.selection.fallbackToRoundRobin,
                },
                {
                  label: t('dashboard.observability.routing.items.failoverAttempts'),
                  value: runtime.selection.failoverAttemptsNonStream + runtime.selection.failoverAttemptsStream,
                },
                {
                  label: t('dashboard.observability.routing.items.failoverDepthAvg'),
                  value: runtime.overview.failoverDepthAvg.toFixed(2),
                },
              ].map((item) => (
                <div key={item.label} className="flex items-center justify-between rounded-xl bg-gray-50 dark:bg-gray-800/60 px-4 py-3">
                  <span className="text-gray-500 dark:text-gray-400">{item.label}</span>
                  <span className="font-semibold text-gray-900 dark:text-white">{item.value}</span>
                </div>
              ))}
            </div>
          </div>

          <div className="bg-white dark:bg-[#1a1a1a] p-6 rounded-2xl shadow-card border border-gray-200/60 dark:border-gray-800">
            <h3 className="text-base font-semibold text-gray-900 dark:text-white mb-4">{t('dashboard.observability.pipeline.title')}</h3>
            <div className="space-y-3 text-sm">
              {[
                {
                  label: t('dashboard.observability.pipeline.items.logDropped'),
                  value: runtime.logPipeline.droppedTotal,
                },
                {
                  label: t('dashboard.observability.pipeline.items.logQueue'),
                  value: runtime.logPipeline.queueSize,
                },
                {
                  label: t('dashboard.observability.pipeline.items.logFlushAvgMs'),
                  value: `${runtime.logPipeline.avgFlushMs.toFixed(1)}ms`,
                },
                {
                  label: t('dashboard.observability.pipeline.items.logBatchAvg'),
                  value: runtime.logPipeline.avgBatchSize.toFixed(1),
                },
                {
                  label: t('dashboard.observability.pipeline.items.bulkheadRejected'),
                  value: runtime.selection.bulkheadRejectedNonStream + runtime.selection.bulkheadRejectedStream,
                },
              ].map((item) => (
                <div key={item.label} className="flex items-center justify-between rounded-xl bg-gray-50 dark:bg-gray-800/60 px-4 py-3">
                  <span className="text-gray-500 dark:text-gray-400">{item.label}</span>
                  <span className="font-semibold text-gray-900 dark:text-white">{item.value}</span>
                </div>
              ))}
            </div>
          </div>
        </div>
      </SlideInItem>

      <SlideInItem delay={260} className="bg-white dark:bg-[#1a1a1a] rounded-2xl shadow-card border border-gray-200/60 dark:border-gray-800 overflow-hidden">
        <div className="px-6 py-5 border-b border-gray-100 dark:border-gray-800">
          <h3 className="text-base font-semibold text-gray-900 dark:text-white">{t('dashboard.observability.providers.title')}</h3>
        </div>
        <div className="overflow-x-auto">
          <div className="px-6 pt-5 grid grid-cols-1 md:grid-cols-3 gap-4">
            <label className="flex flex-col gap-2 text-sm">
              <span className="text-gray-500 dark:text-gray-400">{t('dashboard.observability.providers.filters.provider')}</span>
              <select
                value={runtimeFilters.provider}
                onChange={(event) => handleRuntimeFilterChange('provider', event.target.value)}
                className="rounded-xl border border-gray-200 dark:border-gray-700 bg-white dark:bg-[#111111] px-3 py-2 text-gray-900 dark:text-white outline-none focus:ring-2 focus:ring-indigo-500/30"
              >
                <option value="ALL">{t('dashboard.observability.providers.filters.allProviders')}</option>
                {providerFilterOptions.map((providerName) => (
                  <option key={providerName} value={providerName}>
                    {providerName}
                  </option>
                ))}
              </select>
            </label>
            <label className="flex flex-col gap-2 text-sm">
              <span className="text-gray-500 dark:text-gray-400">{t('dashboard.observability.providers.filters.model')}</span>
              <select
                value={runtimeFilters.model}
                onChange={(event) => handleRuntimeFilterChange('model', event.target.value)}
                className="rounded-xl border border-gray-200 dark:border-gray-700 bg-white dark:bg-[#111111] px-3 py-2 text-gray-900 dark:text-white outline-none focus:ring-2 focus:ring-indigo-500/30"
              >
                <option value="ALL">{t('dashboard.observability.providers.filters.allModels')}</option>
                {modelFilterOptions.map((modelName) => (
                  <option key={modelName} value={modelName}>
                    {modelName}
                  </option>
                ))}
              </select>
            </label>
            <label className="flex flex-col gap-2 text-sm">
              <span className="text-gray-500 dark:text-gray-400">{t('dashboard.observability.providers.filters.state')}</span>
              <select
                value={runtimeFilters.state}
                onChange={(event) => handleRuntimeFilterChange('state', event.target.value)}
                className="rounded-xl border border-gray-200 dark:border-gray-700 bg-white dark:bg-[#111111] px-3 py-2 text-gray-900 dark:text-white outline-none focus:ring-2 focus:ring-indigo-500/30"
              >
                <option value="ALL">{t('dashboard.observability.providers.filters.allStates')}</option>
                <option value="CLOSED">{t('settings.circuitBreaker.states.CLOSED')}</option>
                <option value="OPEN">{t('settings.circuitBreaker.states.OPEN')}</option>
                <option value="HALF_OPEN">{t('settings.circuitBreaker.states.HALF_OPEN')}</option>
              </select>
            </label>
          </div>
          <table className="w-full text-sm text-left">
            <thead className="bg-gray-50 dark:bg-gray-800/50 text-gray-500 dark:text-gray-400 font-medium text-xs uppercase tracking-wider">
              <tr>
                <th className="px-6 py-4">{t('dashboard.observability.providers.columns.provider')}</th>
                <th className="px-6 py-4">{t('dashboard.observability.providers.columns.model')}</th>
                <th className="px-6 py-4">{t('dashboard.observability.providers.columns.state')}</th>
                <th className="px-6 py-4">{t('dashboard.observability.providers.columns.score')}</th>
                <th className="px-6 py-4">{t('dashboard.observability.providers.columns.totalRequests')}</th>
                <th className="px-6 py-4">{t('dashboard.observability.providers.columns.successRequests')}</th>
                <th className="px-6 py-4">{t('dashboard.observability.providers.columns.failureRequests')}</th>
                <th className="px-6 py-4">{t('dashboard.observability.providers.columns.successRate')}</th>
                <th className="px-6 py-4">{t('dashboard.observability.providers.columns.concurrent')}</th>
                <th className="px-6 py-4">{t('dashboard.observability.providers.columns.rejected')}</th>
                <th className="px-6 py-4">{t('dashboard.observability.providers.columns.nextProbe')}</th>
              </tr>
            </thead>
            <tbody className="divide-y divide-gray-50 dark:divide-gray-800">
              {pagedRuntimeProviders.length === 0 ? (
                <tr>
                  <td colSpan={10} className="px-6 py-8 text-center text-gray-400 dark:text-gray-600 italic">
                    {t('dashboard.observability.providers.noData')}
                  </td>
                </tr>
              ) : (
                pagedRuntimeProviders.map((provider) => {
                  const stateInfo = formatCircuitState(provider, t);
                  return (
                    <tr key={provider.providerId} className="hover:bg-gray-50 dark:hover:bg-gray-800/50 transition-colors">
                      <td className="px-6 py-4">
                        <div className="font-medium text-gray-900 dark:text-white">{provider.providerName || provider.providerId}</div>
                        <div className="text-xs text-gray-400 dark:text-gray-500 mt-1">{provider.providerId}</div>
                      </td>
                      <td className="px-6 py-4 text-gray-700 dark:text-gray-300">{provider.modelName || '-'}</td>
                      <td className="px-6 py-4">
                        <span className={`inline-flex items-center px-2 py-0.5 rounded-md text-[10px] font-bold ${stateInfo.className}`}>
                          {stateInfo.label}
                        </span>
                      </td>
                      <td className="px-6 py-4 text-gray-700 dark:text-gray-300">{provider.score.toFixed(1)}</td>
                      <td className="px-6 py-4 text-gray-700 dark:text-gray-300">{formatNumber(provider.totalRequests)}</td>
                      <td className="px-6 py-4 text-gray-700 dark:text-gray-300">{formatNumber(provider.successRequests)}</td>
                      <td className="px-6 py-4 text-gray-700 dark:text-gray-300">{formatNumber(provider.failureRequests)}</td>
                      <td className="px-6 py-4 text-gray-700 dark:text-gray-300">{formatPercent(providerSuccessRate(provider))}</td>
                      <td className="px-6 py-4 text-gray-700 dark:text-gray-300">
                        {provider.currentConcurrent} / {provider.maxConcurrent}
                      </td>
                      <td className="px-6 py-4 text-gray-700 dark:text-gray-300">{provider.bulkheadRejectedCount}</td>
                      <td className="px-6 py-4 text-gray-700 dark:text-gray-300">{formatTimestamp(provider.nextProbeAt)}</td>
                    </tr>
                  );
                })
              )}
            </tbody>
          </table>
        </div>
        {sortedRuntimeProviders.length > 0 && (
          <div className="px-6">
            <Pagination
              current={runtimeCurrentPage}
              size={runtimePagination.size}
              total={sortedRuntimeProviders.length}
              onChange={handleRuntimePageChange}
              className="border-t border-gray-200/50 dark:border-gray-800/50"
            />
          </div>
        )}
      </SlideInItem>

      <SlideInItem delay={300} className="grid grid-cols-1 lg:grid-cols-3 gap-6">
        <div className="lg:col-span-2 bg-white dark:bg-[#1a1a1a] p-6 rounded-2xl shadow-card border border-gray-200/60 dark:border-gray-800">
          <div className="flex items-center justify-between mb-6">
            <h3 className="text-base font-semibold text-gray-900 dark:text-white flex items-center gap-2">
              <Activity size={18} className="text-gray-400" />
              {t('dashboard.traffic')}
            </h3>
          </div>
          <div className="h-72 w-full">
            <ResponsiveContainer width="100%" height="100%">
              <AreaChart data={trafficData}>
                <defs>
                  <linearGradient id="colorRequests" x1="0" y1="0" x2="0" y2="1">
                    <stop offset="5%" stopColor="#6366f1" stopOpacity={0.1} />
                    <stop offset="95%" stopColor="#6366f1" stopOpacity={0} />
                  </linearGradient>
                </defs>
                <CartesianGrid strokeDasharray="3 3" vertical={false} stroke="#E5E7EB" className="dark:stroke-gray-800" />
                <XAxis dataKey="time" axisLine={false} tickLine={false} tick={{ fill: '#9CA3AF', fontSize: 11 }} dy={10} />
                <YAxis axisLine={false} tickLine={false} tick={{ fill: '#9CA3AF', fontSize: 11 }} />
                <Tooltip
                  contentStyle={{
                    borderRadius: '8px',
                    border: '1px solid #E5E7EB',
                    boxShadow: '0 4px 6px -1px rgba(0, 0, 0, 0.05)',
                    backgroundColor: '#fff',
                    color: '#111827',
                    padding: '8px 12px',
                  }}
                  cursor={{ stroke: '#9CA3AF', strokeWidth: 1, strokeDasharray: '4 4' }}
                />
                <Area type="monotone" dataKey="requests" stroke="#6366f1" strokeWidth={2} fillOpacity={1} fill="url(#colorRequests)" />
              </AreaChart>
            </ResponsiveContainer>
          </div>
        </div>

        <div className="bg-white dark:bg-[#1a1a1a] p-6 rounded-2xl shadow-card border border-gray-200/60 dark:border-gray-800">
          <h3 className="text-base font-semibold text-gray-900 dark:text-white mb-6 flex items-center gap-2">
            <BarChart3 size={18} className="text-gray-400" />
            {t('dashboard.tokenUsage')}
          </h3>
          <div className="h-72 w-full">
            <ResponsiveContainer width="100%" height="100%">
              <BarChart data={modelUsageData} layout="vertical" barSize={20}>
                <CartesianGrid strokeDasharray="3 3" horizontal vertical={false} stroke="#E5E7EB" className="dark:stroke-gray-800" />
                <XAxis type="number" hide />
                <YAxis
                  dataKey="name"
                  type="category"
                  width={110}
                  tick={{ fill: '#6B7280', fontSize: 11, fontWeight: 500 }}
                  axisLine={false}
                  tickLine={false}
                  tickFormatter={(value) => (value.length > 15 ? `${value.substring(0, 15)}...` : value)}
                />
                <Tooltip
                  cursor={{ fill: 'transparent' }}
                  contentStyle={{
                    borderRadius: '8px',
                    border: '1px solid #E5E7EB',
                    boxShadow: '0 4px 6px -1px rgba(0, 0, 0, 0.05)',
                    backgroundColor: '#fff',
                    color: '#000',
                  }}
                />
                <Bar dataKey="tokens" radius={[0, 4, 4, 0]}>
                  {modelUsageData.map((_, index) => (
                    <Cell key={`cell-${index}`} fill={index % 2 === 0 ? '#4B5563' : '#9CA3AF'} />
                  ))}
                </Bar>
              </BarChart>
            </ResponsiveContainer>
          </div>
        </div>
      </SlideInItem>

      <SlideInItem delay={340} className="bg-white dark:bg-[#1a1a1a] rounded-2xl shadow-card border border-gray-200/60 dark:border-gray-800 overflow-hidden">
        <div className="px-6 py-5 border-b border-gray-100 dark:border-gray-800 flex flex-col sm:flex-row justify-between items-start sm:items-center gap-4">
          <h3 className="text-base font-semibold text-gray-900 dark:text-white">{t('dashboard.ranking.title')}</h3>

          <div className="flex p-1 bg-gray-100 dark:bg-gray-800 rounded-lg">
            {metrics.map((m) => (
              <button
                key={m}
                onClick={() => setMetric(m)}
                className={`px-3 py-1.5 text-xs font-medium rounded-md whitespace-nowrap transition-all ${
                  metric === m
                    ? 'bg-white dark:bg-gray-600 text-gray-900 dark:text-white shadow-sm'
                    : 'text-gray-500 dark:text-gray-400 hover:text-gray-700 dark:hover:text-gray-200'
                }`}
              >
                {t(`dashboard.ranking.options.${m}`)}
              </button>
            ))}
          </div>
        </div>
        <div className="overflow-x-auto">
          <table className="w-full text-sm text-left">
            <thead className="bg-gray-50 dark:bg-gray-800/50 text-gray-500 dark:text-gray-400 font-medium text-xs uppercase tracking-wider">
              <tr>
                <th className="px-6 py-4 w-20">{t('dashboard.ranking.columns.rank')}</th>
                <th className="px-6 py-4">{t('dashboard.ranking.columns.provider')}</th>
                <th className="px-6 py-4">{t(`dashboard.ranking.options.${metric}`)}</th>
                <th className="px-6 py-4">{t('dashboard.ranking.columns.status')}</th>
              </tr>
            </thead>
            <tbody className="divide-y divide-gray-50 dark:divide-gray-800">
              {providerRanking.length === 0 ? (
                <tr>
                  <td colSpan={4} className="px-6 py-8 text-center text-gray-400 dark:text-gray-600 italic">
                    {t('dashboard.ranking.noData')}
                  </td>
                </tr>
              ) : (
                sortedData.map((provider, index) => {
                  const statusInfo = getRankingStatusInfo(provider, metric);
                  return (
                    <tr key={getRankingRowKey(provider, index)} className="hover:bg-gray-50 dark:hover:bg-gray-800/50 transition-colors group">
                      <td className="px-6 py-4 font-mono text-xs text-gray-400 dark:text-gray-500">#{index + 1}</td>
                      <td className="px-6 py-4 font-medium text-gray-900 dark:text-white">{provider.providerName}</td>
                      <td className="px-6 py-4 text-gray-600 dark:text-gray-300">
                        <div className="flex items-center gap-4">
                          <span className="font-mono text-xs w-20 text-right">{formatRankingValue(provider)}</span>
                          <div className="w-24 h-1.5 bg-gray-100 dark:bg-gray-700 rounded-full overflow-hidden hidden sm:block">
                            <div
                              className={`h-full rounded-full transition-all duration-500 ${getProgressBarColor(provider)}`}
                              style={{ width: getProgressBarWidth(provider, maxValue) }}
                            />
                          </div>
                        </div>
                      </td>
                      <td className="px-6 py-4">
                        {statusInfo ? (
                          <span className={`inline-flex items-center px-2 py-0.5 rounded-md text-[10px] font-bold ${statusInfo.className} border border-transparent dark:border-white/5`}>
                            {statusInfo.label}
                          </span>
                        ) : (
                          <span className="text-gray-300">-</span>
                        )}
                      </td>
                    </tr>
                  );
                })
              )}
            </tbody>
          </table>
        </div>
      </SlideInItem>
    </div>
  );
};
