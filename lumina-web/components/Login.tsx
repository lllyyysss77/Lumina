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
          icon: <Globe className="text-blue-500" />,
          title: t('login.features.unifiedGateway.title'),
          desc: t('login.features.unifiedGateway.desc')
      },
      {
          icon: <Activity className="text-green-500" />,
          title: t('login.features.loadBalancing.title'),
          desc: t('login.features.loadBalancing.desc')
      },
      {
          icon: <Shield className="text-indigo-500" />,
          title: t('login.features.security.title'),
          desc: t('login.features.security.desc')
      },
      {
          icon: <Cpu className="text-purple-500" />,
          title: t('login.features.observability.title'),
          desc: t('login.features.observability.desc')
      },
      {
          icon: <Layers className="text-orange-500" />,
          title: t('login.features.modelMapping.title'),
          desc: t('login.features.modelMapping.desc')
      },
      {
          icon: <Zap className="text-yellow-500" />,
          title: t('login.features.circuitBreaking.title'),
          desc: t('login.features.circuitBreaking.desc')
      }
  ];

  const toggleLanguage = () => {
      setLanguage(language === 'zh' ? 'en' : 'zh');
  };

  return (
    <div className="min-h-screen bg-slate-50 dark:bg-slate-950 text-slate-900 dark:text-slate-50 font-sans selection:bg-indigo-100 dark:selection:bg-indigo-900/30">
      
      {/* Navbar */}
      <nav className="fixed w-full z-40 bg-white/80 dark:bg-slate-950/80 backdrop-blur-md border-b border-slate-200 dark:border-slate-800">
          <div className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8">
              <div className="flex justify-between items-center h-16">
                  <div className="flex items-center gap-2">
                      <div className="w-8 h-8 bg-indigo-600 rounded-lg flex items-center justify-center text-white shadow-lg shadow-indigo-500/30">
                          <Zap size={20} fill="currentColor" />
                      </div>
                      <span className="text-xl font-bold bg-clip-text text-transparent bg-gradient-to-r from-slate-900 to-slate-700 dark:from-white dark:to-slate-300">
                          Lumina
                      </span>
                  </div>
                  <div className="flex items-center gap-4">
                      <button
                          onClick={toggleLanguage}
                          className="px-2 py-1 text-sm font-medium text-slate-500 hover:text-slate-900 dark:text-slate-400 dark:hover:text-white transition-colors"
                      >
                          {language === 'zh' ? 'En' : '中'}
                      </button>
                      <a href="https://github.com/jojomini1231-cloud/Lumina" target="_blank" rel="noreferrer" className="text-slate-500 hover:text-slate-900 dark:text-slate-400 dark:hover:text-white transition-colors hidden sm:block">
                          <Github size={20} />
                      </a>
                      <button 
                          onClick={openLogin}
                          className="px-4 py-2 text-sm font-medium text-slate-700 dark:text-slate-200 hover:bg-slate-100 dark:hover:bg-slate-800 rounded-lg transition-colors"
                      >
                          {t('login.nav.login')}
                      </button>
                      <button 
                          onClick={openLogin}
                          className="px-4 py-2 text-sm font-medium bg-indigo-600 hover:bg-indigo-700 text-white rounded-lg shadow-sm transition-all hover:shadow-indigo-500/25 active:scale-95"
                      >
                          {t('login.nav.getStarted')}
                      </button>
                  </div>
              </div>
          </div>
      </nav>

      {/* Hero Section */}
      <div className="relative pt-32 pb-20 sm:pt-40 sm:pb-24 overflow-hidden">
          {/* Background Gradients */}
          <div className="absolute top-0 left-1/2 -translate-x-1/2 w-full h-full z-0 pointer-events-none">
              <div className="absolute top-20 left-1/4 w-96 h-96 bg-indigo-500/20 rounded-full blur-3xl opacity-50 mix-blend-multiply dark:mix-blend-screen animate-pulse-fast"></div>
              <div className="absolute top-40 right-1/4 w-96 h-96 bg-violet-500/20 rounded-full blur-3xl opacity-50 mix-blend-multiply dark:mix-blend-screen" style={{ animationDelay: '1s' }}></div>
          </div>

          <div className="relative z-10 max-w-7xl mx-auto px-4 sm:px-6 lg:px-8 text-center">
              <div className="inline-flex items-center px-3 py-1 rounded-full border border-indigo-200 dark:border-indigo-800 bg-indigo-50 dark:bg-indigo-900/30 text-indigo-700 dark:text-indigo-300 text-xs font-medium mb-6 animate-fade-in">
                  <span className="flex h-2 w-2 rounded-full bg-indigo-600 dark:bg-indigo-400 mr-2"></span>
                  {t('login.hero.tagline', { version: metadata.version })}
              </div>
              <h1 className="text-5xl sm:text-7xl font-extrabold tracking-tight text-slate-900 dark:text-white mb-8 animate-slide-up">
                  {t('login.hero.titleLine1')} <br className="hidden sm:block" />
                  <span className="text-transparent bg-clip-text bg-gradient-to-r from-indigo-600 via-violet-600 to-indigo-600">
                    {t('login.hero.titleLine2')}
                  </span>
                  {t('login.hero.titleLine3')}
              </h1>
              <p className="max-w-2xl mx-auto text-xl text-slate-600 dark:text-slate-300 mb-10 animate-slide-up" style={{ animationDelay: '0.1s' }}>
                  {t('login.hero.subtitle')}
              </p>
              <div className="flex justify-center gap-4 animate-slide-up" style={{ animationDelay: '0.2s' }}>
                  <button 
                      onClick={openLogin}
                      className="group px-8 py-4 bg-indigo-600 hover:bg-indigo-700 text-white rounded-xl font-semibold text-lg shadow-xl shadow-indigo-500/30 transition-all hover:-translate-y-1 flex items-center"
                  >
                      {t('login.hero.startBtn')}
                      <ArrowRight className="ml-2 group-hover:translate-x-1 transition-transform" size={20} />
                  </button>
                  <a 
                      href="https://github.com/jojomini1231-cloud/Lumina/blob/master/README.md"
                      target="_blank"
                      rel="noreferrer"
                      className="px-8 py-4 bg-white dark:bg-slate-800 border border-slate-200 dark:border-slate-700 hover:bg-slate-50 dark:hover:bg-slate-700 text-slate-900 dark:text-white rounded-xl font-semibold text-lg shadow-sm transition-all hover:-translate-y-1"
                  >
                      {t('login.hero.docBtn')}
                  </a>
              </div>
          </div>
      </div>

      {/* Features Grid */}
      <div className="py-24 bg-white dark:bg-slate-900 border-t border-slate-100 dark:border-slate-800">
          <div className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8">
              <div className="text-center mb-16">
                  <h2 className="text-3xl font-bold text-slate-900 dark:text-white sm:text-4xl">{t('login.features.title')}</h2>
                  <p className="mt-4 text-lg text-slate-500 dark:text-slate-400">{t('login.features.subtitle')}</p>
              </div>
              <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-8">
                  {features.map((feature, idx) => (
                      <div key={idx} className="p-6 rounded-2xl bg-slate-50 dark:bg-slate-800/50 border border-slate-100 dark:border-slate-700/50 hover:border-indigo-200 dark:hover:border-indigo-500/30 transition-colors group">
                          <div className="w-12 h-12 bg-white dark:bg-slate-800 rounded-xl flex items-center justify-center shadow-sm mb-4 group-hover:scale-110 transition-transform duration-300">
                              {feature.icon}
                          </div>
                          <h3 className="text-xl font-bold text-slate-900 dark:text-white mb-2">{feature.title}</h3>
                          <p className="text-slate-500 dark:text-slate-400 leading-relaxed">
                              {feature.desc}
                          </p>
                      </div>
                  ))}
              </div>
          </div>
      </div>

      {/* Footer */}
      <footer className="bg-slate-50 dark:bg-slate-950 py-12 border-t border-slate-200 dark:border-slate-800">
          <div className="max-w-7xl mx-auto px-4 text-center">
              <p className="text-slate-500 dark:text-slate-400 mb-4">
                  {t('login.footer.text')}
              </p>
              <div className="text-sm text-slate-400 dark:text-slate-600">
                  &copy; {new Date().getFullYear()} Lumina Gateway. {t('login.footer.rights')}
              </div>
          </div>
      </footer>

      {/* Login Modal */}
      {isModalOpen && (
          <div className="fixed inset-0 z-50 flex items-center justify-center p-4">
              {/* Backdrop */}
              <div 
                  className="absolute inset-0 bg-slate-900/60 backdrop-blur-sm transition-opacity"
                  onClick={() => setIsModalOpen(false)}
              ></div>

              {/* Modal Content */}
              <div className="relative w-full max-w-md bg-white dark:bg-slate-900 rounded-2xl shadow-2xl overflow-hidden border border-slate-200 dark:border-slate-700 animate-in zoom-in-95 duration-200">
                  <div className="absolute top-0 left-0 w-full h-1.5 bg-gradient-to-r from-indigo-500 to-violet-500"></div>
                  <button 
                      onClick={() => setIsModalOpen(false)}
                      className="absolute top-4 right-4 text-slate-400 hover:text-slate-600 dark:hover:text-slate-300 transition-colors"
                  >
                      <X size={20} />
                  </button>

                  <div className="p-8">
                      <div className="flex flex-col items-center mb-8">
                          <div className="w-12 h-12 bg-indigo-100 dark:bg-indigo-900/30 text-indigo-600 dark:text-indigo-400 rounded-xl flex items-center justify-center mb-4">
                              <Zap size={24} fill="currentColor" />
                          </div>
                          <h2 className="text-2xl font-bold text-slate-900 dark:text-white">{t('login.modal.welcome')}</h2>
                          <p className="text-slate-500 dark:text-slate-400 text-sm mt-1">{t('login.modal.signinDesc')}</p>
                      </div>

                      <form className="space-y-5" onSubmit={handleSubmit}>
                          {error && (
                              <div className="bg-red-50 dark:bg-red-900/20 border border-red-200 dark:border-red-800 rounded-lg p-3 flex items-start gap-3 animate-in slide-in-from-top-2">
                                  <AlertCircle className="text-red-500 mt-0.5 flex-shrink-0" size={16} />
                                  <span className="text-sm text-red-700 dark:text-red-300 leading-snug">{error}</span>
                              </div>
                          )}

                          <div>
                              <label className="block text-sm font-medium text-slate-700 dark:text-slate-300 mb-1.5">{t('login.modal.username')}</label>
                              <input
                                  type="text"
                                  required
                                  value={username}
                                  onChange={(e) => setUsername(e.target.value)}
                                  className="block w-full px-4 py-2.5 bg-slate-50 dark:bg-slate-800 border border-slate-300 dark:border-slate-700 rounded-xl focus:ring-2 focus:ring-indigo-500 focus:border-indigo-500 dark:text-white transition-all"
                                  placeholder={t('login.modal.placeholderUser')}
                              />
                          </div>

                          <div>
                              <label className="block text-sm font-medium text-slate-700 dark:text-slate-300 mb-1.5">{t('login.modal.password')}</label>
                              <input
                                  type="password"
                                  required
                                  value={password}
                                  onChange={(e) => setPassword(e.target.value)}
                                  className="block w-full px-4 py-2.5 bg-slate-50 dark:bg-slate-800 border border-slate-300 dark:border-slate-700 rounded-xl focus:ring-2 focus:ring-indigo-500 focus:border-indigo-500 dark:text-white transition-all"
                                  placeholder={t('login.modal.placeholderPass')}
                              />
                          </div>

                          <button
                              type="submit"
                              disabled={isLoading}
                              className="w-full flex justify-center items-center py-3 px-4 bg-indigo-600 hover:bg-indigo-700 text-white font-semibold rounded-xl shadow-lg shadow-indigo-500/20 transition-all disabled:opacity-70 disabled:cursor-not-allowed hover:-translate-y-0.5"
                          >
                              {isLoading ? (
                                  <>
                                      <Loader2 className="animate-spin -ml-1 mr-2 h-5 w-5" />
                                      {t('login.modal.authenticating')}
                                  </>
                              ) : (
                                  t('login.modal.signinBtn')
                              )}
                          </button>
                      </form>
                      
                      <div className="mt-6 text-center">
                          <p className="text-xs text-slate-400 dark:text-slate-500">
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