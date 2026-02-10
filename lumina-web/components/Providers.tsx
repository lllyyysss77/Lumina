import React, { useState, useEffect, useRef } from 'react';
import { Provider } from '../types';
import { Plus, Box } from 'lucide-react';
import { useLanguage } from './LanguageContext';
import { providerService } from '../services/providerService';
import { CardGridSkeleton } from './Skeletons';
import { SlideInItem } from './Animations';
import { Pagination } from './Pagination';
import { useToast } from './ToastContext';
import { DeleteProviderModal } from './DeleteProviderModal';
import { ProviderFormModal } from './ProviderFormModal';
import { ProviderCard } from './ProviderCard';

export const Providers: React.FC = () => {
  const [providers, setProviders] = useState<Provider[]>([]);
  const [isLoading, setIsLoading] = useState(false);
  const [isModalOpen, setIsModalOpen] = useState(false);
  const [editingProvider, setEditingProvider] = useState<Provider | null>(null);
  const [activeMenuId, setActiveMenuId] = useState<string | null>(null);

  // Pagination State - Default 6 items per page
  const [pagination, setPagination] = useState({
    current: 1,
    size: 6,
    total: 0,
    pages: 0
  });

  const { showToast } = useToast();
  const { t } = useLanguage();

  // Custom Modals State
  const [deleteModal, setDeleteModal] = useState<{ isOpen: boolean, id: string | null, name: string }>({ isOpen: false, id: null, name: '' });

  // Refs
  const menuRef = useRef<HTMLDivElement>(null);

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

  // Close menus when clicking outside
  useEffect(() => {
    const handleClickOutside = (event: MouseEvent) => {
      if (menuRef.current && !menuRef.current.contains(event.target as Node)) {
        setActiveMenuId(null);
      }
    };

    document.addEventListener('mousedown', handleClickOutside);
    return () => {
      document.removeEventListener('mousedown', handleClickOutside);
    };
  }, []);

  const handlePageChange = (page: number) => {
    fetchProviders(page);
  };

  const handleOpenAdd = () => {
    setEditingProvider(null);
    setIsModalOpen(true);
  };

  const handleOpenEdit = (provider: Provider) => {
    setEditingProvider(provider);
    setIsModalOpen(true);
    setActiveMenuId(null);
  };

  const handleDeleteClick = (provider: Provider) => {
    setDeleteModal({ isOpen: true, id: provider.id, name: provider.name });
    setActiveMenuId(null);
  };

  const confirmDelete = async () => {
    if (!deleteModal.id) return;
    try {
      await providerService.delete(deleteModal.id);
      showToast('Provider deleted successfully', 'success');
      setDeleteModal({ isOpen: false, id: null, name: '' });
      fetchProviders();
    } catch (error) {
      console.error(error);
      showToast('Failed to delete provider', 'error');
    }
  };

  const handleSubmit = async (data: Partial<Provider>) => {
    try {
      if (editingProvider) {
        await providerService.update(editingProvider.id, data);
        showToast('Provider updated successfully', 'success');
      } else {
        await providerService.create(data as Provider);
        showToast('Provider added successfully', 'success');
      }
      setIsModalOpen(false);
      fetchProviders();
    } catch (error: any) {
      console.error(error);
      showToast(error.message || (editingProvider ? 'Failed to update provider' : 'Failed to add provider'), 'error');
    }
  };

  return (
    <div className="space-y-6 relative flex flex-col h-full">

      <DeleteProviderModal
        isOpen={deleteModal.isOpen}
        name={deleteModal.name}
        onClose={() => setDeleteModal({ isOpen: false, id: null, name: '' })}
        onConfirm={confirmDelete}
      />

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
                    <ProviderCard
                      provider={provider}
                      isMenuOpen={activeMenuId === provider.id}
                      onToggleMenu={(id) => setActiveMenuId(activeMenuId === id ? null : id)}
                      onEdit={handleOpenEdit}
                      onDelete={handleDeleteClick}
                      menuRef={activeMenuId === provider.id ? menuRef : undefined}
                    />
                  </SlideInItem>
                ))
              )}
            </div>

            {providers.length > 0 && (
              <div className="mt-auto">
                <Pagination
                  current={pagination.current}
                  size={pagination.size}
                  total={pagination.total}
                  onChange={handlePageChange}
                  className="mt-6 pt-4 border-t border-slate-200/50 dark:border-slate-700/50"
                />
              </div>
            )}
          </>
        )}
      </div>

      <ProviderFormModal
        isOpen={isModalOpen}
        onClose={() => setIsModalOpen(false)}
        initialData={editingProvider}
        onSubmit={handleSubmit}
      />
    </div>
  );
};