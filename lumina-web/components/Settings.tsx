import React, { useState, useEffect } from 'react';
import { Save, Shield, Monitor, Globe, Plus, Trash2, Copy, Check, X, AlertTriangle, Key, Loader2, User as UserIcon, CheckCircle2, AlertCircle, Activity, ZapOff } from 'lucide-react';
import { useLanguage } from './LanguageContext';
import { tokenService } from '../services/tokenService';
import { userService } from '../services/userService';
import { circuitBreakerService } from '../services/circuitBreakerService';
import { AccessToken, CircuitBreakerStatus, CircuitState } from '../types';
import { useAuth } from './AuthContext';
import { useTheme } from './ThemeContext';
import { SlideInItem } from './Animations';

export const Settings: React.FC = () => {
  const { t, language, setLanguage } = useLanguage();
  const { theme, setTheme } = useTheme();
  const { user, logout } = useAuth();

  // Toast State
  const [toast, setToast] = useState<{show: boolean, message: string, type: 'success' | 'error' | 'info'}>({ show: false, message: '', type: 'success' });
  
  const showToast = (message: string, type: 'success' | 'error' | 'info' = 'success') => {
    setToast({ show: true, message, type });
    setTimeout(() => setToast(prev => ({ ...prev, show: false })), 3000);
  };

  // Account Settings State
  const [username, setUsername] = useState(user?.username || '');
  const [originalPassword, setOriginalPassword] = useState('');
  const [password, setPassword] = useState('');
  const [confirmPassword, setConfirmPassword] = useState('');
  const [isUpdatingProfile, setIsUpdatingProfile] = useState(false);

  // Token Management State
  const [isTokenModalOpen, setIsTokenModalOpen] = useState(false);
  const [tokens, setTokens] = useState<AccessToken[]>([]);
  const [isTokensLoading, setIsTokensLoading] = useState(false);
  const [isCreatingToken, setIsCreatingToken] = useState(false);
  const [newTokenName, setNewTokenName] = useState('');
  const [createdToken, setCreatedToken] = useState<AccessToken | null>(null); // To store the full token for display
  
  // Circuit Breaker State
  const [circuitBreakers, setCircuitBreakers] = useState<CircuitBreakerStatus[]>([]);
  const [isCBListLoading, setIsCBListLoading] = useState(false);
  const [isCBModalOpen, setIsCBModalOpen] = useState(false);
  const [selectedCB, setSelectedCB] = useState<CircuitBreakerStatus | null>(null);
  const [cbTargetState, setCbTargetState] = useState<CircuitState>('CLOSED');
  const [cbDuration, setCbDuration] = useState<number>(60000); // 60s default
  const [cbReason, setCbReason] = useState('');
  const [isCBSaving, setIsCBSaving] = useState(false);

  // Copy feedback state
  const [copyFeedbackId, setCopyFeedbackId] = useState<string | null>(null);
  
  // Confirmation for revocation
  const [revokeId, setRevokeId] = useState<string | null>(null);

  // Fetch functions
  const fetchTokens = async () => {
    setIsTokensLoading(true);
    try {
      const data = await tokenService.getList();
      setTokens(data);
    } catch (error) {
      console.error("Failed to fetch tokens", error);
    } finally {
      setIsTokensLoading(false);
    }
  };

  const fetchCircuitBreakers = async () => {
    setIsCBListLoading(true);
    try {
      const data = await circuitBreakerService.getList();
      setCircuitBreakers(data);
    } catch (error) {
      console.error("Failed to fetch circuit breakers", error);
      showToast('Failed to fetch circuit breaker status', 'error');
    } finally {
      setIsCBListLoading(false);
    }
  };

  // Load CB list on mount (optional, or load when expanding section)
  useEffect(() => {
    fetchCircuitBreakers();
  }, []);

  const handleManageTokens = () => {
    setIsTokenModalOpen(true);
    fetchTokens();
  };

  const handleCreateToken = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!newTokenName.trim()) return;

    setIsCreatingToken(true);
    try {
      const newToken = await tokenService.create(newTokenName);
      setCreatedToken(newToken); // Store full token result
      fetchTokens(); // Refresh list
    } catch (error) {
      console.error("Failed to create token", error);
      showToast('Failed to create token', 'error');
    } finally {
      setIsCreatingToken(false);
      setNewTokenName('');
    }
  };

  const handleRevokeToken = async (id: string) => {
    try {
      await tokenService.delete(id);
      setRevokeId(null);
      fetchTokens();
      showToast('Token revoked successfully', 'success');
    } catch (error) {
      console.error("Failed to revoke token", error);
      showToast('Failed to revoke token', 'error');
    }
  };

  const handleUpdateProfile = async (e: React.FormEvent) => {
    e.preventDefault();
    if (password && password !== confirmPassword) {
        showToast(t('settings.passwordsDoNotMatch'), 'error');
        return;
    }

    setIsUpdatingProfile(true);
    try {
        await userService.updateProfile({
            username: username,
            originalPassword: originalPassword || undefined,
            password: password || undefined
        });
        showToast(t('settings.updateSuccess'), 'success');
        
        // Delay logout slightly to show success message
        setTimeout(async () => {
            await logout();
        }, 1500);

    } catch (error: any) {
        console.error("Failed to update profile", error);
        showToast(error.message || 'Failed to update profile', 'error');
        setIsUpdatingProfile(false);
    }
  };

  // Circuit Breaker Handlers
  const openControlModal = (cb: CircuitBreakerStatus) => {
    setSelectedCB(cb);
    setCbTargetState(cb.circuitState === 'CLOSED' ? 'OPEN' : 'CLOSED'); // Default toggle
    setCbReason('');
    setCbDuration(60000);
    setIsCBModalOpen(true);
  };

  const handleControlSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!selectedCB) return;

    setIsCBSaving(true);
    try {
        await circuitBreakerService.control({
            providerId: selectedCB.providerId,
            targetState: cbTargetState,
            reason: cbReason,
            durationMs: cbTargetState === 'OPEN' ? cbDuration : undefined
        }, user?.username || 'admin');
        
        showToast('Circuit breaker updated', 'success');
        setIsCBModalOpen(false);
        fetchCircuitBreakers();
    } catch (error: any) {
        console.error("Failed to control circuit breaker", error);
        showToast('Failed to update circuit breaker', 'error');
    } finally {
        setIsCBSaving(false);
    }
  };

  const handleReleaseControl = async (providerId: string) => {
      try {
          await circuitBreakerService.release(providerId);
          showToast('Manual control released', 'success');
          fetchCircuitBreakers();
      } catch (error) {
          console.error("Failed to release control", error);
          showToast('Failed to release control', 'error');
      }
  }

  const copyToClipboard = (text: string, id: string) => {
    navigator.clipboard.writeText(text);
    setCopyFeedbackId(id);
    setTimeout(() => setCopyFeedbackId(null), 2000);
  };

  const closeTokenModal = () => {
    setIsTokenModalOpen(false);
    setCreatedToken(null); // Reset created token view
    setNewTokenName('');
  };

  const getCBStateColor = (state: string) => {
      switch(state) {
          case 'CLOSED': return 'bg-emerald-100 text-emerald-700 dark:bg-emerald-900/30 dark:text-emerald-400';
          case 'OPEN': return 'bg-red-100 text-red-700 dark:bg-red-900/30 dark:text-red-400';
          case 'HALF_OPEN': return 'bg-amber-100 text-amber-700 dark:bg-amber-900/30 dark:text-amber-400';
          default: return 'bg-slate-100 text-slate-700 dark:bg-slate-800 dark:text-slate-400';
      }
  };

  return (
    <div className="max-w-6xl space-y-8 relative">
       {/* Toast Notification */}
       {toast.show && (
          <div className={`fixed top-4 right-4 z-[100] px-4 py-3 rounded-xl shadow-lg border flex items-center animate-in slide-in-from-right duration-300 backdrop-blur-md ${
              toast.type === 'success' ? 'bg-white/90 border-green-200 text-green-700 dark:bg-slate-800/90 dark:border-green-900 dark:text-green-400' : 
              toast.type === 'error' ? 'bg-white/90 border-red-200 text-red-700 dark:bg-slate-800/90 dark:border-red-900 dark:text-red-400' :
              'bg-white/90 border-blue-200 text-blue-700 dark:bg-slate-800/90 dark:border-blue-900 dark:text-blue-400'
          }`}>
              {toast.type === 'success' ? <CheckCircle2 size={18} className="mr-2" /> : 
               toast.type === 'error' ? <AlertCircle size={18} className="mr-2" /> :
               <Activity size={18} className="mr-2" />}
              <span className="text-sm font-medium">{toast.message}</span>
          </div>
      )}

      <SlideInItem>
        <div>
            <h1 className="text-3xl font-extrabold text-slate-900 dark:text-white tracking-tight">{t('settings.title')}</h1>
            <p className="text-slate-500 dark:text-slate-400 mt-2 text-lg">{t('settings.subtitle')}</p>
        </div>
      </SlideInItem>

      <div className="space-y-6">
        {/* Section: Language */}
        <SlideInItem index={1}>
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
                                className={`px-4 py-2 text-sm font-semibold rounded-lg border transition-all ${
                                    language === 'zh' 
                                    ? 'bg-indigo-600 border-indigo-600 text-white shadow-md shadow-indigo-500/20' 
                                    : 'bg-white dark:bg-slate-800 border-slate-200 dark:border-slate-700 text-slate-700 dark:text-slate-200 hover:bg-slate-50 dark:hover:bg-slate-700'
                                }`}
                            >
                                中文 (Chinese)
                            </button>
                            <button 
                                onClick={() => setLanguage('en')}
                                className={`px-4 py-2 text-sm font-semibold rounded-lg border transition-all ${
                                    language === 'en' 
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
        </SlideInItem>

        {/* Section: Account Settings */}
        <SlideInItem index={2}>
            <div className="bg-white/60 dark:bg-slate-900/60 backdrop-blur-md rounded-2xl border border-white/20 dark:border-slate-700/50 p-6 sm:p-8 shadow-sm hover:shadow-md transition-shadow">
            <div className="flex flex-col sm:flex-row sm:items-start justify-between gap-6">
                <div className="flex items-start space-x-5 w-full">
                <div className="p-3 bg-emerald-100 dark:bg-emerald-900/40 text-emerald-600 dark:text-emerald-400 rounded-xl flex-shrink-0">
                    <UserIcon size={24} />
                </div>
                <div className="flex-1 max-w-2xl">
                    <h3 className="text-lg font-bold text-slate-900 dark:text-white">{t('settings.account')}</h3>
                    <p className="text-sm text-slate-500 dark:text-slate-400 mt-1">
                    {t('settings.accountDesc')}
                    </p>
                    
                    <form onSubmit={handleUpdateProfile} className="mt-6 space-y-5">
                        <div>
                            <label className="block text-sm font-semibold text-slate-700 dark:text-slate-300 mb-1.5">{t('settings.username')}</label>
                            <input 
                                type="text" 
                                required
                                value={username}
                                onChange={(e) => setUsername(e.target.value)}
                                className="block w-full rounded-xl border-slate-300 dark:border-slate-600 shadow-sm focus:border-indigo-500 focus:ring-indigo-500 text-sm py-2.5 px-3 bg-white/50 dark:bg-slate-900/50 dark:text-white transition-all focus:bg-white dark:focus:bg-slate-900"
                            />
                        </div>
                        <div>
                            <label className="block text-sm font-semibold text-slate-700 dark:text-slate-300 mb-1.5">{t('settings.originalPassword')}</label>
                            <input 
                                type="password" 
                                value={originalPassword}
                                onChange={(e) => setOriginalPassword(e.target.value)}
                                className="block w-full rounded-xl border-slate-300 dark:border-slate-600 shadow-sm focus:border-indigo-500 focus:ring-indigo-500 text-sm py-2.5 px-3 bg-white/50 dark:bg-slate-900/50 dark:text-white transition-all focus:bg-white dark:focus:bg-slate-900"
                            />
                        </div>
                        <div className="grid grid-cols-1 md:grid-cols-2 gap-5">
                            <div>
                                <label className="block text-sm font-semibold text-slate-700 dark:text-slate-300 mb-1.5">{t('settings.password')}</label>
                                <input 
                                    type="password" 
                                    value={password}
                                    onChange={(e) => setPassword(e.target.value)}
                                    placeholder={t('settings.leaveBlank')}
                                    className="block w-full rounded-xl border-slate-300 dark:border-slate-600 shadow-sm focus:border-indigo-500 focus:ring-indigo-500 text-sm py-2.5 px-3 bg-white/50 dark:bg-slate-900/50 dark:text-white dark:placeholder-slate-500 transition-all focus:bg-white dark:focus:bg-slate-900"
                                />
                            </div>
                            <div>
                                <label className="block text-sm font-semibold text-slate-700 dark:text-slate-300 mb-1.5">{t('settings.confirmPassword')}</label>
                                <input 
                                    type="password" 
                                    value={confirmPassword}
                                    onChange={(e) => setConfirmPassword(e.target.value)}
                                    placeholder={t('settings.leaveBlank')}
                                    className="block w-full rounded-xl border-slate-300 dark:border-slate-600 shadow-sm focus:border-indigo-500 focus:ring-indigo-500 text-sm py-2.5 px-3 bg-white/50 dark:bg-slate-900/50 dark:text-white dark:placeholder-slate-500 transition-all focus:bg-white dark:focus:bg-slate-900"
                                />
                            </div>
                        </div>
                        <div className="pt-2">
                            <button 
                                type="submit" 
                                disabled={isUpdatingProfile}
                                className="flex items-center px-5 py-2.5 bg-indigo-600 hover:bg-indigo-700 text-white text-sm font-semibold rounded-xl shadow-lg shadow-indigo-500/20 transition-all hover:-translate-y-0.5 disabled:opacity-70 disabled:cursor-not-allowed"
                            >
                                {isUpdatingProfile ? <Loader2 size={18} className="mr-2 animate-spin" /> : <Save size={18} className="mr-2" />}
                                {t('settings.update')}
                            </button>
                        </div>
                    </form>
                </div>
                </div>
            </div>
            </div>
        </SlideInItem>

        {/* Section: Security */}
        <SlideInItem index={3}>
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
                        onClick={handleManageTokens}
                        className="px-5 py-2.5 border border-slate-200 dark:border-slate-600 shadow-sm text-sm font-semibold rounded-xl text-slate-700 dark:text-slate-200 bg-white/80 dark:bg-slate-800/80 hover:bg-white dark:hover:bg-slate-700 transition-colors"
                        >
                            {t('settings.manageTokens')}
                        </button>
                    </div>
                </div>
                </div>
            </div>
            </div>
        </SlideInItem>

        {/* Section: Circuit Breaker */}
        <SlideInItem index={4}>
            <div className="bg-white/60 dark:bg-slate-900/60 backdrop-blur-md rounded-2xl border border-white/20 dark:border-slate-700/50 p-6 sm:p-8 shadow-sm hover:shadow-md transition-shadow">
            <div className="flex flex-col sm:flex-row sm:items-start justify-between gap-6">
                <div className="flex items-start space-x-5 w-full">
                <div className="p-3 bg-orange-100 dark:bg-orange-900/40 text-orange-600 dark:text-orange-400 rounded-xl flex-shrink-0">
                    <ZapOff size={24} />
                </div>
                <div className="flex-1 min-w-0">
                    <div className="flex justify-between items-center mb-5">
                        <div>
                            <h3 className="text-lg font-bold text-slate-900 dark:text-white">{t('settings.circuitBreaker.title')}</h3>
                            <p className="text-sm text-slate-500 dark:text-slate-400 mt-1">
                            {t('settings.circuitBreaker.desc')}
                            </p>
                        </div>
                        <button 
                            onClick={fetchCircuitBreakers}
                            className="p-2 text-slate-500 hover:text-indigo-600 dark:text-slate-400 dark:hover:text-indigo-400 rounded-lg hover:bg-slate-50 dark:hover:bg-slate-800 transition-colors"
                            title={t('settings.circuitBreaker.refresh')}
                        >
                            <Activity size={20} className={isCBListLoading ? 'animate-spin' : ''} />
                        </button>
                    </div>
                    
                    <div className="overflow-x-auto border border-slate-200/50 dark:border-slate-700/50 rounded-xl shadow-sm">
                        <table className="min-w-full divide-y divide-slate-100 dark:divide-slate-800">
                            <thead className="bg-slate-50/50 dark:bg-slate-900/50">
                                <tr>
                                    <th className="px-5 py-3 text-left text-xs font-bold text-slate-500 dark:text-slate-400 uppercase tracking-wider">{t('settings.circuitBreaker.table.provider')}</th>
                                    <th className="px-5 py-3 text-left text-xs font-bold text-slate-500 dark:text-slate-400 uppercase tracking-wider">{t('settings.circuitBreaker.table.state')}</th>
                                    <th className="px-5 py-3 text-left text-xs font-bold text-slate-500 dark:text-slate-400 uppercase tracking-wider">{t('settings.circuitBreaker.table.score')}</th>
                                    <th className="px-5 py-3 text-left text-xs font-bold text-slate-500 dark:text-slate-400 uppercase tracking-wider hidden sm:table-cell">{t('settings.circuitBreaker.table.stats')}</th>
                                    <th className="px-5 py-3 text-right text-xs font-bold text-slate-500 dark:text-slate-400 uppercase tracking-wider">{t('settings.circuitBreaker.table.control')}</th>
                                </tr>
                            </thead>
                            <tbody className="bg-white/50 dark:bg-slate-800/50 divide-y divide-slate-100 dark:divide-slate-800">
                                {isCBListLoading && circuitBreakers.length === 0 ? (
                                    <tr><td colSpan={5} className="p-6 text-center text-sm text-slate-500">Loading...</td></tr>
                                ) : circuitBreakers.length === 0 ? (
                                    <tr><td colSpan={5} className="p-6 text-center text-sm text-slate-500">No active providers found</td></tr>
                                ) : (
                                    circuitBreakers.map((cb) => (
                                        <tr key={cb.providerId} className="hover:bg-indigo-50/20 dark:hover:bg-indigo-900/10 transition-colors">
                                            <td className="px-5 py-3.5 whitespace-nowrap text-sm font-semibold text-slate-900 dark:text-white">
                                                {cb.providerName}
                                                {cb.manuallyControlled && (
                                                    <span className="ml-2 inline-flex items-center px-1.5 py-0.5 rounded text-[10px] font-bold bg-purple-100 text-purple-700 dark:bg-purple-900/30 dark:text-purple-400 border border-purple-200 dark:border-purple-800" title={cb.manualControlReason || 'Manual Control'}>
                                                        MANUAL
                                                    </span>
                                                )}
                                            </td>
                                            <td className="px-5 py-3.5 whitespace-nowrap">
                                                <span className={`px-2.5 py-0.5 inline-flex text-xs leading-5 font-bold rounded-full border ${getCBStateColor(cb.circuitState)}`}>
                                                    {t(`settings.circuitBreaker.states.${cb.circuitState}`)}
                                                </span>
                                            </td>
                                            <td className="px-5 py-3.5 whitespace-nowrap text-sm text-slate-500 dark:text-slate-400 font-mono">
                                                {cb.score.toFixed(1)}
                                            </td>
                                            <td className="px-5 py-3.5 whitespace-nowrap text-xs text-slate-500 dark:text-slate-400 hidden sm:table-cell">
                                                <span className="text-red-500 font-medium">{Math.round(cb.errorRate * 100)}% Err</span> <span className="text-slate-300 dark:text-slate-600 mx-1">/</span> <span className="text-amber-500 font-medium">{Math.round(cb.slowRate * 100)}% Slow</span>
                                            </td>
                                            <td className="px-5 py-3.5 whitespace-nowrap text-right text-sm font-medium">
                                                {cb.manuallyControlled ? (
                                                    <button 
                                                        onClick={() => handleReleaseControl(cb.providerId)}
                                                        className="px-3 py-1 bg-orange-50 text-orange-600 border border-orange-200 rounded-lg hover:bg-orange-100 text-xs font-semibold transition-colors dark:bg-orange-900/20 dark:text-orange-400 dark:border-orange-800 dark:hover:bg-orange-900/30"
                                                    >
                                                        {t('settings.circuitBreaker.actions.release')}
                                                    </button>
                                                ) : (
                                                    <button 
                                                        onClick={() => openControlModal(cb)}
                                                        className="px-3 py-1 bg-white text-indigo-600 border border-indigo-200 rounded-lg hover:bg-indigo-50 text-xs font-semibold transition-colors dark:bg-slate-800 dark:text-indigo-400 dark:border-indigo-900 dark:hover:bg-indigo-900/20"
                                                    >
                                                        {t('settings.circuitBreaker.actions.manage')}
                                                    </button>
                                                )}
                                            </td>
                                        </tr>
                                    ))
                                )}
                            </tbody>
                        </table>
                    </div>
                </div>
                </div>
            </div>
            </div>
        </SlideInItem>

        {/* Section: Theme */}
        <SlideInItem index={5}>
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
                        <div 
                            onClick={() => setTheme('light')}
                            className={`cursor-pointer border-2 rounded-xl p-1 transition-all ${
                                theme === 'light' 
                                ? 'border-indigo-600 ring-2 ring-indigo-600/20 scale-105' 
                                : 'border-transparent hover:border-slate-300 dark:hover:border-slate-600'
                            }`}
                        >
                            <div className="w-24 h-16 bg-slate-50 border border-slate-200 rounded-lg flex items-center justify-center text-xs font-bold text-slate-700 shadow-inner">
                                {t('settings.light')}
                            </div>
                        </div>
                        <div 
                            onClick={() => setTheme('dark')}
                            className={`cursor-pointer border-2 rounded-xl p-1 transition-all ${
                                theme === 'dark' 
                                ? 'border-indigo-600 ring-2 ring-indigo-600/20 scale-105' 
                                : 'border-transparent hover:border-slate-300 dark:hover:border-slate-600'
                            }`}
                        >
                            <div className="w-24 h-16 bg-slate-900 border border-slate-700 rounded-lg flex items-center justify-center text-xs font-bold text-white shadow-inner">
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

      {/* --- Token Management Modal --- */}
      {isTokenModalOpen && (
        <div className="fixed inset-0 z-50 flex items-center justify-center bg-slate-900/60 backdrop-blur-sm p-4 animate-fade-in">
            <div className="bg-white dark:bg-slate-900 rounded-2xl shadow-2xl max-w-2xl w-full max-h-[85vh] flex flex-col animate-in zoom-in-95 duration-200 border border-white/20 dark:border-slate-700">
                <div className="px-6 py-5 border-b border-slate-100 dark:border-slate-800 flex justify-between items-center bg-white/95 dark:bg-slate-900/95 backdrop-blur z-10">
                    <div className="flex items-center gap-3">
                        <div className="p-2 bg-indigo-100 dark:bg-indigo-900/40 text-indigo-600 dark:text-indigo-400 rounded-lg">
                            <Key size={20} />
                        </div>
                        <h2 className="text-xl font-bold text-slate-900 dark:text-white">{t('settings.tokens.title')}</h2>
                    </div>
                    <button onClick={closeTokenModal} className="text-slate-400 hover:text-slate-600 dark:hover:text-slate-300 rounded-full p-1 hover:bg-slate-100 dark:hover:bg-slate-800 transition-colors">
                        <X size={24} />
                    </button>
                </div>

                <div className="p-6 flex-1 overflow-y-auto custom-scrollbar">
                    {/* Create Token Section (Or Success View) */}
                    {createdToken ? (
                        <div className="mb-8 p-6 bg-emerald-50 dark:bg-emerald-900/20 border border-emerald-200 dark:border-emerald-800 rounded-2xl animate-in fade-in zoom-in-95">
                            <div className="flex items-start gap-4">
                                <CheckCircleIcon className="text-emerald-600 dark:text-emerald-500 mt-1 flex-shrink-0 w-6 h-6" />
                                <div className="flex-1 min-w-0">
                                    <h3 className="text-base font-bold text-emerald-800 dark:text-emerald-400 mb-1">Token Generated Successfully</h3>
                                    <p className="text-sm text-emerald-700 dark:text-emerald-500 mb-4">{t('settings.tokens.copyWarning')}</p>
                                    
                                    <div className="flex items-center gap-2">
                                        <code className="flex-1 bg-white dark:bg-slate-950 border border-emerald-200 dark:border-emerald-800/50 px-4 py-3 rounded-xl font-mono text-sm text-slate-800 dark:text-slate-200 break-all shadow-sm">
                                            {createdToken.token}
                                        </code>
                                        <button 
                                            onClick={() => copyToClipboard(createdToken.token || '', 'new')}
                                            className="p-3 bg-white dark:bg-slate-950 border border-emerald-200 dark:border-emerald-800/50 text-emerald-600 dark:text-emerald-500 rounded-xl hover:bg-emerald-50 dark:hover:bg-emerald-900/30 transition-colors shadow-sm"
                                        >
                                            {copyFeedbackId === 'new' ? <Check size={20} /> : <Copy size={20} />}
                                        </button>
                                    </div>
                                </div>
                            </div>
                            <div className="pl-10 mt-3">
                                <button 
                                    onClick={() => setCreatedToken(null)}
                                    className="text-sm font-semibold text-emerald-700 dark:text-emerald-400 hover:text-emerald-900 dark:hover:text-emerald-300 underline underline-offset-2"
                                >
                                    Generate another token
                                </button>
                            </div>
                        </div>
                    ) : (
                        <form onSubmit={handleCreateToken} className="mb-8 p-6 bg-slate-50 dark:bg-slate-800/50 rounded-2xl border border-slate-100 dark:border-slate-700/50">
                            <h3 className="text-sm font-bold text-slate-800 dark:text-slate-200 mb-4 uppercase tracking-wide">{t('settings.tokens.create')}</h3>
                            <div className="flex flex-col sm:flex-row gap-3">
                                <input 
                                    type="text" 
                                    value={newTokenName}
                                    onChange={(e) => setNewTokenName(e.target.value)}
                                    placeholder={t('settings.tokens.name') + ' (e.g. "Production App")'}
                                    className="flex-1 rounded-xl border-slate-300 dark:border-slate-600 text-sm focus:ring-indigo-500 focus:border-indigo-500 py-2.5 px-4 border bg-white dark:bg-slate-900 dark:text-white transition-shadow"
                                    required
                                />
                                <button 
                                    type="submit" 
                                    disabled={isCreatingToken || !newTokenName.trim()}
                                    className="px-5 py-2.5 bg-indigo-600 text-white text-sm font-semibold rounded-xl hover:bg-indigo-700 disabled:opacity-70 disabled:cursor-not-allowed flex items-center justify-center shadow-md shadow-indigo-500/20 transition-all hover:-translate-y-0.5"
                                >
                                    {isCreatingToken ? <Loader2 size={18} className="animate-spin" /> : <Plus size={18} className="mr-2" />}
                                    {t('common.add')}
                                </button>
                            </div>
                        </form>
                    )}

                    {/* Token List */}
                    <div>
                        <h3 className="text-sm font-bold text-slate-900 dark:text-white mb-4 uppercase tracking-wide">Active Tokens</h3>
                        {isTokensLoading ? (
                            <div className="flex justify-center py-12">
                                <Loader2 className="w-8 h-8 text-indigo-600 animate-spin" />
                            </div>
                        ) : tokens.length === 0 ? (
                            <div className="text-center py-12 text-slate-500 dark:text-slate-400 bg-white dark:bg-slate-800/50 border border-dashed border-slate-200 dark:border-slate-700 rounded-2xl">
                                {t('settings.tokens.empty')}
                            </div>
                        ) : (
                            <div className="space-y-3">
                                {tokens.map(token => (
                                    <div key={token.id} className="flex flex-col sm:flex-row sm:items-center justify-between p-4 bg-white dark:bg-slate-800/80 border border-slate-200 dark:border-slate-700/80 rounded-xl hover:border-indigo-300 dark:hover:border-indigo-700 hover:shadow-md transition-all">
                                        <div className="mb-3 sm:mb-0">
                                            <div className="flex items-center gap-3 flex-wrap">
                                                <span className="font-bold text-slate-800 dark:text-slate-200">{token.name}</span>
                                                <span className="text-xs font-mono bg-slate-100 dark:bg-slate-900 text-slate-500 dark:text-slate-400 px-2 py-0.5 rounded border border-slate-200 dark:border-slate-700 whitespace-nowrap">
                                                    {token.maskedToken}
                                                </span>
                                                <button 
                                                    onClick={() => copyToClipboard(token.token || '', token.id)}
                                                    className="p-1.5 text-slate-400 hover:text-indigo-600 dark:hover:text-indigo-400 hover:bg-indigo-50 dark:hover:bg-indigo-900/30 rounded-lg transition-colors"
                                                    title={t('common.copy')}
                                                >
                                                    {copyFeedbackId === token.id ? <Check size={16} className="text-green-600" /> : <Copy size={16} />}
                                                </button>
                                            </div>
                                            <div className="flex gap-4 mt-2 text-xs text-slate-500 dark:text-slate-400">
                                                <span>{t('settings.tokens.created')}: {new Date(token.createdAt).toLocaleDateString()}</span>
                                                <span>{t('settings.tokens.lastUsed')}: {token.lastUsedAt ? new Date(token.lastUsedAt).toLocaleDateString() : '-'}</span>
                                            </div>
                                        </div>
                                        
                                        {revokeId === token.id ? (
                                            <div className="flex items-center gap-2 bg-red-50 dark:bg-red-900/20 p-2 rounded-xl border border-red-100 dark:border-red-900/50 animate-in fade-in slide-in-from-right-2 mt-2 sm:mt-0">
                                                <span className="text-xs text-red-700 dark:text-red-400 font-bold px-1">Confirm Revoke?</span>
                                                <button 
                                                    onClick={() => handleRevokeToken(token.id)}
                                                    className="p-1.5 bg-white dark:bg-slate-800 text-red-600 dark:text-red-400 rounded-lg shadow-sm hover:bg-red-100 dark:hover:bg-red-900/30 transition-colors"
                                                >
                                                    <Check size={16} />
                                                </button>
                                                <button 
                                                    onClick={() => setRevokeId(null)}
                                                    className="p-1.5 bg-white dark:bg-slate-800 text-slate-500 dark:text-slate-400 rounded-lg shadow-sm hover:bg-slate-100 dark:hover:bg-slate-700 transition-colors"
                                                >
                                                    <X size={16} />
                                                </button>
                                            </div>
                                        ) : (
                                            <button 
                                                onClick={() => setRevokeId(token.id)}
                                                className="self-start sm:self-center p-2 text-slate-400 hover:text-red-600 hover:bg-red-50 dark:hover:bg-red-900/20 rounded-lg transition-colors mt-2 sm:mt-0"
                                                title={t('common.delete')}
                                            >
                                                <Trash2 size={20} />
                                            </button>
                                        )}
                                    </div>
                                ))}
                            </div>
                        )}
                    </div>
                </div>
            </div>
        </div>
      )}

      {/* --- Circuit Breaker Control Modal --- */}
      {isCBModalOpen && selectedCB && (
        <div className="fixed inset-0 z-50 flex items-center justify-center bg-slate-900/60 backdrop-blur-sm p-4 animate-fade-in">
            <div className="bg-white dark:bg-slate-900 rounded-2xl shadow-2xl max-w-md w-full animate-in zoom-in-95 duration-200 border border-white/20 dark:border-slate-700">
                <div className="px-6 py-4 border-b border-slate-100 dark:border-slate-800 flex justify-between items-center bg-white/95 dark:bg-slate-900/95 backdrop-blur rounded-t-2xl">
                    <h3 className="text-lg font-bold text-slate-900 dark:text-white">{t('settings.circuitBreaker.controlModal.title')}</h3>
                    <button onClick={() => setIsCBModalOpen(false)} className="text-slate-400 hover:text-slate-600 dark:hover:text-slate-300 rounded-full p-1 hover:bg-slate-100 dark:hover:bg-slate-800">
                        <X size={20} />
                    </button>
                </div>
                <div className="p-6">
                    <div className="mb-6">
                        <span className="text-xs font-bold text-slate-500 dark:text-slate-400 uppercase tracking-wide">Target Provider</span>
                        <p className="text-lg font-bold text-slate-900 dark:text-white mt-1">{selectedCB.providerName}</p>
                        {selectedCB.manuallyControlled && (
                            <div className="mt-3 text-xs bg-purple-50 text-purple-800 dark:bg-purple-900/30 dark:text-purple-300 p-3 rounded-xl border border-purple-100 dark:border-purple-800/50">
                                <strong className="block mb-1 font-bold">{t('settings.circuitBreaker.controlModal.manualActive')}</strong>
                                {selectedCB.manualControlReason}
                            </div>
                        )}
                    </div>
                    <form onSubmit={handleControlSubmit} className="space-y-5">
                        <div>
                            <label className="block text-sm font-semibold text-slate-700 dark:text-slate-300 mb-1.5">
                                {t('settings.circuitBreaker.controlModal.targetState')}
                            </label>
                            <select 
                                value={cbTargetState}
                                onChange={(e) => setCbTargetState(e.target.value as CircuitState)}
                                className="block w-full rounded-xl border-slate-300 dark:border-slate-600 shadow-sm focus:border-indigo-500 focus:ring-indigo-500 text-sm py-2.5 px-3 bg-white dark:bg-slate-950 dark:text-white cursor-pointer"
                            >
                                <option value="CLOSED">CLOSED (Normal)</option>
                                <option value="OPEN">OPEN (Force Break)</option>
                                <option value="HALF_OPEN">HALF_OPEN (Probe)</option>
                            </select>
                        </div>
                        
                        {cbTargetState === 'OPEN' && (
                            <div className="animate-in slide-in-from-top-2">
                                <label className="block text-sm font-semibold text-slate-700 dark:text-slate-300 mb-1.5">
                                    {t('settings.circuitBreaker.controlModal.duration')}
                                </label>
                                <input 
                                    type="number"
                                    value={cbDuration}
                                    onChange={(e) => setCbDuration(parseInt(e.target.value) || 0)}
                                    className="block w-full rounded-xl border-slate-300 dark:border-slate-600 shadow-sm focus:border-indigo-500 focus:ring-indigo-500 text-sm py-2.5 px-3 bg-white dark:bg-slate-950 dark:text-white"
                                />
                            </div>
                        )}

                        <div>
                            <label className="block text-sm font-semibold text-slate-700 dark:text-slate-300 mb-1.5">
                                {t('settings.circuitBreaker.controlModal.reason')}
                            </label>
                            <textarea 
                                rows={3}
                                value={cbReason}
                                onChange={(e) => setCbReason(e.target.value)}
                                placeholder={t('settings.circuitBreaker.controlModal.reasonPlaceholder')}
                                className="block w-full rounded-xl border-slate-300 dark:border-slate-600 shadow-sm focus:border-indigo-500 focus:ring-indigo-500 text-sm py-2.5 px-3 bg-white dark:bg-slate-950 dark:text-white resize-none"
                            />
                        </div>

                        <div className="pt-4 flex justify-end gap-3">
                            <button 
                                type="button" 
                                onClick={() => setIsCBModalOpen(false)}
                                className="px-4 py-2.5 border border-slate-300 dark:border-slate-600 shadow-sm text-sm font-semibold rounded-xl text-slate-700 dark:text-slate-200 bg-white dark:bg-slate-800 hover:bg-slate-50 dark:hover:bg-slate-700 transition-colors"
                            >
                                {t('common.cancel')}
                            </button>
                            <button 
                                type="submit"
                                disabled={isCBSaving}
                                className="px-4 py-2.5 bg-indigo-600 hover:bg-indigo-700 text-white text-sm font-semibold rounded-xl shadow-lg shadow-indigo-500/20 flex items-center disabled:opacity-70 disabled:cursor-not-allowed transition-all hover:-translate-y-0.5"
                            >
                                {isCBSaving ? <Loader2 size={18} className="animate-spin mr-2" /> : null}
                                {t('settings.circuitBreaker.controlModal.confirm')}
                            </button>
                        </div>
                    </form>
                </div>
            </div>
        </div>
      )}
    </div>
  );
};

const CheckCircleIcon = ({ className }: { className?: string }) => (
    <svg xmlns="http://www.w3.org/2000/svg" width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" className={className}>
        <path d="M22 11.08V12a10 10 0 1 1-5.93-9.14"></path>
        <polyline points="22 4 12 14.01 9 11.01"></polyline>
    </svg>
);