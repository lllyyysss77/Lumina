import React, { useState, useEffect } from 'react';
import { Search, Loader2, BrainCircuit, Wrench, RefreshCw, Database, Cpu, Calendar, CheckCircle2, AlertCircle, Activity } from 'lucide-react';
import { useLanguage } from './LanguageContext';
import { modelService } from '../services/modelService';
import { ModelPrice } from '../types';
import { CardGridSkeleton } from './Skeletons';
import { SlideInItem } from './Animations';
import { Pagination } from './Pagination';

export const Pricing: React.FC = () => {
  const { t } = useLanguage();
  
  // State
  const [models, setModels] = useState<ModelPrice[]>([]);
  const [isLoading, setIsLoading] = useState(false);
  const [isSyncing, setIsSyncing] = useState(false);
  const [pagination, setPagination] = useState({
    current: 1,
    size: 12, // Increased default size for grid view
    total: 0,
    pages: 0
  });
  const [searchTerm, setSearchTerm] = useState('');
  
  // Toast State
  const [toast, setToast] = useState<{show: boolean, message: string, type: 'success' | 'error' | 'info'}>({ show: false, message: '', type: 'success' });

  const showToast = (message: string, type: 'success' | 'error' | 'info' = 'success') => {
    setToast({ show: true, message, type });
    setTimeout(() => setToast(prev => ({ ...prev, show: false })), 3000);
  };

  // Fetch Models
  const fetchModels = async (page: number, size: number, keyword: string) => {
    setIsLoading(true);
    try {
      const data = await modelService.getPage(page, size, keyword);
      setModels(data.records);
      setPagination({
        current: data.current,
        size: data.size,
        total: data.total,
        pages: data.pages
      });
    } catch (error) {
      console.error("Failed to fetch models:", error);
    } finally {
      setIsLoading(false);
    }
  };

  const handleSync = async () => {
    setIsSyncing(true);
    try {
        await modelService.sync();
        showToast(t('pricing.syncSuccess'), 'success');
        fetchModels(1, pagination.size, searchTerm); // Refresh list
    } catch (error) {
        console.error("Sync failed", error);
        showToast(t('pricing.syncFail'), 'error');
    } finally {
        setIsSyncing(false);
    }
  };

  // Debounced Search
  useEffect(() => {
    const timer = setTimeout(() => {
        fetchModels(1, pagination.size, searchTerm);
    }, 500);
    return () => clearTimeout(timer);
  }, [searchTerm]);

  const handlePageChange = (page: number, size: number) => {
    fetchModels(page, size, searchTerm);
  };

  const handleSizeChange = (e: React.ChangeEvent<HTMLSelectElement>) => {
      const newSize = parseInt(e.target.value);
      setPagination(prev => ({...prev, size: newSize})); // Update state immediately for UI
      fetchModels(1, newSize, searchTerm);
  };

  const formatPrice = (price: number) => {
    if (price === 0) return <span className="text-green-600 dark:text-green-400 font-medium">Free</span>;
    return `$${price.toFixed(2)}`;
  };

  return (
    <div className="space-y-6 relative flex flex-col h-full">
      {/* Toast Notification */}
      {toast.show && (
          <div className={`fixed top-4 right-4 z-[100] px-4 py-3 rounded-lg shadow-lg border flex items-center animate-in slide-in-from-right duration-300 ${
              toast.type === 'success' ? 'bg-white border-green-200 text-green-700 dark:bg-slate-800 dark:border-green-900 dark:text-green-400' : 
              toast.type === 'error' ? 'bg-white border-red-200 text-red-700 dark:bg-slate-800 dark:border-red-900 dark:text-red-400' :
              'bg-white border-blue-200 text-blue-700 dark:bg-slate-800 dark:border-blue-900 dark:text-blue-400'
          }`}>
              {toast.type === 'success' ? <CheckCircle2 size={18} className="mr-2" /> : 
               toast.type === 'error' ? <AlertCircle size={18} className="mr-2" /> :
               <Activity size={18} className="mr-2" />}
              <span className="text-sm font-medium">{toast.message}</span>
          </div>
      )}

      <div className="flex flex-col sm:flex-row justify-between items-start sm:items-center gap-4">
        <div>
          <h1 className="text-2xl font-bold text-slate-900 dark:text-white">{t('pricing.title')}</h1>
          <p className="text-slate-500 dark:text-slate-400 mt-1">{t('pricing.subtitle')}</p>
        </div>
         <div className="flex space-x-2">
            <button 
                onClick={handleSync}
                disabled={isSyncing}
                className="flex items-center px-3 py-2 bg-indigo-600 hover:bg-indigo-700 text-white border border-transparent rounded-lg text-sm font-medium shadow-sm transition-colors disabled:opacity-70 disabled:cursor-not-allowed"
            >
                <RefreshCw size={16} className={`mr-2 ${isSyncing ? 'animate-spin' : ''}`} />
                {isSyncing ? t('pricing.sync') + '...' : t('pricing.sync')}
            </button>
        </div>
      </div>

      {/* Search Bar */}
      <div className="bg-white dark:bg-slate-800 p-4 rounded-xl border border-slate-200 dark:border-slate-700 shadow-sm flex flex-col md:flex-row gap-4">
        <div className="relative flex-1">
            <div className="absolute inset-y-0 left-0 pl-3 flex items-center pointer-events-none">
                <Search size={18} className="text-slate-400" />
            </div>
            <input 
                type="text" 
                value={searchTerm}
                onChange={(e) => setSearchTerm(e.target.value)}
                className="block w-full pl-10 pr-3 py-2 border border-slate-300 dark:border-slate-600 rounded-lg leading-5 bg-white dark:bg-slate-900 text-slate-900 dark:text-white placeholder-slate-400 focus:outline-none focus:ring-2 focus:ring-indigo-500 focus:border-indigo-500 sm:text-sm transition-shadow" 
                placeholder={t('pricing.searchPlaceholder')}
            />
        </div>
        <div>
             <select 
                value={pagination.size}
                onChange={handleSizeChange}
                className="block w-full pl-3 pr-10 py-2 text-base border-slate-300 dark:border-slate-600 focus:outline-none focus:ring-indigo-500 focus:border-indigo-500 sm:text-sm rounded-lg border bg-white dark:bg-slate-900 text-slate-900 dark:text-white cursor-pointer"
            >
                <option value="12">12 / page</option>
                <option value="24">24 / page</option>
                <option value="48">48 / page</option>
                <option value="100">100 / page</option>
            </select>
        </div>
      </div>

      {/* Content Area */}
      <div className="flex-1 flex flex-col min-h-0">
        {isLoading && models.length === 0 ? (
           <CardGridSkeleton count={8} />
        ) : (
            <>
            {/* Grid Layout */}
            <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 xl:grid-cols-4 2xl:grid-cols-5 gap-5 mb-6">
                {models.length === 0 ? (
                    <div className="col-span-full py-12 text-center bg-white dark:bg-slate-800 rounded-xl border border-dashed border-slate-300 dark:border-slate-700 animate-fade-in">
                        <p className="text-slate-500 dark:text-slate-400">No models found matching your criteria.</p>
                    </div>
                ) : (
                    models.map((model, index) => (
                        <SlideInItem key={`${model.modelName}-${model.provider}-${index}`} index={index}>
                        <div className="group bg-white dark:bg-slate-800 rounded-xl border border-slate-200 dark:border-slate-700 shadow-sm hover:shadow-md hover:border-indigo-300 dark:hover:border-indigo-700 transition-all duration-200 flex flex-col h-full overflow-hidden">
                            <div className="p-5 flex flex-col h-full">
                                {/* Header */}
                                <div className="flex justify-between items-start mb-3">
                                    <div className="flex-1 pr-2">
                                        <h3 className="font-bold text-slate-900 dark:text-white text-lg leading-tight break-words" title={model.modelName}>
                                            {model.modelName}
                                        </h3>
                                        <div className="mt-1.5 flex items-center gap-2">
                                            <span className="inline-flex items-center px-2 py-0.5 rounded text-[10px] font-medium bg-slate-100 dark:bg-slate-700 text-slate-600 dark:text-slate-300 border border-slate-200 dark:border-slate-600 uppercase tracking-wide">
                                                {model.provider}
                                            </span>
                                            {/* Capabilities Icons */}
                                            <div className="flex gap-1">
                                                {model.isReasoning && (
                                                    <span className="text-purple-600 dark:text-purple-400" title={t('pricing.capabilities.reasoning')}>
                                                        <BrainCircuit size={14} />
                                                    </span>
                                                )}
                                                {model.isToolCall && (
                                                    <span className="text-blue-600 dark:text-blue-400" title={t('pricing.capabilities.toolCall')}>
                                                        <Wrench size={14} />
                                                    </span>
                                                )}
                                            </div>
                                        </div>
                                    </div>
                                </div>

                                {/* Pricing Box */}
                                <div className="grid grid-cols-2 gap-px bg-slate-100 dark:bg-slate-700/50 border border-slate-100 dark:border-slate-700 rounded-lg overflow-hidden mb-4">
                                    <div className="bg-slate-50 dark:bg-slate-800 p-2.5 text-center">
                                        <div className="text-[10px] text-slate-400 uppercase tracking-wider font-semibold mb-0.5">{t('pricing.table.inputPrice')}</div>
                                        <div className="font-mono text-sm font-bold text-slate-700 dark:text-slate-300">{formatPrice(model.inputPrice)}</div>
                                        <div className="text-[10px] text-slate-400">/ 1M tokens</div>
                                    </div>
                                    <div className="bg-slate-50 dark:bg-slate-800 p-2.5 text-center">
                                        <div className="text-[10px] text-slate-400 uppercase tracking-wider font-semibold mb-0.5">{t('pricing.table.outputPrice')}</div>
                                        <div className="font-mono text-sm font-bold text-slate-700 dark:text-slate-300">{formatPrice(model.outputPrice)}</div>
                                        <div className="text-[10px] text-slate-400">/ 1M tokens</div>
                                    </div>
                                </div>

                                {/* Specs */}
                                <div className="space-y-2 mb-4">
                                    <div className="flex justify-between items-center text-sm">
                                        <div className="flex items-center text-slate-500 dark:text-slate-400" title="Context Window">
                                            <Database size={14} className="mr-1.5" />
                                            <span className="text-xs">Context</span>
                                        </div>
                                        <span className="font-mono text-slate-700 dark:text-slate-300 font-medium">
                                            {model.contextLimit > 0 ? (model.contextLimit / 1000).toFixed(0) + 'k' : 'N/A'}
                                        </span>
                                    </div>
                                    <div className="flex justify-between items-center text-sm">
                                        <div className="flex items-center text-slate-500 dark:text-slate-400" title="Max Output">
                                            <Cpu size={14} className="mr-1.5" />
                                            <span className="text-xs">Max Output</span>
                                        </div>
                                        <span className="font-mono text-slate-700 dark:text-slate-300 font-medium">
                                            {model.outputLimit > 0 ? (model.outputLimit / 1000).toFixed(0) + 'k' : 'N/A'}
                                        </span>
                                    </div>
                                </div>
                                
                                {/* Input Types */}
                                <div className="mt-auto">
                                    <div className="flex flex-wrap gap-1.5">
                                        {model.inputType.split(',').map((type) => (
                                            <span key={type} className="inline-flex items-center px-2 py-0.5 rounded text-[10px] font-medium bg-white dark:bg-slate-900 text-slate-500 dark:text-slate-400 border border-slate-200 dark:border-slate-700 capitalize">
                                                {type}
                                            </span>
                                        ))}
                                    </div>
                                </div>
                            </div>
                            
                            {/* Footer */}
                            <div className="bg-slate-50 dark:bg-slate-900/50 px-5 py-2.5 border-t border-slate-100 dark:border-slate-700 flex justify-between items-center text-xs text-slate-400">
                                <div className="flex items-center">
                                    <Calendar size={12} className="mr-1.5" />
                                    <span>{model.lastUpdatedAt}</span>
                                </div>
                            </div>
                        </div>
                        </SlideInItem>
                    ))
                )}
            </div>

            <div className="mt-auto">
                <Pagination 
                    current={pagination.current}
                    size={pagination.size}
                    total={pagination.total}
                    onChange={handlePageChange}
                    className="border-t border-slate-200 dark:border-slate-800 mt-4 pt-4"
                />
            </div>
            </>
        )}
      </div>
    </div>
  );
};