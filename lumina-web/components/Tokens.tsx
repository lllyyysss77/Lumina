import React, { useEffect, useState } from 'react';
import { Plus, Trash2, Copy, Check, X, Loader2, BarChart3, ToggleLeft, ToggleRight, ChevronDown, ChevronRight } from 'lucide-react';
import { useLanguage } from './LanguageContext';
import { tokenService } from '../services/tokenService';
import { AccessToken } from '../types';
import { SlideInItem } from './Animations';
import { Button } from './ui/Button';
import { useToast } from './ui/ToastContext';

export const Tokens: React.FC = () => {
  const { t } = useLanguage();
  const { showToast } = useToast();

  const [tokens, setTokens] = useState<AccessToken[]>([]);
  const [isLoading, setIsLoading] = useState(true);
  const [isCreatingToken, setIsCreatingToken] = useState(false);
  const [newTokenName, setNewTokenName] = useState('');
  const [createdToken, setCreatedToken] = useState<AccessToken | null>(null);
  const [copyFeedbackId, setCopyFeedbackId] = useState<string | null>(null);
  const [revokeId, setRevokeId] = useState<string | null>(null);
  const [showDisabled, setShowDisabled] = useState(false);

  useEffect(() => {
    fetchTokens();
  }, []);

  const fetchTokens = async () => {
    setIsLoading(true);
    try {
      const data = await tokenService.getList();
      setTokens(data);
    } catch (error) {
      console.error('Failed to fetch tokens', error);
    } finally {
      setIsLoading(false);
    }
  };

  const handleCreateToken = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!newTokenName.trim()) return;
    setIsCreatingToken(true);
    try {
      const newToken = await tokenService.create(newTokenName);
      setCreatedToken(newToken);
      fetchTokens();
    } catch (error) {
      console.error('Failed to create token', error);
      showToast(t('common.fail'), 'error');
    } finally {
      setIsCreatingToken(false);
      setNewTokenName('');
    }
  };
// PLACEHOLDER_TOKENS_CONTINUED

  const handleToggleToken = async (id: string) => {
    try {
      await tokenService.toggle(id);
      fetchTokens();
      showToast(t('common.success'), 'success');
    } catch (error) {
      console.error('Failed to toggle token', error);
      showToast(t('common.fail'), 'error');
    }
  };

  const handleRevokeToken = async (id: string) => {
    try {
      await tokenService.delete(id);
      setRevokeId(null);
      fetchTokens();
      showToast(t('settings.tokens.revokeSuccess'), 'success');
    } catch (error) {
      console.error('Failed to revoke token', error);
      showToast(t('common.fail'), 'error');
    }
  };

  const copyToClipboard = (text: string, id: string) => {
    navigator.clipboard.writeText(text);
    setCopyFeedbackId(id);
    setTimeout(() => setCopyFeedbackId(null), 2000);
  };

  return (
    <div className="space-y-8">
      <SlideInItem>
        <div>
          <h1 className="text-3xl font-extrabold text-gray-900 dark:text-white tracking-tight">{t('settings.tokens.title')}</h1>
          <p className="text-gray-500 dark:text-gray-400 mt-2 text-lg">{t('settings.securityDesc')}</p>
        </div>
      </SlideInItem>

      {/* Create Token */}
      <SlideInItem index={1}>
        {createdToken ? (
          <div className="p-6 bg-emerald-50 dark:bg-emerald-900/20 border border-emerald-100 dark:border-emerald-800/50 rounded-2xl">
            <div className="flex items-start gap-4">
              <CheckCircleIcon className="text-emerald-600 dark:text-emerald-500 mt-1 flex-shrink-0 w-6 h-6" />
              <div className="flex-1 min-w-0">
                <h3 className="text-base font-bold text-emerald-800 dark:text-emerald-400 mb-1">{t('settings.tokens.generatedSuccess')}</h3>
                <p className="text-sm text-emerald-700 dark:text-emerald-500 mb-4">{t('settings.tokens.copyWarning')}</p>
                <div className="flex items-center gap-2">
                  <code className="flex-1 bg-white dark:bg-black border border-emerald-200 dark:border-emerald-800/50 px-4 py-3 rounded-xl font-mono text-sm text-gray-800 dark:text-gray-200 break-all shadow-sm">
                    {createdToken.token}
                  </code>
                  <Button
                    onClick={() => copyToClipboard(createdToken.token || '', 'new')}
                    variant="secondary"
                    className="w-12 px-0 bg-white dark:bg-black border border-emerald-200 dark:border-emerald-800/50 text-emerald-600 dark:text-emerald-500 hover:bg-emerald-50 dark:hover:bg-emerald-900/30"
                  >
                    {copyFeedbackId === 'new' ? <Check size={20} /> : <Copy size={20} />}
                  </Button>
                </div>
              </div>
            </div>
            <div className="pl-10 mt-3">
              <button onClick={() => setCreatedToken(null)} className="text-sm font-semibold text-emerald-700 dark:text-emerald-400 hover:text-emerald-900 dark:hover:text-emerald-300 underline underline-offset-2">
                {t('settings.tokens.generateAnother')}
              </button>
            </div>
          </div>
        ) : (
          <form onSubmit={handleCreateToken} className="p-6 bg-white dark:bg-[#1a1a1a] rounded-2xl border border-gray-200 dark:border-gray-800 shadow-card">
            <h3 className="text-sm font-bold text-gray-800 dark:text-gray-200 mb-4 uppercase tracking-wide">{t('settings.tokens.create')}</h3>
            <div className="flex flex-col sm:flex-row gap-3">
              <input
                type="text"
                value={newTokenName}
                onChange={(e) => setNewTokenName(e.target.value)}
                placeholder={t('settings.tokens.name') + ' (e.g. "Production App")'}
                className="flex-1 rounded-xl border-gray-200 dark:border-gray-700 text-sm focus:ring-black dark:focus:ring-white focus:border-black dark:focus:border-white py-2.5 px-4 border bg-gray-50 dark:bg-gray-900 dark:text-white transition-shadow"
                required
              />
              <Button type="submit" disabled={isCreatingToken || !newTokenName.trim()} variant="primary" leftIcon={isCreatingToken ? <Loader2 size={18} className="animate-spin" /> : <Plus size={18} />}>
                {t('common.add')}
              </Button>
            </div>
          </form>
        )}
      </SlideInItem>

      {/* Token List */}
      <SlideInItem index={2}>
        <div>
          <h3 className="text-sm font-bold text-gray-900 dark:text-white mb-4 uppercase tracking-wide">{t('settings.tokens.activeTokens')}</h3>
          {isLoading ? (
            <div className="flex justify-center py-12">
              <Loader2 className="w-8 h-8 text-gray-600 animate-spin" />
            </div>
          ) : tokens.filter(t => t.status === 'active').length === 0 ? (
            <div className="text-center py-12 text-gray-500 dark:text-gray-400 bg-white dark:bg-[#1a1a1a] border border-dashed border-gray-200 dark:border-gray-800 rounded-2xl">
              {t('settings.tokens.empty')}
            </div>
          ) : (
            <div className="space-y-3">
              {tokens.filter(tk => tk.status === 'active').map(token => (
                <TokenCard
                  key={token.id}
                  token={token}
                  revokeId={revokeId}
                  copyFeedbackId={copyFeedbackId}
                  onCopy={copyToClipboard}
                  onToggle={handleToggleToken}
                  onRevokeClick={setRevokeId}
                  onRevokeConfirm={handleRevokeToken}
                  onRevokeCancel={() => setRevokeId(null)}
                  t={t}
                />
              ))}
            </div>
          )}
        </div>
      </SlideInItem>

      {/* Disabled Tokens */}
      {!isLoading && tokens.filter(tk => tk.status === 'revoked').length > 0 && (
        <SlideInItem index={3}>
          <div>
            <button
              onClick={() => setShowDisabled(!showDisabled)}
              className="flex items-center gap-2 text-sm font-bold text-gray-500 dark:text-gray-400 mb-4 uppercase tracking-wide hover:text-gray-700 dark:hover:text-gray-300 transition-colors"
            >
              {showDisabled ? <ChevronDown size={16} /> : <ChevronRight size={16} />}
              {t('settings.tokens.disabledTokens')} ({tokens.filter(tk => tk.status === 'revoked').length})
            </button>
            {showDisabled && (
              <div className="space-y-3">
                {tokens.filter(tk => tk.status === 'revoked').map(token => (
                  <TokenCard
                    key={token.id}
                    token={token}
                    revokeId={revokeId}
                    copyFeedbackId={copyFeedbackId}
                    onCopy={copyToClipboard}
                    onToggle={handleToggleToken}
                    onRevokeClick={setRevokeId}
                    onRevokeConfirm={handleRevokeToken}
                    onRevokeCancel={() => setRevokeId(null)}
                    t={t}
                  />
                ))}
              </div>
            )}
          </div>
        </SlideInItem>
      )}
    </div>
  );
};
// PLACEHOLDER_TOKEN_CARD

interface TokenCardProps {
  token: AccessToken;
  revokeId: string | null;
  copyFeedbackId: string | null;
  onCopy: (text: string, id: string) => void;
  onToggle: (id: string) => void;
  onRevokeClick: (id: string) => void;
  onRevokeConfirm: (id: string) => void;
  onRevokeCancel: () => void;
  t: (key: string) => string;
}

const TokenCard: React.FC<TokenCardProps> = ({ token, revokeId, copyFeedbackId, onCopy, onToggle, onRevokeClick, onRevokeConfirm, onRevokeCancel, t }) => (
  <div className="p-4 bg-white dark:bg-[#1a1a1a] border border-gray-200 dark:border-gray-800 rounded-xl hover:border-gray-300 dark:hover:border-gray-600 hover:shadow-sm transition-all">
    <div className="flex flex-col sm:flex-row sm:items-center justify-between">
      <div className="mb-3 sm:mb-0">
        <div className="flex items-center gap-3 flex-wrap">
          <span className="font-bold text-gray-800 dark:text-gray-200">{token.name}</span>
          <span className="text-xs font-mono bg-gray-100 dark:bg-gray-900 text-gray-500 dark:text-gray-400 px-2 py-0.5 rounded border border-gray-200 dark:border-gray-700 whitespace-nowrap">
            {token.maskedToken}
          </span>
          <Button onClick={() => onCopy(token.token || '', token.id)} variant="ghost" className="px-2" title={t('common.copy')}>
            {copyFeedbackId === token.id ? <Check size={16} className="text-green-600" /> : <Copy size={16} />}
          </Button>
          {token.expiredAt && token.expiredAt > 0 && (
            <span className={`text-xs px-2 py-0.5 rounded-full font-medium ${
              Date.now() / 1000 > token.expiredAt
                ? 'bg-red-100 dark:bg-red-900/30 text-red-700 dark:text-red-400'
                : 'bg-blue-100 dark:bg-blue-900/30 text-blue-700 dark:text-blue-400'
            }`}>
              {Date.now() / 1000 > token.expiredAt
                ? t('settings.tokens.expired')
                : `${t('settings.tokens.expires')}: ${new Date(token.expiredAt * 1000).toLocaleDateString()}`
              }
            </span>
          )}
        </div>
      </div>

      <div className="flex items-center gap-1 mt-2 sm:mt-0">
        {revokeId === token.id ? (
          <div className="flex items-center gap-2 bg-red-50 dark:bg-red-900/20 p-2 rounded-xl border border-red-100 dark:border-red-900/50">
            <span className="text-xs text-red-700 dark:text-red-400 font-bold px-1">{t('settings.tokens.confirmRevokeShort')}</span>
            <Button onClick={() => onRevokeConfirm(token.id)} variant="secondary" size="sm" className="px-2 bg-white dark:bg-gray-800 text-red-600 dark:text-red-400 hover:bg-red-100 dark:hover:bg-red-900/30">
              <Check size={16} />
            </Button>
            <Button onClick={onRevokeCancel} variant="secondary" size="sm" className="px-2 bg-white dark:bg-gray-800 text-gray-500 dark:text-gray-400 hover:bg-gray-100 dark:hover:bg-gray-700">
              <X size={16} />
            </Button>
          </div>
        ) : (
          <>
            <Button
              onClick={() => onToggle(token.id)}
              variant="ghost"
              className={`px-2 ${token.status === 'active' ? 'text-green-600 hover:text-orange-600 hover:bg-orange-50 dark:hover:bg-orange-900/20' : 'text-gray-400 hover:text-green-600 hover:bg-green-50 dark:hover:bg-green-900/20'}`}
              title={token.status === 'active' ? t('settings.tokens.disable') : t('settings.tokens.enable')}
            >
              {token.status === 'active' ? <ToggleRight size={22} /> : <ToggleLeft size={22} />}
            </Button>
            <Button onClick={() => onRevokeClick(token.id)} variant="ghost" className="px-2 text-gray-400 hover:text-red-600 hover:bg-red-50 dark:hover:bg-red-900/20" title={t('common.delete')}>
              <Trash2 size={20} />
            </Button>
          </>
        )}
      </div>
    </div>

    {/* Usage Stats */}
    <div className="mt-3 pt-3 border-t border-gray-100 dark:border-gray-800 grid grid-cols-2 sm:grid-cols-4 gap-3">
      <div className="flex items-center gap-1.5">
        <BarChart3 size={13} className="text-gray-400" />
        <span className="text-xs text-gray-500 dark:text-gray-400">{t('settings.tokens.requests')}:</span>
        <span className="text-xs font-semibold text-gray-700 dark:text-gray-300">{(token.totalRequests || 0).toLocaleString()}</span>
      </div>
      <div className="flex items-center gap-1.5">
        <span className="text-xs text-gray-500 dark:text-gray-400">{t('settings.tokens.inputTokens')}:</span>
        <span className="text-xs font-semibold text-gray-700 dark:text-gray-300">{formatTokenCount(token.totalInputTokens || 0)}</span>
      </div>
      <div className="flex items-center gap-1.5">
        <span className="text-xs text-gray-500 dark:text-gray-400">{t('settings.tokens.outputTokens')}:</span>
        <span className="text-xs font-semibold text-gray-700 dark:text-gray-300">{formatTokenCount(token.totalOutputTokens || 0)}</span>
      </div>
      <div className="flex items-center gap-1.5">
        <span className="text-xs text-gray-500 dark:text-gray-400">{t('settings.tokens.cost')}:</span>
        <span className="text-xs font-semibold text-emerald-600 dark:text-emerald-400">${(token.totalCost || 0).toFixed(4)}</span>
      </div>
    </div>
  </div>
);

const formatTokenCount = (count: number): string => {
  if (count >= 1_000_000) return `${(count / 1_000_000).toFixed(1)}M`;
  if (count >= 1_000) return `${(count / 1_000).toFixed(1)}K`;
  return String(count);
};

const CheckCircleIcon = ({ className }: { className?: string }) => (
  <svg xmlns="http://www.w3.org/2000/svg" width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" className={className}>
    <path d="M22 11.08V12a10 10 0 1 1-5.93-9.14"></path>
    <polyline points="22 4 12 14.01 9 11.01"></polyline>
  </svg>
);


