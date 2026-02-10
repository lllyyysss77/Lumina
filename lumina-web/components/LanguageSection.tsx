import React from 'react';
import { Globe } from 'lucide-react';
import { useLanguage } from './LanguageContext';

export const LanguageSection: React.FC = () => {
    const { t, language, setLanguage } = useLanguage();

    return (
        <div className="bg-white/60 dark:bg-slate-900/60 backdrop-blur-md rounded-2xl border border-white/20 dark:border-slate-700/50 p-6 sm:p-8 shadow-sm hover:shadow-md transition-shadow">
            <div className="flex flex-col sm:flex-row sm:items-start justify-between gap-6">
                <div className="flex items-start space-x-5">
                    <div className="p-3 bg-indigo-100 dark:bg-indigo-900/40 text-indigo-600 dark:text-indigo-400 rounded-xl flex-shrink-0">
                        <Globe size={24} />
                    </div>
                    <div>
                        <h3 className="text-lg font-bold text-slate-900 dark:text-white">{t('settings.language')}</h3>
                        <p className="text-sm text-slate-500 dark:text-slate-400 mt-1">
                            {t('settings.languageDesc')}
                        </p>
                        <div className="mt-5 flex items-center space-x-3">
                            <button
                                onClick={() => setLanguage('zh')}
                                className={`px-4 py-2 text-sm font-semibold rounded-lg border transition-all ${language === 'zh'
                                    ? 'bg-indigo-600 border-indigo-600 text-white shadow-md shadow-indigo-500/20'
                                    : 'bg-white dark:bg-slate-800 border-slate-200 dark:border-slate-700 text-slate-700 dark:text-slate-200 hover:bg-slate-50 dark:hover:bg-slate-700'
                                    }`}
                            >
                                中文 (Chinese)
                            </button>
                            <button
                                onClick={() => setLanguage('en')}
                                className={`px-4 py-2 text-sm font-semibold rounded-lg border transition-all ${language === 'en'
                                    ? 'bg-indigo-600 border-indigo-600 text-white shadow-md shadow-indigo-500/20'
                                    : 'bg-white dark:bg-slate-800 border-slate-200 dark:border-slate-700 text-slate-700 dark:text-slate-200 hover:bg-slate-50 dark:hover:bg-slate-700'
                                    }`}
                            >
                                English
                            </button>
                        </div>
                    </div>
                </div>
            </div>
        </div>
    );
};
