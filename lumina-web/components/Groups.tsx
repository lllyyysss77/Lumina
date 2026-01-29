import React, { useState, useEffect } from 'react';
import { Group, LoadBalanceMode, Provider } from '../types';
import { Layers, Shuffle, ArrowRightLeft, Scale, PlayCircle, Plus, Settings2, Trash2, X, Save, Check, ChevronDown, ChevronRight, AlertTriangle, Loader2, Search, Filter, Activity } from 'lucide-react';
import { useLanguage } from './LanguageContext';
import { groupService } from '../services/groupService';
import { providerService } from '../services/providerService';
import { CardGridSkeleton } from './Skeletons';
import { SlideInItem } from './Animations';
import { Pagination } from './Pagination';

export const Groups: React.FC = () => {
  const { t } = useLanguage();
  
  // Data State
  const [groups, setGroups] = useState<Group[]>([]);
  const [providers, setProviders] = useState<Provider[]>([]);
  const [isLoading, setIsLoading] = useState(false);
  
  // Pagination State
  const [pagination, setPagination] = useState({
    current: 1,
    size: 12,
    total: 0,
    pages: 0
  });
  
  // Modal & Edit State
  const [isModalOpen, setIsModalOpen] = useState(false);
  const [editingGroup, setEditingGroup] = useState<Group | null>(null);
  const [deleteModal, setDeleteModal] = useState<{isOpen: boolean, id: string | null, name: string}>({ isOpen: false, id: null, name: '' });
  
  // UI State
  const [expandedProviders, setExpandedProviders] = useState<string[]>([]);
  const [isSaving, setIsSaving] = useState(false);
  const [modelFilter, setModelFilter] = useState('');
  const [viewSelectedOnly, setViewSelectedOnly] = useState(false);

  // Form State
  const [formData, setFormData] = useState<Partial<Group>>({
    name: '',
    mode: LoadBalanceMode.SAPR,
    firstTokenTimeout: 3000,
    targets: []
  });

  // Fetch Data
  const fetchData = async (page = pagination.current, size = pagination.size) => {
    setIsLoading(true);
    try {
      const [groupsData, providersData] = await Promise.all([
        groupService.getPage(page, size),
        providerService.getList()
      ]);
      setGroups(groupsData.records);
      setPagination({
        current: groupsData.current,
        size: groupsData.size,
        total: groupsData.total,
        pages: groupsData.pages
      });
      setProviders(providersData);
    } catch (error) {
      console.error("Failed to fetch data:", error);
      // Optional: Show toast error here
    } finally {
      setIsLoading(false);
    }
  };

  useEffect(() => {
    fetchData();
  }, []);

  const handlePageChange = (page: number, size: number) => {
    fetchData(page, size);
  };

  const getModeIcon = (mode: LoadBalanceMode) => {
    switch(mode) {
        case LoadBalanceMode.ROUND_ROBIN: return <ArrowRightLeft size={16} />;
        case LoadBalanceMode.RANDOM: return <Shuffle size={16} />;
        case LoadBalanceMode.WEIGHTED: return <Scale size={16} />;
        case LoadBalanceMode.FAILOVER: return <PlayCircle size={16} />;
        case LoadBalanceMode.SAPR: return <Activity size={16} />;
        default: return <Layers size={16} />;
    }
  };

  const getModeLabel = (mode: LoadBalanceMode) => {
    switch (mode) {
        case LoadBalanceMode.ROUND_ROBIN: return t('groups.modes.roundRobin');
        case LoadBalanceMode.RANDOM: return t('groups.modes.random');
        case LoadBalanceMode.FAILOVER: return t('groups.modes.failover');
        case LoadBalanceMode.WEIGHTED: return t('groups.modes.weighted');
        case LoadBalanceMode.SAPR: return t('groups.modes.sapr');
        default: return mode;
    }
  };

  const handleOpenAdd = () => {
    setEditingGroup(null);
    setFormData({
      name: '',
      mode: LoadBalanceMode.SAPR,
      firstTokenTimeout: 3000,
      targets: []
    });
    setExpandedProviders([]);
    setModelFilter('');
    setViewSelectedOnly(false);
    setIsModalOpen(true);
  };

  const handleOpenEdit = (group: Group) => {
    setEditingGroup(group);
    setFormData({ ...group });
    // Only expand providers that are currently used in the group
    const usedProviderIds = Array.from(new Set(group.targets.map(t => t.providerId)));
    setExpandedProviders(usedProviderIds);
    setModelFilter('');
    // Default to viewing selected only when editing to show current configuration clearly
    setViewSelectedOnly(true);
    setIsModalOpen(true);
  };

  const handleDeleteClick = (id: string, name: string, e: React.MouseEvent) => {
    e.stopPropagation(); 
    setDeleteModal({ isOpen: true, id, name });
  };

  const confirmDelete = async () => {
    if (deleteModal.id) {
        try {
            await groupService.delete(deleteModal.id);
            fetchData();
        } catch (error) {
            console.error("Failed to delete group:", error);
        }
    }
    setDeleteModal({ isOpen: false, id: null, name: '' });
  };

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    setIsSaving(true);
    
    try {
        if (editingGroup) {
            await groupService.update(editingGroup.id, formData);
        } else {
            await groupService.create(formData);
        }
        setIsModalOpen(false);
        fetchData();
    } catch (error) {
        console.error("Failed to save group:", error);
    } finally {
        setIsSaving(false);
    }
  };

  const toggleTargetSelection = (providerId: string, model: string) => {
    const currentTargets = formData.targets || [];
    const exists = currentTargets.some(t => t.providerId === providerId && t.model === model);
    
    if (exists) {
        setFormData({
            ...formData,
            targets: currentTargets.filter(t => !(t.providerId === providerId && t.model === model))
        });
    } else {
        setFormData({
            ...formData,
            targets: [...currentTargets, { providerId, model }]
        });
    }
  };

  const toggleProviderExpand = (providerId: string) => {
    if (expandedProviders.includes(providerId)) {
        setExpandedProviders(expandedProviders.filter(id => id !== providerId));
    } else {
        setExpandedProviders([...expandedProviders, providerId]);
    }
  };
  
  // Helper to find provider name
  const getProviderName = (id: string) => {
      const provider = providers.find(c => c.id === id);
      return provider ? provider.name : `Provider #${id}`;
  };

  const activeProviders = providers.filter(c => c.status === 'active');

  // Logic to identify selected targets that are invalid (missing provider or missing model)
  const invalidTargets = formData.targets?.filter(t => {
    const provider = providers.find(c => c.id === t.providerId);
    if (!provider) return true; // Provider does not exist
    if (!provider.models.includes(t.model)) return true; // Model does not exist in provider
    return false;
  }) || [];

  return (
    <div className="space-y-6 relative flex flex-col h-full">
      <div className="flex flex-col sm:flex-row justify-between items-start sm:items-center gap-4">
        <div>
          <h1 className="text-2xl font-bold text-slate-900 dark:text-white">{t('groups.title')}</h1>
          <p className="text-slate-500 dark:text-slate-400 mt-1">{t('groups.subtitle')}</p>
        </div>
        <button 
          onClick={handleOpenAdd}
          className="flex items-center px-4 py-2 bg-indigo-600 hover:bg-indigo-700 text-white rounded-lg font-medium shadow-sm shadow-indigo-200"
        >
          <Plus size={18} className="mr-2" />
          {t('groups.createGroup')}
        </button>
      </div>

      {/* Delete Confirmation Modal */}
      {deleteModal.isOpen && (
        <div className="fixed inset-0 z-[60] flex items-center justify-center bg-slate-900/40 backdrop-blur-sm p-4 animate-fade-in">
            <div className="bg-white dark:bg-slate-800 rounded-xl shadow-2xl max-w-sm w-full p-6 animate-in zoom-in-95 duration-200 border border-slate-200 dark:border-slate-700">
                <div className="flex items-center justify-center w-12 h-12 bg-red-100 dark:bg-red-900/20 rounded-full mx-auto mb-4 text-red-600 dark:text-red-400">
                    <AlertTriangle size={24} />
                </div>
                <h3 className="text-lg font-bold text-center text-slate-900 dark:text-white mb-2">Delete Group?</h3>
                <p className="text-center text-slate-500 dark:text-slate-400 text-sm mb-6">
                    Are you sure you want to delete <span className="font-semibold text-slate-700 dark:text-slate-300">{deleteModal.name}</span>? This action cannot be undone.
                </p>
                <div className="flex space-x-3">
                    <button 
                        onClick={() => setDeleteModal({isOpen: false, id: null, name: ''})}
                        className="flex-1 px-4 py-2 border border-slate-300 dark:border-slate-600 text-slate-700 dark:text-slate-300 font-medium rounded-lg hover:bg-slate-50 dark:hover:bg-slate-700 transition-colors"
                    >
                        {t('common.cancel')}
                    </button>
                    <button 
                        onClick={confirmDelete}
                        className="flex-1 px-4 py-2 bg-red-600 text-white font-medium rounded-lg hover:bg-red-700 transition-colors shadow-sm"
                    >
                        {t('common.delete')}
                    </button>
                </div>
            </div>
        </div>
      )}

      <div className="flex-1 flex flex-col min-h-0">
          {isLoading ? (
            <CardGridSkeleton count={4} />
          ) : (
            <>
            <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 2xl:grid-cols-4 gap-6">
                {groups.length === 0 ? (
                    <div className="col-span-full text-center py-12 bg-white dark:bg-slate-800 rounded-xl border border-dashed border-slate-300 dark:border-slate-700 animate-fade-in">
                        <p className="text-slate-500 dark:text-slate-400">No groups found. Create one to get started.</p>
                    </div>
                ) : (
                    groups.map((group, index) => (
                    <SlideInItem key={group.id} index={index}>
                    <div className="group bg-white dark:bg-slate-800 rounded-xl border border-slate-200 dark:border-slate-700 shadow-sm hover:border-indigo-300 dark:hover:border-indigo-700 hover:shadow-md transition-all duration-200 flex flex-col h-full">
                        <div className="p-6 flex-1">
                            <div className="flex justify-between items-start mb-4">
                                <div className="p-2 bg-slate-100 dark:bg-slate-900/50 rounded-lg text-slate-600 dark:text-slate-400 group-hover:bg-indigo-50 dark:group-hover:bg-indigo-900/30 group-hover:text-indigo-600 dark:group-hover:text-indigo-400 transition-colors">
                                    <Layers size={24} />
                                </div>
                                <div className="flex space-x-1">
                                <button 
                                    onClick={() => handleOpenEdit(group)}
                                    className="p-2 text-slate-400 hover:text-indigo-600 dark:hover:text-indigo-400 hover:bg-indigo-50 dark:hover:bg-indigo-900/30 rounded-lg transition-colors"
                                >
                                    <Settings2 size={18} />
                                </button>
                                <button 
                                    onClick={(e) => handleDeleteClick(group.id, group.name, e)}
                                    className="p-2 text-slate-400 hover:text-red-600 dark:hover:text-red-400 hover:bg-red-50 dark:hover:bg-red-900/20 rounded-lg transition-colors"
                                >
                                    <Trash2 size={18} />
                                </button>
                                </div>
                            </div>
                            
                            <h3 className="text-lg font-bold text-slate-900 dark:text-white mb-2">{group.name}</h3>
                            
                            <div className="flex items-center space-x-2 mb-6">
                                <span className="flex items-center px-2.5 py-1 rounded-md bg-slate-100 dark:bg-slate-900 text-slate-600 dark:text-slate-300 text-xs font-medium border border-slate-200 dark:border-slate-700">
                                    <span className="mr-1.5">{getModeIcon(group.mode)}</span>
                                    {getModeLabel(group.mode)}
                                </span>
                                <span className="text-xs text-slate-400 px-2 py-1 bg-slate-50 dark:bg-slate-900 rounded border border-slate-100 dark:border-slate-700">
                                    {group.firstTokenTimeout}ms {t('common.timeout')}
                                </span>
                            </div>

                            <div className="space-y-3">
                                <p className="text-xs font-semibold text-slate-500 dark:text-slate-400 uppercase tracking-wider">{t('groups.activeProviders')}</p>
                                {group.targets.length === 0 ? (
                                <p className="text-sm text-slate-400 italic">No models selected</p>
                                ) : (
                                <div className="max-h-48 overflow-y-auto space-y-1.5 pr-1 custom-scrollbar">
                                    {group.targets.map((target, idx) => {
                                        const provider = providers.find(c => c.id === target.providerId);
                                        const isProviderMissing = !provider;
                                        const isModelMissing = provider && !provider.models.includes(target.model);
                                        const isInvalid = isProviderMissing || isModelMissing;

                                        return (
                                        <div key={`${target.providerId}-${target.model}-${idx}`} className={`flex items-center justify-between text-xs p-2 rounded border ${isInvalid ? 'bg-red-50 dark:bg-red-900/20 border-red-200 dark:border-red-800' : 'bg-slate-50 dark:bg-slate-900 border-slate-100 dark:border-slate-800'}`}>
                                            <div className="flex flex-col overflow-hidden">
                                                <span className={`font-semibold truncate ${isInvalid ? 'text-red-700 dark:text-red-400' : 'text-slate-700 dark:text-slate-300'}`}>
                                                    {isProviderMissing ? `Unknown Provider (${target.providerId})` : provider?.name}
                                                </span>
                                                <div className="flex items-center gap-1.5">
                                                    <span className={`font-mono mt-0.5 truncate ${isInvalid ? 'text-red-600 dark:text-red-300' : 'text-slate-500 dark:text-slate-400'}`}>
                                                        {target.model}
                                                    </span>
                                                    {isInvalid && (
                                                        <span className="text-[10px] font-bold text-red-500 dark:text-red-400 bg-red-100/60 dark:bg-red-900/40 px-1 rounded-sm">
                                                            {isProviderMissing ? 'Missing Provider' : 'Invalid Model'}
                                                        </span>
                                                    )}
                                                </div>
                                            </div>
                                            {isInvalid ? (
                                                <AlertTriangle size={14} className="text-red-500 dark:text-red-400 flex-shrink-0 ml-2" />
                                            ) : (
                                                <span className="w-1.5 h-1.5 rounded-full bg-green-500 flex-shrink-0 ml-2"></span>
                                            )}
                                        </div>
                                        );
                                    })}
                                </div>
                                )}
                            </div>
                        </div>
                        <div className="p-4 bg-slate-50 dark:bg-slate-900/50 border-t border-slate-100 dark:border-slate-700 rounded-b-xl flex justify-between items-center text-xs text-slate-500 dark:text-slate-400">
                        <span>ID: {group.id}</span>
                        <span className="font-mono">{group.targets.length} targets</span>
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
                    className="border-t border-slate-200 dark:border-slate-800 mt-4 pt-4"
                />
            </div>
            </>
          )}
      </div>

      {/* Add/Edit Modal */}
      {isModalOpen && (
        <div className="fixed inset-0 z-50 flex items-center justify-center bg-slate-900/50 backdrop-blur-sm p-4 animate-fade-in">
            <div className="bg-white dark:bg-slate-800 rounded-xl shadow-xl max-w-lg w-full max-h-[90vh] overflow-hidden flex flex-col animate-in zoom-in-95 duration-200 border border-slate-200 dark:border-slate-700">
                <div className="px-6 py-4 border-b border-slate-100 dark:border-slate-700 flex justify-between items-center flex-shrink-0">
                    <h2 className="text-lg font-bold text-slate-900 dark:text-white">
                        {editingGroup ? t('groups.modal.titleEdit') : t('groups.modal.titleAdd')}
                    </h2>
                    <button onClick={() => setIsModalOpen(false)} className="text-slate-400 hover:text-slate-600 dark:hover:text-slate-300">
                        <X size={20} />
                    </button>
                </div>
                
                <div className="overflow-y-auto p-6 space-y-4 flex-1">
                    <form id="group-form" onSubmit={handleSubmit} className="space-y-4">
                        {/* Name */}
                        <div>
                            <label className="block text-sm font-medium text-slate-700 dark:text-slate-300 mb-1">{t('groups.modal.name')}</label>
                            <input 
                                type="text" 
                                required
                                value={formData.name}
                                onChange={(e) => setFormData({...formData, name: e.target.value})}
                                className="block w-full rounded-lg border-slate-300 dark:border-slate-600 shadow-sm focus:border-indigo-500 focus:ring-indigo-500 sm:text-sm p-2 border bg-white dark:bg-slate-900 dark:text-white"
                            />
                        </div>
                        
                        <div className="grid grid-cols-2 gap-4">
                            {/* Mode */}
                            <div>
                                <label className="block text-sm font-medium text-slate-700 dark:text-slate-300 mb-1">{t('groups.modal.mode')}</label>
                                <select 
                                    value={formData.mode}
                                    disabled={true}
                                    onChange={(e) => setFormData({...formData, mode: e.target.value as LoadBalanceMode})}
                                    className="block w-full rounded-lg border-slate-300 dark:border-slate-600 shadow-sm focus:border-indigo-500 focus:ring-indigo-500 sm:text-sm p-2 border bg-slate-100 dark:bg-slate-900 text-slate-500 dark:text-slate-400 cursor-not-allowed"
                                >
                                    {Object.values(LoadBalanceMode).map((mode) => (
                                        <option key={mode} value={mode}>{getModeLabel(mode)}</option>
                                    ))}
                                </select>
                            </div>
                            {/* Timeout */}
                            <div>
                                <label className="block text-sm font-medium text-slate-700 dark:text-slate-300 mb-1">{t('groups.modal.timeout')}</label>
                                <input 
                                    type="number"
                                    required
                                    min="100"
                                    value={formData.firstTokenTimeout}
                                    onChange={(e) => setFormData({...formData, firstTokenTimeout: parseInt(e.target.value) || 0})}
                                    className="block w-full rounded-lg border-slate-300 dark:border-slate-600 shadow-sm focus:border-indigo-500 focus:ring-indigo-500 sm:text-sm p-2 border bg-white dark:bg-slate-900 dark:text-white"
                                />
                            </div>
                        </div>

                        {/* Providers & Models Selection */}
                        <div>
                            <label className="block text-sm font-medium text-slate-700 dark:text-slate-300 mb-2">{t('groups.modal.selectedProviders')}</label>
                            
                            {/* Model Search & Filter */}
                            <div className="flex space-x-2 mb-2">
                                <div className="relative flex-1">
                                    <Search className="absolute left-3 top-2.5 text-slate-400" size={16} />
                                    <input 
                                        type="text" 
                                        placeholder={t('groups.modal.searchModels')} 
                                        value={modelFilter}
                                        onChange={(e) => setModelFilter(e.target.value)}
                                        className="block w-full pl-9 pr-3 py-2 border border-slate-300 dark:border-slate-600 rounded-lg leading-5 bg-white dark:bg-slate-900 dark:text-white placeholder-slate-400 focus:outline-none focus:ring-indigo-500 focus:border-indigo-500 sm:text-sm"
                                    />
                                </div>
                                <button
                                    type="button"
                                    onClick={() => setViewSelectedOnly(!viewSelectedOnly)}
                                    className={`px-3 py-2 text-xs font-medium rounded-lg border flex items-center transition-colors ${
                                        viewSelectedOnly 
                                        ? 'bg-indigo-50 dark:bg-indigo-900/30 border-indigo-200 dark:border-indigo-800 text-indigo-700 dark:text-indigo-300' 
                                        : 'bg-white dark:bg-slate-800 border-slate-300 dark:border-slate-600 text-slate-600 dark:text-slate-300 hover:bg-slate-50 dark:hover:bg-slate-700'
                                    }`}
                                >
                                    <Filter size={14} className="mr-1.5" />
                                    {t('groups.modal.viewSelected')}
                                </button>
                            </div>

                            {/* Invalid Selections Warning */}
                            {invalidTargets.length > 0 && (
                                <div className="mb-3 p-3 bg-red-50 dark:bg-red-900/20 border border-red-200 dark:border-red-800 rounded-lg">
                                    <div className="flex items-center mb-2 text-red-800 dark:text-red-300 text-xs font-bold uppercase tracking-wide">
                                        <AlertTriangle size={12} className="mr-1.5" />
                                        {t('groups.modal.invalidSelections')}
                                    </div>
                                    <div className="space-y-2">
                                        {invalidTargets.map((target, idx) => {
                                            const provider = providers.find(c => c.id === target.providerId);
                                            const isProviderMissing = !provider;
                                            return (
                                                <div 
                                                    key={`invalid-${target.providerId}-${target.model}-${idx}`}
                                                    onClick={() => toggleTargetSelection(target.providerId, target.model)}
                                                    className="flex items-center justify-between p-2 bg-white dark:bg-slate-800 border border-red-100 dark:border-red-800 rounded text-xs cursor-pointer hover:bg-red-50 dark:hover:bg-red-900/30 transition-colors group"
                                                    title="Click to remove"
                                                >
                                                    <div className="flex items-center gap-2 overflow-hidden">
                                                        <div className="flex flex-col">
                                                            <span className="font-semibold text-red-700 dark:text-red-400 truncate">
                                                                {isProviderMissing ? `Unknown Provider (${target.providerId})` : provider?.name}
                                                            </span>
                                                            <span className="font-mono text-red-600 dark:text-red-300 truncate">
                                                                {target.model}
                                                            </span>
                                                        </div>
                                                    </div>
                                                    <div className="flex items-center text-red-500 dark:text-red-400 text-[10px] font-medium">
                                                        <span className="mr-2">{isProviderMissing ? 'Provider Missing' : 'Model Missing'}</span>
                                                        <X size={14} className="group-hover:scale-110 transition-transform" />
                                                    </div>
                                                </div>
                                            );
                                        })}
                                    </div>
                                </div>
                            )}

                            <div className="border border-slate-200 dark:border-slate-700 rounded-lg bg-slate-50 dark:bg-slate-900 overflow-hidden">
                                {activeProviders.length === 0 ? (
                                    <div className="p-4 text-center text-sm text-slate-500 dark:text-slate-400">
                                        No active providers available. Please add and enable a provider first.
                                    </div>
                                ) : (
                                    providers.map((provider) => {
                                        // Filter out inactive providers (unless in viewSelectedOnly mode where logic handles check, but here we strictly hide inactive)
                                        if (provider.status !== 'active') return null;

                                        // 1. Filter models based on search term
                                        let displayModels = provider.models;

                                        if (modelFilter) {
                                            displayModels = displayModels.filter(m => m.toLowerCase().includes(modelFilter.toLowerCase()));
                                        }

                                        // 2. Filter by View Selected
                                        if (viewSelectedOnly) {
                                            displayModels = displayModels.filter(m => formData.targets?.some(t => t.providerId === provider.id && t.model === m));
                                        }

                                        // If filtering and no match, hide provider
                                        if ((modelFilter || viewSelectedOnly) && displayModels.length === 0) return null;
                                        
                                        // Determine if expanded: manual expansion OR auto-expand when filtering
                                        const isExpanded = expandedProviders.includes(provider.id) || modelFilter.length > 0 || viewSelectedOnly;
                                        const selectedCount = formData.targets?.filter(t => t.providerId === provider.id).length || 0;

                                        return (
                                            <div key={provider.id} className="border-b border-slate-200 dark:border-slate-700 last:border-0">
                                                <div 
                                                    onClick={() => toggleProviderExpand(provider.id)}
                                                    className="flex items-center justify-between p-3 bg-white dark:bg-slate-800 cursor-pointer hover:bg-slate-50 dark:hover:bg-slate-700 transition-colors"
                                                >
                                                    <div className="flex items-center">
                                                        <span className="text-slate-400 mr-2">
                                                            {isExpanded ? <ChevronDown size={16} /> : <ChevronRight size={16} />}
                                                        </span>
                                                        <span className="font-medium text-slate-700 dark:text-slate-200">{provider.name}</span>
                                                        {selectedCount > 0 && (
                                                            <span className="ml-2 px-1.5 py-0.5 bg-indigo-100 dark:bg-indigo-900/50 text-indigo-700 dark:text-indigo-300 text-[10px] font-bold rounded-full">
                                                                {selectedCount}
                                                            </span>
                                                        )}
                                                    </div>
                                                    <span className="text-xs text-slate-400 uppercase">API</span>
                                                </div>
                                                
                                                {isExpanded && (
                                                    <div className="bg-slate-50 dark:bg-slate-900 p-2 space-y-1 shadow-inner animate-fade-in">
                                                        {displayModels.map((model) => {
                                                            const isSelected = formData.targets?.some(t => t.providerId === provider.id && t.model === model);
                                                            return (
                                                                <div 
                                                                    key={`${provider.id}-${model}`}
                                                                    onClick={() => toggleTargetSelection(provider.id, model)}
                                                                    className={`flex items-center p-2 rounded cursor-pointer transition-all ${
                                                                        isSelected 
                                                                        ? 'bg-white dark:bg-slate-800 text-indigo-700 dark:text-indigo-300 border border-indigo-200 dark:border-indigo-800 shadow-sm' 
                                                                        : 'text-slate-600 dark:text-slate-400 hover:bg-slate-100 dark:hover:bg-slate-800 border border-transparent'
                                                                    }`}
                                                                >
                                                                    <div className={`w-4 h-4 rounded border flex items-center justify-center mr-3 flex-shrink-0 transition-colors ${isSelected ? 'bg-indigo-600 border-indigo-600' : 'bg-white dark:bg-slate-800 border-slate-300 dark:border-slate-600'}`}>
                                                                        {isSelected && <Check size={12} className="text-white" />}
                                                                    </div>
                                                                    <span className="text-sm font-mono">{model}</span>
                                                                </div>
                                                            );
                                                        })}
                                                        {displayModels.length === 0 && (
                                                            <p className="text-xs text-slate-400 italic p-2 text-center">No models available</p>
                                                        )}
                                                    </div>
                                                )}
                                            </div>
                                        );
                                    })
                                )}
                            </div>
                            <div className="mt-2 flex justify-between items-center">
                                <p className="text-xs text-slate-500 dark:text-slate-400">
                                   Click provider to expand models
                                </p>
                                <p className="text-xs font-medium text-indigo-600 dark:text-indigo-400">
                                   {formData.targets?.length} models selected
                                </p>
                            </div>
                        </div>
                    </form>
                </div>

                <div className="px-6 py-4 border-t border-slate-100 dark:border-slate-700 flex justify-end space-x-3 bg-white dark:bg-slate-800 flex-shrink-0">
                    <button 
                        type="button"
                        onClick={() => setIsModalOpen(false)}
                        className="px-4 py-2 border border-slate-300 dark:border-slate-600 shadow-sm text-sm font-medium rounded-lg text-slate-700 dark:text-slate-200 bg-white dark:bg-slate-800 hover:bg-slate-50 dark:hover:bg-slate-700"
                    >
                        {t('common.cancel')}
                    </button>
                    <button 
                        type="submit"
                        form="group-form"
                        disabled={isSaving}
                        className="flex items-center px-4 py-2 border border-transparent text-sm font-medium rounded-lg shadow-sm text-white bg-indigo-600 hover:bg-indigo-700 disabled:opacity-70 disabled:cursor-not-allowed"
                    >
                        {isSaving ? <Loader2 size={16} className="mr-2 animate-spin" /> : <Save size={16} className="mr-2" />}
                        {t('common.save')}
                    </button>
                </div>
            </div>
        </div>
      )}
    </div>
  );
};