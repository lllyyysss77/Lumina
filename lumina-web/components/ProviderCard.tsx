import React, { useRef, useEffect } from 'react';
import { MoreHorizontal, Edit2, Trash2, Key, Link2, Box, CheckCircle2, AlertTriangle, Activity, DownloadCloud } from 'lucide-react';
import { Provider, ProviderType } from '../types';
import { useLanguage } from './LanguageContext';
import { getProviderLabel } from '../utils/providerUtils';
import { useToast } from './ToastContext';

interface ProviderCardProps {
    provider: Provider;
    isMenuOpen: boolean;
    onToggleMenu: (id: string) => void;
    onEdit: (provider: Provider) => void;
    onDelete: (provider: Provider) => void;
    menuRef?: React.RefObject<HTMLDivElement>;
}

export const ProviderCard: React.FC<ProviderCardProps> = ({ provider, isMenuOpen, onToggleMenu, onEdit, onDelete, menuRef }) => {
    const { t } = useLanguage();
    const { showToast } = useToast();


    // Handle copying API Key
    const copyApiKey = (e: React.MouseEvent) => {
        e.stopPropagation();
        navigator.clipboard.writeText(provider.apiKey);
        showToast('API Key copied to clipboard', 'success');
    };

    return (
        <div className="group relative bg-white dark:bg-slate-900 border border-slate-200 dark:border-slate-800 rounded-2xl p-5 hover:shadow-xl hover:shadow-indigo-500/5 hover:-translate-y-1 transition-all duration-300">
            <div className="flex justify-between items-start mb-4">
                <div className="flex items-center space-x-3">
                    <div className={`w-10 h-10 rounded-xl flex items-center justify-center text-lg font-bold shadow-sm transition-transform group-hover:scale-110 ${provider.type === ProviderType.OPENAI_CHAT || provider.type === ProviderType.OPENAI_RESPONSE
                        ? 'bg-green-100 dark:bg-green-900/30 text-green-600 dark:text-green-400'
                        : provider.type === ProviderType.ANTHROPIC
                            ? 'bg-purple-100 dark:bg-purple-900/30 text-purple-600 dark:text-purple-400'
                            : provider.type === ProviderType.GEMINI
                                ? 'bg-blue-100 dark:bg-blue-900/30 text-blue-600 dark:text-blue-400'
                                : 'bg-indigo-100 dark:bg-indigo-900/30 text-indigo-600 dark:text-indigo-400'
                        }`}>
                        {provider.name.charAt(0).toUpperCase()}
                    </div>
                    <div>
                        <h3 className="text-lg font-bold text-slate-900 dark:text-white leading-tight">{provider.name}</h3>
                        <span className="text-xs font-medium text-slate-500 dark:text-slate-400 flex items-center mt-0.5">
                            {getProviderLabel(provider.type)}
                            <span className={`mx-2 w-1 h-1 rounded-full ${provider.status === 'active' ? 'bg-green-500' : 'bg-slate-300 dark:bg-slate-600'}`}></span>
                            <span className={provider.status === 'active' ? 'text-green-600 dark:text-green-400' : 'text-slate-400'}>
                                {provider.status === 'active' ? t('common.active') : t('common.inactive')}
                            </span>
                        </span>
                    </div>
                </div>

                <div className="relative" ref={menuRef}>
                    <button
                        onClick={(e) => {
                            e.stopPropagation();
                            onToggleMenu(provider.id);
                        }}
                        className={`p-2 rounded-lg transition-colors ${isMenuOpen
                            ? 'bg-indigo-50 dark:bg-indigo-900/30 text-indigo-600 dark:text-indigo-400'
                            : 'text-slate-400 hover:text-slate-600 dark:hover:text-slate-300 hover:bg-slate-100 dark:hover:bg-slate-800'
                            }`}
                    >
                        <MoreHorizontal size={20} />
                    </button>

                    {isMenuOpen && (
                        <div className="absolute right-0 mt-2 w-48 bg-white dark:bg-slate-900 rounded-xl shadow-xl border border-slate-100 dark:border-slate-800 z-10 py-1 animate-in fade-in slide-in-from-top-2">
                            <button
                                onClick={(e) => { e.stopPropagation(); onEdit(provider); }}
                                className="w-full text-left px-4 py-2.5 text-sm font-medium text-slate-700 dark:text-slate-200 hover:bg-slate-50 dark:hover:bg-slate-800 flex items-center"
                            >
                                <Edit2 size={16} className="mr-2 text-slate-400" />
                                {t('common.edit')}
                            </button>
                            <button
                                onClick={(e) => { e.stopPropagation(); onDelete(provider); }}
                                className="w-full text-left px-4 py-2.5 text-sm font-medium text-red-600 dark:text-red-400 hover:bg-red-50 dark:hover:bg-red-900/20 flex items-center"
                            >
                                <Trash2 size={16} className="mr-2" />
                                {t('common.delete')}
                            </button>
                        </div>
                    )}
                </div>
            </div>

            <div className="space-y-3 mb-4">
                <div className="flex items-center text-xs text-slate-500 dark:text-slate-400 bg-slate-50 dark:bg-slate-800/50 p-2 rounded-lg font-mono truncate" title={provider.baseUrl}>
                    <Link2 size={14} className="mr-2 flex-shrink-0" />
                    <span className="truncate">{provider.baseUrl}</span>
                </div>
                <div className="flex items-center justify-between text-xs text-slate-500 dark:text-slate-400 bg-slate-50 dark:bg-slate-800/50 p-2 rounded-lg font-mono">
                    <div className="flex items-center truncate mr-2">
                        <Key size={14} className="mr-2 flex-shrink-0" />
                        <span className="truncate">{provider.apiKey.substring(0, 8)}...</span>
                    </div>
                    <button
                        onClick={copyApiKey}
                        className="text-xs font-bold text-indigo-600 dark:text-indigo-400 hover:underline"
                    >
                        {t('common.copy')}
                    </button>
                </div>
            </div>

            <div className="pt-4 border-t border-slate-100 dark:border-slate-800 flex justify-between items-center">
                <div className="flex items-center gap-1.5">
                    <Box size={14} className="text-slate-400" />
                    <span className="text-xs font-bold text-slate-700 dark:text-slate-300">
                        {provider.models?.length || 0} <span className="font-normal text-slate-500 dark:text-slate-500">models</span>
                    </span>
                </div>
                {provider.autoSync && (
                    <div className="flex items-center gap-1.5 px-2 py-1 bg-indigo-50 dark:bg-indigo-900/20 text-indigo-600 dark:text-indigo-400 rounded-md">
                        <DownloadCloud size={12} />
                        <span className="text-[10px] font-bold uppercase tracking-wider">Auto Sync</span>
                    </div>
                )}
            </div>
        </div>
    );
};
