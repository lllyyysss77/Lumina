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
    <div className="flex h-screen bg-slate-50 dark:bg-slate-950 text-slate-900 dark:text-slate-50 overflow-hidden transition-colors duration-200">
      {/* Sidebar - Desktop */}
      <aside className="hidden md:flex flex-col w-64 bg-white dark:bg-slate-900 border-r border-slate-200 dark:border-slate-800 shadow-sm flex-shrink-0 transition-colors">
        <div className="p-6 flex items-center space-x-3 border-b border-slate-100 dark:border-slate-800">
          <div className="w-8 h-8 bg-indigo-600 rounded-lg flex items-center justify-center text-white">
            <Zap size={20} fill="currentColor" />
          </div>
          <span className="text-xl font-bold bg-clip-text text-transparent bg-gradient-to-r from-indigo-600 to-violet-600 dark:from-indigo-400 dark:to-violet-400">
            Lumina
          </span>
        </div>

        <nav className="flex-1 p-4 space-y-1">
          {navItems.map((item) => (
            <button
              key={item.id}
              onClick={() => onChangeView(item.id)}
              className={`flex items-center w-full px-3 py-2.5 rounded-lg text-sm font-medium transition-colors ${
                currentView === item.id
                  ? 'bg-indigo-50 text-indigo-700 dark:bg-indigo-900/30 dark:text-indigo-300'
                  : 'text-slate-600 hover:bg-slate-50 hover:text-slate-900 dark:text-slate-400 dark:hover:bg-slate-800 dark:hover:text-slate-200'
              }`}
            >
              <item.icon size={18} className="mr-3" />
              {item.label}
            </button>
          ))}
        </nav>

        <div className="p-4 border-t border-slate-100 dark:border-slate-800">
            <div className="flex items-center justify-between p-2 rounded-lg hover:bg-slate-50 dark:hover:bg-slate-800 transition-colors border border-transparent hover:border-slate-200 dark:hover:border-slate-700">
                <div className="flex items-center space-x-3">
                    <div className="w-8 h-8 rounded-full bg-indigo-100 dark:bg-indigo-900/50 text-indigo-600 dark:text-indigo-400 flex items-center justify-center text-sm font-bold">
                        {user?.username?.charAt(0).toUpperCase() || 'A'}
                    </div>
                    <div className="flex flex-col overflow-hidden">
                        <span className="text-sm font-medium text-slate-700 dark:text-slate-300 truncate max-w-[80px]">{user?.username || 'Admin'}</span>
                        <span className="text-[10px] text-slate-500 dark:text-slate-400">v0.2.3</span>
                    </div>
                </div>
                <button 
                    onClick={() => logout()} 
                    className="p-1.5 text-slate-400 hover:text-red-600 hover:bg-red-50 dark:hover:bg-red-900/20 rounded-md transition-colors"
                    title={t('common.logout')}
                >
                    <LogOut size={18} />
                </button>
            </div>
        </div>
      </aside>

      {/* Mobile Sidebar Overlay */}
      {isMobileMenuOpen && (
        <div className="fixed inset-0 z-40 bg-slate-900/50 md:hidden backdrop-blur-sm" onClick={toggleMobileMenu} />
      )}

      {/* Sidebar - Mobile */}
      <aside
        className={`fixed inset-y-0 left-0 z-50 w-64 bg-white dark:bg-slate-900 transform transition-transform duration-200 ease-in-out md:hidden flex flex-col shadow-xl ${
          isMobileMenuOpen ? 'translate-x-0' : '-translate-x-full'
        }`}
      >
        <div className="flex items-center justify-between p-6 border-b border-slate-100 dark:border-slate-800">
           <div className="flex items-center space-x-3">
             <div className="w-8 h-8 bg-indigo-600 rounded-lg flex items-center justify-center text-white">
                <Zap size={20} fill="currentColor" />
              </div>
            <span className="text-xl font-bold text-slate-900 dark:text-white">Lumina</span>
          </div>
          <button onClick={toggleMobileMenu} className="text-slate-500 dark:text-slate-400">
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
              className={`flex items-center w-full px-4 py-3 rounded-lg text-sm font-medium ${
                currentView === item.id
                  ? 'bg-indigo-50 text-indigo-700 dark:bg-indigo-900/30 dark:text-indigo-300'
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
                className="flex items-center w-full px-4 py-3 rounded-lg text-sm font-medium text-red-600 hover:bg-red-50 dark:hover:bg-red-900/20"
            >
                <LogOut size={20} className="mr-3" />
                {t('common.logout')}
            </button>
        </div>
      </aside>

      {/* Main Content Area */}
      <main className="flex-1 flex flex-col min-w-0 overflow-hidden">
        {/* Mobile Header */}
        <header className="md:hidden flex items-center justify-between p-4 bg-white dark:bg-slate-900 border-b border-slate-200 dark:border-slate-800">
          <button onClick={toggleMobileMenu} className="text-slate-500 dark:text-slate-400">
            <Menu size={24} />
          </button>
          <span className="font-semibold text-slate-900 dark:text-white">
            {navItems.find((i) => i.id === currentView)?.label}
          </span>
          <div className="w-6" /> {/* Spacer */}
        </header>

        {/* Scrollable Content */}
        <div className="flex-1 overflow-auto p-4 md:p-6 scroll-smooth">
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