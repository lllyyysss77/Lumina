import React, { useEffect, useState } from 'react';
import { Save, Monitor, Globe, Loader2, User as UserIcon, Zap, AlertTriangle } from 'lucide-react';
import { useLanguage } from './LanguageContext';
import { userService } from '../services/userService';
import { settingsService } from '../services/settingsService';
import { useAuth } from './AuthContext';
import { useTheme } from './ThemeContext';
import { SlideInItem } from './Animations';
import { CircuitBreakerSettingsPanel } from './CircuitBreakerSettingsPanel';
import { Button } from './ui/Button';
import { useToast } from './ui/ToastContext';

export const Settings: React.FC = () => {
  const { t, language, setLanguage } = useLanguage();
  const { theme, setTheme } = useTheme();
  const { user, logout } = useAuth();
  const { showToast } = useToast();

  // Account Settings State
  const [username, setUsername] = useState(user?.username || '');
  const [originalPassword, setOriginalPassword] = useState('');
  const [password, setPassword] = useState('');
  const [confirmPassword, setConfirmPassword] = useState('');
  const [isUpdatingProfile, setIsUpdatingProfile] = useState(false);

  // Self-use mode state
  const [selfUseMode, setSelfUseMode] = useState<boolean>(false);
  const [selfUseModeLoaded, setSelfUseModeLoaded] = useState<boolean>(false);
  const [isUpdatingSelfUseMode, setIsUpdatingSelfUseMode] = useState<boolean>(false);

  useEffect(() => {
    let cancelled = false;
    settingsService.getSelfUseMode().then((enabled) => {
      if (!cancelled) {
        setSelfUseMode(enabled);
        setSelfUseModeLoaded(true);
      }
    });
    return () => {
      cancelled = true;
    };
  }, []);

  const handleToggleSelfUseMode = async (nextEnabled: boolean) => {
    if (isUpdatingSelfUseMode) return;
    setIsUpdatingSelfUseMode(true);
    // Optimistic update for snappy UI; revert on failure
    setSelfUseMode(nextEnabled);
    try {
      const confirmed = await settingsService.setSelfUseMode(nextEnabled);
      setSelfUseMode(confirmed);
      showToast(
        nextEnabled ? t('settings.selfUseMode.enabled') : t('settings.selfUseMode.disabled'),
        nextEnabled ? 'success' : 'info'
      );
    } catch (error: any) {
      console.error('Failed to update self-use mode', error);
      setSelfUseMode(!nextEnabled);
      showToast(error?.message || t('common.fail'), 'error');
    } finally {
      setIsUpdatingSelfUseMode(false);
    }
  };

  const handleUpdateProfile = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!originalPassword || !password || !confirmPassword) {
        showToast(t('settings.passwordsRequired'), 'error');
        return;
    }
    if (password !== confirmPassword) {
        showToast(t('settings.passwordsDoNotMatch'), 'error');
        return;
    }

    setIsUpdatingProfile(true);
    try {
        await userService.updateProfile({
            username: username,
            originalPassword,
            password
        });
        showToast(t('settings.updateSuccess'), 'success');
        
        // Delay logout slightly to show success message
        setTimeout(async () => {
            await logout();
        }, 1500);

    } catch (error: any) {
        console.error("Failed to update profile", error);
        showToast(error.message || t('common.fail'), 'error');
        setIsUpdatingProfile(false);
    }
  };

  return (
    <div className="space-y-8 relative">
      <SlideInItem>
        <div>
            <h1 className="text-3xl font-extrabold text-gray-900 dark:text-white tracking-tight">{t('settings.title')}</h1>
            <p className="text-gray-500 dark:text-gray-400 mt-2 text-lg">{t('settings.subtitle')}</p>
        </div>
      </SlideInItem>

      <div className="space-y-6">
        {/* Section: Language */}
        <SlideInItem index={1}>
            <div className="bg-white dark:bg-[#1a1a1a] rounded-2xl border border-gray-200 dark:border-gray-800 p-6 sm:p-8 shadow-card hover:shadow-float transition-shadow duration-300">
                <div className="flex flex-col sm:flex-row sm:items-start justify-between gap-6">
                    <div className="flex items-start space-x-5">
                    <div className="p-3 bg-indigo-50 dark:bg-indigo-900/20 text-indigo-600 dark:text-indigo-400 rounded-xl flex-shrink-0">
                        <Globe size={24} />
                    </div>
                    <div>
                        <h3 className="text-lg font-bold text-gray-900 dark:text-white">{t('settings.language')}</h3>
                        <p className="text-sm text-gray-500 dark:text-gray-400 mt-1">
                        {t('settings.languageDesc')}
                        </p>
                        <div className="mt-5 flex items-center space-x-3">
                            <button 
                                onClick={() => setLanguage('zh')}
                                className={`px-4 py-2 text-sm font-semibold rounded-lg border transition-all ${
                                    language === 'zh' 
                                    ? 'bg-black text-white border-black dark:bg-white dark:text-black dark:border-white shadow-sm' 
                                    : 'bg-white dark:bg-gray-800 border-gray-200 dark:border-gray-700 text-gray-700 dark:text-gray-200 hover:bg-gray-50 dark:hover:bg-gray-700'
                                }`}
                            >
                                中文 (Chinese)
                            </button>
                            <button 
                                onClick={() => setLanguage('en')}
                                className={`px-4 py-2 text-sm font-semibold rounded-lg border transition-all ${
                                    language === 'en' 
                                    ? 'bg-black text-white border-black dark:bg-white dark:text-black dark:border-white shadow-sm' 
                                    : 'bg-white dark:bg-gray-800 border-gray-200 dark:border-gray-700 text-gray-700 dark:text-gray-200 hover:bg-gray-50 dark:hover:bg-gray-700'
                                }`}
                            >
                                English
                            </button>
                        </div>
                    </div>
                    </div>
                </div>
            </div>
        </SlideInItem>

        {/* Section: Account Settings */}
        <SlideInItem index={2}>
            <div className="bg-white dark:bg-[#1a1a1a] rounded-2xl border border-gray-200 dark:border-gray-800 p-6 sm:p-8 shadow-card hover:shadow-float transition-shadow duration-300">
            <div className="flex flex-col sm:flex-row sm:items-start justify-between gap-6">
                <div className="flex items-start space-x-5 w-full">
                <div className="p-3 bg-emerald-50 dark:bg-emerald-900/20 text-emerald-600 dark:text-emerald-400 rounded-xl flex-shrink-0">
                    <UserIcon size={24} />
                </div>
                <div className="flex-1 max-w-2xl">
                    <h3 className="text-lg font-bold text-gray-900 dark:text-white">{t('settings.account')}</h3>
                    <p className="text-sm text-gray-500 dark:text-gray-400 mt-1">
                    {t('settings.accountDesc')}
                    </p>
                    
                    <form onSubmit={handleUpdateProfile} className="mt-6 space-y-5">
                        <div>
                            <label className="block text-sm font-semibold text-gray-700 dark:text-gray-300 mb-1.5">{t('settings.username')}</label>
                            <input 
                                type="text" 
                                required
                                value={username}
                                onChange={(e) => setUsername(e.target.value)}
                                className="block w-full rounded-xl border-gray-200 dark:border-gray-700 shadow-sm focus:border-black dark:focus:border-white focus:ring-black dark:focus:ring-white text-sm py-2.5 px-3 bg-gray-50 dark:bg-gray-900 dark:text-white transition-all"
                            />
                        </div>
                        <div>
                            <label className="block text-sm font-semibold text-gray-700 dark:text-gray-300 mb-1.5">{t('settings.originalPassword')}</label>
                            <input
                                type="password"
                                required
                                value={originalPassword}
                                onChange={(e) => setOriginalPassword(e.target.value)}
                                className="block w-full rounded-xl border-gray-200 dark:border-gray-700 shadow-sm focus:border-black dark:focus:border-white focus:ring-black dark:focus:ring-white text-sm py-2.5 px-3 bg-gray-50 dark:bg-gray-900 dark:text-white transition-all"
                            />
                        </div>
                        <div className="grid grid-cols-1 md:grid-cols-2 gap-5">
                            <div>
                                <label className="block text-sm font-semibold text-gray-700 dark:text-gray-300 mb-1.5">{t('settings.password')}</label>
                                <input
                                    type="password"
                                    required
                                    value={password}
                                    onChange={(e) => setPassword(e.target.value)}
                                    className="block w-full rounded-xl border-gray-200 dark:border-gray-700 shadow-sm focus:border-black dark:focus:border-white focus:ring-black dark:focus:ring-white text-sm py-2.5 px-3 bg-gray-50 dark:bg-gray-900 dark:text-white transition-all"
                                />
                            </div>
                            <div>
                                <label className="block text-sm font-semibold text-gray-700 dark:text-gray-300 mb-1.5">{t('settings.confirmPassword')}</label>
                                <input
                                    type="password"
                                    required
                                    value={confirmPassword}
                                    onChange={(e) => setConfirmPassword(e.target.value)}
                                    className="block w-full rounded-xl border-gray-200 dark:border-gray-700 shadow-sm focus:border-black dark:focus:border-white focus:ring-black dark:focus:ring-white text-sm py-2.5 px-3 bg-gray-50 dark:bg-gray-900 dark:text-white transition-all"
                                />
                            </div>
                        </div>
                        <div className="pt-2">
                            <Button
                              type="submit"
                              disabled={isUpdatingProfile}
                              variant="primary"
                              leftIcon={isUpdatingProfile ? <Loader2 size={18} className="animate-spin" /> : <Save size={18} />}
                            >
                              {t('settings.update')}
                            </Button>
                        </div>
                    </form>
                </div>
                </div>
            </div>
            </div>
        </SlideInItem>

        {/* Section: Self-Use Mode (Danger Zone) */}
        <SlideInItem index={3}>
            <div className={`bg-white dark:bg-[#1a1a1a] rounded-2xl border p-6 sm:p-8 shadow-card hover:shadow-float transition-all duration-300 ${
                selfUseMode
                    ? 'border-amber-300 dark:border-amber-700/60 ring-1 ring-amber-200/60 dark:ring-amber-900/40'
                    : 'border-gray-200 dark:border-gray-800'
            }`}>
                <div className="flex flex-col sm:flex-row sm:items-start justify-between gap-6">
                    <div className="flex items-start space-x-5 flex-1">
                        <div className="p-3 bg-amber-50 dark:bg-amber-900/20 text-amber-600 dark:text-amber-400 rounded-xl flex-shrink-0">
                            <Zap size={24} />
                        </div>
                        <div className="flex-1">
                            <div className="flex items-center gap-2 flex-wrap">
                                <h3 className="text-lg font-bold text-gray-900 dark:text-white">{t('settings.selfUseMode.title')}</h3>
                                {selfUseMode && (
                                    <span className="inline-flex items-center gap-1 px-2 py-0.5 rounded-full text-[11px] font-semibold bg-amber-100 dark:bg-amber-900/40 text-amber-800 dark:text-amber-300">
                                        <span className="w-1.5 h-1.5 rounded-full bg-amber-500 animate-pulse" />
                                        {t('settings.selfUseMode.activeBadge')}
                                    </span>
                                )}
                            </div>
                            <p className="text-sm text-gray-500 dark:text-gray-400 mt-1 max-w-xl">
                                {t('settings.selfUseMode.desc')}
                            </p>
                            <div className="mt-3 flex items-start gap-2 p-3 rounded-xl bg-amber-50/70 dark:bg-amber-900/10 border border-amber-200/80 dark:border-amber-800/40">
                                <AlertTriangle size={16} className="text-amber-600 dark:text-amber-500 mt-0.5 flex-shrink-0" />
                                <p className="text-xs text-amber-800 dark:text-amber-300 leading-relaxed">
                                    {t('settings.selfUseMode.warning')}
                                </p>
                            </div>
                        </div>
                    </div>
                    <div className="flex-shrink-0 flex sm:items-center sm:justify-center">
                        <button
                            type="button"
                            role="switch"
                            aria-checked={selfUseMode}
                            disabled={!selfUseModeLoaded || isUpdatingSelfUseMode}
                            onClick={() => handleToggleSelfUseMode(!selfUseMode)}
                            className={`relative inline-flex h-7 w-12 flex-shrink-0 cursor-pointer rounded-full border-2 border-transparent transition-colors duration-200 ease-in-out focus:outline-none focus:ring-2 focus:ring-offset-2 focus:ring-amber-500 disabled:opacity-50 disabled:cursor-not-allowed ${
                                selfUseMode ? 'bg-amber-500' : 'bg-gray-200 dark:bg-gray-700'
                            }`}
                        >
                            <span
                                aria-hidden="true"
                                className={`pointer-events-none inline-block h-6 w-6 transform rounded-full bg-white shadow ring-0 transition duration-200 ease-in-out ${
                                    selfUseMode ? 'translate-x-5' : 'translate-x-0'
                                }`}
                            >
                                {isUpdatingSelfUseMode && (
                                    <Loader2 size={14} className="absolute inset-0 m-auto text-amber-600 animate-spin" />
                                )}
                            </span>
                        </button>
                    </div>
                </div>
            </div>
        </SlideInItem>

        {/* Section: Circuit Breaker */}
        <SlideInItem index={5}>
            <CircuitBreakerSettingsPanel />
        </SlideInItem>

        {/* Section: Theme */}
        <SlideInItem index={6}>
            <div className="bg-white dark:bg-[#1a1a1a] rounded-2xl border border-gray-200 dark:border-gray-800 p-6 sm:p-8 shadow-card hover:shadow-float transition-shadow duration-300">
            <div className="flex flex-col sm:flex-row sm:items-start justify-between gap-6">
                <div className="flex items-start space-x-5">
                <div className="p-3 bg-purple-50 dark:bg-purple-900/20 text-purple-600 dark:text-purple-400 rounded-xl flex-shrink-0">
                    <Monitor size={24} />
                </div>
                <div>
                    <h3 className="text-lg font-bold text-gray-900 dark:text-white">{t('settings.appearance')}</h3>
                    <p className="text-sm text-gray-500 dark:text-gray-400 mt-1">
                    {t('settings.appearanceDesc')}
                    </p>
                    <div className="mt-5 flex space-x-4">
                        <div 
                            onClick={() => setTheme('light')}
                            className={`cursor-pointer border-2 rounded-xl p-1 transition-all ${
                                theme === 'light' 
                                ? 'border-black ring-2 ring-black/10 scale-105' 
                                : 'border-transparent hover:border-gray-200 dark:hover:border-gray-700'
                            }`}
                        >
                            <div className="w-24 h-16 bg-gray-50 border border-gray-200 rounded-lg flex items-center justify-center text-xs font-bold text-gray-700 shadow-inner">
                                {t('settings.light')}
                            </div>
                        </div>
                        <div 
                            onClick={() => setTheme('dark')}
                            className={`cursor-pointer border-2 rounded-xl p-1 transition-all ${
                                theme === 'dark' 
                                ? 'border-white ring-2 ring-white/20 scale-105' 
                                : 'border-transparent hover:border-gray-200 dark:hover:border-gray-700'
                            }`}
                        >
                            <div className="w-24 h-16 bg-gray-900 border border-gray-700 rounded-lg flex items-center justify-center text-xs font-bold text-white shadow-inner">
                                {t('settings.dark')}
                            </div>
                        </div>
                    </div>
                </div>
                </div>
            </div>
            </div>
        </SlideInItem>
      </div>
    </div>
  );
};
