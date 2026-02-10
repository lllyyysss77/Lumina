import React, { useState } from 'react';
import { useAuth } from './AuthContext';
import { useLanguage } from './LanguageContext';
import { 
  Zap, 
  Loader2, 
  AlertCircle, 
  Check, 
  Shield, 
  Activity, 
  Globe, 
  Cpu, 
  Layers, 
  ArrowRight,
  X,
  Github
} from 'lucide-react';
import metadata from '../constants';

export const Login: React.FC = () => {
  const { login } = useAuth();
  const { t, language, setLanguage } = useLanguage();
  const [username, setUsername] = useState('');
  const [password, setPassword] = useState('');
  const [isLoading, setIsLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [isModalOpen, setIsModalOpen] = useState(false);

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!username || !password) {
      setError(t('login.errors.missingCreds'));
      return;
    }

    setIsLoading(true);
    setError(null);

    try {
      await login(username, password);
    } catch (err: any) {
      console.error("Login component caught error:", err);
      let msg = t('login.errors.failed');
      
      if (err instanceof Error) {
        if (err.message === 'Failed to fetch') {
           msg = t('login.errors.network');
        } else {
           msg = err.message;
        }
      } else if (err.data && err.data.message) {
         msg = err.data.message;
      }
      
      setError(msg);
    } finally {
      setIsLoading(false);
    }
  };

  const openLogin = () => {
      setIsModalOpen(true);
      setError(null);
  };

  const features = [
      {
          icon: <Globe size={20} className="text-gray-900 dark:text-white" />,
          title: t('login.features.unifiedGateway.title'),
          desc: t('login.features.unifiedGateway.desc')
      },
      {
          icon: <Activity size={20} className="text-gray-900 dark:text-white" />,
          title: t('login.features.loadBalancing.title'),
          desc: t('login.features.loadBalancing.desc')
      },
      {
          icon: <Shield size={20} className="text-gray-900 dark:text-white" />,
          title: t('login.features.security.title'),
          desc: t('login.features.security.desc')
      },
      {
          icon: <Cpu size={20} className="text-gray-900 dark:text-white" />,
          title: t('login.features.observability.title'),
          desc: t('login.features.observability.desc')
      },
      {
          icon: <Layers size={20} className="text-gray-900 dark:text-white" />,
          title: t('login.features.modelMapping.title'),
          desc: t('login.features.modelMapping.desc')
      },
      {
          icon: <Zap size={20} className="text-gray-900 dark:text-white" />,
          title: t('login.features.circuitBreaking.title'),
          desc: t('login.features.circuitBreaking.desc')
      }
  ];

  const toggleLanguage = () => {
      setLanguage(language === 'zh' ? 'en' : 'zh');
  };

  return (
    <div className="min-h-screen bg-[#FAFAFA] dark:bg-[#0d0d0d] text-gray-900 dark:text-gray-50 font-sans selection:bg-gray-200 dark:selection:bg-gray-700">
      
      {/* Navbar */}
      <nav className="fixed w-full z-40 bg-[#FAFAFA]/90 dark:bg-[#0d0d0d]/90 backdrop-blur-md border-b border-gray-200 dark:border-gray-800">
          <div className="max-w-6xl mx-auto px-6">
              <div className="flex justify-between items-center h-16">
                  <div className="flex items-center gap-2">
                      <div className="w-8 h-8 bg-black dark:bg-white rounded-lg flex items-center justify-center text-white dark:text-black shadow-sm">
                          <Zap size={18} fill="currentColor" />
                      </div>
                      <span className="text-xl font-bold tracking-tight text-gray-900 dark:text-white">
                          Lumina
                      </span>
                  </div>
                  <div className="flex items-center gap-4">
                      <button
                          onClick={toggleLanguage}
                          className="px-3 py-1.5 text-xs font-semibold uppercase tracking-wide text-gray-500 hover:text-gray-900 dark:text-gray-400 dark:hover:text-white transition-colors"
                      >
                          {language === 'zh' ? 'En' : '中'}
                      </button>
                      <a href="https://github.com/jojomini1231-cloud/Lumina" target="_blank" rel="noreferrer" className="text-gray-500 hover:text-gray-900 dark:text-gray-400 dark:hover:text-white transition-colors hidden sm:block">
                          <Github size={20} />
                      </a>
                      <button 
                          onClick={openLogin}
                          className="px-4 py-2 text-sm font-medium text-gray-600 dark:text-gray-300 hover:bg-gray-100 dark:hover:bg-gray-800 rounded-lg transition-colors"
                      >
                          {t('login.nav.login')}
                      </button>
                      <button 
                          onClick={openLogin}
                          className="px-4 py-2 text-sm font-medium bg-black dark:bg-white text-white dark:text-black rounded-lg shadow-sm hover:opacity-90 transition-all active:scale-95"
                      >
                          {t('login.nav.getStarted')}
                      </button>
                  </div>
              </div>
          </div>
      </nav>

      {/* Hero Section */}
      <div className="relative pt-32 pb-20 sm:pt-40 sm:pb-24 overflow-hidden">
          <div className="relative z-10 max-w-4xl mx-auto px-6 text-center">
              <div className="inline-flex items-center px-3 py-1 rounded-full border border-gray-200 dark:border-gray-800 bg-white dark:bg-gray-900 text-gray-600 dark:text-gray-300 text-xs font-medium mb-8 shadow-sm animate-fade-in">
                  <span className="flex h-2 w-2 rounded-full bg-green-500 mr-2"></span>
                  {t('login.hero.tagline', { version: metadata.version })}
              </div>
              <h1 className="text-5xl sm:text-7xl font-bold tracking-tight text-gray-900 dark:text-white mb-8 animate-slide-up leading-tight">
                  {t('login.hero.titleLine1')} {t('login.hero.titleLine2')} {t('login.hero.titleLine3')}
              </h1>
              <p className="max-w-xl mx-auto text-xl text-gray-500 dark:text-gray-400 mb-10 animate-slide-up leading-relaxed" style={{ animationDelay: '0.1s' }}>
                  {t('login.hero.subtitle')}
              </p>
              <div className="flex justify-center gap-4 animate-slide-up" style={{ animationDelay: '0.2s' }}>
                  <button 
                      onClick={openLogin}
                      className="group px-8 py-3 bg-black dark:bg-white text-white dark:text-black rounded-xl font-semibold text-base shadow-lg transition-all hover:-translate-y-1 flex items-center"
                  >
                      {t('login.hero.startBtn')}
                      <ArrowRight className="ml-2 group-hover:translate-x-1 transition-transform" size={18} />
                  </button>
                  <a 
                      href="https://github.com/jojomini1231-cloud/Lumina/blob/master/README.md"
                      target="_blank"
                      rel="noreferrer"
                      className="px-8 py-3 bg-white dark:bg-[#1a1a1a] border border-gray-200 dark:border-gray-800 hover:bg-gray-50 dark:hover:bg-gray-800 text-gray-900 dark:text-white rounded-xl font-semibold text-base shadow-sm transition-all hover:-translate-y-1"
                  >
                      {t('login.hero.docBtn')}
                  </a>
              </div>
          </div>
      </div>

      {/* Features Grid */}
      <div className="py-24 bg-white dark:bg-[#111] border-t border-gray-100 dark:border-gray-800">
          <div className="max-w-6xl mx-auto px-6">
              <div className="text-center mb-16">
                  <h2 className="text-3xl font-bold text-gray-900 dark:text-white">{t('login.features.title')}</h2>
                  <p className="mt-4 text-lg text-gray-500 dark:text-gray-400">{t('login.features.subtitle')}</p>
              </div>
              <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-8">
                  {features.map((feature, idx) => (
                      <div key={idx} className="p-8 rounded-2xl bg-gray-50 dark:bg-[#1a1a1a] border border-gray-100 dark:border-gray-800 hover:border-gray-300 dark:hover:border-gray-700 transition-colors group">
                          <div className="w-10 h-10 bg-white dark:bg-black rounded-lg flex items-center justify-center shadow-sm mb-4 border border-gray-100 dark:border-gray-800">
                              {feature.icon}
                          </div>
                          <h3 className="text-lg font-bold text-gray-900 dark:text-white mb-2">{feature.title}</h3>
                          <p className="text-sm text-gray-500 dark:text-gray-400 leading-relaxed">
                              {feature.desc}
                          </p>
                      </div>
                  ))}
              </div>
          </div>
      </div>

      {/* Footer */}
      <footer className="bg-[#FAFAFA] dark:bg-[#0d0d0d] py-12 border-t border-gray-200 dark:border-gray-800">
          <div className="max-w-6xl mx-auto px-6 text-center">
              <p className="text-gray-500 dark:text-gray-400 mb-4">
                  {t('login.footer.text')}
              </p>
              <div className="text-sm text-gray-400 dark:text-gray-600">
                  &copy; {new Date().getFullYear()} Lumina Gateway. {t('login.footer.rights')}
              </div>
          </div>
      </footer>

      {/* Login Modal */}
      {isModalOpen && (
          <div className="fixed inset-0 z-50 flex items-center justify-center p-4">
              {/* Backdrop */}
              <div 
                  className="absolute inset-0 bg-white/80 dark:bg-black/80 backdrop-blur-sm transition-opacity"
                  onClick={() => setIsModalOpen(false)}
              ></div>

              {/* Modal Content */}
              <div className="relative w-full max-w-sm bg-white dark:bg-[#1a1a1a] rounded-2xl shadow-float overflow-hidden border border-gray-200 dark:border-gray-800 animate-in zoom-in-95 duration-200">
                  <button 
                      onClick={() => setIsModalOpen(false)}
                      className="absolute top-4 right-4 text-gray-400 hover:text-gray-600 dark:hover:text-gray-300 transition-colors"
                  >
                      <X size={20} />
                  </button>

                  <div className="p-8">
                      <div className="flex flex-col items-center mb-8">
                          <div className="w-10 h-10 bg-black dark:bg-white text-white dark:text-black rounded-xl flex items-center justify-center mb-4 shadow-sm">
                              <Zap size={20} fill="currentColor" />
                          </div>
                          <h2 className="text-xl font-bold text-gray-900 dark:text-white">{t('login.modal.welcome')}</h2>
                      </div>

                      <form className="space-y-4" onSubmit={handleSubmit}>
                          {error && (
                              <div className="bg-red-50 dark:bg-red-900/20 border border-red-200 dark:border-red-800 rounded-lg p-3 flex items-start gap-3 animate-in slide-in-from-top-2">
                                  <AlertCircle className="text-red-600 dark:text-red-400 mt-0.5 flex-shrink-0" size={16} />
                                  <span className="text-xs font-medium text-red-600 dark:text-red-400 leading-snug">{error}</span>
                              </div>
                          )}

                          <div>
                              <label className="block text-xs font-semibold text-gray-500 dark:text-gray-400 uppercase tracking-wide mb-1.5">{t('login.modal.username')}</label>
                              <input
                                  type="text"
                                  required
                                  value={username}
                                  onChange={(e) => setUsername(e.target.value)}
                                  className="block w-full px-3 py-2 bg-gray-50 dark:bg-gray-900 border border-gray-200 dark:border-gray-700 rounded-lg focus:ring-2 focus:ring-gray-900 dark:focus:ring-white focus:border-transparent dark:text-white transition-all sm:text-sm"
                                  placeholder={t('login.modal.placeholderUser')}
                              />
                          </div>

                          <div>
                              <label className="block text-xs font-semibold text-gray-500 dark:text-gray-400 uppercase tracking-wide mb-1.5">{t('login.modal.password')}</label>
                              <input
                                  type="password"
                                  required
                                  value={password}
                                  onChange={(e) => setPassword(e.target.value)}
                                  className="block w-full px-3 py-2 bg-gray-50 dark:bg-gray-900 border border-gray-200 dark:border-gray-700 rounded-lg focus:ring-2 focus:ring-gray-900 dark:focus:ring-white focus:border-transparent dark:text-white transition-all sm:text-sm"
                                  placeholder={t('login.modal.placeholderPass')}
                              />
                          </div>

                          <button
                              type="submit"
                              disabled={isLoading}
                              className="w-full flex justify-center items-center py-2.5 px-4 bg-gray-900 hover:bg-black dark:bg-white dark:hover:bg-gray-200 text-white dark:text-black font-semibold rounded-lg shadow-sm transition-all disabled:opacity-70 disabled:cursor-not-allowed hover:-translate-y-0.5 mt-2"
                          >
                              {isLoading ? (
                                  <>
                                      <Loader2 className="animate-spin -ml-1 mr-2 h-4 w-4" />
                                      {t('login.modal.authenticating')}
                                  </>
                              ) : (
                                  t('login.modal.signinBtn')
                              )}
                          </button>
                      </form>
                      
                      <div className="mt-6 text-center border-t border-gray-100 dark:border-gray-800 pt-4">
                          <p className="text-[10px] text-gray-400 uppercase tracking-wide">
                              {t('login.modal.systemInfo')}
                          </p>
                      </div>
                  </div>
              </div>
          </div>
      )}
    </div>
  );
};