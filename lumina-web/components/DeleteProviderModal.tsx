import React from 'react';
import { Trash2 } from 'lucide-react';
import { useLanguage } from './LanguageContext';

interface DeleteProviderModalProps {
    isOpen: boolean;
    name: string;
    onClose: () => void;
    onConfirm: () => void;
}

export const DeleteProviderModal: React.FC<DeleteProviderModalProps> = ({ isOpen, name, onClose, onConfirm }) => {
    const { t } = useLanguage();

    if (!isOpen) return null;

    return (
        <div className="fixed inset-0 z-[60] flex items-center justify-center bg-slate-900/60 backdrop-blur-sm p-4 animate-fade-in">
            <div className="bg-white dark:bg-slate-900 rounded-2xl shadow-2xl max-w-sm w-full p-6 animate-in zoom-in-95 duration-200 border border-white/20 dark:border-slate-700">
                <div className="flex items-center justify-center w-14 h-14 bg-red-100 dark:bg-red-900/30 rounded-full mx-auto mb-4 text-red-600 dark:text-red-400 shadow-inner">
                    <Trash2 size={28} />
                </div>
                <h3 className="text-xl font-bold text-center text-slate-900 dark:text-white mb-2">{t('common.delete')} Provider?</h3>
                <p className="text-center text-slate-500 dark:text-slate-400 text-sm mb-8 leading-relaxed">
                    Are you sure you want to delete <span className="font-bold text-slate-700 dark:text-slate-200">{name}</span>? This action cannot be undone.
                </p>
                <div className="flex space-x-4">
                    <button
                        onClick={onClose}
                        className="flex-1 px-4 py-3 border border-slate-200 dark:border-slate-700 text-slate-700 dark:text-slate-300 font-medium rounded-xl hover:bg-slate-50 dark:hover:bg-slate-800 transition-colors"
                    >
                        {t('common.cancel')}
                    </button>
                    <button
                        onClick={onConfirm}
                        className="flex-1 px-4 py-3 bg-red-600 hover:bg-red-700 text-white font-medium rounded-xl shadow-lg shadow-red-500/20 transition-all hover:scale-[1.02]"
                    >
                        {t('common.delete')}
                    </button>
                </div>
            </div>
        </div>
    );
};
