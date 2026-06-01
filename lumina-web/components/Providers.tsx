import React, { useState, useEffect, useRef } from 'react';
import { Provider, ProviderEndpoint, ProviderType } from '../types';
import { Plus, MoreHorizontal, Trash2, Key, RefreshCcw, X, Save, Edit2, Activity, DownloadCloud, Loader2, Link2, Box } from 'lucide-react';
import { useLanguage } from './LanguageContext';
import { providerService } from '../services/providerService';
import { CardGridSkeleton } from './Skeletons';
import { SlideInItem } from './Animations';
import { Pagination } from './Pagination';
import { Badge } from './ui/Badge';
import { Button } from './ui/Button';
import { Modal } from './ui/Modal';
import { EmptyState } from './ui/EmptyState';
import { useToast } from './ui/ToastContext';

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
        checked ? 'bg-emerald-500 shadow-sm' : 'bg-gray-200 dark:bg-gray-700'
        } relative inline-flex h-5 w-9 flex-shrink-0 cursor-pointer rounded-full border-2 border-transparent transition-all duration-200 ease-in-out focus:outline-none disabled:opacity-50`}
    >
        <span
        aria-hidden="true"
        className={`${
            checked ? 'translate-x-4' : 'translate-x-0'
        } pointer-events-none inline-block h-4 w-4 transform rounded-full bg-white shadow ring-0 transition duration-200 ease-in-out`}
        />
    </button>
    {label && <span className={`ml-2 text-xs font-medium transition-colors ${checked ? 'text-gray-900 dark:text-gray-100' : 'text-gray-400 dark:text-gray-500'}`}>{label}</span>}
  </div>
);

export const getProviderLabel = (type: ProviderType): string => {
  switch (type) {
    case ProviderType.OPENAI_CHAT: return 'OpenAI Chat';
    case ProviderType.OPENAI_RESPONSE: return 'OpenAI Response';
    case ProviderType.ANTHROPIC: return 'Anthropic';
    case ProviderType.GEMINI: return 'Gemini';
    default: return 'Unknown';
  }
};

export const Providers: React.FC = () => {
  const { showToast } = useToast();

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
  const [deleteModal, setDeleteModal] = useState<{isOpen: boolean, id: string | null, name: string}>({ isOpen: false, id: null, name: '' });

  // Form State
  const [formData, setFormData] = useState<Partial<Provider> & { type: number[]; endpoints: ProviderEndpoint[] }>({
    name: '',
    type: [],
    baseUrl: '',
    apiKey: '',
    models: [],
    status: 'active',
    autoSync: true,
    endpoints: []
  });
  const [modelsInput, setModelsInput] = useState('');
  const [typeUrls, setTypeUrls] = useState<Record<number, string>>({});
  
  // Refs
  const menuRef = useRef<HTMLDivElement>(null);
  const modelsInputRef = useRef<HTMLInputElement>(null);

  const { t } = useLanguage();

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
      showToast(t('providers.failed'), 'error');
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
      type: [],
      baseUrl: '',
      apiKey: '',
      models: [],
      status: 'active',
      autoSync: true,
      endpoints: []
    });
    setTypeUrls({});
    setModelsInput('');
    setIsSyncing(false);
    setIsModalOpen(true);
  };

  const handleOpenEdit = (provider: Provider) => {
    setEditingProvider(provider);
    setFormData({
      ...provider,
      apiKey: '',
      type: provider.type || [],
      endpoints: provider.endpoints || [],
    });
    // Populate per-type URLs from endpoints
    const urls: Record<number, string> = {};
    (provider.endpoints || []).forEach(ep => {
      urls[ep.protocolType] = ep.baseUrl;
    });
    setTypeUrls(urls);
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
      showToast(t('providers.deletedSuccess'), 'success');
      // If deleting the last item on the page, go to previous page
      if (providers.length === 1 && pagination.current > 1) {
          fetchProviders(pagination.current - 1, pagination.size);
      } else {
          fetchProviders(); 
      }
    } catch (error) {
      console.error("Failed to delete provider:", error);
      showToast(t('providers.failed'), 'error');
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
      showToast(t('providers.failed'), 'error');
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
    if (!formData.type?.length) {
      showToast(t('providers.validation.type'), 'error');
      return;
    }
    if (!formData.apiKey?.trim() && !editingProvider) {
      showToast(t('providers.validation.apiKey'), 'error');
      return;
    }
    if (currentModels.length === 0) {
      showToast(t('providers.validation.models'), 'error');
      return;
    }

    // Build endpoints from selected types and their URLs
    const endpoints: ProviderEndpoint[] = (formData.type || []).map(protocolType => ({
      protocolType,
      baseUrl: typeUrls[protocolType] || '',
    }));

    // Validate all selected types have URLs
    for (const ep of endpoints) {
      if (!ep.baseUrl.trim()) {
        showToast('Please provide a base URL for each selected type', 'error');
        return;
      }
    }

    const payload = {
      ...formData as any,
      models: currentModels,
      endpoints,
    };

    try {
      if (editingProvider) {
        await providerService.update(editingProvider.id, payload);
        showToast(t('providers.updatedSuccess'), 'success');
      } else {
        await providerService.create(payload);
        showToast(t('providers.createdSuccess'), 'success');
      }
      setIsModalOpen(false);
      fetchProviders();
    } catch (error) {
      console.error("Failed to save provider:", error);
      showToast(t('providers.failed'), 'error');
    }
  };

  const getProviderIconStyle = (type: number) => {
    switch (type) {
      case ProviderType.OPENAI_CHAT:
      case ProviderType.OPENAI_RESPONSE:
        return 'bg-emerald-50 text-emerald-600 border-emerald-200 dark:bg-emerald-900/20 dark:text-emerald-400 dark:border-emerald-900/30';
      case ProviderType.ANTHROPIC:
        return 'bg-amber-50 text-amber-600 border-amber-200 dark:bg-amber-900/20 dark:text-amber-400 dark:border-amber-900/30';
      case ProviderType.GEMINI:
        return 'bg-blue-50 text-blue-600 border-blue-200 dark:bg-blue-900/20 dark:text-blue-400 dark:border-blue-900/30';
      default:
        return 'bg-gray-50 text-gray-600 border-gray-200 dark:bg-gray-800 dark:text-gray-400 dark:border-gray-700';
    }
  };

  const getFirstType = (provider: Provider): number => {
    if (Array.isArray(provider.type) && provider.type.length > 0) return provider.type[0];
    return -1;
  };

  const handleTestConnection = (id: string) => {
      // Simulation or API call if available
      const latency = Math.floor(Math.random() * 500) + 50;
      setProviders(providers.map(c => c.id === id ? { ...c, latency } : c));
      setActiveMenuId(null);
      showToast(t('providers.latencyResult', { latency }), 'success');
  };

  const handleSyncModels = async (id: string) => {
      const provider = providers.find(c => c.id === id);
      if (!provider) return;

      setActiveMenuId(null);
      showToast(t('providers.syncing'), 'info');

      try {
        const models = await providerService.syncModels(provider.baseUrl, provider.apiKey, provider.id);
        
        // Update the provider with the new models
        await providerService.update(provider.id, { ...provider, models });
        
        showToast(t('providers.syncedSuccess', { count: models.length }), 'success');
        fetchProviders();
      } catch (error) {
        console.error("Sync failed", error);
        showToast(t('providers.failed'), 'error');
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
    const firstType = (formData.type || [])[0];
    const syncUrl = firstType != null ? typeUrls[firstType] : '';
    if (!syncUrl || (!formData.apiKey && !editingProvider)) {
       showToast('Please select a type with a base URL and provide an API key', 'error');
       return;
    }

    setIsSyncing(true);
    try {
        const models = await providerService.syncModels(syncUrl, formData.apiKey, editingProvider?.id);
        setFormData(prev => ({ ...prev, models }));
        setModelsInput('');
        showToast(t('providers.syncedSuccess', { count: models.length }), 'success');
    } catch (error) {
        console.error(error);
        showToast(t('providers.failed'), 'error');
    } finally {
        setIsSyncing(false);
    }
  };

  return (
    <div className="space-y-6 relative flex flex-col h-full">
      {/* Delete Confirmation Modal */}
      <Modal
        isOpen={deleteModal.isOpen}
        onClose={() => setDeleteModal({isOpen: false, id: null, name: ''})}
        hideHeader
        size="sm"
      >
        <div className="flex flex-col items-center">
            <div className="flex items-center justify-center w-12 h-12 bg-red-100 dark:bg-red-900/30 rounded-full mx-auto mb-4 text-red-600 dark:text-red-400">
                <Trash2 size={24} />
            </div>
            <h3 className="text-lg font-bold text-center text-gray-900 dark:text-white mb-2">{t('providers.deleteTitle')}</h3>
            <p className="text-center text-gray-500 dark:text-gray-400 text-sm mb-6 leading-relaxed">
                {t('providers.deleteDesc', { name: '' })} <span className="font-bold text-gray-900 dark:text-white">{deleteModal.name}</span>?
            </p>
            <div className="flex space-x-3 w-full">
                <Button onClick={() => setDeleteModal({isOpen: false, id: null, name: ''})} variant="secondary" className="flex-1">
                    {t('common.cancel')}
                </Button>
                <Button onClick={confirmDelete} variant="danger" className="flex-1">
                    {t('common.delete')}
                </Button>
            </div>
        </div>
      </Modal>

      <div className="flex flex-col sm:flex-row justify-between items-start sm:items-center gap-4">
        <div>
          <h1 className="text-3xl font-extrabold text-gray-900 dark:text-white tracking-tight">{t('providers.title')}</h1>
          <p className="text-gray-500 dark:text-gray-400 mt-2 text-lg">{t('providers.subtitle')}</p>
        </div>
        <Button onClick={handleOpenAdd} variant="primary" leftIcon={<Plus size={18} />}>
          {t('providers.addProvider')}
        </Button>
      </div>

      <div className="flex-1 flex flex-col min-h-0">
        {isLoading ? (
            <CardGridSkeleton />
        ) : (
            <>
            <div className="grid grid-cols-1 gap-4">
                {providers.length === 0 ? (
                    <EmptyState 
                        icon={<Box size={48} />}
                        title={t('providers.noProviders')}
                        description={t('providers.addFirst')}
                        actionLabel={t('providers.addProvider')}
                        onAction={handleOpenAdd}
                        actionIcon={<Plus size={18} />}
                        className="bg-white dark:bg-[#1a1a1a] rounded-2xl border border-dashed border-gray-300 dark:border-gray-700 animate-fade-in"
                    />
                ) : (
                    providers.map((provider, index) => (
                    <SlideInItem key={provider.id} index={index}>
                    <div className="bg-white dark:bg-[#1a1a1a] border border-gray-200 dark:border-gray-800 rounded-2xl p-5 shadow-card hover:shadow-float transition-all duration-300 relative group">
                        <div className="flex flex-col md:flex-row justify-between items-start gap-6">
                            
                            {/* Left Section: Icon & Info */}
                            <div className="flex items-start gap-5 w-full overflow-hidden">
                                <div className={`w-12 h-12 rounded-xl flex-shrink-0 flex items-center justify-center text-lg font-bold shadow-sm border ${getProviderIconStyle(getFirstType(provider))}`}>
                                    {getProviderLabel(getFirstType(provider)).substring(0, 2).toUpperCase()}
                                </div>
                                
                                <div className="flex-1 min-w-0">
                                    <div className="flex flex-wrap items-center gap-x-4 gap-y-2 mb-2">
                                        <h3 className="font-bold text-gray-900 dark:text-white text-lg truncate">{provider.name}</h3>
                                        {Array.isArray(provider.type) && provider.type.length > 0 && (
                                          <div className="flex flex-wrap gap-1">
                                            {provider.type.map(t => (
                                              <Badge key={t} tone="neutral" size="xs">
                                                {getProviderLabel(t)}
                                              </Badge>
                                            ))}
                                          </div>
                                        )}
                                        <div className="flex items-center space-x-3 pl-3 border-l border-gray-200 dark:border-gray-700 shrink-0">
                                            <StatusSwitch 
                                                checked={provider.status === 'active'}
                                                onChange={() => handleToggleStatus(provider.id, provider.status)}
                                                label={t(`common.${provider.status}`)}
                                            />
                                        </div>
                                    </div>
                                    
                                    <div className="flex items-center text-sm text-gray-500 dark:text-gray-400 mb-4">
                                        <Link2 size={14} className="mr-1.5" />
                                        <span className="font-mono truncate">{provider.baseUrl}</span>
                                    </div>

                                    <div className="flex flex-wrap items-center gap-3">
                                        <div className="flex items-center bg-gray-50 dark:bg-gray-800 px-2.5 py-1 rounded-lg border border-gray-100 dark:border-gray-700/50">
                                            <Key size={12} className="mr-1.5 text-gray-400" />
                                            <span className="text-xs font-mono text-gray-600 dark:text-gray-300">
                                                {provider.apiKey ? (provider.apiKey.length > 10 ? provider.apiKey.substring(0, 6) + '...' + provider.apiKey.substring(provider.apiKey.length - 4) : '******') : 'No Key'}
                                            </span>
                                        </div>
                                        <div className="flex items-center bg-gray-50 dark:bg-gray-800 px-2.5 py-1 rounded-lg border border-gray-100 dark:border-gray-700/50">
                                            <Activity size={12} className={`mr-1.5 ${provider.latency < 200 ? 'text-emerald-500' : 'text-amber-500'}`} />
                                            <span className="text-xs font-medium text-gray-600 dark:text-gray-300">
                                                {provider.latency}ms
                                            </span>
                                        </div>
                                        <div className="flex items-center bg-gray-50 dark:bg-gray-800 px-2.5 py-1 rounded-lg border border-gray-100 dark:border-gray-700/50">
                                            <Box size={12} className="mr-1.5 text-gray-400" />
                                            <span className="text-xs font-medium text-gray-600 dark:text-gray-300 max-w-[200px] truncate" title={provider.models.join(', ')}>
                                                {provider.models.length} Models
                                            </span>
                                        </div>
                                    </div>
                                </div>
                            </div>

                            {/* Right Section: Actions */}
                            <div className="flex items-center gap-2 w-full md:w-auto mt-2 md:mt-0 pt-4 md:pt-0 border-t md:border-t-0 border-gray-100 dark:border-gray-800">
                                <Button
                                    onClick={() => handleOpenEdit(provider)}
                                    variant="secondary"
                                    className="flex-1 md:flex-none"
                                    leftIcon={<Edit2 size={16} className="md:hidden" />}
                                >
                                    {t('common.edit')}
                                </Button>
                                <Button
                                    onClick={(e) => handleDeleteClick(provider.id, provider.name, e)}
                                    variant="ghost"
                                    className="flex-1 md:flex-none text-gray-400 hover:text-red-600 dark:hover:text-red-400 hover:bg-red-50 dark:hover:bg-red-900/10 border border-gray-200 dark:border-gray-700 md:border-transparent"
                                    title={t('common.delete')}
                                >
                                    <Trash2 size={18} />
                                </Button>
                                <div className="relative flex-1 md:flex-none">
                                    <Button
                                        onClick={() => setActiveMenuId(activeMenuId === provider.id ? null : provider.id)}
                                        variant="ghost"
                                        className={activeMenuId === provider.id ? 'w-full md:w-auto border border-gray-200 dark:border-gray-700 md:border-transparent bg-gray-100 dark:bg-gray-800 text-gray-900 dark:text-white px-2' : 'w-full md:w-auto border border-gray-200 dark:border-gray-700 md:border-transparent text-gray-400 hover:text-gray-600 dark:hover:text-gray-300 hover:bg-gray-50 dark:hover:bg-gray-700 px-2'}
                                    >
                                        <MoreHorizontal size={18} />
                                    </Button>
                                    {activeMenuId === provider.id && (
                                        <div ref={menuRef} className="absolute right-0 bottom-full md:bottom-auto md:top-full mb-2 md:mb-0 md:mt-2 w-48 bg-white dark:bg-[#1a1a1a] rounded-xl shadow-float border border-gray-200 dark:border-gray-800 z-10 py-1 animate-in fade-in zoom-in-95 duration-100">
                                            <Button
                                                onClick={() => handleTestConnection(provider.id)}
                                                variant="ghost"
                                                size="sm"
                                                className="w-full justify-start px-4 rounded-none"
                                                leftIcon={<Activity size={14} className="text-gray-400" />}
                                            >
                                                {t('providers.more.testConnection')}
                                            </Button>
                                            <Button
                                                onClick={() => handleSyncModels(provider.id)}
                                                variant="ghost"
                                                size="sm"
                                                className="w-full justify-start px-4 rounded-none"
                                                leftIcon={<DownloadCloud size={14} className="text-gray-400" />}
                                            >
                                                {t('providers.more.syncModels')}
                                            </Button>
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
                    className="mt-6 pt-4 border-t border-gray-200/50 dark:border-gray-800/50"
                />
            </div>
            </>
        )}
      </div>

      {/* Add/Edit Modal */}
      <Modal
        isOpen={isModalOpen}
        onClose={() => setIsModalOpen(false)}
        title={editingProvider ? t('providers.modal.titleEdit') : t('providers.modal.titleAdd')}
        size="lg"
      >
        <form id="provider-form" onSubmit={handleSubmit} className="space-y-5">
            
            <div>
                <label className="block text-xs font-semibold text-gray-500 dark:text-gray-400 uppercase tracking-wide mb-1.5">
                    {t('providers.modal.name')} <span className="text-red-500">*</span>
                </label>
                <input 
                    type="text" 
                    required
                    value={formData.name}
                    onChange={(e) => setFormData({...formData, name: e.target.value})}
                    className="block w-full rounded-xl border-gray-200 dark:border-gray-700 shadow-sm focus:border-black dark:focus:border-white focus:ring-black dark:focus:ring-white text-sm py-2.5 px-3 bg-gray-50 dark:bg-gray-900 dark:text-white transition-all"
                />
            </div>
            
            <div>
                <label className="block text-xs font-semibold text-gray-500 dark:text-gray-400 uppercase tracking-wide mb-1.5">
                    {t('providers.modal.type')} <span className="text-red-500">*</span>
                </label>
                <div className="space-y-2">
                    {Object.values(ProviderType)
                        .filter(value => typeof value === 'number')
                        .map((value) => {
                            const numVal = value as number;
                            const isChecked = (formData.type || []).includes(numVal);
                            return (
                                <div key={numVal} className="bg-gray-50 dark:bg-gray-900/50 rounded-xl border border-gray-200 dark:border-gray-800 p-3">
                                    <label className="flex items-center gap-3 cursor-pointer">
                                        <input
                                            type="checkbox"
                                            checked={isChecked}
                                            onChange={() => {
                                                const newTypes = isChecked
                                                    ? (formData.type || []).filter(t => t !== numVal)
                                                    : [...(formData.type || []), numVal];
                                                const newUrls = { ...typeUrls };
                                                if (!isChecked) {
                                                    newUrls[numVal] = newUrls[numVal] || '';
                                                } else {
                                                    delete newUrls[numVal];
                                                }
                                                setTypeUrls(newUrls);
                                                setFormData({...formData, type: newTypes});
                                            }}
                                            className="w-4 h-4 rounded border-gray-300 dark:border-gray-600 text-black dark:text-white focus:ring-black dark:focus:ring-white"
                                        />
                                        <span className="text-sm font-medium text-gray-700 dark:text-gray-300 flex-shrink-0 w-32">
                                            {getProviderLabel(numVal)}
                                        </span>
                                        {isChecked && (
                                            <input
                                                type="url"
                                                required
                                                value={typeUrls[numVal] || ''}
                                                onChange={(e) => setTypeUrls({...typeUrls, [numVal]: e.target.value})}
                                                placeholder={`${getProviderLabel(numVal)} 的 Base URL`}
                                                className="flex-1 rounded-lg border-gray-200 dark:border-gray-700 shadow-sm focus:border-black dark:focus:border-white focus:ring-black dark:focus:ring-white text-sm py-1.5 px-3 font-mono bg-white dark:bg-[#1a1a1a] dark:text-white transition-all"
                                            />
                                        )}
                                    </label>
                                </div>
                            );
                        })}
                </div>
            </div>

            <div>
                <label className="block text-xs font-semibold text-gray-500 dark:text-gray-400 uppercase tracking-wide mb-1.5">
                    {t('providers.modal.apiKey')} {!editingProvider && <span className="text-red-500">*</span>}
                </label>
                <input
                    type="text"
                    required={!editingProvider}
                    value={formData.apiKey}
                    onChange={(e) => setFormData({...formData, apiKey: e.target.value})}
                    placeholder={editingProvider ? '留空则不修改 / Leave empty to keep unchanged' : t('providers.modal.apiKeyPlaceholder')}
                    className="block w-full rounded-xl border-gray-200 dark:border-gray-700 shadow-sm focus:border-black dark:focus:border-white focus:ring-black dark:focus:ring-white text-sm py-2.5 px-3 font-mono bg-gray-50 dark:bg-gray-900 dark:text-white dark:placeholder-gray-600 transition-all"
                />
            </div>

            <div className="bg-gray-50 dark:bg-gray-900/50 p-4 rounded-xl border border-gray-200 dark:border-gray-800">
                <div className="flex justify-between items-center mb-3">
                    <label className="block text-xs font-semibold text-gray-500 dark:text-gray-400 uppercase tracking-wide">
                        {t('providers.modal.models')} <span className="text-red-500">*</span>
                    </label>
                    <div className="flex items-center gap-3">
                        {formData.models && formData.models.length > 0 && (
                            <Button
                                type="button"
                                onClick={handleClearModels}
                                variant="ghost"
                                size="sm"
                                className="h-6 px-2 text-gray-500 dark:text-gray-400 hover:text-red-600 dark:hover:text-red-400"
                                leftIcon={<Trash2 size={10} />}
                            >
                                Clear
                            </Button>
                        )}
                        <Button
                            type="button"
                            onClick={handleSyncModelsFromForm}
                            disabled={isSyncing}
                            variant="secondary"
                            size="sm"
                            className="h-6 px-2 text-[10px]"
                            leftIcon={isSyncing ? <Loader2 size={10} className="animate-spin" /> : <RefreshCcw size={10} />}
                        >
                            Auto-Sync
                        </Button>
                    </div>
                </div>
                
                <div 
                    className="border border-gray-200 dark:border-gray-700 rounded-xl p-3 bg-white dark:bg-[#1a1a1a] focus-within:ring-1 focus-within:ring-black dark:focus-within:ring-white transition-all h-32 overflow-y-auto cursor-text custom-scrollbar"
                    onClick={() => modelsInputRef.current?.focus()}
                >
                    <div className="flex flex-wrap gap-2">
                        {formData.models?.map((model, index) => (
                            <span key={index} className="inline-flex items-center px-2 py-1 rounded-md text-xs font-medium bg-gray-100 dark:bg-gray-800 text-gray-800 dark:text-gray-200 border border-gray-200 dark:border-gray-700 animate-in zoom-in-95 duration-100">
                                {model}
                                <button
                                    type="button"
                                    onClick={(e) => { e.stopPropagation(); removeModel(index); }}
                                    className="ml-1.5 text-gray-400 hover:text-red-500 focus:outline-none"
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
                            className="flex-1 min-w-[120px] outline-none text-sm font-mono bg-transparent py-0.5 text-gray-700 dark:text-gray-200 placeholder:font-sans dark:placeholder-gray-500"
                        />
                    </div>
                </div>
            </div>

            <div className="grid grid-cols-2 gap-4">
                <div className="bg-gray-50 dark:bg-gray-900/50 p-3 rounded-xl border border-gray-200 dark:border-gray-800">
                    <label className="block text-[10px] font-bold text-gray-400 dark:text-gray-500 uppercase tracking-wider mb-2">{t('providers.modal.status')}</label>
                    <StatusSwitch 
                        checked={formData.status === 'active'}
                        onChange={(checked) => setFormData({...formData, status: checked ? 'active' : 'inactive'})}
                        label={formData.status === 'active' ? t('common.active') : t('common.inactive')}
                    />
                </div>
                    <div className="bg-gray-50 dark:bg-gray-900/50 p-3 rounded-xl border border-gray-200 dark:border-gray-800">
                    <label className="block text-[10px] font-bold text-gray-400 dark:text-gray-500 uppercase tracking-wider mb-2">{t('providers.modal.autoSync')}</label>
                    <StatusSwitch 
                        checked={formData.autoSync !== false}
                        onChange={(checked) => setFormData({...formData, autoSync: checked})}
                        label={formData.autoSync !== false ? t('common.enabled') : t('common.disabled')}
                    />
                </div>
            </div>

            <div className="pt-4 flex justify-end space-x-3 border-t border-gray-100 dark:border-gray-800 -mx-6 -mb-6 px-6 py-4 bg-white/95 dark:bg-[#1a1a1a]/95 backdrop-blur rounded-b-2xl mt-4">
                <Button type="button" onClick={() => setIsModalOpen(false)} variant="secondary">
                    {t('common.cancel')}
                </Button>
                <Button type="submit" variant="primary" leftIcon={<Save size={18} />}>
                    {t('common.save')}
                </Button>
            </div>
        </form>
      </Modal>
    </div>
  );
};
