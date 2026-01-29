import React, { useState, useEffect } from 'react';
import { Save, Shield, Monitor, Globe, Plus, Trash2, Copy, Check, X, AlertTriangle, Key, Loader2, User as UserIcon, CheckCircle2, AlertCircle, Activity } from 'lucide-react';
import { useLanguage } from './LanguageContext';
import { tokenService } from '../services/tokenService';
import { userService } from '../services/userService';
import { AccessToken } from '../types';
import { useAuth } from './AuthContext';
import { useTheme } from './ThemeContext';

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
  
  // Copy feedback state
  const [copyFeedbackId, setCopyFeedbackId] = useState<string | null>(null);
  
  // Confirmation for revocation
  const [revokeId, setRevokeId] = useState<string | null>(null);

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

  return (
    <div className="max-w-6xl space-y-8 relative">
       {/* Toast Notification */}
       {toast.show && (
          <div className={`fixed top-4 right-4 z-[100] px-4 py-3 rounded-lg shadow-lg border flex items-center animate-in slide-in-from-right duration-300 ${
              toast.type === 'success' ? 'bg-white border-green-200 text-green-700 dark:bg-slate-800 dark:border-green-900 dark:text-green-400' : 
              toast.type === 'error' ? 'bg-white border-red-200 text-red-700 dark:bg-slate-800 dark:border-red-900 dark:text-red-400' :
              'bg-white border-blue-200 text-blue-700 dark:bg-slate-800 dark:border-blue-900 dark:text-blue-400'
          }`}>
              {toast.type === 'success' ? <CheckCircle2 size={18} className="mr-2" /> : 
               toast.type === 'error' ? <AlertCircle size={18} className="mr-2" /> :
               <Activity size={18} className="mr-2" />}
              <span className="text-sm font-medium">{toast.message}</span>
          </div>
      )}

      <div>
        <h1 className="text-2xl font-bold text-slate-900 dark:text-white">{t('settings.title')}</h1>
        <p className="text-slate-500 dark:text-slate-400 mt-1">{t('settings.subtitle')}</p>
      </div>

      <div className="bg-white dark:bg-slate-800 rounded-xl border border-slate-200 dark:border-slate-700 shadow-sm divide-y divide-slate-100 dark:divide-slate-700">

        {/* Section: Language */}
        <div className="p-4 sm:p-6">
          <div className="flex flex-col sm:flex-row sm:items-start justify-between gap-4">
            <div className="flex items-start space-x-4">
              <div className="p-2 bg-indigo-50 dark:bg-indigo-900/30 text-indigo-600 dark:text-indigo-400 rounded-lg flex-shrink-0">
                <Globe size={20} />
              </div>
              <div>
                <h3 className="text-base font-semibold text-slate-900 dark:text-white">{t('settings.language')}</h3>
                <p className="text-sm text-slate-500 dark:text-slate-400 mt-1">
                  {t('settings.languageDesc')}
                </p>
                <div className="mt-4 flex items-center space-x-3">
                    <button 
                        onClick={() => setLanguage('zh')}
                        className={`px-3 py-1.5 text-sm font-medium rounded-md border transition-colors ${
                            language === 'zh' 
                            ? 'bg-indigo-50 border-indigo-200 text-indigo-700 dark:bg-indigo-900/50 dark:border-indigo-700 dark:text-indigo-300' 
                            : 'bg-white border-slate-300 text-slate-700 hover:bg-slate-50 dark:bg-slate-800 dark:border-slate-600 dark:text-slate-300 dark:hover:bg-slate-700'
                        }`}
                    >
                        中文 (Chinese)
                    </button>
                    <button 
                        onClick={() => setLanguage('en')}
                        className={`px-3 py-1.5 text-sm font-medium rounded-md border transition-colors ${
                            language === 'en' 
                            ? 'bg-indigo-50 border-indigo-200 text-indigo-700 dark:bg-indigo-900/50 dark:border-indigo-700 dark:text-indigo-300' 
                            : 'bg-white border-slate-300 text-slate-700 hover:bg-slate-50 dark:bg-slate-800 dark:border-slate-600 dark:text-slate-300 dark:hover:bg-slate-700'
                        }`}
                    >
                        English
                    </button>
                </div>
              </div>
            </div>
          </div>
        </div>

        {/* Section: Account Settings */}
        <div className="p-4 sm:p-6">
          <div className="flex flex-col sm:flex-row sm:items-start justify-between gap-4">
            <div className="flex items-start space-x-4 w-full">
              <div className="p-2 bg-emerald-50 dark:bg-emerald-900/30 text-emerald-600 dark:text-emerald-400 rounded-lg flex-shrink-0">
                <UserIcon size={20} />
              </div>
              <div className="flex-1 max-w-2xl">
                <h3 className="text-base font-semibold text-slate-900 dark:text-white">{t('settings.account')}</h3>
                <p className="text-sm text-slate-500 dark:text-slate-400 mt-1">
                  {t('settings.accountDesc')}
                </p>
                
                <form onSubmit={handleUpdateProfile} className="mt-5 space-y-4">
                    <div>
                        <label className="block text-sm font-medium text-slate-700 dark:text-slate-300 mb-1">{t('settings.username')}</label>
                        <input 
                            type="text" 
                            required
                            value={username}
                            onChange={(e) => setUsername(e.target.value)}
                            className="block w-full rounded-lg border-slate-300 dark:border-slate-600 shadow-sm focus:border-indigo-500 focus:ring-indigo-500 sm:text-sm p-2 border bg-white dark:bg-slate-900 dark:text-white"
                        />
                    </div>
                    <div>
                        <label className="block text-sm font-medium text-slate-700 dark:text-slate-300 mb-1">{t('settings.originalPassword')}</label>
                        <input 
                            type="password" 
                            value={originalPassword}
                            onChange={(e) => setOriginalPassword(e.target.value)}
                            className="block w-full rounded-lg border-slate-300 dark:border-slate-600 shadow-sm focus:border-indigo-500 focus:ring-indigo-500 sm:text-sm p-2 border bg-white dark:bg-slate-900 dark:text-white"
                        />
                    </div>
                    <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
                        <div>
                            <label className="block text-sm font-medium text-slate-700 dark:text-slate-300 mb-1">{t('settings.password')}</label>
                            <input 
                                type="password" 
                                value={password}
                                onChange={(e) => setPassword(e.target.value)}
                                placeholder={t('settings.leaveBlank')}
                                className="block w-full rounded-lg border-slate-300 dark:border-slate-600 shadow-sm focus:border-indigo-500 focus:ring-indigo-500 sm:text-sm p-2 border bg-white dark:bg-slate-900 dark:text-white dark:placeholder-slate-500"
                            />
                        </div>
                         <div>
                            <label className="block text-sm font-medium text-slate-700 dark:text-slate-300 mb-1">{t('settings.confirmPassword')}</label>
                            <input 
                                type="password" 
                                value={confirmPassword}
                                onChange={(e) => setConfirmPassword(e.target.value)}
                                placeholder={t('settings.leaveBlank')}
                                className="block w-full rounded-lg border-slate-300 dark:border-slate-600 shadow-sm focus:border-indigo-500 focus:ring-indigo-500 sm:text-sm p-2 border bg-white dark:bg-slate-900 dark:text-white dark:placeholder-slate-500"
                            />
                        </div>
                    </div>
                    <div className="pt-2">
                        <button 
                            type="submit" 
                            disabled={isUpdatingProfile}
                            className="flex items-center px-4 py-2 bg-indigo-600 hover:bg-indigo-700 text-white text-sm font-medium rounded-lg shadow-sm transition-colors disabled:opacity-70 disabled:cursor-not-allowed"
                        >
                            {isUpdatingProfile ? <Loader2 size={16} className="mr-2 animate-spin" /> : <Save size={16} className="mr-2" />}
                            {t('settings.update')}
                        </button>
                    </div>
                </form>
              </div>
            </div>
          </div>
        </div>

        {/* Section: Security */}
        <div className="p-4 sm:p-6">
           <div className="flex flex-col sm:flex-row sm:items-start justify-between gap-4">
            <div className="flex items-start space-x-4">
              <div className="p-2 bg-red-50 dark:bg-red-900/30 text-red-600 dark:text-red-400 rounded-lg flex-shrink-0">
                <Shield size={20} />
              </div>
              <div>
                <h3 className="text-base font-semibold text-slate-900 dark:text-white">{t('settings.security')}</h3>
                <p className="text-sm text-slate-500 dark:text-slate-400 mt-1 max-w-lg">
                  {t('settings.securityDesc')}
                </p>
                 <div className="mt-4">
                    <button 
                      onClick={handleManageTokens}
                      className="px-3 py-2 border border-slate-300 dark:border-slate-600 shadow-sm text-sm font-medium rounded-md text-slate-700 dark:text-slate-200 bg-white dark:bg-slate-800 hover:bg-slate-50 dark:hover:bg-slate-700 transition-colors"
                    >
                        {t('settings.manageTokens')}
                    </button>
                 </div>
              </div>
            </div>
          </div>
        </div>

        {/* Section: Theme */}
        <div className="p-4 sm:p-6">
           <div className="flex flex-col sm:flex-row sm:items-start justify-between gap-4">
            <div className="flex items-start space-x-4">
              <div className="p-2 bg-purple-50 dark:bg-purple-900/30 text-purple-600 dark:text-purple-400 rounded-lg flex-shrink-0">
                <Monitor size={20} />
              </div>
              <div>
                <h3 className="text-base font-semibold text-slate-900 dark:text-white">{t('settings.appearance')}</h3>
                <p className="text-sm text-slate-500 dark:text-slate-400 mt-1">
                  {t('settings.appearanceDesc')}
                </p>
                <div className="mt-3 flex space-x-4">
                    <div 
                        onClick={() => setTheme('light')}
                        className={`cursor-pointer border-2 rounded-lg p-1 transition-all ${
                            theme === 'light' 
                            ? 'border-indigo-600 ring-1 ring-indigo-600/20' 
                            : 'border-transparent hover:border-slate-300 dark:hover:border-slate-600'
                        }`}
                    >
                        <div className="w-20 h-12 bg-slate-100 border border-slate-200 rounded flex items-center justify-center text-xs font-medium text-slate-900 shadow-sm">
                            {t('settings.light')}
                        </div>
                    </div>
                    <div 
                        onClick={() => setTheme('dark')}
                        className={`cursor-pointer border-2 rounded-lg p-1 transition-all ${
                            theme === 'dark' 
                            ? 'border-indigo-600 ring-1 ring-indigo-600/20' 
                            : 'border-transparent hover:border-slate-300 dark:hover:border-slate-600'
                        }`}
                    >
                        <div className="w-20 h-12 bg-slate-800 border border-slate-700 rounded flex items-center justify-center text-xs font-medium text-white shadow-sm">
                            {t('settings.dark')}
                        </div>
                    </div>
                </div>
              </div>
            </div>
          </div>
        </div>
      </div>

      {/* --- Token Management Modal --- */}
      {isTokenModalOpen && (
        <div className="fixed inset-0 z-50 flex items-center justify-center bg-slate-900/50 backdrop-blur-sm p-4">
            <div className="bg-white dark:bg-slate-800 rounded-xl shadow-xl max-w-2xl w-full max-h-[85vh] flex flex-col animate-in zoom-in-95 duration-200 border border-slate-200 dark:border-slate-700">
                <div className="px-6 py-4 border-b border-slate-100 dark:border-slate-700 flex justify-between items-center bg-slate-50/50 dark:bg-slate-900/50">
                    <div className="flex items-center gap-2">
                        <div className="p-1.5 bg-indigo-100 dark:bg-indigo-900/40 text-indigo-600 dark:text-indigo-400 rounded-lg">
                            <Key size={18} />
                        </div>
                        <h2 className="text-lg font-bold text-slate-900 dark:text-white">{t('settings.tokens.title')}</h2>
                    </div>
                    <button onClick={closeTokenModal} className="text-slate-400 hover:text-slate-600 dark:hover:text-slate-300 rounded-lg p-1 hover:bg-slate-100 dark:hover:bg-slate-700 transition-colors">
                        <X size={20} />
                    </button>
                </div>

                <div className="p-6 flex-1 overflow-y-auto">
                    {/* Create Token Section (Or Success View) */}
                    {createdToken ? (
                        <div className="mb-8 p-4 bg-green-50 dark:bg-green-900/20 border border-green-200 dark:border-green-800 rounded-xl">
                            <div className="flex items-start gap-3">
                                <CheckCircleIcon className="text-green-600 dark:text-green-500 mt-0.5 flex-shrink-0" />
                                <div className="flex-1">
                                    <h3 className="text-sm font-bold text-green-800 dark:text-green-400 mb-1">Token Generated Successfully</h3>
                                    <p className="text-xs text-green-700 dark:text-green-500 mb-3">{t('settings.tokens.copyWarning')}</p>
                                    
                                    <div className="flex items-center gap-2">
                                        <code className="flex-1 bg-white dark:bg-slate-900 border border-green-200 dark:border-green-800 px-3 py-2 rounded-lg font-mono text-sm text-slate-800 dark:text-slate-200 break-all">
                                            {createdToken.token}
                                        </code>
                                        <button 
                                            onClick={() => copyToClipboard(createdToken.token || '', 'new')}
                                            className="p-2 bg-white dark:bg-slate-900 border border-green-200 dark:border-green-800 text-green-700 dark:text-green-500 rounded-lg hover:bg-green-100 dark:hover:bg-green-900/30 transition-colors"
                                        >
                                            {copyFeedbackId === 'new' ? <Check size={18} /> : <Copy size={18} />}
                                        </button>
                                    </div>
                                </div>
                            </div>
                            <button 
                                onClick={() => setCreatedToken(null)}
                                className="mt-4 text-xs font-medium text-green-700 dark:text-green-400 hover:text-green-800 dark:hover:text-green-300 underline"
                            >
                                Generate another token
                            </button>
                        </div>
                    ) : (
                        <form onSubmit={handleCreateToken} className="mb-8 p-4 bg-slate-50 dark:bg-slate-900/50 rounded-xl border border-slate-100 dark:border-slate-700">
                            <h3 className="text-sm font-bold text-slate-700 dark:text-slate-300 mb-3">{t('settings.tokens.create')}</h3>
                            <div className="flex flex-col sm:flex-row gap-3">
                                <input 
                                    type="text" 
                                    value={newTokenName}
                                    onChange={(e) => setNewTokenName(e.target.value)}
                                    placeholder={t('settings.tokens.name') + ' (e.g. "Production App")'}
                                    className="flex-1 rounded-lg border-slate-300 dark:border-slate-600 text-sm focus:ring-indigo-500 focus:border-indigo-500 p-2 border bg-white dark:bg-slate-800 dark:text-white"
                                    required
                                />
                                <button 
                                    type="submit" 
                                    disabled={isCreatingToken || !newTokenName.trim()}
                                    className="px-4 py-2 bg-indigo-600 text-white text-sm font-medium rounded-lg hover:bg-indigo-700 disabled:opacity-70 disabled:cursor-not-allowed flex items-center justify-center"
                                >
                                    {isCreatingToken ? <Loader2 size={16} className="animate-spin" /> : <Plus size={16} className="mr-2" />}
                                    {t('common.add')}
                                </button>
                            </div>
                        </form>
                    )}

                    {/* Token List */}
                    <div>
                        <h3 className="text-sm font-semibold text-slate-900 dark:text-white mb-3">Active Tokens</h3>
                        {isTokensLoading ? (
                            <div className="flex justify-center py-8">
                                <Loader2 className="w-6 h-6 text-indigo-600 animate-spin" />
                            </div>
                        ) : tokens.length === 0 ? (
                            <div className="text-center py-8 text-slate-500 dark:text-slate-400 bg-white dark:bg-slate-800 border border-dashed border-slate-200 dark:border-slate-700 rounded-xl">
                                {t('settings.tokens.empty')}
                            </div>
                        ) : (
                            <div className="space-y-3">
                                {tokens.map(token => (
                                    <div key={token.id} className="flex flex-col sm:flex-row sm:items-center justify-between p-4 bg-white dark:bg-slate-800 border border-slate-200 dark:border-slate-700 rounded-xl hover:border-indigo-200 dark:hover:border-indigo-800 transition-colors">
                                        <div className="mb-2 sm:mb-0">
                                            <div className="flex items-center gap-2 flex-wrap">
                                                <span className="font-semibold text-slate-800 dark:text-slate-200">{token.name}</span>
                                                <span className="text-xs font-mono bg-slate-100 dark:bg-slate-900 text-slate-500 dark:text-slate-400 px-1.5 py-0.5 rounded border border-slate-200 dark:border-slate-700 whitespace-nowrap">
                                                    {token.maskedToken}
                                                </span>
                                                <button 
                                                    onClick={() => copyToClipboard(token.token || '', token.id)}
                                                    className="p-1.5 text-slate-400 hover:text-indigo-600 dark:hover:text-indigo-400 hover:bg-indigo-50 dark:hover:bg-indigo-900/30 rounded transition-colors"
                                                    title={t('common.copy')}
                                                >
                                                    {copyFeedbackId === token.id ? <Check size={14} className="text-green-600" /> : <Copy size={14} />}
                                                </button>
                                            </div>
                                            <div className="flex gap-4 mt-1 text-xs text-slate-500 dark:text-slate-400">
                                                <span>{t('settings.tokens.created')}: {new Date(token.createdAt).toLocaleDateString()}</span>
                                                <span>{t('settings.tokens.lastUsed')}: {token.lastUsedAt ? new Date(token.lastUsedAt).toLocaleDateString() : '-'}</span>
                                            </div>
                                        </div>
                                        
                                        {revokeId === token.id ? (
                                            <div className="flex items-center gap-2 bg-red-50 dark:bg-red-900/20 p-1.5 rounded-lg border border-red-100 dark:border-red-900 animate-in fade-in slide-in-from-right-2 mt-2 sm:mt-0">
                                                <span className="text-xs text-red-700 dark:text-red-400 font-medium px-1">Confirm?</span>
                                                <button 
                                                    onClick={() => handleRevokeToken(token.id)}
                                                    className="p-1.5 bg-white dark:bg-slate-800 text-red-600 dark:text-red-400 rounded shadow-sm hover:bg-red-100 dark:hover:bg-red-900/30"
                                                >
                                                    <Check size={14} />
                                                </button>
                                                <button 
                                                    onClick={() => setRevokeId(null)}
                                                    className="p-1.5 bg-white dark:bg-slate-800 text-slate-500 dark:text-slate-400 rounded shadow-sm hover:bg-slate-100 dark:hover:bg-slate-700"
                                                >
                                                    <X size={14} />
                                                </button>
                                            </div>
                                        ) : (
                                            <button 
                                                onClick={() => setRevokeId(token.id)}
                                                className="self-start sm:self-center p-2 text-slate-400 hover:text-red-600 hover:bg-red-50 dark:hover:bg-red-900/20 rounded-lg transition-colors mt-2 sm:mt-0"
                                                title={t('common.delete')}
                                            >
                                                <Trash2 size={18} />
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
    </div>
  );
};

const CheckCircleIcon = ({ className }: { className?: string }) => (
    <svg xmlns="http://www.w3.org/2000/svg" width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" className={className}>
        <path d="M22 11.08V12a10 10 0 1 1-5.93-9.14"></path>
        <polyline points="22 4 12 14.01 9 11.01"></polyline>
    </svg>
);