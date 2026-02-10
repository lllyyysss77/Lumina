import React from 'react';
import { Monitor } from 'lucide-react';
import { useLanguage } from './LanguageContext';
import { useTheme } from './ThemeContext';

export const AppearanceSection: React.FC = () => {
    const { t } = useLanguage();
    const { theme, setTheme } = useTheme();

    return (
        <div className="bg-white/60 dark:bg-slate-900/60 backdrop-blur-md rounded-2xl border border-white/20 dark:border-slate-700/50 p-6 sm:p-8 shadow-sm hover:shadow-md transition-shadow">
            <div className="flex flex-col sm:flex-row sm:items-start justify-between gap-6">
                <div className="flex items-start space-x-5">
                    <div className="p-3 bg-purple-100 dark:bg-purple-900/40 text-purple-600 dark:text-purple-400 rounded-xl flex-shrink-0">
                        <Monitor size={24} />
                    </div>
                    <div>
                        <h3 className="text-lg font-bold text-slate-900 dark:text-white">{t('settings.appearance')}</h3>
                        <p className="text-sm text-slate-500 dark:text-slate-400 mt-1">
                            {t('settings.appearanceDesc')}
                        </p>
                        <div className="mt-5 flex space-x-4">
                            <button
                                onClick={() => setTheme('light')}
                                className={`cursor-pointer border-2 rounded-xl p-1 transition-all ${theme === 'light'
                                    ? 'border-indigo-600 ring-2 ring-indigo-600/20 scale-105'
                                    : 'border-transparent hover:border-slate-300 dark:hover:border-slate-600'
                                    }`}
                            >
                                <div className="w-24 h-16 bg-slate-50 border border-slate-200 rounded-lg flex items-center justify-center text-xs font-bold text-slate-700 shadow-inner">
                                    {t('settings.light')}
                                </div>
                            </button>
                            <button
                                onClick={() => setTheme('dark')}
                                className={`cursor-pointer border-2 rounded-xl p-1 transition-all ${theme === 'dark'
                                    ? 'border-indigo-600 ring-2 ring-indigo-600/20 scale-105'
                                    : 'border-transparent hover:border-slate-300 dark:hover:border-slate-600'
                                    }`}
                            >
                                <div className="w-24 h-16 bg-slate-900 border border-slate-700 rounded-lg flex items-center justify-center text-xs font-bold text-white shadow-inner">
                                    {t('settings.dark')}
                                </div>
                            </button>
                        </div>
                    </div>
                </div>
            </div>
        </div>
    );
};
