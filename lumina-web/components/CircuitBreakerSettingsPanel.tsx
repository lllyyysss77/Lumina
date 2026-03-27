import React, { useEffect, useState } from 'react';
import { Activity, AlertTriangle, ChevronDown, ChevronUp, Loader2, ShieldAlert, ZapOff } from 'lucide-react';
import { circuitBreakerService } from '../services/circuitBreakerService';
import { CircuitBreakerRecentEvent, CircuitBreakerStatus, CircuitState } from '../types';
import { useLanguage } from './LanguageContext';
import { useAuth } from './AuthContext';
import { Pagination } from './Pagination';

interface CircuitBreakerSettingsPanelProps {
  showToast: (message: string, type?: 'success' | 'error' | 'info') => void;
}

const formatTimestamp = (timestamp?: number) => {
  if (!timestamp) {
    return '-';
  }
  return new Date(timestamp).toLocaleString();
};

const formatIsoTimestamp = (value?: string) => {
  if (!value) {
    return '-';
  }
  return new Date(value).toLocaleString();
};

const getStateClassName = (state: CircuitState) => {
  switch (state) {
    case 'CLOSED':
      return 'bg-emerald-100 text-emerald-700 dark:bg-emerald-900/30 dark:text-emerald-400 border-emerald-200 dark:border-emerald-800';
    case 'OPEN':
      return 'bg-red-100 text-red-700 dark:bg-red-900/30 dark:text-red-400 border-red-200 dark:border-red-800';
    case 'HALF_OPEN':
      return 'bg-amber-100 text-amber-700 dark:bg-amber-900/30 dark:text-amber-400 border-amber-200 dark:border-amber-800';
    default:
      return 'bg-gray-100 text-gray-700 dark:bg-gray-800 dark:text-gray-400 border-gray-200 dark:border-gray-700';
  }
};

const getSourceLabel = (source: string | undefined, t: (key: string) => string) => {
  const key = `settings.circuitBreaker.sources.${source || 'global'}`;
  const value = t(key);
  return value === key ? source || 'global' : value;
};

const formatThresholdSummary = (cb: CircuitBreakerStatus, t: (key: string) => string) => {
  if (cb.mixedConfig) {
    return t('settings.circuitBreaker.details.mixed');
  }
  if (!cb.effectiveConfig) {
    return '-';
  }
  const config = cb.effectiveConfig;
  return `minCalls ${config.minCalls} · err ${(config.errorRateThreshold * 100).toFixed(0)}% · slow ${(config.slowRateThreshold * 100).toFixed(0)}% · bulkhead ${config.maxConcurrentRequestsPerProvider}`;
};

export const CircuitBreakerSettingsPanel: React.FC<CircuitBreakerSettingsPanelProps> = ({ showToast }) => {
  const { t } = useLanguage();
  const { user } = useAuth();
  const [circuitBreakers, setCircuitBreakers] = useState<CircuitBreakerStatus[]>([]);
  const [recentEvents, setRecentEvents] = useState<CircuitBreakerRecentEvent[]>([]);
  const [isLoading, setIsLoading] = useState(false);
  const [expandedIds, setExpandedIds] = useState<string[]>([]);
  const [pagination, setPagination] = useState({ current: 1, size: 8 });
  const [filters, setFilters] = useState({
    provider: 'ALL',
    model: 'ALL',
    state: 'ALL',
    source: 'ALL',
    manual: 'ALL',
  });
  const [selectedCB, setSelectedCB] = useState<CircuitBreakerStatus | null>(null);
  const [isModalOpen, setIsModalOpen] = useState(false);
  const [targetState, setTargetState] = useState<CircuitState>('OPEN');
  const [durationMs, setDurationMs] = useState(60000);
  const [reason, setReason] = useState('');
  const [isSaving, setIsSaving] = useState(false);

  const fetchData = async () => {
    setIsLoading(true);
    try {
      const [statusList, events] = await Promise.all([
        circuitBreakerService.getManagementList(),
        circuitBreakerService.getRecentEvents(20),
      ]);
      setCircuitBreakers(statusList);
      setRecentEvents(events);
    } catch (error) {
      console.error('Failed to fetch circuit breaker management data', error);
      showToast(t('common.fail'), 'error');
    } finally {
      setIsLoading(false);
    }
  };

  useEffect(() => {
    fetchData();
  }, []);

  const providerOptions = Array.from(
    new Set(circuitBreakers.map((item) => item.providerName || item.providerId).filter(Boolean)),
  ).sort((a, b) => a.localeCompare(b));
  const modelOptions = Array.from(
    new Set(circuitBreakers.map((item) => item.modelName).filter((value): value is string => Boolean(value))),
  ).sort((a, b) => a.localeCompare(b));

  const filteredCircuitBreakers = circuitBreakers.filter((item) => {
    const providerLabel = item.providerName || item.providerId;
    const providerMatch = filters.provider === 'ALL' || providerLabel === filters.provider;
    const modelMatch = filters.model === 'ALL' || item.modelName === filters.model;
    const stateMatch = filters.state === 'ALL' || item.circuitState === filters.state;
    const sourceMatch = filters.source === 'ALL' || (item.effectiveConfigSource || 'global') === filters.source;
    const manualMatch =
      filters.manual === 'ALL' ||
      (filters.manual === 'MANUAL' && item.manuallyControlled) ||
      (filters.manual === 'AUTO' && !item.manuallyControlled);
    return providerMatch && modelMatch && stateMatch && sourceMatch && manualMatch;
  });

  const sortedCircuitBreakers = [...filteredCircuitBreakers].sort((a, b) => {
    if (b.totalRequests !== a.totalRequests) {
      return b.totalRequests - a.totalRequests;
    }
    return b.score - a.score;
  });

  const totalPages = Math.max(1, Math.ceil(sortedCircuitBreakers.length / pagination.size));
  const currentPage = Math.min(pagination.current, totalPages);
  const pagedCircuitBreakers = sortedCircuitBreakers.slice(
    (currentPage - 1) * pagination.size,
    currentPage * pagination.size,
  );

  useEffect(() => {
    if (pagination.current !== currentPage) {
      setPagination((prev) => ({ ...prev, current: currentPage }));
    }
  }, [currentPage, pagination.current]);

  const handlePageChange = (page: number, size: number) => {
    setPagination({ current: page, size });
  };

  const handleFilterChange = (key: 'provider' | 'model' | 'state' | 'source' | 'manual', value: string) => {
    setFilters((prev) => ({ ...prev, [key]: value }));
    setPagination((prev) => ({ ...prev, current: 1 }));
  };

  const toggleExpanded = (providerId: string) => {
    setExpandedIds((prev) => (prev.includes(providerId) ? prev.filter((id) => id !== providerId) : [...prev, providerId]));
  };

  const openControlModal = (cb: CircuitBreakerStatus) => {
    setSelectedCB(cb);
    setTargetState(cb.circuitState === 'CLOSED' ? 'OPEN' : 'CLOSED');
    setDurationMs(60000);
    setReason('');
    setIsModalOpen(true);
  };

  const handleControlSubmit = async (event: React.FormEvent) => {
    event.preventDefault();
    if (!selectedCB) {
      return;
    }
    if (targetState === 'OPEN' && !reason.trim()) {
      showToast(t('settings.circuitBreaker.controlModal.reasonPlaceholder'), 'error');
      return;
    }

    setIsSaving(true);
    try {
      await circuitBreakerService.control(
        {
          providerId: selectedCB.providerId,
          targetState,
          reason: reason.trim() || undefined,
          durationMs: targetState === 'OPEN' ? durationMs : undefined,
        },
        user?.username || 'admin',
      );
      showToast(t('settings.circuitBreaker.updated'), 'success');
      setIsModalOpen(false);
      await fetchData();
    } catch (error) {
      console.error('Failed to update circuit breaker', error);
      showToast(t('common.fail'), 'error');
    } finally {
      setIsSaving(false);
    }
  };

  const handleReleaseControl = async (providerId: string) => {
    try {
      await circuitBreakerService.release(providerId, user?.username || 'admin');
      showToast(t('settings.circuitBreaker.released'), 'success');
      await fetchData();
    } catch (error) {
      console.error('Failed to release circuit breaker control', error);
      showToast(t('common.fail'), 'error');
    }
  };

  const renderWarning = () => {
    if (targetState === 'OPEN') {
      return t('settings.circuitBreaker.controlModal.warningOpen');
    }
    if (targetState === 'HALF_OPEN') {
      return t('settings.circuitBreaker.controlModal.warningHalfOpen');
    }
    return t('settings.circuitBreaker.controlModal.warningClosed');
  };

  return (
    <>
      <div className="bg-white dark:bg-[#1a1a1a] rounded-2xl border border-gray-200 dark:border-gray-800 p-6 sm:p-8 shadow-card hover:shadow-float transition-shadow duration-300">
        <div className="flex flex-col sm:flex-row sm:items-start justify-between gap-6">
          <div className="flex items-start space-x-5 w-full">
            <div className="p-3 bg-orange-50 dark:bg-orange-900/20 text-orange-600 dark:text-orange-400 rounded-xl flex-shrink-0">
              <ZapOff size={24} />
            </div>
            <div className="flex-1 min-w-0">
              <div className="flex justify-between items-center mb-5">
                <div>
                  <h3 className="text-lg font-bold text-gray-900 dark:text-white">{t('settings.circuitBreaker.title')}</h3>
                  <p className="text-sm text-gray-500 dark:text-gray-400 mt-1">{t('settings.circuitBreaker.desc')}</p>
                </div>
                <button
                  onClick={fetchData}
                  className="p-2 text-gray-500 hover:text-black dark:text-gray-400 dark:hover:text-white rounded-lg hover:bg-gray-100 dark:hover:bg-gray-800 transition-colors"
                  title={t('settings.circuitBreaker.refresh')}
                >
                  <Activity size={20} className={isLoading ? 'animate-spin' : ''} />
                </button>
              </div>

              <div className="grid grid-cols-1 md:grid-cols-2 xl:grid-cols-5 gap-3 mb-5">
                <select
                  value={filters.provider}
                  onChange={(event) => handleFilterChange('provider', event.target.value)}
                  className="rounded-xl border border-gray-200 dark:border-gray-700 bg-white dark:bg-[#111111] px-3 py-2 text-sm text-gray-900 dark:text-white"
                >
                  <option value="ALL">{t('settings.circuitBreaker.filters.allProviders')}</option>
                  {providerOptions.map((item) => (
                    <option key={item} value={item}>
                      {item}
                    </option>
                  ))}
                </select>
                <select
                  value={filters.model}
                  onChange={(event) => handleFilterChange('model', event.target.value)}
                  className="rounded-xl border border-gray-200 dark:border-gray-700 bg-white dark:bg-[#111111] px-3 py-2 text-sm text-gray-900 dark:text-white"
                >
                  <option value="ALL">{t('settings.circuitBreaker.filters.allModels')}</option>
                  {modelOptions.map((item) => (
                    <option key={item} value={item}>
                      {item}
                    </option>
                  ))}
                </select>
                <select
                  value={filters.state}
                  onChange={(event) => handleFilterChange('state', event.target.value)}
                  className="rounded-xl border border-gray-200 dark:border-gray-700 bg-white dark:bg-[#111111] px-3 py-2 text-sm text-gray-900 dark:text-white"
                >
                  <option value="ALL">{t('settings.circuitBreaker.filters.allStates')}</option>
                  <option value="CLOSED">{t('settings.circuitBreaker.states.CLOSED')}</option>
                  <option value="OPEN">{t('settings.circuitBreaker.states.OPEN')}</option>
                  <option value="HALF_OPEN">{t('settings.circuitBreaker.states.HALF_OPEN')}</option>
                </select>
                <select
                  value={filters.source}
                  onChange={(event) => handleFilterChange('source', event.target.value)}
                  className="rounded-xl border border-gray-200 dark:border-gray-700 bg-white dark:bg-[#111111] px-3 py-2 text-sm text-gray-900 dark:text-white"
                >
                  <option value="ALL">{t('settings.circuitBreaker.filters.allSources')}</option>
                  <option value="global">{t('settings.circuitBreaker.sources.global')}</option>
                  <option value="group">{t('settings.circuitBreaker.sources.group')}</option>
                  <option value="provider">{t('settings.circuitBreaker.sources.provider')}</option>
                  <option value="mixed">{t('settings.circuitBreaker.sources.mixed')}</option>
                </select>
                <select
                  value={filters.manual}
                  onChange={(event) => handleFilterChange('manual', event.target.value)}
                  className="rounded-xl border border-gray-200 dark:border-gray-700 bg-white dark:bg-[#111111] px-3 py-2 text-sm text-gray-900 dark:text-white"
                >
                  <option value="ALL">{t('settings.circuitBreaker.filters.allModes')}</option>
                  <option value="MANUAL">{t('settings.circuitBreaker.filters.manualOnly')}</option>
                  <option value="AUTO">{t('settings.circuitBreaker.filters.autoOnly')}</option>
                </select>
              </div>

              <div className="overflow-x-auto border border-gray-100 dark:border-gray-800 rounded-xl shadow-sm">
                <table className="min-w-full divide-y divide-gray-100 dark:divide-gray-800">
                  <thead className="bg-gray-50 dark:bg-gray-900">
                    <tr>
                      <th className="px-5 py-3 text-left text-xs font-bold text-gray-500 dark:text-gray-400 uppercase tracking-wider">{t('settings.circuitBreaker.table.provider')}</th>
                      <th className="px-5 py-3 text-left text-xs font-bold text-gray-500 dark:text-gray-400 uppercase tracking-wider">{t('settings.circuitBreaker.table.model')}</th>
                      <th className="px-5 py-3 text-left text-xs font-bold text-gray-500 dark:text-gray-400 uppercase tracking-wider">{t('settings.circuitBreaker.table.state')}</th>
                      <th className="px-5 py-3 text-left text-xs font-bold text-gray-500 dark:text-gray-400 uppercase tracking-wider hidden lg:table-cell">{t('settings.circuitBreaker.table.since')}</th>
                      <th className="px-5 py-3 text-left text-xs font-bold text-gray-500 dark:text-gray-400 uppercase tracking-wider hidden xl:table-cell">{t('settings.circuitBreaker.table.nextProbe')}</th>
                      <th className="px-5 py-3 text-left text-xs font-bold text-gray-500 dark:text-gray-400 uppercase tracking-wider">{t('settings.circuitBreaker.table.score')}</th>
                      <th className="px-5 py-3 text-left text-xs font-bold text-gray-500 dark:text-gray-400 uppercase tracking-wider hidden lg:table-cell">{t('settings.circuitBreaker.table.requests')}</th>
                      <th className="px-5 py-3 text-left text-xs font-bold text-gray-500 dark:text-gray-400 uppercase tracking-wider hidden xl:table-cell">{t('settings.circuitBreaker.table.concurrency')}</th>
                      <th className="px-5 py-3 text-left text-xs font-bold text-gray-500 dark:text-gray-400 uppercase tracking-wider hidden xl:table-cell">{t('settings.circuitBreaker.table.source')}</th>
                      <th className="px-5 py-3 text-right text-xs font-bold text-gray-500 dark:text-gray-400 uppercase tracking-wider">{t('settings.circuitBreaker.table.control')}</th>
                    </tr>
                  </thead>
                  <tbody className="bg-white dark:bg-[#1a1a1a] divide-y divide-gray-100 dark:divide-gray-800">
                    {isLoading && circuitBreakers.length === 0 ? (
                      <tr>
                        <td colSpan={10} className="p-6 text-center text-sm text-gray-500">
                          {t('settings.circuitBreaker.loading')}
                        </td>
                      </tr>
                    ) : sortedCircuitBreakers.length === 0 ? (
                      <tr>
                        <td colSpan={10} className="p-6 text-center text-sm text-gray-500">
                          {t('settings.circuitBreaker.empty')}
                        </td>
                      </tr>
                    ) : (
                      pagedCircuitBreakers.map((cb) => {
                        const isExpanded = expandedIds.includes(cb.providerId);
                        return (
                          <React.Fragment key={cb.providerId}>
                            <tr className="hover:bg-gray-50 dark:hover:bg-gray-800/50 transition-colors">
                              <td className="px-5 py-3.5 text-sm font-semibold text-gray-900 dark:text-white">
                                <div className="flex items-center gap-2">
                                  <button
                                    onClick={() => toggleExpanded(cb.providerId)}
                                    className="p-1 text-gray-400 hover:text-gray-700 dark:hover:text-gray-200"
                                    title={t('settings.circuitBreaker.actions.details')}
                                  >
                                    {isExpanded ? <ChevronUp size={16} /> : <ChevronDown size={16} />}
                                  </button>
                                  <div>
                                    <div>{cb.providerName}</div>
                                    {cb.manuallyControlled && (
                                      <span className="mt-1 inline-flex items-center px-1.5 py-0.5 rounded text-[10px] font-bold bg-purple-100 text-purple-700 dark:bg-purple-900/30 dark:text-purple-400 border border-purple-200 dark:border-purple-800">
                                        MANUAL
                                      </span>
                                    )}
                                  </div>
                                </div>
                              </td>
                              <td className="px-5 py-3.5 text-sm text-gray-600 dark:text-gray-300">{cb.modelName || '-'}</td>
                              <td className="px-5 py-3.5">
                                <span className={`px-2.5 py-0.5 inline-flex text-xs leading-5 font-bold rounded-full border ${getStateClassName(cb.circuitState)}`}>
                                  {t(`settings.circuitBreaker.states.${cb.circuitState}`)}
                                </span>
                              </td>
                              <td className="px-5 py-3.5 text-xs text-gray-500 dark:text-gray-400 hidden lg:table-cell">{formatTimestamp(cb.stateSinceAt)}</td>
                              <td className="px-5 py-3.5 text-xs text-gray-500 dark:text-gray-400 hidden xl:table-cell">{formatTimestamp(cb.nextProbeAt)}</td>
                              <td className="px-5 py-3.5 text-sm text-gray-500 dark:text-gray-400 font-mono">{cb.score.toFixed(1)}</td>
                              <td className="px-5 py-3.5 text-xs text-gray-500 dark:text-gray-400 hidden lg:table-cell">
                                {cb.totalRequests} / {cb.successRequests} / {cb.failureRequests}
                              </td>
                              <td className="px-5 py-3.5 text-xs text-gray-500 dark:text-gray-400 hidden xl:table-cell">
                                {cb.currentConcurrent} / {cb.maxConcurrent}
                              </td>
                              <td className="px-5 py-3.5 text-xs text-gray-500 dark:text-gray-400 hidden xl:table-cell">
                                {getSourceLabel(cb.effectiveConfigSource, t)}
                              </td>
                              <td className="px-5 py-3.5 text-right text-sm font-medium">
                                {cb.manuallyControlled ? (
                                  <button
                                    onClick={() => handleReleaseControl(cb.providerId)}
                                    className="px-3 py-1 bg-orange-50 text-orange-600 border border-orange-200 rounded-lg hover:bg-orange-100 text-xs font-semibold transition-colors dark:bg-orange-900/20 dark:text-orange-400 dark:border-orange-800 dark:hover:bg-orange-900/30"
                                  >
                                    {t('settings.circuitBreaker.actions.release')}
                                  </button>
                                ) : (
                                  <button
                                    onClick={() => openControlModal(cb)}
                                    className="px-3 py-1 bg-white text-gray-700 border border-gray-200 rounded-lg hover:bg-gray-50 text-xs font-semibold transition-colors dark:bg-gray-800 dark:text-gray-300 dark:border-gray-700 dark:hover:bg-gray-700"
                                  >
                                    {t('settings.circuitBreaker.actions.manage')}
                                  </button>
                                )}
                              </td>
                            </tr>
                            {isExpanded && (
                              <tr className="bg-gray-50/70 dark:bg-gray-900/30">
                                <td colSpan={10} className="px-5 py-4">
                                  <div className="grid grid-cols-1 xl:grid-cols-2 gap-4 text-sm">
                                    <div className="space-y-2">
                                      <div>
                                        <div className="text-xs font-bold uppercase tracking-wide text-gray-400">{t('settings.circuitBreaker.details.explanation')}</div>
                                        <div className="mt-1 text-gray-700 dark:text-gray-300">{cb.stateExplanation || '-'}</div>
                                      </div>
                                      <div>
                                        <div className="text-xs font-bold uppercase tracking-wide text-gray-400">{t('settings.circuitBreaker.details.failureType')}</div>
                                        <div className="mt-1 text-gray-700 dark:text-gray-300">{cb.lastFailureType || t('settings.circuitBreaker.details.noReason')}</div>
                                      </div>
                                      <div>
                                        <div className="text-xs font-bold uppercase tracking-wide text-gray-400">{t('settings.circuitBreaker.details.groups')}</div>
                                        <div className="mt-1 text-gray-700 dark:text-gray-300">
                                          {cb.mixedConfig
                                            ? t('settings.circuitBreaker.details.mixed')
                                            : cb.effectiveGroupNames && cb.effectiveGroupNames.length > 0
                                            ? cb.effectiveGroupNames.join(', ')
                                            : '-'}
                                        </div>
                                      </div>
                                      <div>
                                        <div className="text-xs font-bold uppercase tracking-wide text-gray-400">{t('settings.circuitBreaker.details.manualReason')}</div>
                                        <div className="mt-1 text-gray-700 dark:text-gray-300">{cb.manualControlReason || t('settings.circuitBreaker.details.noReason')}</div>
                                      </div>
                                    </div>
                                    <div className="space-y-2">
                                      <div>
                                        <div className="text-xs font-bold uppercase tracking-wide text-gray-400">{t('settings.circuitBreaker.details.thresholds')}</div>
                                        <div className="mt-1 text-gray-700 dark:text-gray-300">{formatThresholdSummary(cb, t)}</div>
                                      </div>
                                      <div className="text-xs text-gray-500 dark:text-gray-400">
                                        err {(cb.errorRate * 100).toFixed(1)}% · slow {(cb.slowRate * 100).toFixed(1)}% · latency {cb.latencyEmaMs?.toFixed(0) || 0}ms
                                      </div>
                                      <div className="text-xs text-gray-500 dark:text-gray-400">
                                        window {cb.windowTotalCount || 0} · bulkhead rejected {cb.bulkheadRejectedCount}
                                      </div>
                                      <div className="text-xs text-gray-500 dark:text-gray-400">
                                        probes {cb.probeRemaining || 0} left · half-open success/fail {cb.halfOpenSuccessCount || 0}/{cb.halfOpenFailureCount || 0}
                                      </div>
                                    </div>
                                  </div>
                                </td>
                              </tr>
                            )}
                          </React.Fragment>
                        );
                      })
                    )}
                  </tbody>
                </table>
              </div>

              {sortedCircuitBreakers.length > 0 && (
                <Pagination
                  current={currentPage}
                  size={pagination.size}
                  total={sortedCircuitBreakers.length}
                  onChange={handlePageChange}
                  className="mt-4 border-t border-gray-200/50 dark:border-gray-800/50"
                />
              )}

              <div className="mt-6 rounded-xl border border-gray-100 dark:border-gray-800 bg-gray-50/60 dark:bg-gray-900/30 p-5">
                <h4 className="text-sm font-bold text-gray-900 dark:text-white mb-4">{t('settings.circuitBreaker.recentEvents')}</h4>
                {recentEvents.length === 0 ? (
                  <div className="text-sm text-gray-500 dark:text-gray-400">{t('settings.circuitBreaker.noRecentEvents')}</div>
                ) : (
                  <div className="space-y-3">
                    {recentEvents.map((event) => (
                      <div key={`${event.timestamp}-${event.providerId}-${event.action}`} className="flex flex-col lg:flex-row lg:items-center lg:justify-between gap-2 rounded-xl bg-white dark:bg-[#1a1a1a] border border-gray-100 dark:border-gray-800 px-4 py-3">
                        <div>
                          <div className="flex items-center gap-2 text-sm font-semibold text-gray-900 dark:text-white">
                            <span className={`inline-flex items-center px-2 py-0.5 rounded-full text-[10px] font-bold ${event.action === 'release' ? 'bg-orange-100 text-orange-700 dark:bg-orange-900/30 dark:text-orange-300' : 'bg-indigo-100 text-indigo-700 dark:bg-indigo-900/30 dark:text-indigo-300'}`}>
                              {t(`settings.circuitBreaker.events.${event.action}`)}
                            </span>
                            <span>{event.providerName}</span>
                            <span className="text-gray-400">/</span>
                            <span className="text-gray-500 dark:text-gray-400">{event.modelName || '-'}</span>
                          </div>
                          <div className="mt-1 text-xs text-gray-500 dark:text-gray-400">
                            {event.fromState} → {event.toState} · {event.reason || t('settings.circuitBreaker.details.noReason')}
                          </div>
                        </div>
                        <div className="text-xs text-gray-500 dark:text-gray-400">
                          {event.operator || 'system'} · {formatIsoTimestamp(event.timestamp)}
                        </div>
                      </div>
                    ))}
                  </div>
                )}
              </div>
            </div>
          </div>
        </div>
      </div>

      {isModalOpen && selectedCB && (
        <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/20 backdrop-blur-sm p-4 animate-fade-in">
          <div className="bg-white dark:bg-[#1a1a1a] rounded-2xl shadow-float max-w-lg w-full animate-in zoom-in-95 duration-200 border border-gray-100 dark:border-gray-800">
            <div className="px-6 py-4 border-b border-gray-100 dark:border-gray-800 flex justify-between items-center bg-white/95 dark:bg-[#1a1a1a]/95 backdrop-blur rounded-t-2xl">
              <h3 className="text-lg font-bold text-gray-900 dark:text-white">{t('settings.circuitBreaker.controlModal.title')}</h3>
              <button onClick={() => setIsModalOpen(false)} className="text-gray-400 hover:text-gray-600 dark:hover:text-gray-300 rounded-full p-1 hover:bg-gray-100 dark:hover:bg-gray-800">
                <ChevronUp size={20} className="rotate-45" />
              </button>
            </div>
            <div className="p-6">
              <div className="mb-6">
                <span className="text-xs font-bold text-gray-500 dark:text-gray-400 uppercase tracking-wide">{t('settings.circuitBreaker.targetProvider')}</span>
                <p className="text-lg font-bold text-gray-900 dark:text-white mt-1">
                  {selectedCB.providerName} / {selectedCB.modelName || '-'}
                </p>
                {selectedCB.manuallyControlled && (
                  <div className="mt-3 flex items-start gap-2 text-xs bg-purple-50 text-purple-800 dark:bg-purple-900/30 dark:text-purple-300 p-3 rounded-xl border border-purple-100 dark:border-purple-800/50">
                    <ShieldAlert size={14} className="mt-0.5" />
                    <div>
                      <strong className="block mb-1 font-bold">{t('settings.circuitBreaker.controlModal.manualActive')}</strong>
                      {selectedCB.manualControlReason || t('settings.circuitBreaker.controlModal.releaseHint')}
                    </div>
                  </div>
                )}
              </div>

              <div className="grid grid-cols-1 sm:grid-cols-3 gap-3 mb-5">
                <div className="rounded-xl border border-gray-100 dark:border-gray-800 bg-gray-50 dark:bg-gray-900/40 p-3">
                  <div className="text-xs text-gray-400">{t('settings.circuitBreaker.controlModal.currentState')}</div>
                  <div className="mt-1 font-semibold text-gray-900 dark:text-white">{t(`settings.circuitBreaker.states.${selectedCB.circuitState}`)}</div>
                </div>
                <div className="rounded-xl border border-gray-100 dark:border-gray-800 bg-gray-50 dark:bg-gray-900/40 p-3">
                  <div className="text-xs text-gray-400">{t('settings.circuitBreaker.controlModal.currentScore')}</div>
                  <div className="mt-1 font-semibold text-gray-900 dark:text-white">{selectedCB.score.toFixed(1)}</div>
                </div>
                <div className="rounded-xl border border-gray-100 dark:border-gray-800 bg-gray-50 dark:bg-gray-900/40 p-3">
                  <div className="text-xs text-gray-400">{t('settings.circuitBreaker.controlModal.currentLoad')}</div>
                  <div className="mt-1 font-semibold text-gray-900 dark:text-white">{selectedCB.currentConcurrent} / {selectedCB.maxConcurrent}</div>
                </div>
              </div>

              <div className="mb-5 flex items-start gap-2 text-xs bg-amber-50 text-amber-800 dark:bg-amber-900/20 dark:text-amber-300 p-3 rounded-xl border border-amber-100 dark:border-amber-900/40">
                <AlertTriangle size={14} className="mt-0.5" />
                <div>{renderWarning()}</div>
              </div>

              <form onSubmit={handleControlSubmit} className="space-y-5">
                <div>
                  <label className="block text-sm font-semibold text-gray-700 dark:text-gray-300 mb-1.5">{t('settings.circuitBreaker.controlModal.targetState')}</label>
                  <select
                    value={targetState}
                    onChange={(event) => setTargetState(event.target.value as CircuitState)}
                    className="block w-full rounded-xl border-gray-200 dark:border-gray-700 shadow-sm text-sm py-2.5 px-3 bg-white dark:bg-gray-950 dark:text-white"
                  >
                    <option value="CLOSED">{t('settings.circuitBreaker.states.CLOSED')}</option>
                    <option value="OPEN">{t('settings.circuitBreaker.states.OPEN')}</option>
                    <option value="HALF_OPEN">{t('settings.circuitBreaker.states.HALF_OPEN')}</option>
                  </select>
                </div>

                {targetState === 'OPEN' && (
                  <div>
                    <label className="block text-sm font-semibold text-gray-700 dark:text-gray-300 mb-1.5">{t('settings.circuitBreaker.controlModal.duration')}</label>
                    <input
                      type="number"
                      value={durationMs}
                      onChange={(event) => setDurationMs(parseInt(event.target.value, 10) || 0)}
                      className="block w-full rounded-xl border-gray-200 dark:border-gray-700 shadow-sm text-sm py-2.5 px-3 bg-white dark:bg-gray-950 dark:text-white"
                    />
                  </div>
                )}

                <div>
                  <label className="block text-sm font-semibold text-gray-700 dark:text-gray-300 mb-1.5">{t('settings.circuitBreaker.controlModal.reason')}</label>
                  <textarea
                    rows={3}
                    value={reason}
                    onChange={(event) => setReason(event.target.value)}
                    placeholder={t('settings.circuitBreaker.controlModal.reasonPlaceholder')}
                    className="block w-full rounded-xl border-gray-200 dark:border-gray-700 shadow-sm text-sm py-2.5 px-3 bg-white dark:bg-gray-950 dark:text-white resize-none"
                  />
                </div>

                <div className="pt-4 flex justify-end gap-3">
                  <button
                    type="button"
                    onClick={() => setIsModalOpen(false)}
                    className="px-4 py-2.5 border border-gray-200 dark:border-gray-700 shadow-sm text-sm font-semibold rounded-xl text-gray-700 dark:text-gray-200 bg-white dark:bg-gray-800 hover:bg-gray-50 dark:hover:bg-gray-700 transition-colors"
                  >
                    {t('common.cancel')}
                  </button>
                  <button
                    type="submit"
                    disabled={isSaving}
                    className="px-4 py-2.5 bg-gray-900 hover:bg-black dark:bg-white dark:hover:bg-gray-200 text-white dark:text-black text-sm font-semibold rounded-xl shadow-sm flex items-center disabled:opacity-70 disabled:cursor-not-allowed"
                  >
                    {isSaving ? <Loader2 size={18} className="animate-spin mr-2" /> : null}
                    {t('settings.circuitBreaker.controlModal.confirm')}
                  </button>
                </div>
              </form>
            </div>
          </div>
        </div>
      )}
    </>
  );
};
