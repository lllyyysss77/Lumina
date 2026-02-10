import React, { useState, useEffect, useRef } from 'react';
import { Search, Filter, Download, Loader2, ChevronLeft, ChevronRight, X, Copy, Check, Eye, RefreshCw, Clock, ScrollText } from 'lucide-react';
import { useLanguage } from './LanguageContext';
import { logService, LogDetail } from '../services/logService';
import { LogEntry } from '../types';
import { TableSkeleton } from './Skeletons';
import { SlideInItem } from './Animations';

export const Logs: React.FC = () => {
  const { t } = useLanguage();

  // State
  const [logs, setLogs] = useState<LogEntry[]>([]);
  const [isLoading, setIsLoading] = useState(false);
  const [pagination, setPagination] = useState({
    current: 1,
    size: 10,
    total: 0,
    pages: 0
  });

  // Auto Refresh State
  const [isAutoRefresh, setIsAutoRefresh] = useState(() => {
    const saved = localStorage.getItem('lumina_logs_auto_refresh');
    return saved === 'true';
  });
  const [refreshInterval, setRefreshInterval] = useState(() => {
    const saved = localStorage.getItem('lumina_logs_refresh_interval');
    const parsed = saved ? parseInt(saved, 10) : 30000;
    return isNaN(parsed) ? 30000 : parsed;
  });

  // Modal State
  const [isDetailOpen, setIsDetailOpen] = useState(false);
  const [selectedLog, setSelectedLog] = useState<LogDetail | null>(null);
  const [isDetailLoading, setIsDetailLoading] = useState(false);
  const [copyFeedback, setCopyFeedback] = useState<string | null>(null);

  // Fetch Logs
  // Added isBackground parameter to avoid full loading spinner during auto-refresh
  const fetchLogs = async (page: number, size: number, isBackground = false) => {
    if (!isBackground) setIsLoading(true);
    try {
      const data = await logService.getPage(page, size);
      setLogs(data.records);
      setPagination({
        current: data.current,
        size: data.size,
        total: data.total,
        pages: data.pages
      });
    } catch (error) {
      console.error("Failed to fetch logs:", error);
    } finally {
      if (!isBackground) setIsLoading(false);
    }
  };

  useEffect(() => {
    fetchLogs(pagination.current, pagination.size);
  }, []); // Initial load

  // Persist Auto Refresh State
  useEffect(() => {
    localStorage.setItem('lumina_logs_auto_refresh', String(isAutoRefresh));
  }, [isAutoRefresh]);

  // Persist Refresh Interval
  useEffect(() => {
    localStorage.setItem('lumina_logs_refresh_interval', String(refreshInterval));
  }, [refreshInterval]);

  // Auto Refresh Logic
  useEffect(() => {
    let intervalId: ReturnType<typeof setInterval>;

    if (isAutoRefresh) {
      intervalId = setInterval(() => {
        fetchLogs(pagination.current, pagination.size, true);
      }, refreshInterval);
    }

    return () => {
      if (intervalId) clearInterval(intervalId);
    };
  }, [isAutoRefresh, refreshInterval, pagination.current, pagination.size]);

  const handlePageChange = (newPage: number) => {
    if (newPage > 0 && newPage <= pagination.pages) {
        fetchLogs(newPage, pagination.size);
    }
  };

  const handleSizeChange = (e: React.ChangeEvent<HTMLSelectElement>) => {
      const newSize = parseInt(e.target.value);
      fetchLogs(1, newSize); // Reset to page 1 on size change
  };

  const handleRefreshClick = () => {
      fetchLogs(pagination.current, pagination.size);
  }

  const handleViewLog = async (id: string) => {
      setIsDetailLoading(true);
      setIsDetailOpen(true);
      setSelectedLog(null); // Clear previous data
      try {
          const detail = await logService.getDetail(id);
          setSelectedLog(detail);
      } catch (error) {
          console.error("Failed to fetch details", error);
          // Optionally handle error state in modal
      } finally {
          setIsDetailLoading(false);
      }
  };

  const handleCopy = (text: string, key: string) => {
    navigator.clipboard.writeText(text);
    setCopyFeedback(key);
    setTimeout(() => setCopyFeedback(null), 2000);
  };

  const formatContent = (content: string) => {
      try {
          // Attempt to parse JSON to pretty print
          const obj = JSON.parse(content);
          return JSON.stringify(obj, null, 2);
      } catch {
          return content;
      }
  };

  return (
    <div className="space-y-6 relative">
        <div className="flex flex-col sm:flex-row justify-between items-start sm:items-center gap-4">
            <div>
            <h1 className="text-3xl font-extrabold text-gray-900 dark:text-white tracking-tight">{t('logs.title')}</h1>
            <p className="text-gray-500 dark:text-gray-400 mt-2 text-lg">{t('logs.subtitle')}</p>
            </div>
            <div className="flex space-x-3">
                <button
                    onClick={handleRefreshClick}
                    className="flex items-center justify-center w-10 h-10 bg-white dark:bg-[#1a1a1a] border border-gray-200 dark:border-gray-800 rounded-xl text-gray-600 dark:text-gray-300 hover:bg-gray-50 dark:hover:bg-gray-800 hover:text-black dark:hover:text-white shadow-sm transition-all active:scale-95"
                    title="Refresh"
                >
                    <RefreshCw size={18} className={`${isLoading ? 'animate-spin' : ''}`} />
                </button>
                <div className="flex items-center bg-white dark:bg-[#1a1a1a] border border-gray-200 dark:border-gray-800 rounded-xl shadow-sm overflow-hidden">
                    <button
                        onClick={() => setIsAutoRefresh(!isAutoRefresh)}
                        className={`flex items-center px-4 py-2 text-sm font-semibold transition-colors h-10 ${isAutoRefresh ? 'bg-green-50 dark:bg-green-900/30 text-green-600 dark:text-green-400' : 'text-gray-600 dark:text-gray-300 hover:bg-gray-50 dark:hover:bg-gray-800'}`}
                    >
                        <Clock size={16} className="mr-2" />
                        {t('logs.autoRefresh')}
                    </button>
                    {isAutoRefresh && (
                        <div className="border-l border-gray-200 dark:border-gray-700 h-full flex items-center">
                             <select
                                value={refreshInterval}
                                onChange={(e) => setRefreshInterval(Number(e.target.value))}
                                className="block w-full py-0 pl-2 pr-7 text-xs font-medium border-none focus:ring-0 bg-transparent text-gray-600 dark:text-gray-300 cursor-pointer h-full"
                            >
                                <option value={5000}>5{t('logs.seconds')}</option>
                                <option value={10000}>10{t('logs.seconds')}</option>
                                <option value={30000}>30{t('logs.seconds')}</option>
                                <option value={60000}>60{t('logs.seconds')}</option>
                            </select>
                        </div>
                    )}
                </div>
                <button className="flex items-center px-4 py-2 bg-white dark:bg-[#1a1a1a] border border-gray-200 dark:border-gray-800 rounded-xl text-sm font-semibold text-gray-700 dark:text-gray-200 hover:bg-gray-50 dark:hover:bg-gray-800 shadow-sm transition-all">
                    <Download size={18} className="mr-2" />
                    {t('common.export')}
                </button>
            </div>
        </div>

        {/* Search & Filter Bar */}
        <div className="bg-white dark:bg-[#1a1a1a] p-4 rounded-2xl border border-gray-200 dark:border-gray-800 shadow-sm flex flex-col md:flex-row gap-4">
            <div className="relative flex-1">
                <div className="absolute inset-y-0 left-0 pl-4 flex items-center pointer-events-none">
                    <Search size={18} className="text-gray-400" />
                </div>
                <input
                    type="text"
                    className="block w-full pl-11 pr-4 py-2.5 border border-gray-200 dark:border-gray-700 rounded-xl leading-5 bg-gray-50 dark:bg-gray-900 text-gray-900 dark:text-white placeholder-gray-400 focus:outline-none focus:ring-2 focus:ring-black dark:focus:ring-white focus:border-transparent transition-all sm:text-sm"
                    placeholder={t('logs.searchPlaceholder')}
                />
            </div>
            <div className="flex gap-3">
                <button className="flex items-center px-4 py-2.5 border border-gray-200 dark:border-gray-700 rounded-xl text-sm font-semibold text-gray-700 dark:text-gray-200 bg-white dark:bg-gray-800 hover:bg-gray-50 dark:hover:bg-gray-700 transition-colors">
                    <Filter size={16} className="mr-2" />
                    {t('common.filter')}
                </button>
                <select
                    value={pagination.size}
                    onChange={handleSizeChange}
                    className="block w-40 pl-4 pr-10 py-2.5 text-sm font-medium border-gray-200 dark:border-gray-700 focus:outline-none focus:ring-2 focus:ring-black dark:focus:ring-white focus:border-transparent rounded-xl border bg-white dark:bg-[#1a1a1a] text-gray-900 dark:text-white cursor-pointer"
                >
                    <option value="10">10 / page</option>
                    <option value="20">20 / page</option>
                    <option value="50">50 / page</option>
                    <option value="100">100 / page</option>
                </select>
            </div>
        </div>

        {/* Logs Table */}
        <div className="bg-white dark:bg-[#1a1a1a] rounded-2xl shadow-sm border border-gray-200 dark:border-gray-800 overflow-hidden">
            {isLoading && logs.length === 0 ? (
               <div className="p-6">
                 <TableSkeleton rows={10} />
               </div>
            ) : (
                <>
                <div className="overflow-x-auto">
                    <table className="min-w-full divide-y divide-gray-100 dark:divide-gray-800">
                        <thead className="bg-gray-50/50 dark:bg-gray-900/30">
                            <tr>
                                <th scope="col" className="px-6 py-4 text-left text-xs font-bold text-gray-500 dark:text-gray-400 uppercase tracking-wider">{t('logs.table.status')}</th>
                                <th scope="col" className="px-6 py-4 text-left text-xs font-bold text-gray-500 dark:text-gray-400 uppercase tracking-wider">{t('logs.table.time')}</th>
                                <th scope="col" className="px-6 py-4 text-left text-xs font-bold text-gray-500 dark:text-gray-400 uppercase tracking-wider">{t('logs.table.requestModel')}</th>
                                <th scope="col" className="px-6 py-4 text-left text-xs font-bold text-gray-500 dark:text-gray-400 uppercase tracking-wider">{t('logs.table.actualModel')}</th>
                                <th scope="col" className="px-6 py-4 text-left text-xs font-bold text-gray-500 dark:text-gray-400 uppercase tracking-wider">{t('logs.table.provider')}</th>
                                <th scope="col" className="px-6 py-4 text-left text-xs font-bold text-gray-500 dark:text-gray-400 uppercase tracking-wider">{t('logs.table.latency')}</th>
                                <th scope="col" className="px-6 py-4 text-left text-xs font-bold text-gray-500 dark:text-gray-400 uppercase tracking-wider">{t('logs.table.tokens')}</th>
                                <th scope="col" className="px-6 py-4 text-left text-xs font-bold text-gray-500 dark:text-gray-400 uppercase tracking-wider">{t('logs.table.cost')}</th>
                                <th scope="col" className="relative px-6 py-4">
                                    <span className="sr-only">{t('common.details')}</span>
                                </th>
                            </tr>
                        </thead>
                        <tbody className="bg-transparent divide-y divide-gray-100 dark:divide-gray-800">
                            {logs.length === 0 ? (
                                <tr>
                                    <td colSpan={9} className="px-6 py-16 text-center">
                                        <div className="flex flex-col items-center justify-center">
                                            <ScrollText className="text-gray-300 dark:text-gray-700 mb-3" size={48} />
                                            <p className="text-gray-500 dark:text-gray-400 text-lg font-medium">{t('logs.noLogs')}</p>
                                            <p className="text-gray-400 dark:text-gray-500 text-sm mt-1">{t('logs.noLogsDesc')}</p>
                                        </div>
                                    </td>
                                </tr>
                            ) : (
                                logs.map((log, index) => (
                                    <tr key={log.id} className="hover:bg-gray-50 dark:hover:bg-gray-800/50 transition-colors animate-fade-in group">
                                        <td className="px-6 py-4 whitespace-nowrap">
                                            <span className={`px-2.5 py-0.5 inline-flex text-[10px] font-bold uppercase tracking-wide rounded-full border ${
                                                log.status === 'SUCCESS'
                                                ? 'bg-green-50 text-green-700 border-green-200 dark:bg-green-900/20 dark:text-green-400 dark:border-green-800'
                                                : 'bg-red-50 text-red-700 border-red-200 dark:bg-red-900/20 dark:text-red-400 dark:border-red-800'
                                            }`}>
                                                {log.status === 'SUCCESS' ? t('common.success') : t('common.fail')}
                                            </span>
                                        </td>
                                        <td className="px-6 py-4 whitespace-nowrap text-sm text-gray-600 dark:text-gray-400 font-mono">
                                            {log.timestamp}
                                        </td>
                                        <td className="px-6 py-4 whitespace-nowrap text-sm font-semibold text-gray-900 dark:text-white">
                                            {log.requestModel}
                                        </td>
                                        <td className="px-6 py-4 whitespace-nowrap text-sm text-gray-600 dark:text-gray-300">
                                            {log.actualModel}
                                        </td>
                                        <td className="px-6 py-4 whitespace-nowrap text-sm text-gray-600 dark:text-gray-300">
                                            {log.providerName}
                                        </td>
                                        <td className="px-6 py-4 whitespace-nowrap text-sm">
                                            <span className={`font-mono ${log.latency > 5000 ? 'text-amber-600 dark:text-amber-400' : 'text-gray-600 dark:text-gray-400'}`}>
                                                {log.latency}ms
                                            </span>
                                        </td>
                                        <td className="px-6 py-4 whitespace-nowrap text-sm text-gray-600 dark:text-gray-400 font-mono">
                                            {log.tokens}
                                        </td>
                                        <td className="px-6 py-4 whitespace-nowrap text-sm font-bold text-gray-700 dark:text-gray-300">
                                            {log.cost > 0 ? `$${log.cost.toFixed(5)}` : '-'}
                                        </td>
                                        <td className="px-6 py-4 whitespace-nowrap text-right text-sm font-medium">
                                            <button
                                                onClick={() => handleViewLog(log.id)}
                                                className="text-gray-400 hover:text-black dark:hover:text-white transition-colors opacity-0 group-hover:opacity-100 p-2 hover:bg-gray-100 dark:hover:bg-gray-700 rounded-lg"
                                                title={t('common.view')}
                                            >
                                                <Eye size={18} />
                                            </button>
                                        </td>
                                    </tr>
                                ))
                            )}
                        </tbody>
                    </table>
                </div>

                {/* Pagination Controls */}
                <div className="px-6 py-4 border-t border-gray-200 dark:border-gray-800 flex items-center justify-between">
                    <div className="flex-1 flex justify-between sm:hidden">
                        <button
                            onClick={() => handlePageChange(pagination.current - 1)}
                            disabled={pagination.current === 1}
                            className="relative inline-flex items-center px-4 py-2 border border-gray-300 dark:border-gray-600 text-sm font-medium rounded-md text-gray-700 dark:text-gray-300 bg-white dark:bg-gray-800 hover:bg-gray-50 dark:hover:bg-gray-700 disabled:opacity-50 disabled:cursor-not-allowed"
                        >
                            {t('logs.pagination.prev')}
                        </button>
                        <button
                            onClick={() => handlePageChange(pagination.current + 1)}
                            disabled={pagination.current >= pagination.pages}
                            className="ml-3 relative inline-flex items-center px-4 py-2 border border-gray-300 dark:border-gray-600 text-sm font-medium rounded-md text-gray-700 dark:text-gray-300 bg-white dark:bg-gray-800 hover:bg-gray-50 dark:hover:bg-gray-700 disabled:opacity-50 disabled:cursor-not-allowed"
                        >
                            {t('logs.pagination.next')}
                        </button>
                    </div>
                    <div className="hidden sm:flex-1 sm:flex sm:items-center sm:justify-between">
                        <div>
                            <p className="text-sm text-gray-500 dark:text-gray-400">
                                {t('logs.pagination.showing')} <span className="font-bold text-gray-900 dark:text-white">{pagination.total > 0 ? (pagination.current - 1) * pagination.size + 1 : 0}</span> {t('logs.pagination.to')} <span className="font-bold text-gray-900 dark:text-white">{Math.min(pagination.current * pagination.size, pagination.total)}</span> {t('logs.pagination.of')} <span className="font-bold text-gray-900 dark:text-white">{pagination.total}</span> {t('logs.pagination.results')}
                            </p>
                        </div>
                        <div>
                            <nav className="isolate inline-flex -space-x-px rounded-xl shadow-sm bg-white dark:bg-[#1a1a1a]" aria-label="Pagination">
                                <button
                                    onClick={() => handlePageChange(pagination.current - 1)}
                                    disabled={pagination.current === 1 || isLoading}
                                    className="relative inline-flex items-center px-3 py-2 rounded-l-xl ring-1 ring-inset ring-gray-200 dark:ring-gray-800 hover:bg-gray-50 dark:hover:bg-gray-800 focus:z-20 focus:outline-offset-0 disabled:opacity-50 disabled:cursor-not-allowed"
                                >
                                    <span className="sr-only">Previous</span>
                                    <ChevronLeft size={16} className="text-gray-400" />
                                </button>
                                <span className="relative inline-flex items-center px-4 py-2 text-sm font-semibold text-gray-900 dark:text-white ring-1 ring-inset ring-gray-200 dark:ring-gray-800 focus:outline-offset-0">
                                    {pagination.current} / {pagination.pages || 1}
                                </span>
                                <button
                                    onClick={() => handlePageChange(pagination.current + 1)}
                                    disabled={pagination.current >= pagination.pages || isLoading}
                                    className="relative inline-flex items-center px-3 py-2 rounded-r-xl ring-1 ring-inset ring-gray-200 dark:ring-gray-800 hover:bg-gray-50 dark:hover:bg-gray-800 focus:z-20 focus:outline-offset-0 disabled:opacity-50 disabled:cursor-not-allowed"
                                >
                                    <span className="sr-only">Next</span>
                                    <ChevronRight size={16} className="text-gray-400" />
                                </button>
                            </nav>
                        </div>
                    </div>
                </div>
                </>
            )}
        </div>

        {/* Detail Modal */}
        {isDetailOpen && (
            <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/20 backdrop-blur-sm p-4 animate-fade-in">
                <div className="bg-white dark:bg-[#1a1a1a] rounded-2xl shadow-float max-w-4xl w-full max-h-[90vh] flex flex-col overflow-hidden animate-in zoom-in-95 duration-200 border border-gray-100 dark:border-gray-800">
                    <div className="px-6 py-5 border-b border-gray-100 dark:border-gray-800 flex justify-between items-center bg-white/95 dark:bg-[#1a1a1a]/95 backdrop-blur z-10">
                        <div className="flex items-center gap-3">
                            <h2 className="text-xl font-bold text-gray-900 dark:text-white">{t('logs.detail.title')}</h2>
                            {selectedLog && (
                                <span className={`px-2.5 py-0.5 rounded-full text-xs font-bold uppercase tracking-wide border ${
                                    selectedLog.status === 'SUCCESS'
                                    ? 'bg-green-50 text-green-700 border-green-200 dark:bg-green-900/20 dark:text-green-400 dark:border-green-800'
                                    : 'bg-red-50 text-red-700 border-red-200 dark:bg-red-900/20 dark:text-red-400 dark:border-red-800'
                                }`}>
                                    {selectedLog.status}
                                </span>
                            )}
                        </div>
                        <button onClick={() => setIsDetailOpen(false)} className="text-gray-400 hover:text-gray-600 dark:hover:text-gray-300 rounded-full p-1 hover:bg-gray-100 dark:hover:bg-gray-800 transition-colors">
                            <X size={24} />
                        </button>
                    </div>

                    <div className="flex-1 overflow-y-auto p-8 custom-scrollbar">
                        {isDetailLoading ? (
                            <div className="flex flex-col items-center justify-center h-64">
                                <Loader2 className="w-10 h-10 text-gray-900 animate-spin mb-4" />
                                <p className="text-gray-500 dark:text-gray-400 font-medium">{t('logs.detail.loading')}</p>
                            </div>
                        ) : selectedLog ? (
                            <div className="space-y-8 animate-fade-in">
                                {/* Info Grid */}
                                <div className="grid grid-cols-1 md:grid-cols-2 gap-8">
                                    <div className="space-y-4">
                                        <h3 className="text-sm font-bold text-gray-900 dark:text-white border-b border-gray-200 dark:border-gray-700 pb-2 uppercase tracking-wide">{t('logs.detail.info')}</h3>
                                        <div className="space-y-3">
                                            <div className="flex justify-between items-start group">
                                                <span className="text-sm font-medium text-gray-500 dark:text-gray-400">{t('logs.detail.requestId')}</span>
                                                <div className="flex items-center gap-2">
                                                    <span className="text-sm font-mono text-gray-700 dark:text-gray-300 break-all">{selectedLog.requestId}</span>
                                                    <button
                                                        onClick={() => handleCopy(selectedLog.requestId, 'reqId')}
                                                        className="text-gray-300 dark:text-gray-600 hover:text-black dark:hover:text-white opacity-0 group-hover:opacity-100 transition-opacity"
                                                    >
                                                        {copyFeedback === 'reqId' ? <Check size={14} className="text-green-500" /> : <Copy size={14} />}
                                                    </button>
                                                </div>
                                            </div>
                                            <div className="flex justify-between">
                                                <span className="text-sm font-medium text-gray-500 dark:text-gray-400">{t('logs.detail.created')}</span>
                                                <span className="text-sm text-gray-700 dark:text-gray-300 font-mono">
                                                    {selectedLog.createdAt ? new Date(selectedLog.createdAt).toLocaleString() : new Date(selectedLog.requestTime * 1000).toLocaleString()}
                                                </span>
                                            </div>
                                            <div className="flex justify-between">
                                                <span className="text-sm font-medium text-gray-500 dark:text-gray-400">{t('logs.detail.provider')}</span>
                                                <span className="text-sm font-semibold text-gray-700 dark:text-gray-300">{selectedLog.providerName} <span className="text-gray-400 dark:text-gray-500 font-normal ml-1 text-xs">(ID: {selectedLog.providerId})</span></span>
                                            </div>
                                            <div className="flex justify-between">
                                                <span className="text-sm font-medium text-gray-500 dark:text-gray-400">{t('logs.table.requestModel')}</span>
                                                <span className="text-xs font-bold text-gray-900 dark:text-white bg-gray-100 dark:bg-gray-800 px-2.5 py-1 rounded-md">{selectedLog.requestModelName || 'N/A'}</span>
                                            </div>
                                            <div className="flex justify-between">
                                                <span className="text-sm font-medium text-gray-500 dark:text-gray-400">{t('logs.table.actualModel')}</span>
                                                <span className="text-sm font-semibold text-gray-700 dark:text-gray-300">{selectedLog.actualModelName || '-'}</span>
                                            </div>
                                            <div className="flex justify-between">
                                                <span className="text-sm font-medium text-gray-500 dark:text-gray-400">{t('logs.detail.type')}</span>
                                                <span className="text-sm font-semibold text-gray-700 dark:text-gray-300">{selectedLog.requestType}</span>
                                            </div>
                                        </div>
                                    </div>

                                    <div className="space-y-4">
                                        <h3 className="text-sm font-bold text-gray-900 dark:text-white border-b border-gray-200 dark:border-gray-700 pb-2 uppercase tracking-wide">{t('logs.detail.performance')}</h3>
                                        <div className="space-y-3">
                                            <div className="flex justify-between">
                                                <span className="text-sm font-medium text-gray-500 dark:text-gray-400">{t('logs.table.latency')}</span>
                                                <span className="text-sm font-mono font-semibold text-gray-700 dark:text-gray-300">{selectedLog.firstTokenMs} ms</span>
                                            </div>
                                            <div className="flex justify-between">
                                                <span className="text-sm font-medium text-gray-500 dark:text-gray-400">{t('logs.detail.duration')}</span>
                                                <span className="text-sm font-mono font-semibold text-gray-700 dark:text-gray-300">{selectedLog.totalTimeMs} ms</span>
                                            </div>
                                            <div className="flex justify-between items-start">
                                                <span className="text-sm font-medium text-gray-500 dark:text-gray-400">{t('logs.table.tokens')}</span>
                                                <div className="text-right">
                                                    <div className="text-sm font-mono font-bold text-gray-700 dark:text-gray-300">{selectedLog.inputTokens + selectedLog.outputTokens} Total</div>
                                                    <div className="text-xs text-gray-400 dark:text-gray-500 mt-0.5">
                                                        <span className="text-emerald-600 dark:text-emerald-400">{selectedLog.inputTokens} in</span> / <span className="text-blue-600 dark:text-blue-400">{selectedLog.outputTokens} out</span>
                                                    </div>
                                                </div>
                                            </div>
                                            <div className="flex justify-between">
                                                <span className="text-sm font-medium text-gray-500 dark:text-gray-400">{t('logs.table.cost')}</span>
                                                <span className="text-sm font-mono font-bold text-emerald-600 dark:text-emerald-400 bg-emerald-50 dark:bg-emerald-900/20 px-2 py-0.5 rounded">${selectedLog.cost.toFixed(6)}</span>
                                            </div>
                                             <div className="flex justify-between">
                                                <span className="text-sm font-medium text-gray-500 dark:text-gray-400">{t('logs.detail.retry')}</span>
                                                <span className="text-sm font-mono text-gray-700 dark:text-gray-300">{selectedLog.retryCount}</span>
                                            </div>
                                        </div>
                                    </div>
                                </div>

                                {/* Content Section */}
                                <div>
                                    <div className="flex items-center justify-between mb-3">
                                        <h3 className="text-sm font-bold text-gray-900 dark:text-white uppercase tracking-wide">{t('logs.detail.content')}</h3>
                                        <button
                                            onClick={() => handleCopy(selectedLog.requestContent, 'content')}
                                            className="text-xs flex items-center font-medium px-2 py-1 rounded hover:bg-gray-100 dark:hover:bg-gray-800 transition-colors text-gray-600 dark:text-gray-400"
                                        >
                                            {copyFeedback === 'content' ? <Check size={14} className="mr-1" /> : <Copy size={14} className="mr-1" />}
                                            {copyFeedback === 'content' ? t('common.copied') : t('common.copy')}
                                        </button>
                                    </div>
                                    <div className="bg-gray-900 dark:bg-black rounded-xl p-5 overflow-hidden border border-gray-800 dark:border-gray-800 shadow-inner">
                                        <pre className="text-xs font-mono text-gray-300 whitespace-pre-wrap break-words max-h-96 overflow-y-auto custom-scrollbar leading-relaxed">
                                            {formatContent(selectedLog.requestContent)}
                                        </pre>
                                    </div>
                                </div>

                                {/* Response Content Section */}
                                {selectedLog.responseContent && (
                                    <div>
                                        <div className="flex items-center justify-between mb-3">
                                            <h3 className="text-sm font-bold text-gray-900 dark:text-white uppercase tracking-wide">{t('logs.detail.responseContent')}</h3>
                                            <button
                                                onClick={() => handleCopy(selectedLog.responseContent || '', 'response')}
                                                className="text-xs flex items-center font-medium px-2 py-1 rounded hover:bg-gray-100 dark:hover:bg-gray-800 transition-colors text-gray-600 dark:text-gray-400"
                                            >
                                                {copyFeedback === 'response' ? <Check size={14} className="mr-1" /> : <Copy size={14} className="mr-1" />}
                                                {copyFeedback === 'response' ? t('common.copied') : t('common.copy')}
                                            </button>
                                        </div>
                                        <div className="bg-gray-900 dark:bg-black rounded-xl p-5 overflow-hidden border border-gray-800 dark:border-gray-800 shadow-inner">
                                            <pre className="text-xs font-mono text-gray-300 whitespace-pre-wrap break-words max-h-96 overflow-y-auto custom-scrollbar leading-relaxed">
                                                {formatContent(selectedLog.responseContent)}
                                            </pre>
                                        </div>
                                    </div>
                                )}

                                {/* Error Message Section */}
                                {selectedLog.errorMessage && (
                                    <div className="animate-in slide-in-from-bottom-2">
                                        <div className="flex items-center justify-between mb-3">
                                            <h3 className="text-sm font-bold text-red-600 dark:text-red-400 uppercase tracking-wide">{t('logs.detail.error')}</h3>
                                            <button
                                                onClick={() => handleCopy(selectedLog.errorMessage || '', 'error')}
                                                className="text-xs flex items-center font-medium px-2 py-1 rounded hover:bg-red-50 dark:hover:bg-red-900/30 transition-colors text-red-600 dark:text-red-400"
                                            >
                                                {copyFeedback === 'error' ? <Check size={14} className="mr-1" /> : <Copy size={14} className="mr-1" />}
                                                {copyFeedback === 'error' ? t('common.copied') : t('common.copy')}
                                            </button>
                                        </div>
                                        <div className="bg-red-50 dark:bg-red-900/20 border border-red-200 dark:border-red-800 rounded-xl p-5 overflow-hidden">
                                            <pre className="text-xs font-mono text-red-800 dark:text-red-300 whitespace-pre-wrap break-words max-h-48 overflow-y-auto custom-scrollbar">
                                                {selectedLog.errorMessage}
                                            </pre>
                                        </div>
                                    </div>
                                )}
                            </div>
                        ) : (
                            <div className="text-center text-gray-500 dark:text-gray-400 py-12">
                                {t('logs.detail.failed')}
                            </div>
                        )}
                    </div>

                    <div className="px-6 py-4 border-t border-gray-100 dark:border-gray-800 flex justify-end bg-white/50 dark:bg-[#1a1a1a]/50 backdrop-blur">
                         <button
                            onClick={() => setIsDetailOpen(false)}
                            className="px-6 py-2.5 bg-white dark:bg-gray-800 border border-gray-200 dark:border-gray-700 shadow-sm text-sm font-semibold rounded-xl text-gray-700 dark:text-gray-200 hover:bg-gray-50 dark:hover:bg-gray-700 transition-colors"
                        >
                            {t('common.close')}
                        </button>
                    </div>
                </div>
            </div>
        )}
    </div>
  );
};