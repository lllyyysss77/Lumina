import React, { useState, useEffect, useRef } from 'react';
import { Search, Filter, Download, Loader2, ChevronLeft, ChevronRight, X, Copy, Check, Eye, RefreshCw, Clock } from 'lucide-react';
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
  const [isAutoRefresh, setIsAutoRefresh] = useState(false);
  const [refreshInterval, setRefreshInterval] = useState(30000); // Default 30s
  
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
            <h1 className="text-2xl font-bold text-slate-900 dark:text-white">{t('logs.title')}</h1>
            <p className="text-slate-500 dark:text-slate-400 mt-1">{t('logs.subtitle')}</p>
            </div>
            <div className="flex space-x-2">
                <button 
                    onClick={handleRefreshClick}
                    className="flex items-center px-3 py-2 bg-white dark:bg-slate-800 border border-slate-300 dark:border-slate-700 rounded-lg text-sm font-medium text-slate-700 dark:text-slate-200 hover:bg-slate-50 dark:hover:bg-slate-700 shadow-sm transition-all active:scale-95"
                    title="Refresh"
                >
                    <RefreshCw size={16} className={`${isLoading ? 'animate-spin' : ''}`} />
                </button>
                <div className="flex items-center bg-white dark:bg-slate-800 border border-slate-300 dark:border-slate-700 rounded-lg shadow-sm overflow-hidden">
                    <button 
                        onClick={() => setIsAutoRefresh(!isAutoRefresh)}
                        className={`flex items-center px-3 py-2 text-sm font-medium transition-colors ${isAutoRefresh ? 'bg-green-50 dark:bg-green-900/30 text-green-700 dark:text-green-400 hover:bg-green-100 dark:hover:bg-green-900/40' : 'text-slate-700 dark:text-slate-200 hover:bg-slate-50 dark:hover:bg-slate-700'}`}
                    >
                        <Clock size={16} className="mr-2" />
                        {t('logs.autoRefresh')}
                    </button>
                    {isAutoRefresh && (
                        <div className="border-l border-slate-300 dark:border-slate-700">
                             <select 
                                value={refreshInterval}
                                onChange={(e) => setRefreshInterval(Number(e.target.value))}
                                className="block w-full py-2 pl-2 pr-6 text-xs border-none focus:ring-0 bg-transparent text-slate-600 dark:text-slate-300 cursor-pointer"
                            >
                                <option value={5000}>5{t('logs.seconds')}</option>
                                <option value={10000}>10{t('logs.seconds')}</option>
                                <option value={30000}>30{t('logs.seconds')}</option>
                                <option value={60000}>60{t('logs.seconds')}</option>
                            </select>
                        </div>
                    )}
                </div>
                <button className="flex items-center px-3 py-2 bg-white dark:bg-slate-800 border border-slate-300 dark:border-slate-700 rounded-lg text-sm font-medium text-slate-700 dark:text-slate-200 hover:bg-slate-50 dark:hover:bg-slate-700 shadow-sm">
                    <Download size={16} className="mr-2" />
                    {t('common.export')}
                </button>
            </div>
        </div>

        {/* Search & Filter Bar */}
        <div className="bg-white dark:bg-slate-800 p-4 rounded-xl border border-slate-200 dark:border-slate-700 shadow-sm flex flex-col md:flex-row gap-4">
            <div className="relative flex-1">
                <div className="absolute inset-y-0 left-0 pl-3 flex items-center pointer-events-none">
                    <Search size={18} className="text-slate-400" />
                </div>
                <input 
                    type="text" 
                    className="block w-full pl-10 pr-3 py-2 border border-slate-300 dark:border-slate-600 rounded-lg leading-5 bg-white dark:bg-slate-900 text-slate-900 dark:text-white placeholder-slate-400 focus:outline-none focus:ring-2 focus:ring-indigo-500 focus:border-indigo-500 sm:text-sm" 
                    placeholder={t('logs.searchPlaceholder')}
                />
            </div>
            <div className="flex gap-2">
                <button className="flex items-center px-4 py-2 border border-slate-300 dark:border-slate-600 rounded-lg text-sm font-medium text-slate-700 dark:text-slate-200 bg-white dark:bg-slate-800 hover:bg-slate-50 dark:hover:bg-slate-700">
                    <Filter size={16} className="mr-2" />
                    {t('common.filter')}
                </button>
                <select 
                    value={pagination.size}
                    onChange={handleSizeChange}
                    className="block w-40 pl-3 pr-10 py-2 text-base border-slate-300 dark:border-slate-600 focus:outline-none focus:ring-indigo-500 focus:border-indigo-500 sm:text-sm rounded-lg border bg-white dark:bg-slate-900 text-slate-900 dark:text-white"
                >
                    <option value="10">10 / page</option>
                    <option value="20">20 / page</option>
                    <option value="50">50 / page</option>
                    <option value="100">100 / page</option>
                </select>
            </div>
        </div>

        {/* Logs Table */}
        <div className="bg-white dark:bg-slate-800 rounded-xl shadow-sm border border-slate-200 dark:border-slate-700 overflow-hidden">
            {isLoading && logs.length === 0 ? (
               <TableSkeleton rows={10} />
            ) : (
                <>
                <div className="overflow-x-auto">
                    <table className="min-w-full divide-y divide-slate-200 dark:divide-slate-700">
                        <thead className="bg-slate-50 dark:bg-slate-900/50">
                            <tr>
                                <th scope="col" className="px-6 py-3 text-left text-xs font-medium text-slate-500 dark:text-slate-400 uppercase tracking-wider">{t('logs.table.status')}</th>
                                <th scope="col" className="px-6 py-3 text-left text-xs font-medium text-slate-500 dark:text-slate-400 uppercase tracking-wider">{t('logs.table.time')}</th>
                                <th scope="col" className="px-6 py-3 text-left text-xs font-medium text-slate-500 dark:text-slate-400 uppercase tracking-wider">{t('logs.table.model')}</th>
                                <th scope="col" className="px-6 py-3 text-left text-xs font-medium text-slate-500 dark:text-slate-400 uppercase tracking-wider">{t('logs.table.provider')}</th>
                                <th scope="col" className="px-6 py-3 text-left text-xs font-medium text-slate-500 dark:text-slate-400 uppercase tracking-wider">{t('logs.table.latency')}</th>
                                <th scope="col" className="px-6 py-3 text-left text-xs font-medium text-slate-500 dark:text-slate-400 uppercase tracking-wider">{t('logs.table.tokens')}</th>
                                <th scope="col" className="px-6 py-3 text-left text-xs font-medium text-slate-500 dark:text-slate-400 uppercase tracking-wider">{t('logs.table.cost')}</th>
                                <th scope="col" className="relative px-6 py-3">
                                    <span className="sr-only">{t('common.details')}</span>
                                </th>
                            </tr>
                        </thead>
                        <tbody className="bg-white dark:bg-slate-800 divide-y divide-slate-200 dark:divide-slate-700">
                            {logs.length === 0 ? (
                                <tr>
                                    <td colSpan={8} className="px-6 py-12 text-center text-slate-500 dark:text-slate-400 text-sm">
                                        No logs found
                                    </td>
                                </tr>
                            ) : (
                                logs.map((log, index) => (
                                    <tr key={log.id} className="hover:bg-slate-50 dark:hover:bg-slate-700 transition-colors animate-fade-in" style={{ animationDelay: `${index * 30}ms` }}>
                                        <td className="px-6 py-4 whitespace-nowrap">
                                            <span className={`px-2 inline-flex text-xs leading-5 font-semibold rounded-full ${
                                                log.status === 'SUCCESS' ? 'bg-green-100 text-green-800 dark:bg-green-900/30 dark:text-green-400' : 'bg-red-100 text-red-800 dark:bg-red-900/30 dark:text-red-400'
                                            }`}>
                                                {log.status === 'SUCCESS' ? t('common.success') : t('common.fail')}
                                            </span>
                                        </td>
                                        <td className="px-6 py-4 whitespace-nowrap text-sm text-slate-500 dark:text-slate-400 font-mono">
                                            {log.timestamp}
                                        </td>
                                        <td className="px-6 py-4 whitespace-nowrap text-sm font-medium text-slate-900 dark:text-white">
                                            {log.model}
                                        </td>
                                        <td className="px-6 py-4 whitespace-nowrap text-sm text-slate-500 dark:text-slate-400">
                                            {log.providerName}
                                        </td>
                                        <td className="px-6 py-4 whitespace-nowrap text-sm text-slate-500 dark:text-slate-400">
                                            {log.latency}ms
                                        </td>
                                        <td className="px-6 py-4 whitespace-nowrap text-sm text-slate-500 dark:text-slate-400">
                                            {log.tokens}
                                        </td>
                                        <td className="px-6 py-4 whitespace-nowrap text-sm text-slate-500 dark:text-slate-400">
                                            {log.cost > 0 ? `$${log.cost.toFixed(5)}` : '-'}
                                        </td>
                                        <td className="px-6 py-4 whitespace-nowrap text-right text-sm font-medium">
                                            <button 
                                                onClick={() => handleViewLog(log.id)}
                                                className="text-indigo-600 dark:text-indigo-400 hover:text-indigo-900 dark:hover:text-indigo-300 flex items-center justify-end w-full"
                                            >
                                                {t('common.view')}
                                                <Eye size={14} className="ml-1" />
                                            </button>
                                        </td>
                                    </tr>
                                ))
                            )}
                        </tbody>
                    </table>
                </div>
                
                {/* Pagination Controls */}
                <div className="bg-white dark:bg-slate-800 px-4 py-3 border-t border-slate-200 dark:border-slate-700 flex items-center justify-between sm:px-6">
                    <div className="flex-1 flex justify-between sm:hidden">
                        <button 
                            onClick={() => handlePageChange(pagination.current - 1)}
                            disabled={pagination.current === 1}
                            className="relative inline-flex items-center px-4 py-2 border border-slate-300 dark:border-slate-600 text-sm font-medium rounded-md text-slate-700 dark:text-slate-300 bg-white dark:bg-slate-800 hover:bg-slate-50 dark:hover:bg-slate-700 disabled:opacity-50 disabled:cursor-not-allowed"
                        >
                            {t('logs.pagination.prev')}
                        </button>
                        <button 
                            onClick={() => handlePageChange(pagination.current + 1)}
                            disabled={pagination.current >= pagination.pages}
                            className="ml-3 relative inline-flex items-center px-4 py-2 border border-slate-300 dark:border-slate-600 text-sm font-medium rounded-md text-slate-700 dark:text-slate-300 bg-white dark:bg-slate-800 hover:bg-slate-50 dark:hover:bg-slate-700 disabled:opacity-50 disabled:cursor-not-allowed"
                        >
                            {t('logs.pagination.next')}
                        </button>
                    </div>
                    <div className="hidden sm:flex-1 sm:flex sm:items-center sm:justify-between">
                        <div>
                            <p className="text-sm text-slate-700 dark:text-slate-300">
                                {t('logs.pagination.showing')} <span className="font-medium">{pagination.total > 0 ? (pagination.current - 1) * pagination.size + 1 : 0}</span> {t('logs.pagination.to')} <span className="font-medium">{Math.min(pagination.current * pagination.size, pagination.total)}</span> {t('logs.pagination.of')} <span className="font-medium">{pagination.total}</span> {t('logs.pagination.results')}
                            </p>
                        </div>
                        <div>
                            <nav className="relative z-0 inline-flex rounded-md shadow-sm -space-x-px" aria-label="Pagination">
                                <button 
                                    onClick={() => handlePageChange(pagination.current - 1)}
                                    disabled={pagination.current === 1 || isLoading}
                                    className="relative inline-flex items-center px-2 py-2 rounded-l-md border border-slate-300 dark:border-slate-600 bg-white dark:bg-slate-800 text-sm font-medium text-slate-500 dark:text-slate-400 hover:bg-slate-50 dark:hover:bg-slate-700 disabled:opacity-50 disabled:cursor-not-allowed"
                                >
                                    <span className="sr-only">Previous</span>
                                    <ChevronLeft size={16} />
                                </button>
                                <span className="relative inline-flex items-center px-4 py-2 border border-slate-300 dark:border-slate-600 bg-white dark:bg-slate-800 text-sm font-medium text-slate-700 dark:text-slate-300">
                                    {pagination.current} / {pagination.pages || 1}
                                </span>
                                <button 
                                    onClick={() => handlePageChange(pagination.current + 1)}
                                    disabled={pagination.current >= pagination.pages || isLoading}
                                    className="relative inline-flex items-center px-2 py-2 rounded-r-md border border-slate-300 dark:border-slate-600 bg-white dark:bg-slate-800 text-sm font-medium text-slate-500 dark:text-slate-400 hover:bg-slate-50 dark:hover:bg-slate-700 disabled:opacity-50 disabled:cursor-not-allowed"
                                >
                                    <span className="sr-only">Next</span>
                                    <ChevronRight size={16} />
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
            <div className="fixed inset-0 z-50 flex items-center justify-center bg-slate-900/50 backdrop-blur-sm p-4 animate-fade-in">
                <div className="bg-white dark:bg-slate-800 rounded-xl shadow-xl max-w-4xl w-full max-h-[90vh] flex flex-col overflow-hidden animate-in zoom-in-95 duration-200 border border-slate-200 dark:border-slate-700">
                    <div className="px-6 py-4 border-b border-slate-100 dark:border-slate-700 flex justify-between items-center bg-slate-50/50 dark:bg-slate-900/50">
                        <div className="flex items-center gap-3">
                            <h2 className="text-lg font-bold text-slate-900 dark:text-white">{t('logs.detail.title')}</h2>
                            {selectedLog && (
                                <span className={`px-2 py-0.5 rounded text-xs font-bold ${selectedLog.status === 'SUCCESS' ? 'bg-green-100 text-green-700 dark:bg-green-900/30 dark:text-green-400' : 'bg-red-100 text-red-700 dark:bg-red-900/30 dark:text-red-400'}`}>
                                    {selectedLog.status}
                                </span>
                            )}
                        </div>
                        <button onClick={() => setIsDetailOpen(false)} className="text-slate-400 hover:text-slate-600 dark:hover:text-slate-300 rounded-lg p-1 hover:bg-slate-100 dark:hover:bg-slate-700">
                            <X size={20} />
                        </button>
                    </div>

                    <div className="flex-1 overflow-y-auto p-6">
                        {isDetailLoading ? (
                            <div className="flex flex-col items-center justify-center h-64">
                                <Loader2 className="w-8 h-8 text-indigo-600 animate-spin mb-2" />
                                <p className="text-slate-500 dark:text-slate-400 text-sm">Loading details...</p>
                            </div>
                        ) : selectedLog ? (
                            <div className="space-y-6 animate-fade-in">
                                {/* Info Grid */}
                                <div className="grid grid-cols-1 md:grid-cols-2 gap-6">
                                    <div className="space-y-4">
                                        <h3 className="text-sm font-semibold text-slate-900 dark:text-white border-b border-slate-100 dark:border-slate-700 pb-2">{t('logs.detail.info')}</h3>
                                        <div className="space-y-3">
                                            <div className="flex justify-between items-start group">
                                                <span className="text-sm text-slate-500 dark:text-slate-400">{t('logs.detail.requestId')}</span>
                                                <div className="flex items-center gap-2">
                                                    <span className="text-sm font-mono text-slate-700 dark:text-slate-300 break-all">{selectedLog.requestId}</span>
                                                    <button 
                                                        onClick={() => handleCopy(selectedLog.requestId, 'reqId')}
                                                        className="text-slate-300 dark:text-slate-600 hover:text-indigo-600 dark:hover:text-indigo-400 opacity-0 group-hover:opacity-100 transition-opacity"
                                                    >
                                                        {copyFeedback === 'reqId' ? <Check size={14} className="text-green-500" /> : <Copy size={14} />}
                                                    </button>
                                                </div>
                                            </div>
                                            <div className="flex justify-between">
                                                <span className="text-sm text-slate-500 dark:text-slate-400">{t('logs.detail.created')}</span>
                                                <span className="text-sm text-slate-700 dark:text-slate-300 font-mono">
                                                    {selectedLog.createdAt ? new Date(selectedLog.createdAt).toLocaleString() : new Date(selectedLog.requestTime * 1000).toLocaleString()}
                                                </span>
                                            </div>
                                            <div className="flex justify-between">
                                                <span className="text-sm text-slate-500 dark:text-slate-400">{t('logs.detail.provider')}</span>
                                                <span className="text-sm font-medium text-slate-700 dark:text-slate-300">{selectedLog.providerName} <span className="text-slate-400 dark:text-slate-500 font-normal">(ID: {selectedLog.providerId})</span></span>
                                            </div>
                                            <div className="flex justify-between">
                                                <span className="text-sm text-slate-500 dark:text-slate-400">{t('logs.table.model')}</span>
                                                <span className="text-sm font-medium text-indigo-600 dark:text-indigo-400 bg-indigo-50 dark:bg-indigo-900/30 px-2 py-0.5 rounded">{selectedLog.requestModelName || 'N/A'}</span>
                                            </div>
                                            <div className="flex justify-between">
                                                <span className="text-sm text-slate-500 dark:text-slate-400">{t('logs.detail.type')}</span>
                                                <span className="text-sm text-slate-700 dark:text-slate-300">{selectedLog.requestType}</span>
                                            </div>
                                        </div>
                                    </div>

                                    <div className="space-y-4">
                                        <h3 className="text-sm font-semibold text-slate-900 dark:text-white border-b border-slate-100 dark:border-slate-700 pb-2">{t('logs.detail.performance')}</h3>
                                        <div className="space-y-3">
                                            <div className="flex justify-between">
                                                <span className="text-sm text-slate-500 dark:text-slate-400">{t('logs.table.latency')}</span>
                                                <span className="text-sm font-mono text-slate-700 dark:text-slate-300">{selectedLog.firstTokenMs} ms</span>
                                            </div>
                                            <div className="flex justify-between">
                                                <span className="text-sm text-slate-500 dark:text-slate-400">{t('logs.detail.duration')}</span>
                                                <span className="text-sm font-mono text-slate-700 dark:text-slate-300">{selectedLog.totalTimeMs} ms</span>
                                            </div>
                                            <div className="flex justify-between">
                                                <span className="text-sm text-slate-500 dark:text-slate-400">{t('logs.table.tokens')}</span>
                                                <div className="text-sm text-slate-700 dark:text-slate-300 text-right">
                                                    <div className="font-mono">{selectedLog.inputTokens + selectedLog.outputTokens} Total</div>
                                                    <div className="text-xs text-slate-400 dark:text-slate-500">
                                                        ({selectedLog.inputTokens} in / {selectedLog.outputTokens} out)
                                                    </div>
                                                </div>
                                            </div>
                                            <div className="flex justify-between">
                                                <span className="text-sm text-slate-500 dark:text-slate-400">{t('logs.table.cost')}</span>
                                                <span className="text-sm font-mono font-bold text-emerald-600 dark:text-emerald-400">${selectedLog.cost.toFixed(6)}</span>
                                            </div>
                                             <div className="flex justify-between">
                                                <span className="text-sm text-slate-500 dark:text-slate-400">{t('logs.detail.retry')}</span>
                                                <span className="text-sm font-mono text-slate-700 dark:text-slate-300">{selectedLog.retryCount}</span>
                                            </div>
                                        </div>
                                    </div>
                                </div>

                                {/* Content Section */}
                                <div>
                                    <div className="flex items-center justify-between mb-2">
                                        <h3 className="text-sm font-semibold text-slate-900 dark:text-white">{t('logs.detail.content')}</h3>
                                        <button 
                                            onClick={() => handleCopy(selectedLog.requestContent, 'content')}
                                            className="text-xs flex items-center text-indigo-600 dark:text-indigo-400 hover:text-indigo-700 dark:hover:text-indigo-300 font-medium"
                                        >
                                            {copyFeedback === 'content' ? <Check size={14} className="mr-1" /> : <Copy size={14} className="mr-1" />}
                                            {copyFeedback === 'content' ? 'Copied' : t('common.copy')}
                                        </button>
                                    </div>
                                    <div className="bg-slate-900 dark:bg-black rounded-lg p-4 overflow-hidden border border-slate-800 dark:border-slate-800">
                                        <pre className="text-xs font-mono text-slate-300 whitespace-pre-wrap break-words max-h-96 overflow-y-auto custom-scrollbar">
                                            {formatContent(selectedLog.requestContent)}
                                        </pre>
                                    </div>
                                </div>

                                {/* Response Content Section */}
                                {selectedLog.responseContent && (
                                    <div>
                                        <div className="flex items-center justify-between mb-2">
                                            <h3 className="text-sm font-semibold text-slate-900 dark:text-white">{t('logs.detail.responseContent')}</h3>
                                            <button 
                                                onClick={() => handleCopy(selectedLog.responseContent || '', 'response')}
                                                className="text-xs flex items-center text-indigo-600 dark:text-indigo-400 hover:text-indigo-700 dark:hover:text-indigo-300 font-medium"
                                            >
                                                {copyFeedback === 'response' ? <Check size={14} className="mr-1" /> : <Copy size={14} className="mr-1" />}
                                                {copyFeedback === 'response' ? 'Copied' : t('common.copy')}
                                            </button>
                                        </div>
                                        <div className="bg-slate-900 dark:bg-black rounded-lg p-4 overflow-hidden border border-slate-800 dark:border-slate-800">
                                            <pre className="text-xs font-mono text-slate-300 whitespace-pre-wrap break-words max-h-96 overflow-y-auto custom-scrollbar">
                                                {formatContent(selectedLog.responseContent)}
                                            </pre>
                                        </div>
                                    </div>
                                )}

                                {/* Error Message Section */}
                                {selectedLog.errorMessage && (
                                    <div>
                                        <div className="flex items-center justify-between mb-2">
                                            <h3 className="text-sm font-semibold text-red-600 dark:text-red-400">{t('logs.detail.error')}</h3>
                                            <button 
                                                onClick={() => handleCopy(selectedLog.errorMessage || '', 'error')}
                                                className="text-xs flex items-center text-red-600 dark:text-red-400 hover:text-red-700 dark:hover:text-red-300 font-medium"
                                            >
                                                {copyFeedback === 'error' ? <Check size={14} className="mr-1" /> : <Copy size={14} className="mr-1" />}
                                                {copyFeedback === 'error' ? 'Copied' : t('common.copy')}
                                            </button>
                                        </div>
                                        <div className="bg-red-50 dark:bg-red-900/20 border border-red-200 dark:border-red-800 rounded-lg p-4 overflow-hidden">
                                            <pre className="text-xs font-mono text-red-800 dark:text-red-300 whitespace-pre-wrap break-words max-h-48 overflow-y-auto custom-scrollbar">
                                                {selectedLog.errorMessage}
                                            </pre>
                                        </div>
                                    </div>
                                )}
                            </div>
                        ) : (
                            <div className="text-center text-slate-500 dark:text-slate-400 py-12">
                                Failed to load details.
                            </div>
                        )}
                    </div>
                    
                    <div className="px-6 py-4 border-t border-slate-100 dark:border-slate-700 flex justify-end">
                         <button 
                            onClick={() => setIsDetailOpen(false)}
                            className="px-4 py-2 bg-white dark:bg-slate-800 border border-slate-300 dark:border-slate-600 shadow-sm text-sm font-medium rounded-lg text-slate-700 dark:text-slate-200 hover:bg-slate-50 dark:hover:bg-slate-700"
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