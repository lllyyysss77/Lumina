import React, { useState, useEffect } from 'react';
import { Plus, Trash2, Copy, Check, X, Key, Loader2 } from 'lucide-react';
import { useLanguage } from './LanguageContext';
import { tokenService } from '../services/tokenService';
import { AccessToken } from '../types';
import { useToast } from './ToastContext';

interface TokenManageModalProps {
    isOpen: boolean;
    onClose: () => void;
}

export const TokenManageModal: React.FC<TokenManageModalProps> = ({ isOpen, onClose }) => {
    const { t } = useLanguage();
    const { showToast } = useToast();

    const [tokens, setTokens] = useState<AccessToken[]>([]);
    const [isTokensLoading, setIsTokensLoading] = useState(false);
    const [isCreatingToken, setIsCreatingToken] = useState(false);
    const [newTokenName, setNewTokenName] = useState('');
    const [createdToken, setCreatedToken] = useState<AccessToken | null>(null);
    const [copyFeedbackId, setCopyFeedbackId] = useState<string | null>(null);
    const [revokeId, setRevokeId] = useState<string | null>(null);

    const fetchTokens = async () => {
        setIsTokensLoading(true);
        try {
            const data = await tokenService.getList();
            setTokens(data);
        } catch (error) {
            console.error("Failed to fetch tokens", error);
        } finally {
            setIsTokensLoading(false);
        }
    };

    useEffect(() => {
        if (isOpen) {
            fetchTokens();
        }
    }, [isOpen]);

    const handleCreateToken = async (e: React.FormEvent) => {
        e.preventDefault();
        if (!newTokenName.trim()) return;

        setIsCreatingToken(true);
        try {
            const newToken = await tokenService.create(newTokenName);
            setCreatedToken(newToken);
            fetchTokens();
        } catch (error) {
            console.error("Failed to create token", error);
            showToast(t('settings.tokens.createFail'), 'error');
        } finally {
            setIsCreatingToken(false);
            setNewTokenName('');
        }
    };

    const handleRevokeToken = async (id: string) => {
        try {
            await tokenService.delete(id);
            setRevokeId(null);
            fetchTokens();
            showToast(t('settings.tokens.revokedSuccess'), 'success');
        } catch (error) {
            console.error("Failed to revoke token", error);
            showToast(t('settings.tokens.revokedFail'), 'error');
        }
    };

    const copyToClipboard = (text: string, id: string) => {
        navigator.clipboard.writeText(text);
        setCopyFeedbackId(id);
        setTimeout(() => setCopyFeedbackId(null), 2000);
    };

    const handleClose = () => {
        setCreatedToken(null);
        setNewTokenName('');
        onClose();
    };

    if (!isOpen) return null;

    return (
        <div className="fixed inset-0 z-50 flex items-center justify-center bg-slate-900/60 backdrop-blur-sm p-4 animate-fade-in">
            <div className="bg-white dark:bg-slate-900 rounded-2xl shadow-2xl max-w-2xl w-full max-h-[85vh] flex flex-col animate-in zoom-in-95 duration-200 border border-white/20 dark:border-slate-700">
                <div className="px-6 py-5 border-b border-slate-100 dark:border-slate-800 flex justify-between items-center bg-white/95 dark:bg-slate-900/95 backdrop-blur z-10">
                    <div className="flex items-center gap-3">
                        <div className="p-2 bg-indigo-100 dark:bg-indigo-900/40 text-indigo-600 dark:text-indigo-400 rounded-lg">
                            <Key size={20} />
                        </div>
                        <h2 className="text-xl font-bold text-slate-900 dark:text-white">{t('settings.tokens.title')}</h2>
                    </div>
                    <button onClick={handleClose} className="text-slate-400 hover:text-slate-600 dark:hover:text-slate-300 rounded-full p-1 hover:bg-slate-100 dark:hover:bg-slate-800 transition-colors">
                        <X size={24} />
                    </button>
                </div>

                <div className="p-6 flex-1 overflow-y-auto custom-scrollbar">
                    {/* Create Token Section (Or Success View) */}
                    {createdToken ? (
                        <div className="mb-8 p-6 bg-emerald-50 dark:bg-emerald-900/20 border border-emerald-200 dark:border-emerald-800 rounded-2xl animate-in fade-in zoom-in-95">
                            <div className="flex items-start gap-4">
                                <CheckCircleIcon className="text-emerald-600 dark:text-emerald-500 mt-1 flex-shrink-0 w-6 h-6" />
                                <div className="flex-1 min-w-0">
                                    <h3 className="text-base font-bold text-emerald-800 dark:text-emerald-400 mb-1">{t('settings.tokens.tokenCreated')}</h3>
                                    <p className="text-sm text-emerald-700 dark:text-emerald-500 mb-4">{t('settings.tokens.copyWarning')}</p>

                                    <div className="flex items-center gap-2">
                                        <code className="flex-1 bg-white dark:bg-slate-950 border border-emerald-200 dark:border-emerald-800/50 px-4 py-3 rounded-xl font-mono text-sm text-slate-800 dark:text-slate-200 break-all shadow-sm">
                                            {createdToken.token}
                                        </code>
                                        <button
                                            onClick={() => copyToClipboard(createdToken.token || '', 'new')}
                                            className="p-3 bg-white dark:bg-slate-950 border border-emerald-200 dark:border-emerald-800/50 text-emerald-600 dark:text-emerald-500 rounded-xl hover:bg-emerald-50 dark:hover:bg-emerald-900/30 transition-colors shadow-sm"
                                        >
                                            {copyFeedbackId === 'new' ? <Check size={20} /> : <Copy size={20} />}
                                        </button>
                                    </div>
                                </div>
                            </div>
                            <div className="pl-10 mt-3">
                                <button
                                    onClick={() => setCreatedToken(null)}
                                    className="text-sm font-semibold text-emerald-700 dark:text-emerald-400 hover:text-emerald-900 dark:hover:text-emerald-300 underline underline-offset-2"
                                >
                                    {t('settings.tokens.generateAnother')}
                                </button>
                            </div>
                        </div>
                    ) : (
                        <form onSubmit={handleCreateToken} className="mb-8 p-6 bg-slate-50 dark:bg-slate-800/50 rounded-2xl border border-slate-100 dark:border-slate-700/50">
                            <h3 className="text-sm font-bold text-slate-800 dark:text-slate-200 mb-4 uppercase tracking-wide">{t('settings.tokens.create')}</h3>
                            <div className="flex flex-col sm:flex-row gap-3">
                                <input
                                    type="text"
                                    value={newTokenName}
                                    onChange={(e) => setNewTokenName(e.target.value)}
                                    placeholder={t('settings.tokens.name') + ' (e.g. "Production App")'}
                                    className="flex-1 rounded-xl border-slate-300 dark:border-slate-600 text-sm focus:ring-indigo-500 focus:border-indigo-500 py-2.5 px-4 border bg-white dark:bg-slate-900 dark:text-white transition-shadow"
                                    required
                                />
                                <button
                                    type="submit"
                                    disabled={isCreatingToken || !newTokenName.trim()}
                                    className="px-5 py-2.5 bg-indigo-600 text-white text-sm font-semibold rounded-xl hover:bg-indigo-700 disabled:opacity-70 disabled:cursor-not-allowed flex items-center justify-center shadow-md shadow-indigo-500/20 transition-all hover:-translate-y-0.5"
                                >
                                    {isCreatingToken ? <Loader2 size={18} className="animate-spin" /> : <Plus size={18} className="mr-2" />}
                                    {t('common.add')}
                                </button>
                            </div>
                        </form>
                    )}

                    {/* Token List */}
                    <div>
                        <h3 className="text-sm font-bold text-slate-900 dark:text-white mb-4 uppercase tracking-wide">{t('settings.tokens.activeTokens')}</h3>
                        {isTokensLoading ? (
                            <div className="flex justify-center py-12">
                                <Loader2 className="w-8 h-8 text-indigo-600 animate-spin" />
                            </div>
                        ) : tokens.length === 0 ? (
                            <div className="text-center py-12 text-slate-500 dark:text-slate-400 bg-white dark:bg-slate-800/50 border border-dashed border-slate-200 dark:border-slate-700 rounded-2xl">
                                {t('settings.tokens.empty')}
                            </div>
                        ) : (
                            <div className="space-y-3">
                                {tokens.map(token => (
                                    <div key={token.id} className="flex flex-col sm:flex-row sm:items-center justify-between p-4 bg-white dark:bg-slate-800/80 border border-slate-200 dark:border-slate-700/80 rounded-xl hover:border-indigo-300 dark:hover:border-indigo-700 hover:shadow-md transition-all">
                                        <div className="mb-3 sm:mb-0">
                                            <div className="flex items-center gap-3 flex-wrap">
                                                <span className="font-bold text-slate-800 dark:text-slate-200">{token.name}</span>
                                                <span className="text-xs font-mono bg-slate-100 dark:bg-slate-900 text-slate-500 dark:text-slate-400 px-2 py-0.5 rounded border border-slate-200 dark:border-slate-700 whitespace-nowrap">
                                                    {token.maskedToken}
                                                </span>
                                                <button
                                                    onClick={() => copyToClipboard(token.token || '', token.id)}
                                                    className="p-1.5 text-slate-400 hover:text-indigo-600 dark:hover:text-indigo-400 hover:bg-indigo-50 dark:hover:bg-indigo-900/30 rounded-lg transition-colors"
                                                    title={t('common.copy')}
                                                >
                                                    {copyFeedbackId === token.id ? <Check size={16} className="text-green-600" /> : <Copy size={16} />}
                                                </button>
                                            </div>
                                            <div className="flex gap-4 mt-2 text-xs text-slate-500 dark:text-slate-400">
                                                <span>{t('settings.tokens.created')}: {new Date(token.createdAt).toLocaleDateString()}</span>
                                                <span>{t('settings.tokens.lastUsed')}: {token.lastUsedAt ? new Date(token.lastUsedAt).toLocaleDateString() : '-'}</span>
                                            </div>
                                        </div>

                                        {revokeId === token.id ? (
                                            <div className="flex items-center gap-2 bg-red-50 dark:bg-red-900/20 p-2 rounded-xl border border-red-100 dark:border-red-900/50 animate-in fade-in slide-in-from-right-2 mt-2 sm:mt-0">
                                                <span className="text-xs text-red-700 dark:text-red-400 font-bold px-1">{t('settings.tokens.confirmRevokeShort')}</span>
                                                <button
                                                    onClick={() => handleRevokeToken(token.id)}
                                                    className="p-1.5 bg-white dark:bg-slate-800 text-red-600 dark:text-red-400 rounded-lg shadow-sm hover:bg-red-100 dark:hover:bg-red-900/30 transition-colors"
                                                >
                                                    <Check size={16} />
                                                </button>
                                                <button
                                                    onClick={() => setRevokeId(null)}
                                                    className="p-1.5 bg-white dark:bg-slate-800 text-slate-500 dark:text-slate-400 rounded-lg shadow-sm hover:bg-slate-100 dark:hover:bg-slate-700 transition-colors"
                                                >
                                                    <X size={16} />
                                                </button>
                                            </div>
                                        ) : (
                                            <button
                                                onClick={() => setRevokeId(token.id)}
                                                className="self-start sm:self-center p-2 text-slate-400 hover:text-red-600 hover:bg-red-50 dark:hover:bg-red-900/20 rounded-lg transition-colors mt-2 sm:mt-0"
                                                title={t('common.delete')}
                                            >
                                                <Trash2 size={20} />
                                            </button>
                                        )}
                                    </div>
                                ))}
                            </div>
                        )}
                    </div>
                </div>
            </div>
        </div>
    );
};

const CheckCircleIcon = ({ className }: { className?: string }) => (
    <svg xmlns="http://www.w3.org/2000/svg" width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" className={className}>
        <path d="M22 11.08V12a10 10 0 1 1-5.93-9.14"></path>
        <polyline points="22 4 12 14.01 9 11.01"></polyline>
    </svg>
);
