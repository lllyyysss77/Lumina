import React, { useState, useEffect } from 'react';
import { Activity, X, Loader2, ZapOff } from 'lucide-react';
import { useLanguage } from './LanguageContext';
import { circuitBreakerService } from '../services/circuitBreakerService';
import { CircuitBreakerStatus, CircuitState } from '../types';
import { useToast } from './ToastContext';

interface CircuitBreakerSectionProps {
    username: string;
}

export const CircuitBreakerSection: React.FC<CircuitBreakerSectionProps> = ({ username }) => {
    const { t } = useLanguage();
    const { showToast } = useToast();

    const [circuitBreakers, setCircuitBreakers] = useState<CircuitBreakerStatus[]>([]);
    const [isCBListLoading, setIsCBListLoading] = useState(false);
    const [isCBModalOpen, setIsCBModalOpen] = useState(false);
    const [selectedCB, setSelectedCB] = useState<CircuitBreakerStatus | null>(null);
    const [cbTargetState, setCbTargetState] = useState<CircuitState>('CLOSED');
    const [cbDuration, setCbDuration] = useState<number>(60000);
    const [cbReason, setCbReason] = useState('');
    const [isCBSaving, setIsCBSaving] = useState(false);

    const fetchCircuitBreakers = async () => {
        setIsCBListLoading(true);
        try {
            const data = await circuitBreakerService.getList();
            setCircuitBreakers(data);
        } catch (error) {
            console.error("Failed to fetch circuit breakers", error);
            showToast(t('settings.circuitBreaker.fetchFail'), 'error');
        } finally {
            setIsCBListLoading(false);
        }
    };

    useEffect(() => {
        fetchCircuitBreakers();
    }, []);

    const openControlModal = (cb: CircuitBreakerStatus) => {
        setSelectedCB(cb);
        setCbTargetState(cb.circuitState === 'CLOSED' ? 'OPEN' : 'CLOSED');
        setCbReason('');
        setCbDuration(60000);
        setIsCBModalOpen(true);
    };

    const handleControlSubmit = async (e: React.FormEvent) => {
        e.preventDefault();
        if (!selectedCB) return;

        setIsCBSaving(true);
        try {
            await circuitBreakerService.control({
                providerId: selectedCB.providerId,
                targetState: cbTargetState,
                reason: cbReason,
                durationMs: cbTargetState === 'OPEN' ? cbDuration : undefined
            }, username || 'admin');

            showToast(t('settings.circuitBreaker.updateSuccess'), 'success');
            setIsCBModalOpen(false);
            fetchCircuitBreakers();
        } catch (error: any) {
            console.error("Failed to control circuit breaker", error);
            showToast(t('settings.circuitBreaker.updateFail'), 'error');
        } finally {
            setIsCBSaving(false);
        }
    };

    const handleReleaseControl = async (providerId: string) => {
        try {
            await circuitBreakerService.release(providerId);
            showToast(t('settings.circuitBreaker.releaseSuccess'), 'success');
            fetchCircuitBreakers();
        } catch (error) {
            console.error("Failed to release control", error);
            showToast(t('settings.circuitBreaker.releaseFail'), 'error');
        }
    };

    const getCBStateColor = (state: string) => {
        switch (state) {
            case 'CLOSED': return 'bg-emerald-100 text-emerald-700 dark:bg-emerald-900/30 dark:text-emerald-400';
            case 'OPEN': return 'bg-red-100 text-red-700 dark:bg-red-900/30 dark:text-red-400';
            case 'HALF_OPEN': return 'bg-amber-100 text-amber-700 dark:bg-amber-900/30 dark:text-amber-400';
            default: return 'bg-slate-100 text-slate-700 dark:bg-slate-800 dark:text-slate-400';
        }
    };

    return (
        <>
            <div className="bg-white/60 dark:bg-slate-900/60 backdrop-blur-md rounded-2xl border border-white/20 dark:border-slate-700/50 p-6 sm:p-8 shadow-sm hover:shadow-md transition-shadow">
                <div className="flex flex-col sm:flex-row sm:items-start justify-between gap-6">
                    <div className="flex items-start space-x-5 w-full">
                        <div className="p-3 bg-orange-100 dark:bg-orange-900/40 text-orange-600 dark:text-orange-400 rounded-xl flex-shrink-0">
                            <ZapOff size={24} />
                        </div>
                        <div className="flex-1 min-w-0">
                            <div className="flex justify-between items-center mb-5">
                                <div>
                                    <h3 className="text-lg font-bold text-slate-900 dark:text-white">{t('settings.circuitBreaker.title')}</h3>
                                    <p className="text-sm text-slate-500 dark:text-slate-400 mt-1">
                                        {t('settings.circuitBreaker.desc')}
                                    </p>
                                </div>
                                <button
                                    onClick={fetchCircuitBreakers}
                                    className="p-2 text-slate-500 hover:text-indigo-600 dark:text-slate-400 dark:hover:text-indigo-400 rounded-lg hover:bg-slate-50 dark:hover:bg-slate-800 transition-colors"
                                    title={t('settings.circuitBreaker.refresh')}
                                >
                                    <Activity size={20} className={isCBListLoading ? 'animate-spin' : ''} />
                                </button>
                            </div>

                            <div className="overflow-x-auto border border-slate-200/50 dark:border-slate-700/50 rounded-xl shadow-sm">
                                <table className="min-w-full divide-y divide-slate-100 dark:divide-slate-800">
                                    <thead className="bg-slate-50/50 dark:bg-slate-900/50">
                                        <tr>
                                            <th className="px-5 py-3 text-left text-xs font-bold text-slate-500 dark:text-slate-400 uppercase tracking-wider">{t('settings.circuitBreaker.table.provider')}</th>
                                            <th className="px-5 py-3 text-left text-xs font-bold text-slate-500 dark:text-slate-400 uppercase tracking-wider">{t('settings.circuitBreaker.table.state')}</th>
                                            <th className="px-5 py-3 text-left text-xs font-bold text-slate-500 dark:text-slate-400 uppercase tracking-wider">{t('settings.circuitBreaker.table.score')}</th>
                                            <th className="px-5 py-3 text-left text-xs font-bold text-slate-500 dark:text-slate-400 uppercase tracking-wider hidden sm:table-cell">{t('settings.circuitBreaker.table.stats')}</th>
                                            <th className="px-5 py-3 text-right text-xs font-bold text-slate-500 dark:text-slate-400 uppercase tracking-wider">{t('settings.circuitBreaker.table.control')}</th>
                                        </tr>
                                    </thead>
                                    <tbody className="bg-white/50 dark:bg-slate-800/50 divide-y divide-slate-100 dark:divide-slate-800">
                                        {isCBListLoading && circuitBreakers.length === 0 ? (
                                            <tr><td colSpan={5} className="p-6 text-center text-sm text-slate-500">{t('settings.circuitBreaker.loading')}</td></tr>
                                        ) : circuitBreakers.length === 0 ? (
                                            <tr><td colSpan={5} className="p-6 text-center text-sm text-slate-500">{t('settings.circuitBreaker.noProviders')}</td></tr>
                                        ) : (
                                            circuitBreakers.map((cb) => (
                                                <tr key={cb.providerId} className="hover:bg-indigo-50/20 dark:hover:bg-indigo-900/10 transition-colors">
                                                    <td className="px-5 py-3.5 whitespace-nowrap text-sm font-semibold text-slate-900 dark:text-white">
                                                        {cb.providerName}
                                                        {cb.manuallyControlled && (
                                                            <span className="ml-2 inline-flex items-center px-1.5 py-0.5 rounded text-[10px] font-bold bg-purple-100 text-purple-700 dark:bg-purple-900/30 dark:text-purple-400 border border-purple-200 dark:border-purple-800" title={cb.manualControlReason || 'Manual Control'}>
                                                                MANUAL
                                                            </span>
                                                        )}
                                                    </td>
                                                    <td className="px-5 py-3.5 whitespace-nowrap">
                                                        <span className={`px-2.5 py-0.5 inline-flex text-xs leading-5 font-bold rounded-full border ${getCBStateColor(cb.circuitState)}`}>
                                                            {t(`settings.circuitBreaker.states.${cb.circuitState}`)}
                                                        </span>
                                                    </td>
                                                    <td className="px-5 py-3.5 whitespace-nowrap text-sm text-slate-500 dark:text-slate-400 font-mono">
                                                        {cb.score.toFixed(1)}
                                                    </td>
                                                    <td className="px-5 py-3.5 whitespace-nowrap text-xs text-slate-500 dark:text-slate-400 hidden sm:table-cell">
                                                        <span className="text-red-500 font-medium">{Math.round(cb.errorRate * 100)}% Err</span> <span className="text-slate-300 dark:text-slate-600 mx-1">/</span> <span className="text-amber-500 font-medium">{Math.round(cb.slowRate * 100)}% Slow</span>
                                                    </td>
                                                    <td className="px-5 py-3.5 whitespace-nowrap text-right text-sm font-medium">
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
                                                                className="px-3 py-1 bg-white text-indigo-600 border border-indigo-200 rounded-lg hover:bg-indigo-50 text-xs font-semibold transition-colors dark:bg-slate-800 dark:text-indigo-400 dark:border-indigo-900 dark:hover:bg-indigo-900/20"
                                                            >
                                                                {t('settings.circuitBreaker.actions.manage')}
                                                            </button>
                                                        )}
                                                    </td>
                                                </tr>
                                            ))
                                        )}
                                    </tbody>
                                </table>
                            </div>
                        </div>
                    </div>
                </div>
            </div>

            {/* Circuit Breaker Control Modal */}
            {isCBModalOpen && selectedCB && (
                <div className="fixed inset-0 z-50 flex items-center justify-center bg-slate-900/60 backdrop-blur-sm p-4 animate-fade-in">
                    <div className="bg-white dark:bg-slate-900 rounded-2xl shadow-2xl max-w-md w-full animate-in zoom-in-95 duration-200 border border-white/20 dark:border-slate-700">
                        <div className="px-6 py-4 border-b border-slate-100 dark:border-slate-800 flex justify-between items-center bg-white/95 dark:bg-slate-900/95 backdrop-blur rounded-t-2xl">
                            <h3 className="text-lg font-bold text-slate-900 dark:text-white">{t('settings.circuitBreaker.controlModal.title')}</h3>
                            <button onClick={() => setIsCBModalOpen(false)} className="text-slate-400 hover:text-slate-600 dark:hover:text-slate-300 rounded-full p-1 hover:bg-slate-100 dark:hover:bg-slate-800">
                                <X size={20} />
                            </button>
                        </div>
                        <div className="p-6">
                            <div className="mb-6">
                                <span className="text-xs font-bold text-slate-500 dark:text-slate-400 uppercase tracking-wide">{t('settings.circuitBreaker.targetProvider')}</span>
                                <p className="text-lg font-bold text-slate-900 dark:text-white mt-1">{selectedCB.providerName}</p>
                                {selectedCB.manuallyControlled && (
                                    <div className="mt-3 text-xs bg-purple-50 text-purple-800 dark:bg-purple-900/30 dark:text-purple-300 p-3 rounded-xl border border-purple-100 dark:border-purple-800/50">
                                        <strong className="block mb-1 font-bold">{t('settings.circuitBreaker.controlModal.manualActive')}</strong>
                                        {selectedCB.manualControlReason}
                                    </div>
                                )}
                            </div>
                            <form onSubmit={handleControlSubmit} className="space-y-5">
                                <div>
                                    <label className="block text-sm font-semibold text-slate-700 dark:text-slate-300 mb-1.5">
                                        {t('settings.circuitBreaker.controlModal.targetState')}
                                    </label>
                                    <select
                                        value={cbTargetState}
                                        onChange={(e) => setCbTargetState(e.target.value as CircuitState)}
                                        className="block w-full rounded-xl border-slate-300 dark:border-slate-600 shadow-sm focus:border-indigo-500 focus:ring-indigo-500 text-sm py-2.5 px-3 bg-white dark:bg-slate-950 dark:text-white cursor-pointer"
                                    >
                                        <option value="CLOSED">{t('settings.circuitBreaker.closedOption')}</option>
                                        <option value="OPEN">{t('settings.circuitBreaker.openOption')}</option>
                                        <option value="HALF_OPEN">{t('settings.circuitBreaker.halfOpenOption')}</option>
                                    </select>
                                </div>

                                {cbTargetState === 'OPEN' && (
                                    <div className="animate-in slide-in-from-top-2">
                                        <label className="block text-sm font-semibold text-slate-700 dark:text-slate-300 mb-1.5">
                                            {t('settings.circuitBreaker.controlModal.duration')}
                                        </label>
                                        <input
                                            type="number"
                                            value={cbDuration}
                                            onChange={(e) => setCbDuration(parseInt(e.target.value) || 0)}
                                            className="block w-full rounded-xl border-slate-300 dark:border-slate-600 shadow-sm focus:border-indigo-500 focus:ring-indigo-500 text-sm py-2.5 px-3 bg-white dark:bg-slate-950 dark:text-white"
                                        />
                                    </div>
                                )}

                                <div>
                                    <label className="block text-sm font-semibold text-slate-700 dark:text-slate-300 mb-1.5">
                                        {t('settings.circuitBreaker.controlModal.reason')}
                                    </label>
                                    <textarea
                                        rows={3}
                                        value={cbReason}
                                        onChange={(e) => setCbReason(e.target.value)}
                                        placeholder={t('settings.circuitBreaker.controlModal.reasonPlaceholder')}
                                        className="block w-full rounded-xl border-slate-300 dark:border-slate-600 shadow-sm focus:border-indigo-500 focus:ring-indigo-500 text-sm py-2.5 px-3 bg-white dark:bg-slate-950 dark:text-white resize-none"
                                    />
                                </div>

                                <div className="pt-4 flex justify-end gap-3">
                                    <button
                                        type="button"
                                        onClick={() => setIsCBModalOpen(false)}
                                        className="px-4 py-2.5 border border-slate-300 dark:border-slate-600 shadow-sm text-sm font-semibold rounded-xl text-slate-700 dark:text-slate-200 bg-white dark:bg-slate-800 hover:bg-slate-50 dark:hover:bg-slate-700 transition-colors"
                                    >
                                        {t('common.cancel')}
                                    </button>
                                    <button
                                        type="submit"
                                        disabled={isCBSaving}
                                        className="px-4 py-2.5 bg-indigo-600 hover:bg-indigo-700 text-white text-sm font-semibold rounded-xl shadow-lg shadow-indigo-500/20 flex items-center disabled:opacity-70 disabled:cursor-not-allowed transition-all hover:-translate-y-0.5"
                                    >
                                        {isCBSaving ? <Loader2 size={18} className="animate-spin mr-2" /> : null}
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
