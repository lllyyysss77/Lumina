import React, { useState } from 'react';
import { Save, User as UserIcon, Loader2 } from 'lucide-react';
import { useLanguage } from './LanguageContext';
import { userService } from '../services/userService';
import { useAuth } from './AuthContext';
import { useToast } from './ToastContext';

export const AccountSection: React.FC = () => {
    const { t } = useLanguage();
    const { user, logout } = useAuth();
    const { showToast } = useToast();

    const [username, setUsername] = useState(user?.username || '');
    const [originalPassword, setOriginalPassword] = useState('');
    const [password, setPassword] = useState('');
    const [confirmPassword, setConfirmPassword] = useState('');
    const [isUpdatingProfile, setIsUpdatingProfile] = useState(false);

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
            showToast(error.message || t('settings.circuitBreaker.updateProfileFail'), 'error');
            setIsUpdatingProfile(false);
        }
    };

    return (
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
    );
};
