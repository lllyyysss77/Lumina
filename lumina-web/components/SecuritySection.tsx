import React, { useState } from 'react';
import { Shield } from 'lucide-react';
import { useLanguage } from './LanguageContext';
import { TokenManageModal } from './TokenManageModal';

export const SecuritySection: React.FC = () => {
    const { t } = useLanguage();
    const [isTokenModalOpen, setIsTokenModalOpen] = useState(false);

    return (
        <>
            <div className="bg-white/60 dark:bg-slate-900/60 backdrop-blur-md rounded-2xl border border-white/20 dark:border-slate-700/50 p-6 sm:p-8 shadow-sm hover:shadow-md transition-shadow">
                <div className="flex flex-col sm:flex-row sm:items-start justify-between gap-6">
                    <div className="flex items-start space-x-5">
                        <div className="p-3 bg-red-100 dark:bg-red-900/40 text-red-600 dark:text-red-400 rounded-xl flex-shrink-0">
                            <Shield size={24} />
                        </div>
                        <div>
                            <h3 className="text-lg font-bold text-slate-900 dark:text-white">{t('settings.security')}</h3>
                            <p className="text-sm text-slate-500 dark:text-slate-400 mt-1 max-w-lg">
                                {t('settings.securityDesc')}
                            </p>
                            <div className="mt-5">
                                <button
                                    onClick={() => setIsTokenModalOpen(true)}
                                    className="px-5 py-2.5 border border-slate-200 dark:border-slate-600 shadow-sm text-sm font-semibold rounded-xl text-slate-700 dark:text-slate-200 bg-white/80 dark:bg-slate-800/80 hover:bg-white dark:hover:bg-slate-700 transition-colors"
                                >
                                    {t('settings.manageTokens')}
                                </button>
                            </div>
                        </div>
                    </div>
                </div>
            </div>

            <TokenManageModal
                isOpen={isTokenModalOpen}
                onClose={() => setIsTokenModalOpen(false)}
            />
        </>
    );
};
