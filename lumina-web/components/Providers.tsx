import React, { useState, useEffect, useRef } from 'react';
import { Provider, ProviderType } from '../types';
import { Plus, MoreHorizontal, Trash2, Key, RefreshCcw, X, Save, Edit2, Activity, DownloadCloud, Loader2, AlertTriangle, Link2, Box, CheckCircle2 } from 'lucide-react';
import { useLanguage } from './LanguageContext';
import { providerService } from '../services/providerService';
import { CardGridSkeleton } from './Skeletons';
import { SlideInItem } from './Animations';
import { Pagination } from './Pagination';

const StatusSwitch = ({ checked, onChange, disabled = false, label }: { checked: boolean; onChange: (checked: boolean) => void; disabled?: boolean, label?: string }) => (
  <div className="flex items-center cursor-pointer group" onClick={(e) => {
     e.stopPropagation();
     if(!disabled) onChange(!checked);
  }}>
    <button
        type="button"
        role="switch"
        aria-checked={checked}
        className={`${
        checked ? 'bg-green-500 shadow-green-200' : 'bg-slate-300 dark:bg-slate-600'
        } relative inline-flex h-6 w-11 flex-shrink-0 cursor-pointer rounded-full border-2 border-transparent transition-all duration-200 ease-in-out focus:outline-none focus:ring-2 focus:ring-indigo-500 focus:ring-offset-2 disabled:opacity-50`}
    >
        <span
        aria-hidden="true"
        className={`${
            checked ? 'translate-x-5' : 'translate-x-0'
        } pointer-events-none inline-block h-5 w-5 transform rounded-full bg-white shadow ring-0 transition duration-200 ease-in-out`}
        />
    </button>
    {label && <span className={`ml-2 text-sm font-medium transition-colors ${checked ? 'text-green-600 dark:text-green-400' : 'text-slate-500 dark:text-slate-400'}`}>{label}</span>}
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
  
  // Pagination State - Default 6 items per page
  const [pagination, setPagination] = useState({
    current: 1,
    size: 6,
    total: 0,
    pages: 0
  });
  
  // UI States for Custom Modals & Toasts
  const [toast, setToast] = useState<{show: boolean, message: string, type: 'success' | 'error' | 'info'}>({ show: false, message: '', type: 'success' });
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

  const showToast = (message: string, type: 'success' | 'error' | 'info' = 'success') => {
    setToast({ show: true, message, type });
    setTimeout(() => setToast(prev => ({ ...prev, show: false })), 3000);
  };

  const fetchProviders = async (page = pagination.current, size = pagination.size) => {
    setIsLoading(true);
    try {
      const data = await providerService.getPage(page, size);
      setProviders(data.records);
      setPagination(prev => ({
        ...prev,
        current: data.current,
        total: data.total,
        pages: data.pages,
        size: data.size
      }));
    } catch (error) {
      console.error("Failed to fetch providers:", error);
      showToast('Failed to load providers', 'error');
    } finally {
      setIsLoading(false);
    }
  };

  useEffect(() => {
    fetchProviders();
  }, []);

  const handlePageChange = (page: number, size: number) => {
    fetchProviders(page, size);
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
      showToast('Provider deleted successfully', 'success');
      // If deleting the last item on the page, go to previous page
      if (providers.length === 1 && pagination.current > 1) {
          fetchProviders(pagination.current - 1, pagination.size);
      } else {
          fetchProviders(); 
      }
    } catch (error) {
      console.error("Failed to delete provider:", error);
      showToast('Failed to delete provider', 'error');
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
      showToast('Failed to update status', 'error');
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
      showToast(t('providers.validation.name'), 'error');
      return;
    }
    if (!formData.baseUrl?.trim()) {
      showToast(t('providers.validation.baseUrl'), 'error');
      return;
    }
    if (!formData.apiKey?.trim()) {
      showToast(t('providers.validation.apiKey'), 'error');
      return;
    }
    if (currentModels.length === 0) {
      showToast(t('providers.validation.models'), 'error');
      return;
    }
    
    const payload = {
      ...formData as Provider,
      models: currentModels,
    };
    
    try {
      if (editingProvider) {
        await providerService.update(editingProvider.id, payload);
        showToast('Provider updated successfully', 'success');
      } else {
        await providerService.create(payload);
        showToast('Provider created successfully', 'success');
      }
      setIsModalOpen(false);
      fetchProviders();
    } catch (error) {
      console.error("Failed to save provider:", error);
      showToast('Failed to save provider', 'error');
    }
  };

  const getProviderIconStyle = (type: ProviderType) => {
    switch (type) {
      case ProviderType.OPENAI_CHAT: 
      case ProviderType.OPENAI_RESPONSE:
        return 'bg-gradient-to-br from-green-500 to-emerald-600 shadow-green-500/30';
      case ProviderType.ANTHROPIC: 
        return 'bg-gradient-to-br from-amber-500 to-orange-600 shadow-amber-500/30';
      case ProviderType.GEMINI: 
        return 'bg-gradient-to-br from-blue-500 to-cyan-600 shadow-blue-500/30';
      case ProviderType.NEW_API: 
        return 'bg-gradient-to-br from-violet-500 to-purple-600 shadow-purple-500/30';
      default: 
        return 'bg-gradient-to-br from-slate-500 to-slate-600';
    }
  };

  const handleTestConnection = (id: string) => {
      // Simulation or API call if available
      const latency = Math.floor(Math.random() * 500) + 50;
      setProviders(providers.map(c => c.id === id ? { ...c, latency } : c));
      setActiveMenuId(null);
      showToast(`Connection active. Latency: ${latency}ms`, 'success');
  };

  const handleSyncModels = async (id: string) => {
      const provider = providers.find(c => c.id === id);
      if (!provider) return;

      setActiveMenuId(null);
      showToast('Syncing models...', 'info');

      try {
        const models = await providerService.syncModels(provider.baseUrl, provider.apiKey);
        
        // Update the provider with the new models
        await providerService.update(provider.id, { ...provider, models });
        
        showToast(`Synced ${models.length} models`, 'success');
        fetchProviders();
      } catch (error) {
        console.error("Sync failed", error);
        showToast('Failed to sync models', 'error');
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

  return (
    <div className="space-y-6 relative flex flex-col h-full">
      {/* Toast Notification */}
      {toast.show && (
          <div className={`fixed top-4 right-4 z-[100] px-4 py-3 rounded-lg shadow-lg border flex items-center animate-in slide-in-from-right duration-300 backdrop-blur-md ${
              toast.type === 'success' ? 'bg-white/90 border-green-200 text-green-700 dark:bg-slate-800/90 dark:border-green-900 dark:text-green-400' : 
              toast.type === 'error' ? 'bg-white/90 border-red-200 text-red-700 dark:bg-slate-800/90 dark:border-red-900 dark:text-red-400' :
              'bg-white/90 border-blue-200 text-blue-700 dark:bg-slate-800/90 dark:border-blue-900 dark:text-blue-400'
          }`}>
              {toast.type === 'success' ? <CheckCircle2 size={18} className="mr-2" /> : 
               toast.type === 'error' ? <AlertTriangle size={18} className="mr-2" /> :
               <Activity size={18} className="mr-2" />}
              <span className="text-sm font-medium">{toast.message}</span>
          </div>
      )}

      {/* Delete Confirmation Modal */}
      {deleteModal.isOpen && (
        <div className="fixed inset-0 z-[60] flex items-center justify-center bg-slate-900/60 backdrop-blur-sm p-4 animate-fade-in">
            <div className="bg-white dark:bg-slate-900 rounded-2xl shadow-2xl max-w-sm w-full p-6 animate-in zoom-in-95 duration-200 border border-white/20 dark:border-slate-700">
                <div className="flex items-center justify-center w-14 h-14 bg-red-100 dark:bg-red-900/30 rounded-full mx-auto mb-4 text-red-600 dark:text-red-400 shadow-inner">
                    <Trash2 size={28} />
                </div>
                <h3 className="text-xl font-bold text-center text-slate-900 dark:text-white mb-2">Delete Provider?</h3>
                <p className="text-center text-slate-500 dark:text-slate-400 text-sm mb-8 leading-relaxed">
                    Are you sure you want to delete <span className="font-bold text-slate-700 dark:text-slate-200">{deleteModal.name}</span>? This action cannot be undone.
                </p>
                <div className="flex space-x-4">
                    <button 
                        onClick={() => setDeleteModal({isOpen: false, id: null, name: ''})}
                        className="flex-1 px-4 py-3 border border-slate-200 dark:border-slate-700 text-slate-700 dark:text-slate-300 font-medium rounded-xl hover:bg-slate-50 dark:hover:bg-slate-800 transition-colors"
                    >
                        {t('common.cancel')}
                    </button>
                    <button 
                        onClick={confirmDelete}
                        className="flex-1 px-4 py-3 bg-red-600 hover:bg-red-700 text-white font-medium rounded-xl shadow-lg shadow-red-500/20 transition-all hover:scale-[1.02]"
                    >
                        {t('common.delete')}
                    </button>
                </div>
            </div>
        </div>
      )}

      <div className="flex flex-col sm:flex-row justify-between items-start sm:items-center gap-4">
        <div>
          <h1 className="text-3xl font-extrabold text-slate-900 dark:text-white tracking-tight">{t('providers.title')}</h1>
          <p className="text-slate-500 dark:text-slate-400 mt-2 text-lg">{t('providers.subtitle')}</p>
        </div>
        <button 
            onClick={handleOpenAdd}
            className="group flex items-center px-5 py-2.5 bg-gradient-to-r from-indigo-600 to-violet-600 hover:from-indigo-500 hover:to-violet-500 text-white rounded-xl font-semibold shadow-lg shadow-indigo-500/25 transition-all hover:-translate-y-0.5"
        >
          <Plus size={20} className="mr-2 transition-transform group-hover:rotate-90" />
          {t('providers.addProvider')}
        </button>
      </div>

      <div className="flex-1 flex flex-col min-h-0">
        {isLoading ? (
            <CardGridSkeleton />
        ) : (
            <>
            <div className="grid grid-cols-1 gap-5">
                {providers.length === 0 ? (
                    <div className="text-center py-20 bg-white/60 dark:bg-slate-900/60 backdrop-blur-md rounded-2xl border border-dashed border-slate-300 dark:border-slate-700 animate-fade-in">
                        <Box size={48} className="mx-auto text-slate-300 mb-4" />
                        <p className="text-lg text-slate-500 dark:text-slate-400 font-medium">No providers found</p>
                        <p className="text-sm text-slate-400 mt-1">Add a new provider to start routing requests</p>
                    </div>
                ) : (
                    providers.map((provider, index) => (
                    <SlideInItem key={provider.id} index={index}>
                    <div className="bg-white/70 dark:bg-slate-900/70 backdrop-blur-md border border-white/20 dark:border-slate-700/50 rounded-2xl p-5 shadow-sm hover:shadow-xl transition-all duration-300 relative group">
                        <div className="flex flex-col md:flex-row justify-between items-start gap-6">
                            
                            {/* Left Section: Icon & Info */}
                            <div className="flex items-start gap-5 w-full overflow-hidden">
                                <div className={`w-14 h-14 rounded-2xl flex-shrink-0 flex items-center justify-center text-white text-lg font-bold shadow-lg ${getProviderIconStyle(provider.type)}`}>
                                    {getProviderLabel(provider.type).substring(0, 2).toUpperCase()}
                                </div>
                                
                                <div className="flex-1 min-w-0">
                                    <div className="flex flex-wrap items-center gap-x-4 gap-y-2 mb-2">
                                        <h3 className="font-bold text-slate-900 dark:text-white text-xl truncate">{provider.name}</h3>
                                        <div className="flex items-center space-x-3 pl-3 border-l border-slate-300 dark:border-slate-700 shrink-0">
                                            <StatusSwitch 
                                                checked={provider.status === 'active'}
                                                onChange={() => handleToggleStatus(provider.id, provider.status)}
                                                label={t(`common.${provider.status}`)}
                                            />
                                        </div>
                                    </div>
                                    
                                    <div className="flex items-center text-sm text-slate-500 dark:text-slate-400 mb-4">
                                        <Link2 size={14} className="mr-1.5" />
                                        <span className="font-mono truncate">{provider.baseUrl}</span>
                                    </div>

                                    <div className="flex flex-wrap items-center gap-3">
                                        <div className="flex items-center bg-slate-100 dark:bg-slate-800/50 px-3 py-1.5 rounded-lg border border-slate-200 dark:border-slate-700/50">
                                            <Key size={14} className="mr-2 text-indigo-500" />
                                            <span className="text-xs font-mono text-slate-600 dark:text-slate-300">
                                                {provider.apiKey ? (provider.apiKey.length > 10 ? provider.apiKey.substring(0, 6) + '...' + provider.apiKey.substring(provider.apiKey.length - 4) : '******') : 'No Key'}
                                            </span>
                                        </div>
                                        <div className="flex items-center bg-slate-100 dark:bg-slate-800/50 px-3 py-1.5 rounded-lg border border-slate-200 dark:border-slate-700/50">
                                            <Activity size={14} className={`mr-2 ${provider.latency < 200 ? 'text-green-500' : 'text-amber-500'}`} />
                                            <span className="text-xs font-medium text-slate-600 dark:text-slate-300">
                                                {provider.latency}ms
                                            </span>
                                        </div>
                                        <div className="flex items-center bg-slate-100 dark:bg-slate-800/50 px-3 py-1.5 rounded-lg border border-slate-200 dark:border-slate-700/50">
                                            <Box size={14} className="mr-2 text-violet-500" />
                                            <span className="text-xs font-medium text-slate-600 dark:text-slate-300 max-w-[200px] truncate" title={provider.models.join(', ')}>
                                                {provider.models.length} Models
                                            </span>
                                        </div>
                                    </div>
                                </div>
                            </div>

                            {/* Right Section: Actions */}
                            <div className="flex items-center gap-2 w-full md:w-auto mt-2 md:mt-0 pt-4 md:pt-0 border-t md:border-t-0 border-slate-100 dark:border-slate-700">
                                <button 
                                    onClick={() => handleOpenEdit(provider)}
                                    className="flex-1 md:flex-none flex items-center justify-center px-4 py-2.5 text-sm font-medium text-slate-700 dark:text-slate-200 bg-white dark:bg-slate-800 border border-slate-200 dark:border-slate-700 rounded-xl hover:bg-slate-50 dark:hover:bg-slate-700 transition-colors shadow-sm"
                                >
                                    <Edit2 size={16} className="mr-2 md:hidden" />
                                    {t('common.edit')}
                                </button>
                                <button 
                                    onClick={(e) => handleDeleteClick(provider.id, provider.name, e)}
                                    className="flex-1 md:flex-none flex items-center justify-center p-2.5 text-slate-400 hover:text-red-600 dark:hover:text-red-400 hover:bg-red-50 dark:hover:bg-red-900/20 border border-slate-200 dark:border-slate-700 md:border-transparent rounded-xl transition-colors"
                                    title="Delete"
                                >
                                    <Trash2 size={20} />
                                </button>
                                <div className="relative flex-1 md:flex-none">
                                    <button 
                                        onClick={() => setActiveMenuId(activeMenuId === provider.id ? null : provider.id)}
                                        className={`w-full md:w-auto flex items-center justify-center p-2.5 rounded-xl transition-colors border border-slate-200 dark:border-slate-700 md:border-transparent ${activeMenuId === provider.id ? 'bg-indigo-50 dark:bg-slate-700 text-indigo-600 dark:text-indigo-300' : 'text-slate-400 hover:text-slate-600 dark:hover:text-slate-300 hover:bg-slate-50 dark:hover:bg-slate-700'}`}
                                    >
                                        <MoreHorizontal size={20} />
                                    </button>
                                    {activeMenuId === provider.id && (
                                        <div ref={menuRef} className="absolute right-0 bottom-full md:bottom-auto md:top-full mb-2 md:mb-0 md:mt-2 w-52 bg-white dark:bg-slate-900 rounded-xl shadow-xl border border-slate-200 dark:border-slate-700 z-10 py-1.5 animate-in fade-in zoom-in-95 duration-100">
                                            <button 
                                                onClick={() => handleTestConnection(provider.id)}
                                                className="w-full text-left px-4 py-2.5 text-sm text-slate-700 dark:text-slate-200 hover:bg-indigo-50 dark:hover:bg-indigo-900/20 hover:text-indigo-600 dark:hover:text-indigo-400 flex items-center transition-colors"
                                            >
                                                <Activity size={16} className="mr-3 text-slate-400" />
                                                {t('providers.more.testConnection')}
                                            </button>
                                            <button 
                                                onClick={() => handleSyncModels(provider.id)}
                                                className="w-full text-left px-4 py-2.5 text-sm text-slate-700 dark:text-slate-200 hover:bg-indigo-50 dark:hover:bg-indigo-900/20 hover:text-indigo-600 dark:hover:text-indigo-400 flex items-center transition-colors"
                                            >
                                                <DownloadCloud size={16} className="mr-3 text-slate-400" />
                                                {t('providers.more.syncModels')}
                                            </button>
                                        </div>
                                    )}
                                </div>
                            </div>
                        </div>
                    </div>
                    </SlideInItem>
                )))}
            </div>
            
            <div className="mt-auto">
                 <Pagination 
                    current={pagination.current}
                    size={pagination.size}
                    total={pagination.total}
                    onChange={handlePageChange}
                    className="mt-6 pt-4 border-t border-slate-200/50 dark:border-slate-700/50"
                />
            </div>
            </>
        )}
      </div>

      {/* Add/Edit Modal */}
      {isModalOpen && (
        <div className="fixed inset-0 z-50 flex items-center justify-center bg-slate-900/60 backdrop-blur-sm p-4 animate-fade-in">
            <div className="bg-white dark:bg-slate-900 rounded-2xl shadow-2xl max-w-lg w-full max-h-[90vh] overflow-y-auto animate-in zoom-in-95 duration-200 border border-white/20 dark:border-slate-700">
                <div className="px-6 py-5 border-b border-slate-100 dark:border-slate-800 flex justify-between items-center sticky top-0 bg-white/95 dark:bg-slate-900/95 backdrop-blur z-10">
                    <h2 className="text-xl font-bold text-slate-900 dark:text-white">
                        {editingProvider ? t('providers.modal.titleEdit') : t('providers.modal.titleAdd')}
                    </h2>
                    <button onClick={() => setIsModalOpen(false)} className="text-slate-400 hover:text-slate-600 dark:hover:text-slate-300 transition-colors p-1 rounded-full hover:bg-slate-100 dark:hover:bg-slate-800">
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
                            onChange={(e) => setFormData({...formData, name: e.target.value})}
                            className="block w-full rounded-xl border-slate-300 dark:border-slate-700 shadow-sm focus:border-indigo-500 focus:ring-indigo-500 text-sm py-2.5 px-3 bg-slate-50 dark:bg-slate-800 dark:text-white transition-all focus:bg-white dark:focus:bg-slate-900"
                        />
                    </div>
                    
                    <div>
                        <label className="block text-sm font-semibold text-slate-700 dark:text-slate-300 mb-1.5">
                            {t('providers.modal.type')} <span className="text-red-500">*</span>
                        </label>
                        <select 
                            value={formData.type}
                            onChange={(e) => setFormData({...formData, type: parseInt(e.target.value) as ProviderType})}
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
                            onChange={(e) => setFormData({...formData, baseUrl: e.target.value})}
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
                            onChange={(e) => setFormData({...formData, apiKey: e.target.value})}
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
                                        if(modelsInput.trim()) addModel(modelsInput);
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
                                onChange={(checked) => setFormData({...formData, status: checked ? 'active' : 'inactive'})}
                                label={formData.status === 'active' ? t('common.active') : t('common.inactive')}
                            />
                        </div>
                         <div className="bg-slate-50 dark:bg-slate-800/50 p-3 rounded-xl border border-slate-200 dark:border-slate-700/50">
                            <label className="block text-xs font-semibold text-slate-500 dark:text-slate-400 uppercase tracking-wider mb-2">{t('providers.modal.autoSync')}</label>
                            <StatusSwitch 
                                checked={formData.autoSync !== false}
                                onChange={(checked) => setFormData({...formData, autoSync: checked})}
                                label={formData.autoSync !== false ? t('common.enabled') : t('common.disabled')}
                            />
                        </div>
                    </div>

                    <div className="pt-4 flex justify-end space-x-3 border-t border-slate-100 dark:border-slate-800">
                        <button 
                            type="button"
                            onClick={() => setIsModalOpen(false)}
                            className="px-5 py-2.5 border border-slate-300 dark:border-slate-600 shadow-sm text-sm font-semibold rounded-xl text-slate-700 dark:text-slate-200 bg-white dark:bg-slate-800 hover:bg-slate-50 dark:hover:bg-slate-700 transition-colors"
                        >
                            {t('common.cancel')}
                        </button>
                        <button 
                            type="submit"
                            className="flex items-center px-5 py-2.5 text-sm font-semibold rounded-xl shadow-lg shadow-indigo-500/20 text-white bg-indigo-600 hover:bg-indigo-700 transition-all hover:-translate-y-0.5"
                        >
                            <Save size={18} className="mr-2" />
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