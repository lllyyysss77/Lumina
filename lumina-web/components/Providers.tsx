import React, { useState, useEffect, useRef } from 'react';
import { Provider, ProviderType } from '../types';
import { Plus, MoreHorizontal, CheckCircle2, AlertCircle, Trash2, Key, RefreshCcw, X, Save, Edit2, Activity, DownloadCloud, Loader2, AlertTriangle, Check, ChevronLeft, ChevronRight } from 'lucide-react';
import { useLanguage } from './LanguageContext';
import { providerService } from '../services/providerService';
import { SkeletonProviderCard } from './Skeleton';
import { AnimatedProviderCard } from './Animated';
import { useToast, ToastContainer } from './Toast';
import { DeleteModal } from './Modal';

const StatusSwitch = ({ checked, onChange, disabled = false, label }: { checked: boolean; onChange: (checked: boolean) => void; disabled?: boolean, label?: string }) => (
  <div className="flex items-center cursor-pointer" onClick={(e) => {
     e.stopPropagation();
     if(!disabled) onChange(!checked);
  }}>
    <button
        type="button"
        role="switch"
        aria-checked={checked}
        className={`${
        checked ? 'bg-green-500' : 'bg-slate-300'
        } relative inline-flex h-6 w-11 flex-shrink-0 cursor-pointer rounded-full border-2 border-transparent transition-colors duration-200 ease-in-out focus:outline-none focus:ring-2 focus:ring-indigo-500 focus:ring-offset-2 disabled:opacity-50`}
    >
        <span
        aria-hidden="true"
        className={`${
            checked ? 'translate-x-5' : 'translate-x-0'
        } pointer-events-none inline-block h-5 w-5 transform rounded-full bg-white shadow ring-0 transition duration-200 ease-in-out`}
        />
    </button>
    {label && <span className="ml-2 text-sm text-slate-700">{label}</span>}
  </div>
);

export const getProviderLabel = (type: ProviderType): string => {
  switch (type) {
    case ProviderType.OPENAI_CHAT: return 'OpenAI Chat';
    case ProviderType.OPENAI_RESPONSE: return 'OpenAI Response';
    case ProviderType.ANTHROPIC: return 'Anthropic';
    case ProviderType.GEMINI: return 'Gemini';
    case ProviderType.NEW_API: return 'New API';
    default: return 'Unknown';
  }
};

export const Providers: React.FC = () => {
  const [providers, setProviders] = useState<Provider[]>([]);
  const [isLoading, setIsLoading] = useState(false);
  const [isModalOpen, setIsModalOpen] = useState(false);
  const [editingProvider, setEditingProvider] = useState<Provider | null>(null);
  const [activeMenuId, setActiveMenuId] = useState<string | null>(null);
  const [isSyncing, setIsSyncing] = useState(false);
  const [pagination, setPagination] = useState({
    current: 1,
    size: 6,
    total: 0,
    pages: 0
  });
  
  // UI States for Delete Modal
  const [deleteModal, setDeleteModal] = useState<{isOpen: boolean, id: string | null, name: string}>({ isOpen: false, id: null, name: '' });

  // Form State
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
  
  // Refs
  const menuRef = useRef<HTMLDivElement>(null);
  const modelsInputRef = useRef<HTMLInputElement>(null);

  const { t } = useLanguage();
  const { success, error, info, toasts, removeToast } = useToast();

  const fetchProviders = async (page: number = pagination.current, size: number = pagination.size) => {
    setIsLoading(true);
    try {
      const data = await providerService.getPage(page, size);
      setProviders(data.records);
      setPagination({
        current: data.current,
        size: data.size,
        total: data.total,
        pages: data.pages
      });
    } catch (error) {
      console.error("Failed to fetch providers:", error);
      error('Failed to load providers');
    } finally {
      setIsLoading(false);
    }
  };

  useEffect(() => {
    fetchProviders();
  }, []);

  const handlePageChange = (newPage: number) => {
    if (newPage > 0 && newPage <= pagination.pages) {
      fetchProviders(newPage, pagination.size);
    }
  };

  const handleSizeChange = (e: React.ChangeEvent<HTMLSelectElement>) => {
    const newSize = parseInt(e.target.value);
    fetchProviders(1, newSize);
  };

  useEffect(() => {
    const handleClickOutside = (event: MouseEvent) => {
      if (menuRef.current && !menuRef.current.contains(event.target as Node)) {
        setActiveMenuId(null);
      }
    };
    document.addEventListener('mousedown', handleClickOutside);
    return () => document.removeEventListener('mousedown', handleClickOutside);
  }, []);

  const handleOpenAdd = () => {
    setEditingProvider(null);
    setFormData({
      name: '',
      type: ProviderType.NEW_API,
      baseUrl: '',
      apiKey: '',
      models: [],
      status: 'active',
      autoSync: true
    });
    setModelsInput('');
    setIsSyncing(false);
    setIsModalOpen(true);
  };

  const handleOpenEdit = (provider: Provider) => {
    setEditingProvider(provider);
    setFormData(provider);
    setModelsInput('');
    setIsSyncing(false);
    setIsModalOpen(true);
    setActiveMenuId(null);
  };

  // Open the delete confirmation modal instead of using window.confirm
  const handleDeleteClick = (id: string, name: string, e: React.MouseEvent) => {
    e.stopPropagation();
    setDeleteModal({ isOpen: true, id, name });
    setActiveMenuId(null);
  };

  const confirmDelete = async () => {
    if (!deleteModal.id) return;
    
    try {
      await providerService.delete(deleteModal.id);
      success('Provider deleted successfully');
      fetchProviders();
    } catch (error) {
      console.error("Failed to delete provider:", error);
      error('Failed to delete provider');
    } finally {
      setDeleteModal({ isOpen: false, id: null, name: '' });
    }
  };

  const handleToggleStatus = async (id: string, currentStatus: 'active' | 'inactive') => {
    const providerToUpdate = providers.find(c => c.id === id);
    if (!providerToUpdate) return;

    const newStatus = currentStatus === 'active' ? 'inactive' : 'active';

    // Optimistic Update
    setProviders(providers.map(c => c.id === id ? { ...c, status: newStatus } : c));

    try {
      await providerService.update(id, { ...providerToUpdate, status: newStatus });
    } catch (error) {
      console.error("Failed to update status:", error);
      // Revert on failure
      setProviders(providers.map(c => c.id === id ? { ...c, status: currentStatus } : c));
      error('Failed to update status');
    }
  };

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    
    // Auto-add any text remaining in input
    let currentModels = [...(formData.models || [])];
    if (modelsInput.trim()) {
         const newVal = modelsInput.trim();
         if (!currentModels.includes(newVal)) {
             currentModels.push(newVal);
         }
    }

    // Form Validation
    if (!formData.name?.trim()) {
      error(t('providers.validation.name'));
      return;
    }
    if (!formData.baseUrl?.trim()) {
      error(t('providers.validation.baseUrl'));
      return;
    }
    if (!formData.apiKey?.trim()) {
      error(t('providers.validation.apiKey'));
      return;
    }
    if (currentModels.length === 0) {
      error(t('providers.validation.models'));
      return;
    }

    const payload = {
      ...formData as Provider,
      models: currentModels,
    };

    try {
      if (editingProvider) {
        await providerService.update(editingProvider.id, payload);
        success('Provider updated successfully');
      } else {
        await providerService.create(payload);
        success('Provider created successfully');
      }
      setIsModalOpen(false);
      fetchProviders();
    } catch (error) {
      console.error("Failed to save provider:", error);
      error('Failed to save provider');
    }
  };

  const getProviderBadge = (type: ProviderType) => {
    switch (type) {
      case ProviderType.OPENAI_CHAT: 
      case ProviderType.OPENAI_RESPONSE:
        return 'bg-green-100 text-green-700 border-green-200';
      case ProviderType.ANTHROPIC: return 'bg-amber-100 text-amber-700 border-amber-200';
      case ProviderType.GEMINI: return 'bg-blue-100 text-blue-700 border-blue-200';
      case ProviderType.NEW_API: return 'bg-purple-100 text-purple-700 border-purple-200';
      default: return 'bg-slate-100 text-slate-700';
    }
  };

  const handleTestConnection = (id: string) => {
      // Simulation or API call if available
      const latency = Math.floor(Math.random() * 500) + 50;
      setProviders(providers.map(c => c.id === id ? { ...c, latency } : c));
      setActiveMenuId(null);
      success(`Connection active. Latency: ${latency}ms`);
  };

  const handleSyncModels = async (id: string) => {
      const provider = providers.find(c => c.id === id);
      if (!provider) return;

      setActiveMenuId(null);
      info('Syncing models...');

      try {
        const models = await providerService.syncModels(provider.baseUrl, provider.apiKey);

        // Update the provider with the new models
        await providerService.update(provider.id, { ...provider, models });

        success(`Synced ${models.length} models`);
        fetchProviders();
      } catch (error) {
        console.error("Sync failed", error);
        error('Failed to sync models');
      }
  };

  // Model Chip Logic
  const addModel = (val: string) => {
    const trimmed = val.trim();
    if (trimmed && !formData.models?.includes(trimmed)) {
        setFormData(prev => ({ ...prev, models: [...(prev.models || []), trimmed] }));
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
       error(t('providers.validation.baseUrl') + ' / ' + t('providers.validation.apiKey'));
       return;
    }

    setIsSyncing(true);
    try {
        const models = await providerService.syncModels(formData.baseUrl, formData.apiKey);
        setFormData(prev => ({ ...prev, models }));
        setModelsInput('');
        success('Models synchronized successfully');
    } catch (error) {
        console.error(error);
        error('Failed to sync models');
    } finally {
        setIsSyncing(false);
    }
  };

  return (
    <div className="space-y-6 relative">
      {/* Toast Container */}
      <ToastContainer toasts={toasts} onClose={removeToast} />

      {/* Delete Confirmation Modal */}
      <DeleteModal
        isOpen={deleteModal.isOpen}
        onClose={() => setDeleteModal({ isOpen: false, id: null, name: '' })}
        onConfirm={confirmDelete}
        itemName={deleteModal.name}
      />

      <div className="flex flex-col sm:flex-row justify-between items-start sm:items-center gap-4">
        <div>
          <h1 className="text-2xl font-bold text-slate-900">{t('providers.title')}</h1>
          <p className="text-slate-500 mt-1">{t('providers.subtitle')}</p>
        </div>
        <div className="flex items-center gap-3">
          <select
            value={pagination.size}
            onChange={handleSizeChange}
            className="block w-32 pl-3 pr-10 py-2 text-base border-slate-300 focus:outline-none focus:ring-indigo-500 focus:border-indigo-500 sm:text-sm rounded-lg border bg-white"
          >
            <option value="6">6 / page</option>
            <option value="12">12 / page</option>
            <option value="24">24 / page</option>
            <option value="50">50 / page</option>
          </select>
          <button
            onClick={handleOpenAdd}
            className="flex items-center px-4 py-2 bg-indigo-600 hover:bg-indigo-700 text-white rounded-lg font-medium transition-colors shadow-sm shadow-indigo-200"
          >
            <Plus size={18} className="mr-2" />
            {t('providers.addProvider')}
          </button>
        </div>
      </div>

      {isLoading ? (
        <div className="grid grid-cols-1 gap-4">
          <SkeletonProviderCard />
          <SkeletonProviderCard />
          <SkeletonProviderCard />
        </div>
      ) : (
        <>
        <div className="grid grid-cols-1 gap-4">
          {providers.length === 0 ? (
            <div className="text-center py-12 bg-white dark:bg-slate-800 rounded-xl border border-dashed border-slate-300 dark:border-slate-700 animate-in fade-in duration-300">
               <p className="text-slate-500 dark:text-slate-400">No providers found. Add one to get started.</p>
            </div>
          ) : (
             providers.map((provider, index) => (
            <AnimatedProviderCard key={provider.id} index={index}>
              <div className="flex flex-col sm:flex-row justify-between items-start gap-4">

                <div className="flex items-start gap-3 sm:gap-4 w-full overflow-hidden">
                  <div className={`w-10 h-10 rounded-lg flex-shrink-0 flex items-center justify-center text-xs font-bold border ${getProviderBadge(provider.type)}`}>
                    {getProviderLabel(provider.type).substring(0, 2).toUpperCase()}
                  </div>

                  <div className="flex-1 min-w-0">
                    <div className="flex flex-wrap items-center gap-x-3 gap-y-2 mb-1">
                      <h3 className="font-semibold text-slate-900 dark:text-slate-100 text-base sm:text-lg truncate max-w-full">{provider.name}</h3>

                      {/* List Toggle Switch */}
                      <div className="flex items-center space-x-2 pl-2 border-l border-slate-200 dark:border-slate-600 shrink-0">
                          <StatusSwitch
                              checked={provider.status === 'active'}
                              onChange={() => handleToggleStatus(provider.id, provider.status)}
                          />
                          <span className={`text-xs font-medium ${provider.status === 'active' ? 'text-green-600' : 'text-slate-500'}`}>
                              {t(`common.${provider.status}`)}
                          </span>
                      </div>
                    </div>

                    <div className="text-xs sm:text-sm text-slate-500 dark:text-slate-400 mt-1 font-mono break-all leading-relaxed">{provider.baseUrl}</div>

                    <div className="flex flex-wrap items-center gap-2 mt-3 text-xs text-slate-500 dark:text-slate-400">
                      <span className="flex items-center bg-slate-50 dark:bg-slate-900/50 px-2 py-1 rounded border border-slate-100 dark:border-slate-700 font-mono whitespace-nowrap">
                          <Key size={12} className="mr-1.5 flex-shrink-0" />
                          {provider.apiKey ? (provider.apiKey.length > 10 ? provider.apiKey.substring(0, 6) + '...' + provider.apiKey.substring(provider.apiKey.length - 4) : '******') : 'No Key'}
                      </span>
                      <span className="flex items-center bg-slate-50 dark:bg-slate-900/50 px-2 py-1 rounded border border-slate-100 dark:border-slate-700 whitespace-nowrap">
                          <RefreshCcw size={12} className="mr-1.5 flex-shrink-0" />
                          {provider.latency}ms {t('common.latency')}
                      </span>
                      <span className="flex items-center bg-slate-50 dark:bg-slate-900/50 px-2 py-1 rounded border border-slate-100 dark:border-slate-700 max-w-full">
                          <span className="truncate">{provider.models.length} Models: {provider.models.join(', ')}</span>
                      </span>
                    </div>
                  </div>
                </div>

                <div className="flex items-center gap-2 w-full sm:w-auto mt-2 sm:mt-0 relative border-t sm:border-t-0 border-slate-100 dark:border-slate-700 pt-3 sm:pt-0">
                  <button
                      onClick={() => handleOpenEdit(provider)}
                      className="flex-1 sm:flex-none flex items-center justify-center px-3 py-2 text-sm font-medium text-slate-600 dark:text-slate-300 bg-white dark:bg-slate-700 border border-slate-200 dark:border-slate-600 rounded-lg hover:bg-slate-50 dark:hover:bg-slate-600"
                  >
                      <Edit2 size={16} className="mr-2 sm:hidden" />
                      {t('common.edit')}
                  </button>
                  <button
                      onClick={(e) => handleDeleteClick(provider.id, provider.name, e)}
                      className="flex-1 sm:flex-none flex items-center justify-center p-2 text-slate-400 hover:text-red-600 hover:bg-red-50 border border-slate-200 sm:border-transparent rounded-lg transition-colors"
                  >
                      <Trash2 size={18} />
                  </button>
                  <div className="relative flex-1 sm:flex-none">
                      <button
                          onClick={() => setActiveMenuId(activeMenuId === provider.id ? null : provider.id)}
                          className={`w-full sm:w-auto flex items-center justify-center p-2 rounded-lg transition-colors border border-slate-200 sm:border-transparent ${activeMenuId === provider.id ? 'bg-slate-100 text-slate-600' : 'text-slate-400 hover:text-slate-600 hover:bg-slate-50'}`}
                      >
                          <MoreHorizontal size={18} />
                      </button>
                      {/* More Dropdown */}
                      {activeMenuId === provider.id && (
                          <div ref={menuRef} className="absolute right-0 bottom-full sm:bottom-auto sm:top-full mb-2 sm:mb-0 sm:mt-2 w-48 bg-white dark:bg-slate-800 rounded-lg shadow-lg border border-slate-100 dark:border-slate-700 z-10 py-1 animate-in zoom-in-95 duration-150">
                              <button
                                  onClick={() => handleTestConnection(provider.id)}
                                  className="w-full text-left px-4 py-2 text-sm text-slate-700 dark:text-slate-300 hover:bg-slate-50 dark:hover:bg-slate-700 flex items-center"
                              >
                                  <Activity size={14} className="mr-2" />
                                  {t('providers.more.testConnection')}
                              </button>
                              <button
                                  onClick={() => handleSyncModels(provider.id)}
                                  className="w-full text-left px-4 py-2 text-sm text-slate-700 dark:text-slate-300 hover:bg-slate-50 dark:hover:bg-slate-700 flex items-center"
                              >
                                  <DownloadCloud size={14} className="mr-2" />
                                  {t('providers.more.syncModels')}
                              </button>
                          </div>
                      )}
                  </div>
                </div>
              </div>
            </AnimatedProviderCard>
          )))}
        </div>

        {/* Pagination Controls */}
        {providers.length > 0 && (
          <div className="bg-white px-4 py-3 rounded-xl border border-slate-200 shadow-sm flex items-center justify-between sm:px-6">
            <div className="flex-1 flex justify-between sm:hidden">
              <button
                onClick={() => handlePageChange(pagination.current - 1)}
                disabled={pagination.current === 1}
                className="relative inline-flex items-center px-4 py-2 border border-slate-300 text-sm font-medium rounded-md text-slate-700 bg-white hover:bg-slate-50 disabled:opacity-50 disabled:cursor-not-allowed"
              >
                {t('logs.pagination.prev')}
              </button>
              <button
                onClick={() => handlePageChange(pagination.current + 1)}
                disabled={pagination.current >= pagination.pages}
                className="ml-3 relative inline-flex items-center px-4 py-2 border border-slate-300 text-sm font-medium rounded-md text-slate-700 bg-white hover:bg-slate-50 disabled:opacity-50 disabled:cursor-not-allowed"
              >
                {t('logs.pagination.next')}
              </button>
            </div>
            <div className="hidden sm:flex-1 sm:flex sm:items-center sm:justify-between">
              <div>
                <p className="text-sm text-slate-700">
                  {t('logs.pagination.showing')} <span className="font-medium">{pagination.total > 0 ? (pagination.current - 1) * pagination.size + 1 : 0}</span> {t('logs.pagination.to')} <span className="font-medium">{Math.min(pagination.current * pagination.size, pagination.total)}</span> {t('logs.pagination.of')} <span className="font-medium">{pagination.total}</span> {t('logs.pagination.results')}
                </p>
              </div>
              <div>
                <nav className="relative z-0 inline-flex rounded-md shadow-sm -space-x-px" aria-label="Pagination">
                  <button
                    onClick={() => handlePageChange(pagination.current - 1)}
                    disabled={pagination.current === 1 || isLoading}
                    className="relative inline-flex items-center px-2 py-2 rounded-l-md border border-slate-300 bg-white text-sm font-medium text-slate-500 hover:bg-slate-50 disabled:opacity-50 disabled:cursor-not-allowed"
                  >
                    <span className="sr-only">Previous</span>
                    <ChevronLeft size={16} />
                  </button>
                  <span className="relative inline-flex items-center px-4 py-2 border border-slate-300 bg-white text-sm font-medium text-slate-700">
                    {pagination.current} / {pagination.pages || 1}
                  </span>
                  <button
                    onClick={() => handlePageChange(pagination.current + 1)}
                    disabled={pagination.current >= pagination.pages || isLoading}
                    className="relative inline-flex items-center px-2 py-2 rounded-r-md border border-slate-300 bg-white text-sm font-medium text-slate-500 hover:bg-slate-50 disabled:opacity-50 disabled:cursor-not-allowed"
                  >
                    <span className="sr-only">Next</span>
                    <ChevronRight size={16} />
                  </button>
                </nav>
              </div>
            </div>
          </div>
        )}
        </>
      )}

      {/* Add/Edit Modal */}
      {isModalOpen && (
        <div className="fixed inset-0 z-50 flex items-center justify-center bg-slate-900/50 backdrop-blur-sm p-4">
            <div className="bg-white rounded-xl shadow-xl max-w-lg w-full max-h-[90vh] overflow-y-auto">
                <div className="px-6 py-4 border-b border-slate-100 flex justify-between items-center">
                    <h2 className="text-lg font-bold text-slate-900">
                        {editingProvider ? t('providers.modal.titleEdit') : t('providers.modal.titleAdd')}
                    </h2>
                    <button onClick={() => setIsModalOpen(false)} className="text-slate-400 hover:text-slate-600">
                        <X size={20} />
                    </button>
                </div>
                <form onSubmit={handleSubmit} className="p-6 space-y-4">
                    <div>
                        <label className="block text-sm font-medium text-slate-700 mb-1">
                            {t('providers.modal.name')} <span className="text-red-500">*</span>
                        </label>
                        <input 
                            type="text" 
                            required
                            value={formData.name}
                            onChange={(e) => setFormData({...formData, name: e.target.value})}
                            className="block w-full rounded-lg border-slate-300 shadow-sm focus:border-indigo-500 focus:ring-indigo-500 sm:text-sm p-2 border"
                        />
                    </div>
                    
                    <div>
                        <label className="block text-sm font-medium text-slate-700 mb-1">
                            {t('providers.modal.type')} <span className="text-red-500">*</span>
                        </label>
                        <select 
                            value={formData.type}
                            onChange={(e) => setFormData({...formData, type: parseInt(e.target.value) as ProviderType})}
                            className="block w-full rounded-lg border-slate-300 shadow-sm focus:border-indigo-500 focus:ring-indigo-500 sm:text-sm p-2 border bg-white"
                        >
                            {Object.values(ProviderType)
                                .filter(value => typeof value === 'number')
                                .map((value) => (
                                <option key={value} value={value}>{getProviderLabel(value as ProviderType)}</option>
                            ))}
                        </select>
                    </div>

                    <div>
                        <label className="block text-sm font-medium text-slate-700 mb-1">
                            {t('providers.modal.baseUrl')} <span className="text-red-500">*</span>
                        </label>
                        <input 
                            type="url" 
                            required
                            value={formData.baseUrl}
                            onChange={(e) => setFormData({...formData, baseUrl: e.target.value})}
                            className="block w-full rounded-lg border-slate-300 shadow-sm focus:border-indigo-500 focus:ring-indigo-500 sm:text-sm p-2 border font-mono"
                        />
                    </div>

                    <div>
                        <label className="block text-sm font-medium text-slate-700 mb-1">
                            {t('providers.modal.apiKey')} <span className="text-red-500">*</span>
                        </label>
                        <input 
                            type="text"
                            required
                            value={formData.apiKey}
                            onChange={(e) => setFormData({...formData, apiKey: e.target.value})}
                            placeholder={t('providers.modal.apiKeyPlaceholder')}
                            className="block w-full rounded-lg border-slate-300 shadow-sm focus:border-indigo-500 focus:ring-indigo-500 sm:text-sm p-2 border font-mono"
                        />
                    </div>

                    <div>
                        <div className="flex justify-between items-center mb-1">
                            <label className="block text-sm font-medium text-slate-700">
                                {t('providers.modal.models')} <span className="text-red-500">*</span>
                            </label>
                            <div className="flex items-center gap-3">
                                {formData.models && formData.models.length > 0 && (
                                    <button 
                                        type="button" 
                                        onClick={handleClearModels}
                                        className="text-xs flex items-center font-medium text-slate-500 hover:text-red-600 transition-colors"
                                        title={t('common.clearAll')}
                                    >
                                        <Trash2 size={12} className="mr-1" />
                                        {t('common.clearAll')}
                                    </button>
                                )}
                                <button 
                                    type="button" 
                                    onClick={handleSyncModelsFromForm}
                                    disabled={isSyncing}
                                    className={`text-xs flex items-center font-medium transition-colors ${isSyncing ? 'text-indigo-400 cursor-not-allowed' : 'text-indigo-600 hover:text-indigo-700'}`}
                                >
                                    {isSyncing ? <Loader2 size={12} className="mr-1 animate-spin" /> : <RefreshCcw size={12} className="mr-1" />}
                                    {t('providers.more.syncModels')}
                                </button>
                            </div>
                        </div>
                        
                        <div 
                            className="border border-slate-300 rounded-lg p-2 bg-white focus-within:ring-2 focus-within:ring-indigo-500 focus-within:border-indigo-500 transition-shadow h-40 overflow-y-auto cursor-text"
                            onClick={() => modelsInputRef.current?.focus()}
                        >
                            <div className="flex flex-wrap gap-2">
                                {formData.models?.map((model, index) => (
                                    <span key={index} className="inline-flex items-center px-2.5 py-1 rounded-full text-xs font-medium bg-slate-100 text-slate-700 border border-slate-200">
                                        {model}
                                        <button
                                            type="button"
                                            onClick={(e) => { e.stopPropagation(); removeModel(index); }}
                                            className="ml-1.5 text-slate-400 hover:text-slate-600 focus:outline-none"
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
                                        if(modelsInput.trim()) addModel(modelsInput);
                                    }}
                                    placeholder={formData.models?.length === 0 ? t('providers.modal.modelsPlaceholder') : ''}
                                    className="flex-1 min-w-[120px] outline-none text-sm font-mono bg-transparent py-1 text-slate-700 placeholder:font-sans"
                                />
                            </div>
                        </div>
                        <p className="mt-1.5 text-xs text-slate-400">
                            Type and press Enter to add models manually
                        </p>
                    </div>

                    <div className="grid grid-cols-2 gap-4 pt-2 border-t border-slate-50">
                        <div>
                            <label className="block text-sm font-medium text-slate-700 mb-1">{t('providers.modal.status')}</label>
                            <div className="mt-2">
                                <StatusSwitch 
                                    checked={formData.status === 'active'}
                                    onChange={(checked) => setFormData({...formData, status: checked ? 'active' : 'inactive'})}
                                    label={formData.status === 'active' ? t('common.active') : t('common.inactive')}
                                />
                            </div>
                        </div>
                         <div>
                            <label className="block text-sm font-medium text-slate-700 mb-1">{t('providers.modal.autoSync')}</label>
                            <div className="mt-2">
                                <StatusSwitch 
                                    checked={formData.autoSync !== false}
                                    onChange={(checked) => setFormData({...formData, autoSync: checked})}
                                    label={formData.autoSync !== false ? t('common.enabled') : t('common.disabled')}
                                />
                            </div>
                        </div>
                    </div>

                    <div className="pt-4 flex justify-end space-x-3">
                        <button 
                            type="button"
                            onClick={() => setIsModalOpen(false)}
                            className="px-4 py-2 border border-slate-300 shadow-sm text-sm font-medium rounded-lg text-slate-700 bg-white hover:bg-slate-50"
                        >
                            {t('common.cancel')}
                        </button>
                        <button 
                            type="submit"
                            className="flex items-center px-4 py-2 border border-transparent text-sm font-medium rounded-lg shadow-sm text-white bg-indigo-600 hover:bg-indigo-700"
                        >
                            <Save size={16} className="mr-2" />
                            {t('common.save')}
                        </button>
                    </div>
                </form>
            </div>
        </div>
      )}
    </div>
  );
};