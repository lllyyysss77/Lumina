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
  Bar,
  Cell
} from 'recharts';
import { ArrowUpRight, ArrowDownRight, Zap, Coins, Clock, Activity, BarChart3 } from 'lucide-react';
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
}> = ({ title, value, trend, trendDirection, trendPositive, icon: Icon }) => (
  <div className="bg-white dark:bg-[#1a1a1a] rounded-2xl p-6 shadow-card border border-gray-200/60 dark:border-gray-800 hover:shadow-soft transition-all duration-300 group">
    <div className="flex items-center justify-between mb-4">
      <div className="p-2.5 rounded-xl bg-gray-50 dark:bg-gray-800 text-gray-900 dark:text-gray-100 group-hover:scale-105 transition-transform duration-300">
        <Icon size={20} strokeWidth={2} />
      </div>
      <div className={`flex items-center px-2 py-1 rounded-lg text-xs font-semibold ${trendPositive ? 'bg-green-50 text-green-700 dark:bg-green-900/20 dark:text-green-400' : 'bg-red-50 text-red-700 dark:bg-red-900/20 dark:text-red-400'}`}>
        {trendDirection === 'up' ? <ArrowUpRight size={12} className="mr-1" /> : <ArrowDownRight size={12} className="mr-1" />}
        {trend}
      </div>
    </div>
    <h3 className="text-gray-500 dark:text-gray-400 text-xs font-medium uppercase tracking-wide">{title}</h3>
    <p className="text-2xl font-bold text-gray-900 dark:text-white mt-1 tracking-tight">{value}</p>
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
          if (item.successRate >= 99) return 'bg-emerald-500';
          if (item.successRate >= 95) return 'bg-amber-500'; 
          return 'bg-red-500';
      }
      if (metric === 'latency') {
          if (item.avgLatency <= 1000) return 'bg-emerald-500';
          if (item.avgLatency <= 3000) return 'bg-amber-500';
          return 'bg-red-500';
      }
      return 'bg-gray-800 dark:bg-gray-200'; // Monochrome bar for standard stats
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
    if (currentMetric === 'calls') {
      if (item.callCount < 50) return { label: t('dashboard.ranking.status.observation'), className: 'bg-gray-100 dark:bg-gray-800 text-gray-600 dark:text-gray-300' };
      if (item.callCount <= 1000) return { label: t('dashboard.ranking.status.normal'), className: 'bg-blue-50 dark:bg-blue-900/30 text-blue-700 dark:text-blue-300' };
      return { label: t('dashboard.ranking.status.active'), className: 'bg-green-50 dark:bg-green-900/30 text-green-700 dark:text-green-300' };
    }
    
    if (currentMetric === 'cost') return null;
    
    if (currentMetric === 'latency') {
        if (item.avgLatency <= 15000) return { label: t('dashboard.ranking.status.excellent'), className: 'bg-emerald-50 dark:bg-emerald-900/30 text-emerald-700 dark:text-emerald-300' };
        if (item.avgLatency <= 35000) return { label: t('dashboard.ranking.status.normal'), className: 'bg-amber-50 dark:bg-amber-900/30 text-amber-700 dark:text-amber-300' };
        return { label: t('dashboard.ranking.status.slow'), className: 'bg-red-50 dark:bg-red-900/30 text-red-700 dark:text-red-300' };
    }
    
    if (currentMetric === 'successRate') {
        if (item.callCount === 0) return { label: t('dashboard.ranking.status.observation'), className: 'bg-gray-100 dark:bg-gray-800 text-gray-600 dark:text-gray-300' };
        if (item.successRate >= 99) return { label: t('dashboard.ranking.status.excellent'), className: 'bg-emerald-50 dark:bg-emerald-900/30 text-emerald-700 dark:text-emerald-300' };
        if (item.successRate >= 95) return { label: t('dashboard.ranking.status.normal'), className: 'bg-blue-50 dark:bg-blue-900/30 text-blue-700 dark:text-blue-300' };
        return { label: t('dashboard.ranking.status.abnormal'), className: 'bg-red-50 dark:bg-red-900/30 text-red-700 dark:text-red-300' };
    }
    
    return null;
  };

  const sortedData = getSortedData();
  const maxValue = Math.max(...providerRanking.map(c => {
      if(metric === 'calls') return c.callCount;
      if(metric === 'cost') return c.estimatedCost;
      if(metric === 'latency') return c.avgLatency;
      return 100;
  }), 1);

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
    <div className="space-y-8">
      <SlideInItem>
        <div className="mb-8">
            <h1 className="text-3xl font-bold text-gray-900 dark:text-white tracking-tight">{t('dashboard.title')}</h1>
            <p className="text-gray-500 dark:text-gray-400 mt-1">{t('dashboard.subtitle')}</p>
        </div>
      </SlideInItem>

      {/* KPI Grid */}
      <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-4 gap-4">
        {[
          {
            title: t('dashboard.totalRequests'),
            value: safeOverview.totalRequests.toLocaleString(),
            trend: `${safeOverview.requestGrowthRate.toFixed(1)}%`,
            trendDirection: safeOverview.requestGrowthRate >= 0 ? 'up' : 'down' as 'up'|'down',
            trendPositive: safeOverview.requestGrowthRate >= 0,
            icon: Zap
          },
          {
            title: t('dashboard.totalCost'),
            value: `$${safeOverview.totalCost.toFixed(4)}`,
            trend: `${safeOverview.costGrowthRate.toFixed(1)}%`,
            trendDirection: safeOverview.costGrowthRate >= 0 ? 'up' : 'down' as 'up'|'down',
            trendPositive: true,
            icon: Coins
          },
          {
            title: t('dashboard.avgLatency'),
            value: `${safeOverview.avgLatency.toFixed(0)}ms`,
            trend: `${Math.abs(safeOverview.latencyChange).toFixed(0)}ms`,
            trendDirection: safeOverview.latencyChange >= 0 ? 'up' : 'down' as 'up'|'down',
            trendPositive: safeOverview.latencyChange <= 0,
            icon: Clock
          },
          {
            title: t('dashboard.successRate'),
            value: `${safeOverview.successRate.toFixed(1)}%`,
            trend: `${safeOverview.successRateChange.toFixed(1)}%`,
            trendDirection: safeOverview.successRateChange >= 0 ? 'up' : 'down' as 'up'|'down',
            trendPositive: safeOverview.successRateChange >= 0,
            icon: Activity
          }
        ].map((item, index) => (
            <SlideInItem key={index} index={index} delay={index * 50}>
                <StatCard {...item} />
            </SlideInItem>
        ))}
      </div>

      {/* Charts Row */}
      <SlideInItem delay={200} className="grid grid-cols-1 lg:grid-cols-3 gap-6">
        {/* Main Traffic Chart */}
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
                    <stop offset="5%" stopColor="#6366f1" stopOpacity={0.1}/>
                    <stop offset="95%" stopColor="#6366f1" stopOpacity={0}/>
                  </linearGradient>
                </defs>
                <CartesianGrid strokeDasharray="3 3" vertical={false} stroke="#E5E7EB" className="dark:stroke-gray-800" />
                <XAxis dataKey="time" axisLine={false} tickLine={false} tick={{fill: '#9CA3AF', fontSize: 11}} dy={10} />
                <YAxis axisLine={false} tickLine={false} tick={{fill: '#9CA3AF', fontSize: 11}} />
                <Tooltip 
                  contentStyle={{
                    borderRadius: '8px', 
                    border: '1px solid #E5E7EB', 
                    boxShadow: '0 4px 6px -1px rgba(0, 0, 0, 0.05)',
                    backgroundColor: '#fff',
                    color: '#111827',
                    padding: '8px 12px'
                  }}
                  cursor={{stroke: '#9CA3AF', strokeWidth: 1, strokeDasharray: '4 4'}}
                />
                <Area type="monotone" dataKey="requests" stroke="#6366f1" strokeWidth={2} fillOpacity={1} fill="url(#colorRequests)" />
              </AreaChart>
            </ResponsiveContainer>
          </div>
        </div>

        {/* Top Models Chart */}
        <div className="bg-white dark:bg-[#1a1a1a] p-6 rounded-2xl shadow-card border border-gray-200/60 dark:border-gray-800">
          <h3 className="text-base font-semibold text-gray-900 dark:text-white mb-6 flex items-center gap-2">
              <BarChart3 size={18} className="text-gray-400" />
              {t('dashboard.tokenUsage')}
          </h3>
           <div className="h-72 w-full">
            <ResponsiveContainer width="100%" height="100%">
              <BarChart data={modelUsageData} layout="vertical" barSize={20}>
                <CartesianGrid strokeDasharray="3 3" horizontal={true} vertical={false} stroke="#E5E7EB" className="dark:stroke-gray-800" />
                <XAxis type="number" hide />
                <YAxis 
                  dataKey="name" 
                  type="category" 
                  width={110} 
                  tick={{fill: '#6B7280', fontSize: 11, fontWeight: 500}} 
                  axisLine={false} 
                  tickLine={false}
                  tickFormatter={(value) => value.length > 15 ? `${value.substring(0, 15)}...` : value}
                />
                <Tooltip 
                    cursor={{fill: 'transparent'}} 
                    contentStyle={{borderRadius: '8px', border: '1px solid #E5E7EB', boxShadow: '0 4px 6px -1px rgba(0, 0, 0, 0.05)', backgroundColor: '#fff', color: '#000'}}
                />
                <Bar dataKey="tokens" radius={[0, 4, 4, 0]}>
                    {modelUsageData.map((entry, index) => (
                         <Cell key={`cell-${index}`} fill={index % 2 === 0 ? '#4B5563' : '#9CA3AF'} />
                    ))}
                </Bar>
              </BarChart>
            </ResponsiveContainer>
          </div>
        </div>
      </SlideInItem>
      
      {/* Provider Ranking Table */}
      <SlideInItem delay={400} className="bg-white dark:bg-[#1a1a1a] rounded-2xl shadow-card border border-gray-200/60 dark:border-gray-800 overflow-hidden">
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
                            const statusInfo = getStatusInfo(provider, metric);
                            return (
                                <tr key={provider.providerId} className="hover:bg-gray-50 dark:hover:bg-gray-800/50 transition-colors group">
                                    <td className="px-6 py-4 font-mono text-xs text-gray-400 dark:text-gray-500">#{index + 1}</td>
                                    <td className="px-6 py-4 font-medium text-gray-900 dark:text-white">
                                        {provider.providerName}
                                    </td>
                                    <td className="px-6 py-4 text-gray-600 dark:text-gray-300">
                                        <div className="flex items-center gap-4">
                                            <span className="font-mono text-xs w-20 text-right">{formatValue(provider)}</span>
                                            <div className="w-24 h-1.5 bg-gray-100 dark:bg-gray-700 rounded-full overflow-hidden hidden sm:block">
                                                <div 
                                                    className={`h-full rounded-full transition-all duration-500 ${getProgressBarColor(provider)}`} 
                                                    style={{ width: getProgressBarWidth(provider, maxValue) }}
                                                ></div>
                                            </div>
                                        </div>
                                    </td>
                                    <td className="px-6 py-4">
                                        {statusInfo ? (
                                            <span className={`inline-flex items-center px-2 py-0.5 rounded-md text-[10px] font-bold ${statusInfo.className}`}>
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