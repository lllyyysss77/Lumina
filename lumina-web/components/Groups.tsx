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
        case LoadBalanceMode.ROUND_ROBIN: return <ArrowRightLeft size={14} />;
        case LoadBalanceMode.RANDOM: return <Shuffle size={14} />;
        case LoadBalanceMode.WEIGHTED: return <Scale size={14} />;
        case LoadBalanceMode.FAILOVER: return <PlayCircle size={14} />;
        case LoadBalanceMode.SAPR: return <Activity size={14} />;
        default: return <Layers size={14} />;
    }
  };

  const getModeColor = (mode: LoadBalanceMode) => {
    switch(mode) {
        case LoadBalanceMode.ROUND_ROBIN: return 'text-blue-600 bg-blue-50 dark:bg-blue-900/30 border-blue-100 dark:border-blue-900/50';
        case LoadBalanceMode.RANDOM: return 'text-orange-600 bg-orange-50 dark:bg-orange-900/30 border-orange-100 dark:border-orange-900/50';
        case LoadBalanceMode.WEIGHTED: return 'text-purple-600 bg-purple-50 dark:bg-purple-900/30 border-purple-100 dark:border-purple-900/50';
        case LoadBalanceMode.FAILOVER: return 'text-red-600 bg-red-50 dark:bg-red-900/30 border-red-100 dark:border-red-900/50';
        case LoadBalanceMode.SAPR: return 'text-emerald-600 bg-emerald-50 dark:bg-emerald-900/30 border-emerald-100 dark:border-emerald-900/50';
        default: return 'text-gray-600 bg-gray-50';
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
          <h1 className="text-3xl font-extrabold text-gray-900 dark:text-white tracking-tight">{t('groups.title')}</h1>
          <p className="text-gray-500 dark:text-gray-400 mt-2 text-lg">{t('groups.subtitle')}</p>
        </div>
        <button 
          onClick={handleOpenAdd}
          className="group flex items-center px-5 py-2.5 bg-gray-900 hover:bg-black dark:bg-white dark:hover:bg-gray-100 text-white dark:text-black rounded-xl font-semibold shadow-sm transition-all hover:-translate-y-0.5"
        >
          <Plus size={18} className="mr-2" />
          {t('groups.createGroup')}
        </button>
      </div>

      {/* Delete Confirmation Modal */}
      {deleteModal.isOpen && (
        <div className="fixed inset-0 z-[60] flex items-center justify-center bg-black/20 backdrop-blur-sm p-4 animate-fade-in">
            <div className="bg-white dark:bg-[#1a1a1a] rounded-2xl shadow-float max-w-sm w-full p-6 animate-in zoom-in-95 duration-200 border border-gray-100 dark:border-gray-800">
                <div className="flex items-center justify-center w-12 h-12 bg-red-100 dark:bg-red-900/30 rounded-full mx-auto mb-4 text-red-600 dark:text-red-400">
                    <Trash2 size={24} />
                </div>
                <h3 className="text-lg font-bold text-center text-gray-900 dark:text-white mb-2">{t('groups.deleteTitle')}</h3>
                <p className="text-center text-gray-500 dark:text-gray-400 text-sm mb-6 leading-relaxed">
                    {t('groups.deleteDesc', { name: deleteModal.name })}
                </p>
                <div className="flex space-x-3">
                    <button 
                        onClick={() => setDeleteModal({isOpen: false, id: null, name: ''})}
                        className="flex-1 px-4 py-2.5 border border-gray-200 dark:border-gray-700 text-gray-700 dark:text-gray-300 font-medium rounded-xl hover:bg-gray-50 dark:hover:bg-gray-800 transition-colors"
                    >
                        {t('common.cancel')}
                    </button>
                    <button 
                        onClick={confirmDelete}
                        className="flex-1 px-4 py-2.5 bg-red-600 hover:bg-red-700 text-white font-medium rounded-xl shadow-sm transition-all hover:scale-[1.02]"
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
                    <div className="col-span-full py-20 text-center bg-white dark:bg-[#1a1a1a] rounded-2xl border border-dashed border-gray-300 dark:border-gray-700 animate-fade-in">
                        <Layers size={48} className="mx-auto text-gray-300 mb-4" />
                        <p className="text-lg text-gray-500 dark:text-gray-400 font-medium">{t('groups.noGroups')}</p>
                        <p className="text-sm text-gray-400 mt-1">{t('groups.createFirst')}</p>
                    </div>
                ) : (
                    groups.map((group, index) => (
                    <SlideInItem key={group.id} index={index}>
                    <div className="group bg-white dark:bg-[#1a1a1a] rounded-2xl border border-gray-200 dark:border-gray-800 shadow-card hover:shadow-float transition-all duration-300 flex flex-col h-full hover:-translate-y-1 relative overflow-hidden">
                        
                        <div className="p-5 flex-1">
                            <div className="flex justify-between items-start mb-4">
                                <div className="p-2.5 bg-gray-100 dark:bg-gray-800 rounded-xl text-gray-600 dark:text-gray-400">
                                    <Layers size={20} />
                                </div>
                                <div className="flex space-x-1 opacity-0 group-hover:opacity-100 transition-opacity">
                                <button 
                                    onClick={() => handleOpenEdit(group)}
                                    className="p-1.5 text-gray-400 hover:text-gray-900 dark:hover:text-white hover:bg-gray-100 dark:hover:bg-gray-800 rounded-lg transition-colors"
                                    title="Edit"
                                >
                                    <Settings2 size={16} />
                                </button>
                                <button 
                                    onClick={(e) => handleDeleteClick(group.id, group.name, e)}
                                    className="p-1.5 text-gray-400 hover:text-red-600 dark:hover:text-red-400 hover:bg-red-50 dark:hover:bg-red-900/20 rounded-lg transition-colors"
                                    title="Delete"
                                >
                                    <Trash2 size={16} />
                                </button>
                                </div>
                            </div>
                            
                            <h3 className="text-lg font-bold text-gray-900 dark:text-white mb-3 truncate">{group.name}</h3>
                            
                            <div className="flex flex-wrap items-center gap-2 mb-6">
                                <span className={`flex items-center px-2 py-1 rounded-lg text-xs font-bold border ${getModeColor(group.mode)}`}>
                                    <span className="mr-1.5">{getModeIcon(group.mode)}</span>
                                    {getModeLabel(group.mode)}
                                </span>
                                <span className="flex items-center text-xs font-medium text-gray-500 dark:text-gray-400 px-2 py-1 bg-gray-50 dark:bg-gray-800 rounded-lg border border-gray-100 dark:border-gray-700">
                                    <Clock size={12} className="mr-1" />
                                    {group.firstTokenTimeout}ms
                                </span>
                            </div>

                            <div className="space-y-3">
                                <div className="flex items-center justify-between">
                                    <p className="text-[10px] font-bold text-gray-400 dark:text-gray-500 uppercase tracking-wider">{t('groups.activeProviders')}</p>
                                    <span className="text-[10px] font-mono text-gray-500 bg-gray-100 dark:bg-gray-800 px-1.5 py-0.5 rounded">{group.targets.length}</span>
                                </div>
                                
                                {group.targets.length === 0 ? (
                                <div className="text-center py-4 bg-gray-50 dark:bg-gray-800/50 rounded-xl border border-dashed border-gray-200 dark:border-gray-700">
                                    <p className="text-xs text-gray-400 italic">{t('groups.modal.noModelsSelected')}</p>
                                </div>
                                ) : (
                                <div className="max-h-40 overflow-y-auto space-y-1.5 pr-1 custom-scrollbar">
                                    {group.targets.map((target, idx) => {
                                        const provider = providers.find(c => c.id === target.providerId);
                                        const isProviderMissing = !provider;
                                        const isModelMissing = provider && !provider.models.includes(target.model);
                                        const isInvalid = isProviderMissing || isModelMissing;

                                        return (
                                        <div key={`${target.providerId}-${target.model}-${idx}`} className={`flex items-center justify-between text-xs p-2 rounded-lg border transition-all ${isInvalid ? 'bg-red-50 dark:bg-red-900/20 border-red-200 dark:border-red-800' : 'bg-gray-50/50 dark:bg-gray-800/30 border-transparent hover:border-gray-200 dark:hover:border-gray-700'}`}>
                                            <div className="flex flex-col min-w-0">
                                                <div className="flex items-center gap-1.5">
                                                    <div className={`w-1.5 h-1.5 rounded-full ${isInvalid ? 'bg-red-500' : 'bg-emerald-500'}`}></div>
                                                    <span className={`font-semibold truncate ${isInvalid ? 'text-red-700 dark:text-red-400' : 'text-gray-700 dark:text-gray-200'}`}>
                                                        {isProviderMissing ? `ID: ${target.providerId}` : provider?.name}
                                                    </span>
                                                </div>
                                                <div className="flex items-center gap-1.5 pl-3 mt-0.5">
                                                    <span className={`font-mono truncate max-w-[140px] ${isInvalid ? 'text-red-600 dark:text-red-300' : 'text-gray-500 dark:text-gray-400'}`}>
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
                        <div className="px-5 py-3 bg-gray-50 dark:bg-[#151515] border-t border-gray-100 dark:border-gray-800 flex justify-between items-center text-[10px] text-gray-400 font-mono">
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
                    className="mt-6 pt-4 border-t border-gray-200/50 dark:border-gray-800/50"
                />
            </div>
            </>
          )}
      </div>

      {/* Add/Edit Modal */}
      {isModalOpen && (
        <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/20 backdrop-blur-sm p-4 animate-fade-in">
            <div className="bg-white dark:bg-[#1a1a1a] rounded-2xl shadow-float max-w-5xl w-full max-h-[90vh] overflow-hidden flex flex-col animate-in zoom-in-95 duration-200 border border-gray-100 dark:border-gray-800">
                <div className="px-6 py-4 border-b border-gray-100 dark:border-gray-800 flex justify-between items-center bg-white/95 dark:bg-[#1a1a1a]/95 backdrop-blur z-10">
                    <h2 className="text-lg font-bold text-gray-900 dark:text-white">
                        {editingGroup ? t('groups.modal.titleEdit') : t('groups.modal.titleAdd')}
                    </h2>
                    <button onClick={() => setIsModalOpen(false)} className="text-gray-400 hover:text-gray-600 dark:hover:text-gray-300 transition-colors p-1 rounded-full hover:bg-gray-100 dark:hover:bg-gray-800">
                        <X size={20} />
                    </button>
                </div>
                
                <div className="overflow-y-auto p-6 space-y-6 flex-1 custom-scrollbar">
                    <form id="group-form" onSubmit={handleSubmit} className="space-y-6">
                        {/* Name and Basic Settings */}
                        <div className="grid grid-cols-1 md:grid-cols-3 gap-6">
                            <div className="md:col-span-1">
                                <label className="block text-xs font-semibold text-gray-500 dark:text-gray-400 uppercase tracking-wide mb-1.5">{t('groups.modal.name')}</label>
                                <input 
                                    type="text" 
                                    required
                                    value={formData.name}
                                    onChange={(e) => setFormData({...formData, name: e.target.value})}
                                    className="block w-full rounded-xl border-gray-200 dark:border-gray-700 shadow-sm focus:border-black dark:focus:border-white focus:ring-black dark:focus:ring-white text-sm py-2.5 px-3 bg-gray-50 dark:bg-gray-900 dark:text-white transition-all"
                                />
                            </div>
                            <div>
                                <label className="block text-xs font-semibold text-gray-500 dark:text-gray-400 uppercase tracking-wide mb-1.5">{t('groups.modal.mode')}</label>
                                <div className="relative">
                                    <select 
                                        value={formData.mode}
                                        disabled={true}
                                        onChange={(e) => setFormData({...formData, mode: e.target.value as LoadBalanceMode})}
                                        className="block w-full rounded-xl border-gray-200 dark:border-gray-700 shadow-sm focus:border-black dark:focus:border-white focus:ring-black dark:focus:ring-white text-sm py-2.5 px-3 bg-gray-100 dark:bg-gray-800 text-gray-500 dark:text-gray-400 cursor-not-allowed appearance-none"
                                    >
                                        {Object.values(LoadBalanceMode).map((mode) => (
                                            <option key={mode} value={mode}>{getModeLabel(mode)}</option>
                                        ))}
                                    </select>
                                    <div className="absolute inset-y-0 right-0 flex items-center pr-3 pointer-events-none">
                                        <ChevronDown size={14} className="text-gray-400" />
                                    </div>
                                </div>
                            </div>
                            <div>
                                <label className="block text-xs font-semibold text-gray-500 dark:text-gray-400 uppercase tracking-wide mb-1.5">{t('groups.modal.timeout')}</label>
                                <div className="relative">
                                    <input 
                                        type="number"
                                        required
                                        min="100"
                                        value={formData.firstTokenTimeout}
                                        onChange={(e) => setFormData({...formData, firstTokenTimeout: parseInt(e.target.value) || 0})}
                                        className="block w-full rounded-xl border-gray-200 dark:border-gray-700 shadow-sm focus:border-black dark:focus:border-white focus:ring-black dark:focus:ring-white text-sm py-2.5 px-3 bg-gray-50 dark:bg-gray-900 dark:text-white transition-all"
                                    />
                                    <div className="absolute inset-y-0 right-0 flex items-center pr-3 pointer-events-none text-xs text-gray-400 font-medium">
                                        ms
                                    </div>
                                </div>
                            </div>
                        </div>

                        {/* Dual Pane Selection */}
                        <div className="grid grid-cols-1 md:grid-cols-2 gap-4 h-[500px]">
                            {/* Left Column: Available Models */}
                            <div className="flex flex-col bg-gray-50 dark:bg-gray-900 rounded-xl border border-gray-200 dark:border-gray-800 overflow-hidden shadow-sm">
                                <div className="p-3 border-b border-gray-200 dark:border-gray-800 bg-white dark:bg-[#1a1a1a] z-10 space-y-3">
                                    <label className="text-[10px] font-bold text-gray-400 dark:text-gray-500 uppercase tracking-wider block">
                                        Available Models
                                    </label>
                                    <div className="relative">
                                        <Search className="absolute left-2.5 top-2.5 text-gray-400" size={14} />
                                        <input 
                                            type="text" 
                                            placeholder={t('groups.modal.searchModels')} 
                                            value={modelFilter}
                                            onChange={(e) => setModelFilter(e.target.value)}
                                            className="block w-full pl-8 pr-3 py-2 border border-gray-200 dark:border-gray-700 rounded-lg text-sm bg-gray-50 dark:bg-gray-900 dark:text-white placeholder-gray-400 focus:outline-none focus:ring-2 focus:ring-black dark:focus:ring-white focus:border-transparent transition-all"
                                        />
                                    </div>
                                </div>
                                
                                <div className="flex-1 overflow-y-auto p-3 custom-scrollbar space-y-2">
                                     {activeProviders.length === 0 ? (
                                        <div className="h-full flex flex-col items-center justify-center text-gray-400 text-sm">
                                            <AlertTriangle size={24} className="mb-2 opacity-50" />
                                            {t('groups.modal.noActiveProviders')}
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
                                                <div key={provider.id} className="bg-white dark:bg-[#1a1a1a] rounded-lg border border-gray-200 dark:border-gray-800 overflow-hidden transition-shadow">
                                                    <div 
                                                        onClick={() => toggleProviderExpand(provider.id)}
                                                        className="flex items-center justify-between p-3 cursor-pointer bg-gray-50/50 dark:bg-gray-800/30 hover:bg-gray-100 dark:hover:bg-gray-800 transition-colors"
                                                    >
                                                        <div className="flex items-center gap-2 overflow-hidden">
                                                            <ChevronRight size={14} className={`text-gray-400 transition-transform duration-200 ${isExpanded ? 'rotate-90' : ''}`} />
                                                            <span className="font-semibold text-sm text-gray-700 dark:text-gray-200 truncate">{provider.name}</span>
                                                        </div>
                                                        {selectedCount > 0 && (
                                                            <span className="text-[10px] font-bold px-1.5 py-0.5 bg-indigo-50 dark:bg-indigo-900/30 text-indigo-600 dark:text-indigo-400 rounded-full">
                                                                {selectedCount}
                                                            </span>
                                                        )}
                                                    </div>
                                                    
                                                    {isExpanded && (
                                                        <div className="border-t border-gray-100 dark:border-gray-800 divide-y divide-gray-50 dark:divide-gray-800/50">
                                                            {displayModels.map(model => {
                                                                const isSelected = formData.targets?.some(t => t.providerId === provider.id && t.model === model);
                                                                return (
                                                                    <div 
                                                                        key={model}
                                                                        onClick={() => toggleTargetSelection(provider.id, model)}
                                                                        className={`flex items-center p-2.5 text-sm cursor-pointer hover:bg-gray-50 dark:hover:bg-gray-800 transition-colors ${isSelected ? 'bg-indigo-50/50 dark:bg-indigo-900/10' : ''}`}
                                                                    >
                                                                        <div className={`w-4 h-4 rounded border flex items-center justify-center mr-3 transition-all flex-shrink-0 ${isSelected ? 'bg-indigo-600 border-indigo-600' : 'border-gray-300 dark:border-gray-600 bg-white dark:bg-gray-900'}`}>
                                                                            {isSelected && <Check size={10} className="text-white" />}
                                                                        </div>
                                                                        <span className={`truncate font-mono ${isSelected ? 'text-indigo-700 dark:text-indigo-300 font-medium' : 'text-gray-600 dark:text-gray-400'}`}>{model}</span>
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
                            <div className="flex flex-col bg-gray-50 dark:bg-gray-900 rounded-xl border border-gray-200 dark:border-gray-800 overflow-hidden shadow-sm">
                                <div className="p-3 border-b border-gray-200 dark:border-gray-800 bg-white dark:bg-[#1a1a1a] flex justify-between items-center h-[60px]">
                                    <label className="text-[10px] font-bold text-gray-400 dark:text-gray-500 uppercase tracking-wider">
                                        Selected ({formData.targets?.length || 0})
                                    </label>
                                    {formData.targets && formData.targets.length > 0 && (
                                        <button 
                                            type="button"
                                            onClick={clearAllSelected}
                                            className="text-xs font-bold text-red-500 hover:text-red-700 dark:text-red-400 dark:hover:text-red-300 px-2 py-1 rounded-md hover:bg-red-50 dark:hover:bg-red-900/20 transition-colors"
                                        >
                                            {t('common.clearAll')}
                                        </button>
                                    )}
                                </div>
                                <div className="flex-1 overflow-y-auto p-3 custom-scrollbar space-y-2">
                                    {(!formData.targets || formData.targets.length === 0) ? (
                                        <div className="h-full flex flex-col items-center justify-center text-gray-400">
                                            <Target size={32} className="mb-2 opacity-30" />
                                            <p className="text-xs">{t('groups.modal.noModelsSelected')}</p>
                                        </div>
                                    ) : (
                                        formData.targets.map((target, idx) => {
                                            const provider = providers.find(p => p.id === target.providerId);
                                            const isInvalid = !provider || (provider && !provider.models.includes(target.model));
                                            
                                            return (
                                                <div key={`${target.providerId}-${target.model}-${idx}`} className="flex items-center justify-between p-2.5 bg-white dark:bg-[#1a1a1a] border border-gray-200 dark:border-gray-800 rounded-lg group hover:border-indigo-300 dark:hover:border-indigo-700 transition-colors shadow-sm animate-in fade-in zoom-in-95 duration-200">
                                                    <div className="flex flex-col overflow-hidden mr-2">
                                                        <span className="text-xs font-bold text-gray-700 dark:text-gray-300 truncate">
                                                            {provider ? provider.name : <span className="text-red-500">Unknown Provider ({target.providerId})</span>}
                                                        </span>
                                                        <span className={`text-xs font-mono truncate ${isInvalid ? 'text-red-500' : 'text-gray-500 dark:text-gray-400'}`}>
                                                            {target.model}
                                                        </span>
                                                    </div>
                                                    <button 
                                                        type="button"
                                                        onClick={() => toggleTargetSelection(target.providerId, target.model)}
                                                        className="p-1.5 text-gray-400 hover:text-red-500 hover:bg-red-50 dark:hover:bg-red-900/20 rounded transition-colors"
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

                <div className="px-6 py-4 border-t border-gray-100 dark:border-gray-800 flex justify-end space-x-3 bg-white/95 dark:bg-[#1a1a1a]/95 backdrop-blur flex-shrink-0">
                    <button 
                        type="button"
                        onClick={() => setIsModalOpen(false)}
                        className="px-4 py-2.5 border border-gray-300 dark:border-gray-700 shadow-sm text-sm font-semibold rounded-xl text-gray-700 dark:text-gray-200 bg-white dark:bg-gray-800 hover:bg-gray-50 dark:hover:bg-gray-700 transition-colors"
                    >
                        {t('common.cancel')}
                    </button>
                    <button 
                        type="submit"
                        form="group-form"
                        disabled={isSaving}
                        className="flex items-center px-4 py-2.5 text-sm font-semibold rounded-xl shadow-sm text-white bg-gray-900 hover:bg-black dark:bg-white dark:text-black dark:hover:bg-gray-200 transition-all hover:-translate-y-0.5"
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