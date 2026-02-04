import React, { useState } from 'react';
import { 
  LayoutDashboard, 
  Network, 
  Layers, 
  DollarSign, 
  ScrollText, 
  Settings, 
  Menu, 
  X,
  Zap,
  LogOut
} from 'lucide-react';
import { ViewState } from '../types';
import { useLanguage } from './LanguageContext';
import { useAuth } from './AuthContext';
import { PageTransition } from './Animations';
import metadata from '../constants';

interface LayoutProps {
  currentView: ViewState;
  onChangeView: (view: ViewState) => void;
  children: React.ReactNode;
}

export const Layout: React.FC<LayoutProps> = ({ currentView, onChangeView, children }) => {
  const [isMobileMenuOpen, setIsMobileMenuOpen] = useState(false);
  const { t } = useLanguage();
  const { user, logout } = useAuth();

  const navItems = [
    { id: 'dashboard' as ViewState, label: t('nav.dashboard'), icon: LayoutDashboard },
    { id: 'providers' as ViewState, label: t('nav.providers'), icon: Network },
    { id: 'groups' as ViewState, label: t('nav.groups'), icon: Layers },
    { id: 'pricing' as ViewState, label: t('nav.pricing'), icon: DollarSign },
    { id: 'logs' as ViewState, label: t('nav.logs'), icon: ScrollText },
    { id: 'settings' as ViewState, label: t('nav.settings'), icon: Settings },
  ];

  const toggleMobileMenu = () => setIsMobileMenuOpen(!isMobileMenuOpen);

  return (
    <div className="flex h-screen bg-slate-50 dark:bg-slate-950 text-slate-900 dark:text-slate-50 overflow-hidden transition-colors duration-200 relative">
      
      {/* Global Background Gradients (Matches Login Page) */}
      <div className="fixed inset-0 z-0 pointer-events-none overflow-hidden">
          <div className="absolute top-[-10%] left-[-10%] w-[50%] h-[50%] rounded-full bg-indigo-500/5 blur-[120px]" />
          <div className="absolute bottom-[-10%] right-[-10%] w-[50%] h-[50%] rounded-full bg-violet-500/5 blur-[120px]" />
          <div className="absolute top-[20%] right-[20%] w-[30%] h-[30%] rounded-full bg-blue-500/5 blur-[100px]" />
      </div>

      {/* Sidebar - Desktop */}
      <aside className="hidden md:flex flex-col w-72 bg-white/70 dark:bg-slate-900/70 backdrop-blur-xl border-r border-slate-200/60 dark:border-slate-800/60 shadow-sm flex-shrink-0 transition-all z-20">
        <div className="p-6 flex items-center space-x-3 border-b border-slate-100/50 dark:border-slate-800/50">
          <div className="w-9 h-9 bg-gradient-to-br from-indigo-600 to-violet-600 rounded-xl flex items-center justify-center text-white shadow-lg shadow-indigo-500/30">
            <Zap size={20} fill="currentColor" />
          </div>
          <div className="flex flex-col">
              <span className="text-xl font-bold bg-clip-text text-transparent bg-gradient-to-r from-slate-900 to-slate-700 dark:from-white dark:to-slate-300 leading-tight">
                Lumina
              </span>
              <span className="text-[10px] text-slate-500 dark:text-slate-400 font-mono">v{metadata.version}</span>
          </div>
        </div>

        <nav className="flex-1 p-4 space-y-2">
          {navItems.map((item) => (
            <button
              key={item.id}
              onClick={() => onChangeView(item.id)}
              className={`group flex items-center w-full px-4 py-3 rounded-xl text-sm font-medium transition-all duration-200 ${
                currentView === item.id
                  ? 'bg-gradient-to-r from-indigo-600 to-violet-600 text-white shadow-md shadow-indigo-500/25'
                  : 'text-slate-600 hover:bg-white hover:text-indigo-600 dark:text-slate-400 dark:hover:bg-slate-800/50 dark:hover:text-indigo-400 hover:shadow-sm'
              }`}
            >
              <item.icon size={18} className={`mr-3 transition-transform duration-200 ${currentView === item.id ? 'scale-110' : 'group-hover:scale-110'}`} />
              {item.label}
            </button>
          ))}
        </nav>

        <div className="p-4 border-t border-slate-100/50 dark:border-slate-800/50">
            <div className="flex items-center justify-between p-3 rounded-xl bg-white/50 dark:bg-slate-800/50 border border-slate-200/50 dark:border-slate-700/50 hover:border-indigo-200 dark:hover:border-indigo-800 transition-all duration-200">
                <div className="flex items-center space-x-3">
                    <div className="w-9 h-9 rounded-full bg-gradient-to-br from-indigo-100 to-violet-100 dark:from-indigo-900/50 dark:to-violet-900/50 text-indigo-600 dark:text-indigo-400 flex items-center justify-center text-sm font-bold shadow-inner">
                        {user?.username?.charAt(0).toUpperCase() || 'A'}
                    </div>
                    <div className="flex flex-col overflow-hidden">
                        <span className="text-sm font-bold text-slate-700 dark:text-slate-200 truncate max-w-[90px]">{user?.username || 'Admin'}</span>
                        <span className="text-[10px] text-slate-500 dark:text-slate-400 font-medium">Administrator</span>
                    </div>
                </div>
                <button 
                    onClick={() => logout()} 
                    className="p-2 text-slate-400 hover:text-red-500 hover:bg-red-50 dark:hover:bg-red-900/20 rounded-lg transition-colors"
                    title={t('common.logout')}
                >
                    <LogOut size={18} />
                </button>
            </div>
        </div>
      </aside>

      {/* Mobile Sidebar Overlay */}
      {isMobileMenuOpen && (
        <div className="fixed inset-0 z-40 bg-slate-900/60 backdrop-blur-sm md:hidden" onClick={toggleMobileMenu} />
      )}

      {/* Sidebar - Mobile */}
      <aside
        className={`fixed inset-y-0 left-0 z-50 w-72 bg-white dark:bg-slate-900 transform transition-transform duration-300 cubic-bezier(0.4, 0, 0.2, 1) md:hidden flex flex-col shadow-2xl ${
          isMobileMenuOpen ? 'translate-x-0' : '-translate-x-full'
        }`}
      >
        <div className="flex items-center justify-between p-6 border-b border-slate-100 dark:border-slate-800">
           <div className="flex items-center space-x-3">
             <div className="w-9 h-9 bg-gradient-to-br from-indigo-600 to-violet-600 rounded-xl flex items-center justify-center text-white">
                <Zap size={20} fill="currentColor" />
              </div>
            <span className="text-xl font-bold text-slate-900 dark:text-white">Lumina</span>
          </div>
          <button onClick={toggleMobileMenu} className="text-slate-500 dark:text-slate-400 hover:text-slate-800 dark:hover:text-white transition-colors">
            <X size={24} />
          </button>
        </div>
        <nav className="flex-1 p-4 space-y-2 overflow-y-auto">
          {navItems.map((item) => (
            <button
              key={item.id}
              onClick={() => {
                onChangeView(item.id);
                toggleMobileMenu();
              }}
              className={`flex items-center w-full px-4 py-3 rounded-xl text-sm font-medium ${
                 currentView === item.id
                  ? 'bg-indigo-600 text-white shadow-md shadow-indigo-500/25'
                  : 'text-slate-600 hover:bg-slate-50 dark:text-slate-400 dark:hover:bg-slate-800'
              }`}
            >
              <item.icon size={20} className="mr-3" />
              {item.label}
            </button>
          ))}
        </nav>
        <div className="p-4 border-t border-slate-100 dark:border-slate-800">
            <button 
                onClick={() => logout()}
                className="flex items-center w-full px-4 py-3 rounded-xl text-sm font-medium text-red-600 hover:bg-red-50 dark:hover:bg-red-900/20 transition-colors"
            >
                <LogOut size={20} className="mr-3" />
                {t('common.logout')}
            </button>
        </div>
      </aside>

      {/* Main Content Area */}
      <main className="flex-1 flex flex-col min-w-0 overflow-hidden relative z-10">
        {/* Mobile Header */}
        <header className="md:hidden flex items-center justify-between p-4 bg-white/80 dark:bg-slate-900/80 backdrop-blur-md border-b border-slate-200 dark:border-slate-800 sticky top-0 z-30">
          <button onClick={toggleMobileMenu} className="text-slate-500 dark:text-slate-400">
            <Menu size={24} />
          </button>
          <span className="font-bold text-slate-900 dark:text-white">
            {navItems.find((i) => i.id === currentView)?.label}
          </span>
          <div className="w-6" />
        </header>

        {/* Scrollable Content */}
        <div className="flex-1 overflow-auto p-4 md:p-8 scroll-smooth custom-scrollbar">
          <div className="max-w-[1600px] mx-auto w-full">
            <PageTransition key={currentView}>
                {children}
            </PageTransition>
          </div>
        </div>
      </main>
    </div>
  );
};