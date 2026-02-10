import React, { useState, useEffect } from 'react';
import { Search, Loader2, BrainCircuit, Wrench, RefreshCw, Database, Cpu, Calendar, Tag } from 'lucide-react';
import { useLanguage } from './LanguageContext';
import { modelService } from '../services/modelService';
import { ModelPrice } from '../types';
import { CardGridSkeleton } from './Skeletons';
import { SlideInItem } from './Animations';
import { Pagination } from './Pagination';
import { useToast } from './ToastContext';

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

    const { showToast } = useToast();

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
        setPagination(prev => ({ ...prev, size: newSize })); // Update state immediately for UI
        fetchModels(1, newSize, searchTerm);
    };

    const formatPrice = (price: number) => {
        if (price === 0) return <span className="text-emerald-600 dark:text-emerald-400 font-bold">Free</span>;
        return `$${price.toFixed(2)}`;
    };

    return (
        <div className="space-y-6 relative flex flex-col h-full">


            <div className="flex flex-col sm:flex-row justify-between items-start sm:items-center gap-4">
                <div>
                    <h1 className="text-3xl font-extrabold text-slate-900 dark:text-white tracking-tight">{t('pricing.title')}</h1>
                    <p className="text-slate-500 dark:text-slate-400 mt-2 text-lg">{t('pricing.subtitle')}</p>
                </div>
                <div className="flex space-x-2">
                    <button
                        onClick={handleSync}
                        disabled={isSyncing}
                        className="group flex items-center px-4 py-2 bg-white/60 dark:bg-slate-800/60 backdrop-blur-md text-indigo-600 dark:text-indigo-400 border border-indigo-200 dark:border-indigo-800/50 rounded-xl text-sm font-semibold shadow-sm hover:shadow-md transition-all hover:bg-white hover:-translate-y-0.5 disabled:opacity-70 disabled:cursor-not-allowed"
                    >
                        <RefreshCw size={16} className={`mr-2 group-hover:rotate-180 transition-transform duration-500 ${isSyncing ? 'animate-spin' : ''}`} />
                        {isSyncing ? t('pricing.sync') + '...' : t('pricing.sync')}
                    </button>
                </div>
            </div>

            {/* Search Bar */}
            <div className="bg-white/60 dark:bg-slate-900/60 backdrop-blur-md p-4 rounded-2xl border border-white/20 dark:border-slate-700/50 shadow-sm flex flex-col md:flex-row gap-4">
                <div className="relative flex-1">
                    <div className="absolute inset-y-0 left-0 pl-4 flex items-center pointer-events-none">
                        <Search size={18} className="text-slate-400" />
                    </div>
                    <input
                        type="text"
                        value={searchTerm}
                        onChange={(e) => setSearchTerm(e.target.value)}
                        className="block w-full pl-11 pr-4 py-2.5 border border-slate-200 dark:border-slate-700 rounded-xl leading-5 bg-white/50 dark:bg-slate-900/50 text-slate-900 dark:text-white placeholder-slate-400 focus:outline-none focus:ring-2 focus:ring-indigo-500 focus:border-transparent transition-all sm:text-sm"
                        placeholder={t('pricing.searchPlaceholder')}
                    />
                </div>
                <div>
                    <select
                        value={pagination.size}
                        onChange={handleSizeChange}
                        className="block w-full pl-3 pr-10 py-2.5 text-base border-slate-200 dark:border-slate-700 focus:outline-none focus:ring-2 focus:ring-indigo-500 focus:border-transparent sm:text-sm rounded-xl border bg-white/50 dark:bg-slate-900/50 text-slate-900 dark:text-white cursor-pointer"
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
                        <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 xl:grid-cols-4 2xl:grid-cols-5 gap-6 mb-6">
                            {models.length === 0 ? (
                                <div className="col-span-full py-20 text-center bg-white/60 dark:bg-slate-900/60 backdrop-blur-md rounded-2xl border border-dashed border-slate-300 dark:border-slate-700 animate-fade-in">
                                    <Tag size={48} className="mx-auto text-slate-300 mb-4" />
                                    <p className="text-lg font-medium text-slate-500 dark:text-slate-400">{t('pricing.noModels')}</p>
                                    <p className="text-sm text-slate-400 mt-1">{t('pricing.adjustSearch')}</p>
                                </div>
                            ) : (
                                models.map((model, index) => (
                                    <SlideInItem key={`${model.modelName}-${model.provider}-${index}`} index={index}>
                                        <div className="group bg-white/70 dark:bg-slate-900/70 backdrop-blur-md rounded-2xl border border-white/20 dark:border-slate-700/50 shadow-sm hover:shadow-xl hover:border-indigo-200 dark:hover:border-indigo-800 transition-all duration-300 flex flex-col h-full overflow-hidden hover:-translate-y-1">
                                            <div className="p-5 flex flex-col h-full">
                                                {/* Header */}
                                                <div className="flex justify-between items-start mb-4">
                                                    <div className="flex-1 pr-2">
                                                        <h3 className="font-bold text-slate-900 dark:text-white text-lg leading-tight break-words" title={model.modelName}>
                                                            {model.modelName}
                                                        </h3>
                                                        <div className="mt-2 flex items-center gap-2">
                                                            <span className="inline-flex items-center px-2.5 py-1 rounded-md text-[10px] font-bold bg-slate-100 dark:bg-slate-800 text-slate-600 dark:text-slate-300 border border-slate-200 dark:border-slate-700 uppercase tracking-wide">
                                                                {model.provider}
                                                            </span>
                                                            {/* Capabilities Icons */}
                                                            <div className="flex gap-1.5">
                                                                {model.isReasoning && (
                                                                    <div className="p-1 bg-purple-100 dark:bg-purple-900/30 rounded text-purple-600 dark:text-purple-400" title={t('pricing.capabilities.reasoning')}>
                                                                        <BrainCircuit size={12} />
                                                                    </div>
                                                                )}
                                                                {model.isToolCall && (
                                                                    <div className="p-1 bg-blue-100 dark:bg-blue-900/30 rounded text-blue-600 dark:text-blue-400" title={t('pricing.capabilities.toolCall')}>
                                                                        <Wrench size={12} />
                                                                    </div>
                                                                )}
                                                            </div>
                                                        </div>
                                                    </div>
                                                </div>

                                                {/* Pricing Box */}
                                                <div className="grid grid-cols-2 gap-px bg-slate-200/50 dark:bg-slate-700/50 border border-slate-200/50 dark:border-slate-700/50 rounded-xl overflow-hidden mb-5">
                                                    <div className="bg-slate-50/80 dark:bg-slate-800/80 p-3 text-center backdrop-blur-sm">
                                                        <div className="text-[10px] text-slate-400 uppercase tracking-wider font-bold mb-0.5">{t('pricing.table.inputPrice')}</div>
                                                        <div className="font-mono text-sm font-bold text-slate-800 dark:text-slate-200">{formatPrice(model.inputPrice)}</div>
                                                        <div className="text-[10px] text-slate-400/80">/ 1M tokens</div>
                                                    </div>
                                                    <div className="bg-slate-50/80 dark:bg-slate-800/80 p-3 text-center backdrop-blur-sm">
                                                        <div className="text-[10px] text-slate-400 uppercase tracking-wider font-bold mb-0.5">{t('pricing.table.outputPrice')}</div>
                                                        <div className="font-mono text-sm font-bold text-slate-800 dark:text-slate-200">{formatPrice(model.outputPrice)}</div>
                                                        <div className="text-[10px] text-slate-400/80">/ 1M tokens</div>
                                                    </div>
                                                </div>

                                                {/* Specs */}
                                                <div className="space-y-2 mb-4 bg-white/50 dark:bg-slate-800/30 rounded-lg p-3">
                                                    <div className="flex justify-between items-center text-sm">
                                                        <div className="flex items-center text-slate-500 dark:text-slate-400" title="Context Window">
                                                            <Database size={14} className="mr-2 text-indigo-400" />
                                                            <span className="text-xs font-medium">Context</span>
                                                        </div>
                                                        <span className="font-mono text-slate-700 dark:text-slate-300 font-bold text-xs">
                                                            {model.contextLimit > 0 ? (model.contextLimit / 1000).toFixed(0) + 'k' : 'N/A'}
                                                        </span>
                                                    </div>
                                                    <div className="flex justify-between items-center text-sm">
                                                        <div className="flex items-center text-slate-500 dark:text-slate-400" title="Max Output">
                                                            <Cpu size={14} className="mr-2 text-violet-400" />
                                                            <span className="text-xs font-medium">Max Output</span>
                                                        </div>
                                                        <span className="font-mono text-slate-700 dark:text-slate-300 font-bold text-xs">
                                                            {model.outputLimit > 0 ? (model.outputLimit / 1000).toFixed(0) + 'k' : 'N/A'}
                                                        </span>
                                                    </div>
                                                </div>

                                                {/* Input Types */}
                                                <div className="mt-auto pt-2">
                                                    <div className="flex flex-wrap gap-1.5">
                                                        {model.inputType.split(',').map((type) => (
                                                            <span key={type} className="inline-flex items-center px-2 py-0.5 rounded text-[10px] font-semibold bg-slate-100 dark:bg-slate-800 text-slate-500 dark:text-slate-400 border border-slate-200 dark:border-slate-700 capitalize">
                                                                {type}
                                                            </span>
                                                        ))}
                                                    </div>
                                                </div>
                                            </div>

                                            {/* Footer */}
                                            <div className="bg-slate-50/80 dark:bg-slate-900/50 px-5 py-3 border-t border-slate-100 dark:border-slate-800/50 flex justify-between items-center text-xs text-slate-400 font-medium">
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
                                className="border-t border-slate-200/50 dark:border-slate-700/50 mt-4 pt-4"
                            />
                        </div>
                    </>
                )}
            </div>
        </div>
    );
};