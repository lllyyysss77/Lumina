import React, { useState, useEffect, useRef } from 'react';
import { RefreshCcw, X, Save, Trash2, Loader2 } from 'lucide-react';
import { Provider, ProviderType } from '../types';
import { useLanguage } from './LanguageContext';
import { useToast } from './ToastContext';
import { providerService } from '../services/providerService';
import { getProviderLabel } from '../utils/providerUtils';

interface ProviderFormModalProps {
    isOpen: boolean;
    onClose: () => void;
    initialData?: Provider | null;
    onSubmit: (data: Partial<Provider>) => Promise<void>;
}

const StatusSwitch = ({ checked, onChange, disabled = false, label }: { checked: boolean; onChange: (checked: boolean) => void; disabled?: boolean, label?: string }) => (
    <div className="flex items-center cursor-pointer group" onClick={(e) => {
        e.stopPropagation();
        if (!disabled) onChange(!checked);
    }}>
        <button
            type="button"
            role="switch"
            aria-checked={checked}
            className={`${checked ? 'bg-green-500 shadow-green-200' : 'bg-slate-300 dark:bg-slate-600'
                } relative inline-flex h-6 w-11 flex-shrink-0 cursor-pointer rounded-full border-2 border-transparent transition-all duration-200 ease-in-out focus:outline-none focus:ring-2 focus:ring-indigo-500 focus:ring-offset-2 disabled:opacity-50`}
        >
            <span
                aria-hidden="true"
                className={`${checked ? 'translate-x-5' : 'translate-x-0'
                    } pointer-events-none inline-block h-5 w-5 transform rounded-full bg-white shadow ring-0 transition duration-200 ease-in-out`}
            />
        </button>
        {label && <span className={`ml-2 text-sm font-medium transition-colors ${checked ? 'text-green-600 dark:text-green-400' : 'text-slate-500 dark:text-slate-400'}`}>{label}</span>}
    </div>
);

export const ProviderFormModal: React.FC<ProviderFormModalProps> = ({ isOpen, onClose, initialData, onSubmit }) => {
    const { t } = useLanguage();
    const { showToast } = useToast();

    const [formData, setFormData] = useState<Partial<Provider>>({
        name: '',
        type: ProviderType.NEW_API,
        baseUrl: '',
        apiKey: '',
        models: [],
        status: 'active',
        autoSync: true
    });
    const [modelsInput, setModelsInput] = useState('');
    const [isSyncing, setIsSyncing] = useState(false);
    const [isSubmitting, setIsSubmitting] = useState(false);

    const modelsInputRef = useRef<HTMLInputElement>(null);

    useEffect(() => {
        if (isOpen) {
            if (initialData) {
                setFormData({ ...initialData });
            } else {
                // Reset for add mode
                setFormData({
                    name: '',
                    type: ProviderType.NEW_API,
                    baseUrl: '',
                    apiKey: '',
                    models: [],
                    status: 'active',
                    autoSync: true
                });
            }
            setModelsInput('');
        }
    }, [isOpen, initialData]);

    const addModel = (model: string) => {
        const trimmed = model.trim();
        if (!trimmed) return;
        if (!formData.models?.includes(trimmed)) {
            setFormData(prev => ({
                ...prev,
                models: [...(prev.models || []), trimmed]
            }));
        }
        setModelsInput('');
    };

    const removeModel = (index: number) => {
        const newModels = [...(formData.models || [])];
        newModels.splice(index, 1);
        setFormData(prev => ({ ...prev, models: newModels }));
    };

    const handleClearModels = () => {
        setFormData(prev => ({ ...prev, models: [] }));
        setModelsInput('');
    };

    const handleKeyDown = (e: React.KeyboardEvent<HTMLInputElement>) => {
        if (e.key === 'Enter' || e.key === ',') {
            e.preventDefault();
            addModel(modelsInput);
        } else if (e.key === 'Backspace' && !modelsInput && formData.models?.length) {
            const newModels = [...(formData.models || [])];
            newModels.pop();
            setFormData(prev => ({ ...prev, models: newModels }));
        }
    };

    const handleSyncModelsFromForm = async () => {
        if (!formData.baseUrl || !formData.apiKey) {
            showToast(t('providers.validation.baseUrl') + ' / ' + t('providers.validation.apiKey'), 'error');
            return;
        }

        setIsSyncing(true);
        try {
            const models = await providerService.syncModels(formData.baseUrl, formData.apiKey);
            setFormData(prev => ({ ...prev, models }));
            setModelsInput('');
            showToast('Models synchronized successfully', 'success');
        } catch (error) {
            console.error(error);
            showToast('Failed to sync models', 'error');
        } finally {
            setIsSyncing(false);
        }
    };

    const handleSubmit = async (e: React.FormEvent) => {
        e.preventDefault();
        setIsSubmitting(true);
        try {
            // Include pending input as model if present
            const currentData = { ...formData };
            if (modelsInput.trim() && !currentData.models?.includes(modelsInput.trim())) {
                currentData.models = [...(currentData.models || []), modelsInput.trim()];
            }

            await onSubmit(currentData);
        } finally {
            setIsSubmitting(false);
        }
    };

    if (!isOpen) return null;

    return (
        <div className="fixed inset-0 z-50 flex items-center justify-center bg-slate-900/60 backdrop-blur-sm p-4 animate-fade-in">
            <div className="bg-white dark:bg-slate-900 rounded-2xl shadow-2xl max-w-lg w-full max-h-[90vh] overflow-y-auto animate-in zoom-in-95 duration-200 border border-white/20 dark:border-slate-700">
                <div className="px-6 py-5 border-b border-slate-100 dark:border-slate-800 flex justify-between items-center sticky top-0 bg-white/95 dark:bg-slate-900/95 backdrop-blur z-10">
                    <h2 className="text-xl font-bold text-slate-900 dark:text-white">
                        {initialData ? t('providers.modal.titleEdit') : t('providers.modal.titleAdd')}
                    </h2>
                    <button onClick={onClose} className="text-slate-400 hover:text-slate-600 dark:hover:text-slate-300 transition-colors p-1 rounded-full hover:bg-slate-100 dark:hover:bg-slate-800">
                        <X size={24} />
                    </button>
                </div>
                <form onSubmit={handleSubmit} className="p-6 space-y-6">

                    <div>
                        <label className="block text-sm font-semibold text-slate-700 dark:text-slate-300 mb-1.5">
                            {t('providers.modal.name')} <span className="text-red-500">*</span>
                        </label>
                        <input
                            type="text"
                            required
                            value={formData.name}
                            onChange={(e) => setFormData({ ...formData, name: e.target.value })}
                            className="block w-full rounded-xl border-slate-300 dark:border-slate-700 shadow-sm focus:border-indigo-500 focus:ring-indigo-500 text-sm py-2.5 px-3 bg-slate-50 dark:bg-slate-800 dark:text-white transition-all focus:bg-white dark:focus:bg-slate-900"
                        />
                    </div>

                    <div>
                        <label className="block text-sm font-semibold text-slate-700 dark:text-slate-300 mb-1.5">
                            {t('providers.modal.type')} <span className="text-red-500">*</span>
                        </label>
                        <select
                            value={formData.type}
                            onChange={(e) => setFormData({ ...formData, type: parseInt(e.target.value) as ProviderType })}
                            className="block w-full rounded-xl border-slate-300 dark:border-slate-700 shadow-sm focus:border-indigo-500 focus:ring-indigo-500 text-sm py-2.5 px-3 bg-slate-50 dark:bg-slate-800 dark:text-white cursor-pointer"
                        >
                            {Object.values(ProviderType)
                                .filter(value => typeof value === 'number')
                                .map((value) => (
                                    <option key={value} value={value}>{getProviderLabel(value as ProviderType)}</option>
                                ))}
                        </select>
                    </div>

                    <div>
                        <label className="block text-sm font-semibold text-slate-700 dark:text-slate-300 mb-1.5">
                            {t('providers.modal.baseUrl')} <span className="text-red-500">*</span>
                        </label>
                        <input
                            type="url"
                            required
                            value={formData.baseUrl}
                            onChange={(e) => setFormData({ ...formData, baseUrl: e.target.value })}
                            className="block w-full rounded-xl border-slate-300 dark:border-slate-700 shadow-sm focus:border-indigo-500 focus:ring-indigo-500 text-sm py-2.5 px-3 font-mono bg-slate-50 dark:bg-slate-800 dark:text-white transition-all focus:bg-white dark:focus:bg-slate-900"
                        />
                    </div>

                    <div>
                        <label className="block text-sm font-semibold text-slate-700 dark:text-slate-300 mb-1.5">
                            {t('providers.modal.apiKey')} <span className="text-red-500">*</span>
                        </label>
                        <input
                            type="text"
                            required
                            value={formData.apiKey}
                            onChange={(e) => setFormData({ ...formData, apiKey: e.target.value })}
                            placeholder={t('providers.modal.apiKeyPlaceholder')}
                            className="block w-full rounded-xl border-slate-300 dark:border-slate-700 shadow-sm focus:border-indigo-500 focus:ring-indigo-500 text-sm py-2.5 px-3 font-mono bg-slate-50 dark:bg-slate-800 dark:text-white dark:placeholder-slate-500 transition-all focus:bg-white dark:focus:bg-slate-900"
                        />
                    </div>

                    <div className="bg-slate-50 dark:bg-slate-800/50 p-4 rounded-xl border border-slate-200 dark:border-slate-700/50">
                        <div className="flex justify-between items-center mb-3">
                            <label className="block text-sm font-semibold text-slate-700 dark:text-slate-300">
                                {t('providers.modal.models')} <span className="text-red-500">*</span>
                            </label>
                            <div className="flex items-center gap-3">
                                {formData.models && formData.models.length > 0 && (
                                    <button
                                        type="button"
                                        onClick={handleClearModels}
                                        className="text-xs flex items-center font-medium text-slate-500 dark:text-slate-400 hover:text-red-600 dark:hover:text-red-400 transition-colors"
                                    >
                                        <Trash2 size={12} className="mr-1" />
                                        Clear
                                    </button>
                                )}
                                <button
                                    type="button"
                                    onClick={handleSyncModelsFromForm}
                                    disabled={isSyncing}
                                    className={`text-xs flex items-center font-bold px-2 py-1 rounded-lg transition-colors ${isSyncing ? 'text-indigo-400 cursor-not-allowed' : 'bg-indigo-50 dark:bg-indigo-900/30 text-indigo-600 dark:text-indigo-400 hover:bg-indigo-100 dark:hover:bg-indigo-900/50'}`}
                                >
                                    {isSyncing ? <Loader2 size={12} className="mr-1 animate-spin" /> : <RefreshCcw size={12} className="mr-1" />}
                                    Auto-Sync
                                </button>
                            </div>
                        </div>

                        <div
                            className="border border-slate-300 dark:border-slate-600 rounded-xl p-3 bg-white dark:bg-slate-900 focus-within:ring-2 focus-within:ring-indigo-500 focus-within:border-indigo-500 transition-all h-32 overflow-y-auto cursor-text custom-scrollbar"
                            onClick={() => modelsInputRef.current?.focus()}
                        >
                            <div className="flex flex-wrap gap-2">
                                {formData.models?.map((model, index) => (
                                    <span key={index} className="inline-flex items-center px-2 py-1 rounded-md text-xs font-medium bg-slate-100 dark:bg-slate-800 text-slate-800 dark:text-slate-200 border border-slate-200 dark:border-slate-700 animate-in zoom-in-95 duration-100">
                                        {model}
                                        <button
                                            type="button"
                                            onClick={(e) => { e.stopPropagation(); removeModel(index); }}
                                            className="ml-1.5 text-slate-400 hover:text-red-500 focus:outline-none"
                                        >
                                            <X size={12} />
                                        </button>
                                    </span>
                                ))}
                                <input
                                    ref={modelsInputRef}
                                    type="text"
                                    value={modelsInput}
                                    onChange={(e) => setModelsInput(e.target.value)}
                                    onKeyDown={handleKeyDown}
                                    onBlur={() => {
                                        if (modelsInput.trim()) addModel(modelsInput);
                                    }}
                                    placeholder={formData.models?.length === 0 ? "Type model & Enter..." : ''}
                                    className="flex-1 min-w-[120px] outline-none text-sm font-mono bg-transparent py-0.5 text-slate-700 dark:text-slate-200 placeholder:font-sans dark:placeholder-slate-500"
                                />
                            </div>
                        </div>
                    </div>

                    <div className="grid grid-cols-2 gap-6 pt-2">
                        <div className="bg-slate-50 dark:bg-slate-800/50 p-3 rounded-xl border border-slate-200 dark:border-slate-700/50">
                            <label className="block text-xs font-semibold text-slate-500 dark:text-slate-400 uppercase tracking-wider mb-2">{t('providers.modal.status')}</label>
                            <StatusSwitch
                                checked={formData.status === 'active'}
                                onChange={(checked) => setFormData({ ...formData, status: checked ? 'active' : 'inactive' })}
                                label={formData.status === 'active' ? t('common.active') : t('common.inactive')}
                            />
                        </div>
                        <div className="bg-slate-50 dark:bg-slate-800/50 p-3 rounded-xl border border-slate-200 dark:border-slate-700/50">
                            <label className="block text-xs font-semibold text-slate-500 dark:text-slate-400 uppercase tracking-wider mb-2">{t('providers.modal.autoSync')}</label>
                            <StatusSwitch
                                checked={formData.autoSync !== false}
                                onChange={(checked) => setFormData({ ...formData, autoSync: checked })}
                                label={formData.autoSync !== false ? t('common.enabled') : t('common.disabled')}
                            />
                        </div>
                    </div>

                    <div className="pt-4 flex justify-end space-x-3 border-t border-slate-100 dark:border-slate-800">
                        <button
                            type="button"
                            onClick={onClose}
                            className="px-5 py-2.5 border border-slate-300 dark:border-slate-600 shadow-sm text-sm font-semibold rounded-xl text-slate-700 dark:text-slate-200 bg-white dark:bg-slate-800 hover:bg-slate-50 dark:hover:bg-slate-700 transition-colors"
                        >
                            {t('common.cancel')}
                        </button>
                        <button
                            type="submit"
                            disabled={isSubmitting}
                            className="flex items-center px-5 py-2.5 text-sm font-semibold rounded-xl shadow-lg shadow-indigo-500/20 text-white bg-indigo-600 hover:bg-indigo-700 transition-all hover:-translate-y-0.5 disabled:opacity-70 disabled:cursor-not-allowed"
                        >
                            {isSubmitting ? <Loader2 size={18} className="mr-2 animate-spin" /> : <Save size={18} className="mr-2" />}
                            {t('common.save')}
                        </button>
                    </div>
                </form>
            </div>
        </div>
    );
};
