import React, { useState, useEffect } from 'react';
import { 
  AreaChart, 
  Area, 
  XAxis, 
  YAxis, 
  CartesianGrid, 
  Tooltip, 
  ResponsiveContainer, 
  BarChart,
  Bar
} from 'recharts';
import { ArrowUpRight, ArrowDownRight, Zap, Coins, Clock, Activity } from 'lucide-react';
import { useLanguage } from './LanguageContext';
import { dashboardService, ProviderStats } from '../services/dashboardService';
import { DashboardOverview } from '../types';
import { DashboardSkeleton } from './Skeletons';
import { SlideInItem } from './Animations';

const StatCard: React.FC<{
  title: string;
  value: string;
  trend: string;
  trendDirection: 'up' | 'down';
  trendPositive: boolean;
  icon: React.ElementType;
  colorClass: string;
}> = ({ title, value, trend, trendDirection, trendPositive, icon: Icon, colorClass }) => (
  <div className="bg-white dark:bg-slate-800 rounded-xl p-6 shadow-sm border border-slate-100 dark:border-slate-700 transition-all hover:shadow-md hover:-translate-y-1 duration-300">
    <div className="flex items-center justify-between mb-4">
      <div className={`p-2 rounded-lg ${colorClass} bg-opacity-10 dark:bg-opacity-20`}>
        <Icon size={20} />
      </div>
      <div className={`flex items-center text-xs font-medium ${trendPositive ? 'text-green-600 dark:text-green-400' : 'text-red-600 dark:text-red-400'}`}>
        {trendDirection === 'up' ? <ArrowUpRight size={14} className="mr-1" /> : <ArrowDownRight size={14} className="mr-1" />}
        {trend}
      </div>
    </div>
    <h3 className="text-slate-500 dark:text-slate-400 text-sm font-medium">{title}</h3>
    <p className="text-2xl font-bold text-slate-900 dark:text-white mt-1">{value}</p>
  </div>
);

type RankingMetric = 'calls' | 'cost' | 'latency' | 'successRate';

export const Dashboard: React.FC = () => {
  const { t } = useLanguage();
  const [metric, setMetric] = useState<RankingMetric>('calls');
  const [overview, setOverview] = useState<DashboardOverview | null>(null);
  const [trafficData, setTrafficData] = useState<{ time: string; requests: number }[]>([]);
  const [modelUsageData, setModelUsageData] = useState<{ name: string; tokens: number }[]>([]);
  const [providerRanking, setProviderRanking] = useState<ProviderStats[]>([]);
  const [isLoading, setIsLoading] = useState(true);

  useEffect(() => {
    const fetchDashboardData = async () => {
        setIsLoading(true);
        try {
            const [overviewData, trafficRaw, modelUsageRaw, providerStatsRaw] = await Promise.all([
                dashboardService.getOverview(),
                dashboardService.getTraffic(),
                dashboardService.getModelTokenUsage(),
                dashboardService.getProviderStats()
            ]);
            setOverview(overviewData);

            // Process Traffic Data
            if (trafficRaw && trafficRaw.length > 0) {
                const formatted = trafficRaw
                    .sort((a, b) => a.timestamp - b.timestamp)
                    .map(item => ({
                        time: `${item.hour.toString().padStart(2, '0')}:00`,
                        requests: item.requestCount
                    }));
                setTrafficData(formatted);
            } else {
                setTrafficData([]);
            }

            // Process Model Token Usage Data
            if (modelUsageRaw && modelUsageRaw.length > 0) {
                const formattedUsage = modelUsageRaw.map(item => ({
                    name: item.modelName,
                    tokens: item.totalTokens
                }));
                setModelUsageData(formattedUsage);
            } else {
                setModelUsageData([]);
            }

            // Process Provider Stats
            if (providerStatsRaw) {
                setProviderRanking(providerStatsRaw);
            }

        } catch (error) {
            console.error("Failed to load dashboard data", error);
        } finally {
            setIsLoading(false);
        }
    };
    fetchDashboardData();
  }, []);

  const getSortedData = () => {
    return [...providerRanking].sort((a, b) => {
      switch (metric) {
        case 'calls': return b.callCount - a.callCount;
        case 'cost': return b.estimatedCost - a.estimatedCost;
        case 'latency': return a.avgLatency - b.avgLatency; // Lower is better for latency
        case 'successRate': return b.successRate - a.successRate;
        default: return 0;
      }
    });
  };

  const formatValue = (item: ProviderStats) => {
    switch (metric) {
      case 'calls': return item.callCount.toLocaleString();
      case 'cost': return `$${item.estimatedCost.toFixed(4)}`;
      case 'latency': return `${item.avgLatency.toFixed(0)}ms`;
      case 'successRate': return `${item.successRate.toFixed(1)}%`;
      default: return '';
    }
  };

  const getProgressBarColor = (item: ProviderStats) => {
      if (metric === 'successRate') {
          if (item.successRate >= 99) return 'bg-green-500';
          if (item.successRate >= 95) return 'bg-yellow-500'; // Normal
          if (item.successRate >= 80) return 'bg-orange-500'; // Volatile
          return 'bg-red-500'; // Abnormal
      }
      if (metric === 'latency') {
          if (item.avgLatency <= 15000) return 'bg-green-500';
          if (item.avgLatency <= 35000) return 'bg-yellow-500'; // Normal
          if (item.avgLatency <= 60000) return 'bg-orange-500'; // Slow
          return 'bg-red-500'; // Abnormal
      }
      return 'bg-indigo-500';
  }

  const getProgressBarWidth = (item: ProviderStats, maxVal: number) => {
      let val = 0;
      if (metric === 'calls') val = item.callCount;
      if (metric === 'cost') val = item.estimatedCost;
      if (metric === 'latency') val = item.avgLatency;
      if (metric === 'successRate') val = item.successRate;
      
      return `${maxVal > 0 ? (val / maxVal) * 100 : 0}%`;
  }
  
  const getStatusInfo = (item: ProviderStats, currentMetric: RankingMetric) => {
    // Determine status label and color based on metric
    if (currentMetric === 'calls') {
      if (item.callCount < 50) return { label: t('dashboard.ranking.status.observation'), className: 'bg-slate-100 dark:bg-slate-700 text-slate-600 dark:text-slate-300' };
      if (item.callCount <= 1000) return { label: t('dashboard.ranking.status.normal'), className: 'bg-amber-100 dark:bg-amber-900/30 text-amber-800 dark:text-amber-300' };
      return { label: t('dashboard.ranking.status.active'), className: 'bg-green-100 dark:bg-green-900/30 text-green-800 dark:text-green-300' };
    }
    
    // No status for cost
    if (currentMetric === 'cost') {
        return null;
    }
    
    if (currentMetric === 'latency') {
        if (item.avgLatency <= 15000) return { label: t('dashboard.ranking.status.excellent'), className: 'bg-green-100 dark:bg-green-900/30 text-green-800 dark:text-green-300' };
        if (item.avgLatency <= 35000) return { label: t('dashboard.ranking.status.normal'), className: 'bg-amber-100 dark:bg-amber-900/30 text-amber-800 dark:text-amber-300' };
        if (item.avgLatency <= 60000) return { label: t('dashboard.ranking.status.slow'), className: 'bg-orange-100 dark:bg-orange-900/30 text-orange-800 dark:text-orange-300' };
        return { label: t('dashboard.ranking.status.abnormal'), className: 'bg-red-100 dark:bg-red-900/30 text-red-800 dark:text-red-300' };
    }
    
    if (currentMetric === 'successRate') {
        // Only show "Observation" if there are absolutely no calls or very few (e.g. 0), meaning we can't determine a rate.
        // Otherwise, trust the rate even if sample size is small, per user request to separate statuses.
        if (item.callCount === 0) return { label: t('dashboard.ranking.status.observation'), className: 'bg-slate-100 dark:bg-slate-700 text-slate-600 dark:text-slate-300' };
        
        if (item.successRate >= 99) return { label: t('dashboard.ranking.status.excellent'), className: 'bg-green-100 dark:bg-green-900/30 text-green-800 dark:text-green-300' };
        if (item.successRate >= 95) return { label: t('dashboard.ranking.status.normal'), className: 'bg-amber-100 dark:bg-amber-900/30 text-amber-800 dark:text-amber-300' };
        if (item.successRate >= 80) return { label: t('dashboard.ranking.status.volatile'), className: 'bg-orange-100 dark:bg-orange-900/30 text-orange-800 dark:text-orange-300' };
        return { label: t('dashboard.ranking.status.abnormal'), className: 'bg-red-100 dark:bg-red-900/30 text-red-800 dark:text-red-300' };
    }
    
    return null;
  };

  const sortedData = getSortedData();
  const maxValue = Math.max(...providerRanking.map(c => {
      if(metric === 'calls') return c.callCount;
      if(metric === 'cost') return c.estimatedCost;
      if(metric === 'latency') return c.avgLatency;
      return 100;
  }), 1); // Ensure max value is at least 1 to avoid division by zero

  const metrics: RankingMetric[] = ['calls', 'cost', 'latency', 'successRate'];
  
  const safeOverview = overview || {
    totalRequests: 0,
    requestGrowthRate: 0,
    totalCost: 0,
    costGrowthRate: 0,
    avgLatency: 0,
    latencyChange: 0,
    successRate: 0,
    successRateChange: 0
  };

  if (isLoading) {
    return <DashboardSkeleton />;
  }

  return (
    <div className="space-y-6">
      <SlideInItem>
        <div>
            <h1 className="text-2xl font-bold text-slate-900 dark:text-white">{t('dashboard.title')}</h1>
            <p className="text-slate-500 dark:text-slate-400 mt-1">{t('dashboard.subtitle')}</p>
        </div>
      </SlideInItem>

      {/* KPI Grid */}
      <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-4 gap-6">
        {[
          {
            title: t('dashboard.totalRequests'),
            value: safeOverview.totalRequests.toLocaleString(),
            trend: `${safeOverview.requestGrowthRate.toFixed(1)}%`,
            trendDirection: safeOverview.requestGrowthRate >= 0 ? 'up' : 'down' as 'up'|'down',
            trendPositive: safeOverview.requestGrowthRate >= 0,
            icon: Zap,
            colorClass: "bg-blue-50 text-blue-600 dark:bg-blue-900/30 dark:text-blue-400"
          },
          {
            title: t('dashboard.totalCost'),
            value: `$${safeOverview.totalCost.toFixed(4)}`,
            trend: `${safeOverview.costGrowthRate.toFixed(1)}%`,
            trendDirection: safeOverview.costGrowthRate >= 0 ? 'up' : 'down' as 'up'|'down',
            trendPositive: true,
            icon: Coins,
            colorClass: "bg-amber-50 text-amber-600 dark:bg-amber-900/30 dark:text-amber-400"
          },
          {
            title: t('dashboard.avgLatency'),
            value: `${safeOverview.avgLatency.toFixed(0)}ms`,
            trend: `${Math.abs(safeOverview.latencyChange).toFixed(0)}ms`,
            trendDirection: safeOverview.latencyChange >= 0 ? 'up' : 'down' as 'up'|'down',
            trendPositive: safeOverview.latencyChange <= 0,
            icon: Clock,
            colorClass: "bg-purple-50 text-purple-600 dark:bg-purple-900/30 dark:text-purple-400"
          },
          {
            title: t('dashboard.successRate'),
            value: `${safeOverview.successRate.toFixed(1)}%`,
            trend: `${safeOverview.successRateChange.toFixed(1)}%`,
            trendDirection: safeOverview.successRateChange >= 0 ? 'up' : 'down' as 'up'|'down',
            trendPositive: safeOverview.successRateChange >= 0,
            icon: Activity,
            colorClass: "bg-green-50 text-green-600 dark:bg-green-900/30 dark:text-green-400"
          }
        ].map((item, index) => (
            <SlideInItem key={index} index={index} delay={index * 100}>
                <StatCard {...item} />
            </SlideInItem>
        ))}
      </div>

      {/* Charts Row */}
      <SlideInItem delay={400} className="grid grid-cols-1 lg:grid-cols-3 gap-6">
        {/* Main Traffic Chart */}
        <div className="lg:col-span-2 bg-white dark:bg-slate-800 p-6 rounded-xl shadow-sm border border-slate-100 dark:border-slate-700">
          <h3 className="text-lg font-semibold text-slate-900 dark:text-white mb-6">{t('dashboard.traffic')}</h3>
          <div className="h-72 w-full">
            <ResponsiveContainer width="100%" height="100%">
              <AreaChart data={trafficData}>
                <defs>
                  <linearGradient id="colorRequests" x1="0" y1="0" x2="0" y2="1">
                    <stop offset="5%" stopColor="#6366f1" stopOpacity={0.2}/>
                    <stop offset="95%" stopColor="#6366f1" stopOpacity={0}/>
                  </linearGradient>
                </defs>
                <CartesianGrid strokeDasharray="3 3" vertical={false} stroke="#f1f5f9" className="dark:opacity-10" />
                <XAxis dataKey="time" axisLine={false} tickLine={false} tick={{fill: '#64748b', fontSize: 12}} />
                <YAxis axisLine={false} tickLine={false} tick={{fill: '#64748b', fontSize: 12}} />
                <Tooltip 
                  contentStyle={{
                    borderRadius: '8px', 
                    border: 'none', 
                    boxShadow: '0 4px 6px -1px rgb(0 0 0 / 0.1)',
                    backgroundColor: '#fff',
                    color: '#000'
                  }} 
                />
                <Area type="monotone" dataKey="requests" stroke="#6366f1" strokeWidth={2} fillOpacity={1} fill="url(#colorRequests)" />
              </AreaChart>
            </ResponsiveContainer>
          </div>
        </div>

        {/* Top Models Chart */}
        <div className="bg-white dark:bg-slate-800 p-6 rounded-xl shadow-sm border border-slate-100 dark:border-slate-700">
          <h3 className="text-lg font-semibold text-slate-900 dark:text-white mb-6">{t('dashboard.tokenUsage')}</h3>
           <div className="h-72 w-full">
            <ResponsiveContainer width="100%" height="100%">
              <BarChart data={modelUsageData} layout="vertical">
                <CartesianGrid strokeDasharray="3 3" horizontal={true} vertical={false} stroke="#f1f5f9" className="dark:opacity-10" />
                <XAxis type="number" hide />
                <YAxis 
                  dataKey="name" 
                  type="category" 
                  width={140} 
                  tick={{fill: '#64748b', fontSize: 12}} 
                  axisLine={false} 
                  tickLine={false}
                  tickFormatter={(value) => value.length > 20 ? `${value.substring(0, 18)}...` : value}
                />
                <Tooltip cursor={{fill: 'transparent'}} contentStyle={{borderRadius: '8px', color: '#000'}} />
                <Bar dataKey="tokens" fill="#8b5cf6" radius={[0, 4, 4, 0]} barSize={32} />
              </BarChart>
            </ResponsiveContainer>
          </div>
        </div>
      </SlideInItem>
      
      {/* Provider Ranking Table with Metric Switcher */}
      <SlideInItem delay={600} className="bg-white dark:bg-slate-800 rounded-xl shadow-sm border border-slate-100 dark:border-slate-700 overflow-hidden">
        <div className="px-6 py-5 border-b border-slate-100 dark:border-slate-700 flex flex-col sm:flex-row justify-between items-start sm:items-center gap-4">
            <h3 className="font-semibold text-slate-900 dark:text-white">{t('dashboard.ranking.title')}</h3>
            
            <div className="flex p-1 bg-slate-100 dark:bg-slate-700/50 rounded-lg overflow-x-auto max-w-full">
                {metrics.map((m) => (
                    <button
                        key={m}
                        onClick={() => setMetric(m)}
                        className={`px-3 py-1.5 text-xs font-medium rounded-md whitespace-nowrap transition-all ${
                            metric === m 
                            ? 'bg-white dark:bg-slate-600 text-indigo-600 dark:text-indigo-300 shadow-sm' 
                            : 'text-slate-500 dark:text-slate-400 hover:text-slate-700 dark:hover:text-slate-200'
                        }`}
                    >
                        {t(`dashboard.ranking.options.${m}`)}
                    </button>
                ))}
            </div>
        </div>
        <div className="overflow-x-auto">
            <table className="w-full text-sm text-left">
                <thead className="bg-slate-50 dark:bg-slate-900/50 text-slate-500 dark:text-slate-400 font-medium">
                    <tr>
                        <th className="px-6 py-3 w-20">{t('dashboard.ranking.columns.rank')}</th>
                        <th className="px-6 py-3">{t('dashboard.ranking.columns.provider')}</th>
                        <th className="px-6 py-3">{t(`dashboard.ranking.options.${metric}`)}</th>
                        <th className="px-6 py-3">{t('dashboard.ranking.columns.status')}</th>
                    </tr>
                </thead>
                <tbody className="divide-y divide-slate-100 dark:divide-slate-700">
                    {providerRanking.length === 0 ? (
                        <tr>
                            <td colSpan={4} className="px-6 py-4 text-center text-slate-500 dark:text-slate-400">
                                No data available
                            </td>
                        </tr>
                    ) : (
                        sortedData.map((provider, index) => {
                            const statusInfo = getStatusInfo(provider, metric);
                            return (
                                <tr key={provider.providerId} className="hover:bg-slate-50/50 dark:hover:bg-slate-700/30 transition-colors">
                                    <td className="px-6 py-3 font-medium text-slate-500 dark:text-slate-400">#{index + 1}</td>
                                    <td className="px-6 py-3 font-medium text-slate-800 dark:text-slate-200">
                                        {provider.providerName}
                                    </td>
                                    <td className="px-6 py-3 text-slate-600 dark:text-slate-300">
                                        <div className="flex items-center gap-3">
                                            <span className="font-mono w-20">{formatValue(provider)}</span>
                                            <div className="w-24 bg-slate-100 dark:bg-slate-700 rounded-full h-1.5 overflow-hidden hidden sm:block">
                                                <div 
                                                    className={`h-full ${getProgressBarColor(provider)}`} 
                                                    style={{ width: getProgressBarWidth(provider, maxValue) }}
                                                ></div>
                                            </div>
                                        </div>
                                    </td>
                                    <td className="px-6 py-3">
                                        {statusInfo ? (
                                            <span className={`inline-flex items-center px-2 py-0.5 rounded text-xs font-medium ${statusInfo.className}`}>
                                                {statusInfo.label}
                                            </span>
                                        ) : (
                                            <span className="text-slate-400">-</span>
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