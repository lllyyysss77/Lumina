import React, { useState, useEffect } from 'react';
import { Group, LoadBalanceMode, Provider } from '../types';
import { Layers, Shuffle, ArrowRightLeft, Scale, PlayCircle, Plus, Settings2, Trash2, X, Save, Check, ChevronDown, ChevronRight, AlertTriangle, Loader2, Search, Filter, Activity, Target, Clock, ArrowRight } from 'lucide-react';
import { useLanguage } from './LanguageContext';
import { groupService } from '../services/groupService';
import { providerService } from '../services/providerService';
import { CardGridSkeleton } from './Skeletons';
import { SlideInItem } from './Animations';
import { Pagination } from './Pagination';
import metadata from '../constants';

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

  const getModeColor = (mode: LoadBalanceMode) => {
    switch(mode) {
        case LoadBalanceMode.ROUND_ROBIN: return 'text-blue-500 bg-blue-50 dark:bg-blue-900/20 border-blue-200 dark:border-blue-800';
        case LoadBalanceMode.RANDOM: return 'text-orange-500 bg-orange-50 dark:bg-orange-900/20 border-orange-200 dark:border-orange-800';
        case LoadBalanceMode.WEIGHTED: return 'text-purple-500 bg-purple-50 dark:bg-purple-900/20 border-purple-200 dark:border-purple-800';
        case LoadBalanceMode.FAILOVER: return 'text-red-500 bg-red-50 dark:bg-red-900/20 border-red-200 dark:border-red-800';
        case LoadBalanceMode.SAPR: return 'text-emerald-500 bg-emerald-50 dark:bg-emerald-900/20 border-emerald-200 dark:border-emerald-800';
        default: return 'text-slate-500 bg-slate-50';
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
    setIsModalOpen(true);
  };

  const handleOpenEdit = (group: Group) => {
    setEditingGroup(group);
    setFormData({ ...group });
    // Only expand providers that are currently used in the group
    const usedProviderIds = Array.from(new Set(group.targets.map(t => t.providerId)));
    setExpandedProviders(usedProviderIds);
    setModelFilter('');
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

  const clearAllSelected = () => {
      setFormData({...formData, targets: []});
  }

  const activeProviders = providers.filter(c => c.status === 'active');

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
          <h1 className="text-3xl font-extrabold text-slate-900 dark:text-white tracking-tight">{t('groups.title')}</h1>
          <p className="text-slate-500 dark:text-slate-400 mt-2 text-lg">{t('groups.subtitle')}</p>
        </div>
        <button 
          onClick={handleOpenAdd}
          className="group flex items-center px-5 py-2.5 bg-gradient-to-r from-indigo-600 to-violet-600 hover:from-indigo-500 hover:to-violet-500 text-white rounded-xl font-semibold shadow-lg shadow-indigo-500/25 transition-all hover:-translate-y-0.5"
        >
          <Plus size={20} className="mr-2 transition-transform group-hover:rotate-90" />
          {t('groups.createGroup')}
        </button>
      </div>

      {/* Delete Confirmation Modal */}
      {deleteModal.isOpen && (
        <div className="fixed inset-0 z-[60] flex items-center justify-center bg-slate-900/60 backdrop-blur-sm p-4 animate-fade-in">
            <div className="bg-white dark:bg-slate-900 rounded-2xl shadow-2xl max-w-sm w-full p-6 animate-in zoom-in-95 duration-200 border border-white/20 dark:border-slate-700">
                <div className="flex items-center justify-center w-14 h-14 bg-red-100 dark:bg-red-900/30 rounded-full mx-auto mb-4 text-red-600 dark:text-red-400 shadow-inner">
                    <Trash2 size={28} />
                </div>
                <h3 className="text-xl font-bold text-center text-slate-900 dark:text-white mb-2">Delete Group?</h3>
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

      <div className="flex-1 flex flex-col min-h-0">
          {isLoading ? (
            <CardGridSkeleton count={4} />
          ) : (
            <>
            <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 2xl:grid-cols-4 gap-6">
                {groups.length === 0 ? (
                    <div className="col-span-full py-20 text-center bg-white/60 dark:bg-slate-900/60 backdrop-blur-md rounded-2xl border border-dashed border-slate-300 dark:border-slate-700 animate-fade-in">
                        <Layers size={48} className="mx-auto text-slate-300 mb-4" />
                        <p className="text-lg text-slate-500 dark:text-slate-400 font-medium">No groups found</p>
                        <p className="text-sm text-slate-400 mt-1">Create a group to route requests</p>
                    </div>
                ) : (
                    groups.map((group, index) => (
                    <SlideInItem key={group.id} index={index}>
                    <div className="group bg-white/70 dark:bg-slate-900/70 backdrop-blur-md rounded-2xl border border-white/20 dark:border-slate-700/50 shadow-sm hover:shadow-xl transition-all duration-300 flex flex-col h-full hover:-translate-y-1 relative overflow-hidden">
                        
                        {/* Top Accent Gradient */}
                        <div className="absolute top-0 left-0 w-full h-1.5 bg-gradient-to-r from-indigo-500 via-purple-500 to-pink-500 opacity-80" />

                        <div className="p-6 flex-1">
                            <div className="flex justify-between items-start mb-5">
                                <div className="p-2.5 bg-gradient-to-br from-slate-100 to-slate-200 dark:from-slate-800 dark:to-slate-900 rounded-xl text-slate-600 dark:text-slate-400 shadow-inner">
                                    <Layers size={22} />
                                </div>
                                <div className="flex space-x-1">
                                <button 
                                    onClick={() => handleOpenEdit(group)}
                                    className="p-2 text-slate-400 hover:text-indigo-600 dark:hover:text-indigo-400 hover:bg-indigo-50 dark:hover:bg-indigo-900/30 rounded-lg transition-colors"
                                    title="Edit"
                                >
                                    <Settings2 size={18} />
                                </button>
                                <button 
                                    onClick={(e) => handleDeleteClick(group.id, group.name, e)}
                                    className="p-2 text-slate-400 hover:text-red-600 dark:hover:text-red-400 hover:bg-red-50 dark:hover:bg-red-900/20 rounded-lg transition-colors"
                                    title="Delete"
                                >
                                    <Trash2 size={18} />
                                </button>
                                </div>
                            </div>
                            
                            <h3 className="text-xl font-bold text-slate-900 dark:text-white mb-3 truncate">{group.name}</h3>
                            
                            <div className="flex flex-wrap items-center gap-2 mb-6">
                                <span className={`flex items-center px-2.5 py-1 rounded-lg text-xs font-bold border ${getModeColor(group.mode)}`}>
                                    <span className="mr-1.5">{getModeIcon(group.mode)}</span>
                                    {getModeLabel(group.mode)}
                                </span>
                                <span className="flex items-center text-xs font-medium text-slate-500 dark:text-slate-400 px-2 py-1 bg-slate-100 dark:bg-slate-800/50 rounded-lg border border-slate-200 dark:border-slate-700/50">
                                    <Clock size={12} className="mr-1" />
                                    {group.firstTokenTimeout}ms
                                </span>
                            </div>

                            <div className="space-y-3">
                                <div className="flex items-center justify-between">
                                    <p className="text-xs font-bold text-slate-400 dark:text-slate-500 uppercase tracking-wider">{t('groups.activeProviders')}</p>
                                    <span className="text-xs font-mono text-slate-400 bg-slate-50 dark:bg-slate-800 px-1.5 rounded">{group.targets.length}</span>
                                </div>
                                
                                {group.targets.length === 0 ? (
                                <div className="text-center py-4 bg-slate-50/50 dark:bg-slate-800/30 rounded-xl border border-dashed border-slate-200 dark:border-slate-700">
                                    <p className="text-xs text-slate-400 italic">No models selected</p>
                                </div>
                                ) : (
                                <div className="max-h-40 overflow-y-auto space-y-2 pr-1 custom-scrollbar">
                                    {group.targets.map((target, idx) => {
                                        const provider = providers.find(c => c.id === target.providerId);
                                        const isProviderMissing = !provider;
                                        const isModelMissing = provider && !provider.models.includes(target.model);
                                        const isInvalid = isProviderMissing || isModelMissing;

                                        return (
                                        <div key={`${target.providerId}-${target.model}-${idx}`} className={`flex items-center justify-between text-xs p-2.5 rounded-lg border transition-all ${isInvalid ? 'bg-red-50 dark:bg-red-900/20 border-red-200 dark:border-red-800' : 'bg-white dark:bg-slate-800 border-slate-100 dark:border-slate-700/50 shadow-sm'}`}>
                                            <div className="flex flex-col min-w-0">
                                                <div className="flex items-center gap-1.5">
                                                    <div className={`w-1.5 h-1.5 rounded-full ${isInvalid ? 'bg-red-500' : 'bg-emerald-500'}`}></div>
                                                    <span className={`font-semibold truncate ${isInvalid ? 'text-red-700 dark:text-red-400' : 'text-slate-700 dark:text-slate-200'}`}>
                                                        {isProviderMissing ? `ID: ${target.providerId}` : provider?.name}
                                                    </span>
                                                </div>
                                                <div className="flex items-center gap-1.5 pl-3 mt-0.5">
                                                    <span className={`font-mono truncate max-w-[140px] ${isInvalid ? 'text-red-600 dark:text-red-300' : 'text-slate-500 dark:text-slate-400'}`}>
                                                        {target.model}
                                                    </span>
                                                </div>
                                            </div>
                                            {isInvalid && (
                                                <AlertTriangle size={14} className="text-red-500 dark:text-red-400 flex-shrink-0 ml-1" />
                                            )}
                                        </div>
                                        );
                                    })}
                                </div>
                                )}
                            </div>
                        </div>
                        <div className="px-6 py-3 bg-slate-50/80 dark:bg-slate-900/80 border-t border-slate-100 dark:border-slate-700/50 flex justify-between items-center text-[10px] text-slate-400 uppercase font-medium tracking-wide">
                            <span>ID: {group.id}</span>
                            <span>v{metadata.version}</span>
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
            <div className="bg-white dark:bg-slate-900 rounded-2xl shadow-2xl max-w-5xl w-full max-h-[90vh] overflow-hidden flex flex-col animate-in zoom-in-95 duration-200 border border-white/20 dark:border-slate-700">
                <div className="px-6 py-5 border-b border-slate-100 dark:border-slate-800 flex justify-between items-center bg-white/95 dark:bg-slate-900/95 backdrop-blur z-10">
                    <h2 className="text-xl font-bold text-slate-900 dark:text-white">
                        {editingGroup ? t('groups.modal.titleEdit') : t('groups.modal.titleAdd')}
                    </h2>
                    <button onClick={() => setIsModalOpen(false)} className="text-slate-400 hover:text-slate-600 dark:hover:text-slate-300 transition-colors p-1 rounded-full hover:bg-slate-100 dark:hover:bg-slate-800">
                        <X size={24} />
                    </button>
                </div>
                
                <div className="overflow-y-auto p-6 space-y-6 flex-1 custom-scrollbar">
                    <form id="group-form" onSubmit={handleSubmit} className="space-y-6">
                        {/* Name and Basic Settings */}
                        <div className="grid grid-cols-1 md:grid-cols-3 gap-6">
                            <div className="md:col-span-1">
                                <label className="block text-sm font-semibold text-slate-700 dark:text-slate-300 mb-1.5">{t('groups.modal.name')}</label>
                                <input 
                                    type="text" 
                                    required
                                    value={formData.name}
                                    onChange={(e) => setFormData({...formData, name: e.target.value})}
                                    className="block w-full rounded-xl border-slate-300 dark:border-slate-700 shadow-sm focus:border-indigo-500 focus:ring-indigo-500 text-sm py-2.5 px-3 bg-slate-50 dark:bg-slate-800 dark:text-white transition-all focus:bg-white dark:focus:bg-slate-900"
                                />
                            </div>
                            <div>
                                <label className="block text-sm font-semibold text-slate-700 dark:text-slate-300 mb-1.5">{t('groups.modal.mode')}</label>
                                <div className="relative">
                                    <select 
                                        value={formData.mode}
                                        disabled={true}
                                        onChange={(e) => setFormData({...formData, mode: e.target.value as LoadBalanceMode})}
                                        className="block w-full rounded-xl border-slate-300 dark:border-slate-700 shadow-sm focus:border-indigo-500 focus:ring-indigo-500 text-sm py-2.5 px-3 bg-slate-100 dark:bg-slate-800 text-slate-500 dark:text-slate-400 cursor-not-allowed appearance-none"
                                    >
                                        {Object.values(LoadBalanceMode).map((mode) => (
                                            <option key={mode} value={mode}>{getModeLabel(mode)}</option>
                                        ))}
                                    </select>
                                    <div className="absolute inset-y-0 right-0 flex items-center pr-3 pointer-events-none">
                                        <ChevronDown size={14} className="text-slate-400" />
                                    </div>
                                </div>
                            </div>
                            <div>
                                <label className="block text-sm font-semibold text-slate-700 dark:text-slate-300 mb-1.5">{t('groups.modal.timeout')}</label>
                                <div className="relative">
                                    <input 
                                        type="number"
                                        required
                                        min="100"
                                        value={formData.firstTokenTimeout}
                                        onChange={(e) => setFormData({...formData, firstTokenTimeout: parseInt(e.target.value) || 0})}
                                        className="block w-full rounded-xl border-slate-300 dark:border-slate-700 shadow-sm focus:border-indigo-500 focus:ring-indigo-500 text-sm py-2.5 px-3 bg-slate-50 dark:bg-slate-800 dark:text-white transition-all focus:bg-white dark:focus:bg-slate-900"
                                    />
                                    <div className="absolute inset-y-0 right-0 flex items-center pr-3 pointer-events-none text-xs text-slate-400 font-medium">
                                        ms
                                    </div>
                                </div>
                            </div>
                        </div>

                        {/* Dual Pane Selection */}
                        <div className="grid grid-cols-1 md:grid-cols-2 gap-4 h-[500px]">
                            {/* Left Column: Available Models */}
                            <div className="flex flex-col bg-slate-50 dark:bg-slate-800/30 rounded-xl border border-slate-200 dark:border-slate-700/50 overflow-hidden shadow-sm">
                                <div className="p-3 border-b border-slate-200 dark:border-slate-700/50 bg-white/50 dark:bg-slate-900/50 backdrop-blur z-10 space-y-3">
                                    <label className="text-xs font-bold text-slate-500 dark:text-slate-400 uppercase tracking-wider block">
                                        Available Models
                                    </label>
                                    <div className="relative">
                                        <Search className="absolute left-2.5 top-2.5 text-slate-400" size={14} />
                                        <input 
                                            type="text" 
                                            placeholder={t('groups.modal.searchModels')} 
                                            value={modelFilter}
                                            onChange={(e) => setModelFilter(e.target.value)}
                                            className="block w-full pl-8 pr-3 py-2 border border-slate-200 dark:border-slate-600 rounded-lg text-sm bg-white dark:bg-slate-900 dark:text-white placeholder-slate-400 focus:outline-none focus:ring-2 focus:ring-indigo-500 focus:border-transparent transition-all"
                                        />
                                    </div>
                                </div>
                                
                                <div className="flex-1 overflow-y-auto p-3 custom-scrollbar space-y-2">
                                     {activeProviders.length === 0 ? (
                                        <div className="h-full flex flex-col items-center justify-center text-slate-400 text-sm">
                                            <AlertTriangle size={24} className="mb-2 opacity-50" />
                                            No active providers
                                        </div>
                                     ) : (
                                        activeProviders.map(provider => {
                                            // Filter models
                                            let displayModels = provider.models;
                                            if (modelFilter) {
                                                displayModels = displayModels.filter(m => m.toLowerCase().includes(modelFilter.toLowerCase()));
                                            }
                                            
                                            if (displayModels.length === 0) return null;

                                            const isExpanded = expandedProviders.includes(provider.id) || modelFilter.length > 0;
                                            
                                            // Calculate how many selected in this provider
                                            const selectedCount = formData.targets?.filter(t => t.providerId === provider.id && displayModels.includes(t.model)).length || 0;

                                            return (
                                                <div key={provider.id} className="bg-white dark:bg-slate-900 rounded-lg border border-slate-200 dark:border-slate-700/50 overflow-hidden shadow-sm transition-shadow hover:shadow">
                                                    <div 
                                                        onClick={() => toggleProviderExpand(provider.id)}
                                                        className="flex items-center justify-between p-3 cursor-pointer bg-slate-50/50 dark:bg-slate-800/50 hover:bg-slate-100 dark:hover:bg-slate-800 transition-colors"
                                                    >
                                                        <div className="flex items-center gap-2 overflow-hidden">
                                                            <ChevronRight size={14} className={`text-slate-400 transition-transform duration-200 ${isExpanded ? 'rotate-90' : ''}`} />
                                                            <span className="font-semibold text-sm text-slate-700 dark:text-slate-200 truncate">{provider.name}</span>
                                                        </div>
                                                        {selectedCount > 0 && (
                                                            <span className="text-[10px] font-bold px-1.5 py-0.5 bg-indigo-100 dark:bg-indigo-900/50 text-indigo-600 dark:text-indigo-400 rounded-full">
                                                                {selectedCount}
                                                            </span>
                                                        )}
                                                    </div>
                                                    
                                                    {isExpanded && (
                                                        <div className="border-t border-slate-100 dark:border-slate-800 divide-y divide-slate-100 dark:divide-slate-800">
                                                            {displayModels.map(model => {
                                                                const isSelected = formData.targets?.some(t => t.providerId === provider.id && t.model === model);
                                                                return (
                                                                    <div 
                                                                        key={model}
                                                                        onClick={() => toggleTargetSelection(provider.id, model)}
                                                                        className={`flex items-center p-2.5 text-sm cursor-pointer hover:bg-slate-50 dark:hover:bg-slate-800 transition-colors ${isSelected ? 'bg-indigo-50/50 dark:bg-indigo-900/10' : ''}`}
                                                                    >
                                                                        <div className={`w-4 h-4 rounded border flex items-center justify-center mr-3 transition-all flex-shrink-0 ${isSelected ? 'bg-indigo-600 border-indigo-600' : 'border-slate-300 dark:border-slate-600 bg-white dark:bg-slate-900'}`}>
                                                                            {isSelected && <Check size={10} className="text-white" />}
                                                                        </div>
                                                                        <span className={`truncate font-mono ${isSelected ? 'text-indigo-700 dark:text-indigo-300 font-medium' : 'text-slate-600 dark:text-slate-400'}`}>{model}</span>
                                                                    </div>
                                                                )
                                                            })}
                                                        </div>
                                                    )}
                                                </div>
                                            )
                                        })
                                     )}
                                </div>
                            </div>

                            {/* Right Column: Selected Models */}
                            <div className="flex flex-col bg-slate-50 dark:bg-slate-800/30 rounded-xl border border-slate-200 dark:border-slate-700/50 overflow-hidden shadow-sm">
                                <div className="p-3 border-b border-slate-200 dark:border-slate-700/50 bg-white/50 dark:bg-slate-900/50 backdrop-blur flex justify-between items-center h-[85px]">
                                    <label className="text-xs font-bold text-slate-500 dark:text-slate-400 uppercase tracking-wider">
                                        Selected ({formData.targets?.length || 0})
                                    </label>
                                    {formData.targets && formData.targets.length > 0 && (
                                        <button 
                                            type="button"
                                            onClick={clearAllSelected}
                                            className="text-xs font-bold text-red-500 hover:text-red-700 dark:text-red-400 dark:hover:text-red-300 px-2 py-1 rounded-md hover:bg-red-50 dark:hover:bg-red-900/20 transition-colors"
                                        >
                                            Clear All
                                        </button>
                                    )}
                                </div>
                                <div className="flex-1 overflow-y-auto p-3 custom-scrollbar space-y-2">
                                    {(!formData.targets || formData.targets.length === 0) ? (
                                        <div className="h-full flex flex-col items-center justify-center text-slate-400">
                                            <Target size={32} className="mb-2 opacity-30" />
                                            <p className="text-xs">No models selected</p>
                                        </div>
                                    ) : (
                                        formData.targets.map((target, idx) => {
                                            const provider = providers.find(p => p.id === target.providerId);
                                            const isInvalid = !provider || (provider && !provider.models.includes(target.model));
                                            
                                            return (
                                                <div key={`${target.providerId}-${target.model}-${idx}`} className="flex items-center justify-between p-2.5 bg-white dark:bg-slate-900 border border-slate-200 dark:border-slate-700/50 rounded-lg group hover:border-indigo-300 dark:hover:border-indigo-700 transition-colors shadow-sm animate-in fade-in zoom-in-95 duration-200">
                                                    <div className="flex flex-col overflow-hidden mr-2">
                                                        <span className="text-xs font-bold text-slate-700 dark:text-slate-300 truncate">
                                                            {provider ? provider.name : <span className="text-red-500">Unknown Provider ({target.providerId})</span>}
                                                        </span>
                                                        <span className={`text-xs font-mono truncate ${isInvalid ? 'text-red-500' : 'text-slate-500 dark:text-slate-400'}`}>
                                                            {target.model}
                                                        </span>
                                                    </div>
                                                    <button 
                                                        type="button"
                                                        onClick={() => toggleTargetSelection(target.providerId, target.model)}
                                                        className="p-1.5 text-slate-400 hover:text-red-500 hover:bg-red-50 dark:hover:bg-red-900/20 rounded transition-colors"
                                                    >
                                                        <X size={14} />
                                                    </button>
                                                </div>
                                            )
                                        })
                                    )}
                                </div>
                            </div>
                        </div>

                        {/* Invalid Selections Warning */}
                        {invalidTargets.length > 0 && (
                            <div className="p-3 bg-red-50 dark:bg-red-900/20 border border-red-200 dark:border-red-800 rounded-lg animate-in slide-in-from-top-2">
                                <div className="flex items-center mb-2 text-red-800 dark:text-red-300 text-xs font-bold uppercase tracking-wide">
                                    <AlertTriangle size={12} className="mr-1.5" />
                                    {t('groups.modal.invalidSelections')}
                                </div>
                                <p className="text-xs text-red-600 dark:text-red-400">
                                    There are {invalidTargets.length} invalid selections. They may refer to deleted providers or models. Please check the 'Selected' list.
                                </p>
                            </div>
                        )}

                    </form>
                </div>

                <div className="px-6 py-5 border-t border-slate-100 dark:border-slate-800 flex justify-end space-x-3 bg-white/95 dark:bg-slate-900/95 backdrop-blur flex-shrink-0">
                    <button 
                        type="button"
                        onClick={() => setIsModalOpen(false)}
                        className="px-5 py-2.5 border border-slate-300 dark:border-slate-600 shadow-sm text-sm font-semibold rounded-xl text-slate-700 dark:text-slate-200 bg-white dark:bg-slate-800 hover:bg-slate-50 dark:hover:bg-slate-700 transition-colors"
                    >
                        {t('common.cancel')}
                    </button>
                    <button 
                        type="submit"
                        form="group-form"
                        disabled={isSaving}
                        className="flex items-center px-5 py-2.5 border border-transparent text-sm font-semibold rounded-xl shadow-lg shadow-indigo-500/20 text-white bg-indigo-600 hover:bg-indigo-700 disabled:opacity-70 disabled:cursor-not-allowed transition-all hover:-translate-y-0.5"
                    >
                        {isSaving ? <Loader2 size={18} className="mr-2 animate-spin" /> : <Save size={18} className="mr-2" />}
                        {t('common.save')}
                    </button>
                </div>
            </div>
        </div>
      )}
    </div>
  );
};