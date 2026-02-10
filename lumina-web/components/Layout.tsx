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
  LogOut,
  ChevronRight
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
    <div className="flex h-screen bg-[#FAFAFA] dark:bg-[#0d0d0d] text-gray-900 dark:text-gray-50 overflow-hidden transition-colors duration-200">
      
      {/* Subtle Noise/Gradient Texture (Very faint) */}
      <div className="fixed inset-0 z-0 pointer-events-none opacity-40 dark:opacity-20">
         {/* Removed the heavy blobs. Replaced with a very subtle top gradient */}
         <div className="absolute top-0 left-0 w-full h-64 bg-gradient-to-b from-gray-100/50 to-transparent dark:from-gray-800/10"></div>
      </div>

      {/* Sidebar - Desktop (Notion/Raycast Style) */}
      <aside className="hidden md:flex flex-col w-64 bg-[#FAFAFA] dark:bg-[#0d0d0d] border-r border-gray-200 dark:border-gray-800 flex-shrink-0 z-20">
        {/* Brand Area */}
        <div className="px-5 py-6 flex items-center space-x-3 mb-2">
          <div className="w-8 h-8 bg-gray-900 dark:bg-white rounded-lg flex items-center justify-center text-white dark:text-black shadow-sm">
            <Zap size={16} fill="currentColor" />
          </div>
          <div className="flex flex-col">
              <span className="text-sm font-bold text-gray-900 dark:text-white leading-tight tracking-tight">
                Lumina
              </span>
              <span className="text-[10px] text-gray-500 dark:text-gray-500 font-medium">Gateway v{metadata.version}</span>
          </div>
        </div>

        {/* Navigation */}
        <nav className="flex-1 px-3 space-y-1">
          <div className="text-[10px] font-semibold text-gray-400 dark:text-gray-600 uppercase tracking-wider px-3 mb-2 mt-4">
            Menu
          </div>
          {navItems.map((item) => {
            const isActive = currentView === item.id;
            return (
              <button
                key={item.id}
                onClick={() => onChangeView(item.id)}
                className={`group flex items-center w-full px-3 py-2 rounded-lg text-sm font-medium transition-all duration-200 ease-out ${
                  isActive
                    ? 'bg-gray-200/60 dark:bg-gray-800 text-gray-900 dark:text-white'
                    : 'text-gray-500 hover:bg-gray-100 dark:text-gray-400 dark:hover:bg-gray-800/50 hover:text-gray-900 dark:hover:text-gray-200'
                }`}
              >
                <item.icon 
                    size={18} 
                    strokeWidth={isActive ? 2.5 : 2}
                    className={`mr-3 transition-colors ${isActive ? 'text-gray-900 dark:text-white' : 'text-gray-400 group-hover:text-gray-600 dark:text-gray-500 dark:group-hover:text-gray-300'}`} 
                />
                {item.label}
                {isActive && <div className="ml-auto w-1.5 h-1.5 rounded-full bg-gray-900 dark:bg-white" />}
              </button>
            )
          })}
        </nav>

        {/* User Profile */}
        <div className="p-3 mt-auto">
            <div className="flex items-center justify-between p-2 rounded-xl hover:bg-gray-100 dark:hover:bg-gray-800 transition-colors duration-200 border border-transparent hover:border-gray-200 dark:hover:border-gray-700">
                <div className="flex items-center space-x-3 overflow-hidden">
                    <div className="w-8 h-8 rounded-full bg-gray-200 dark:bg-gray-700 flex items-center justify-center text-xs font-bold text-gray-600 dark:text-gray-300">
                        {user?.username?.charAt(0).toUpperCase() || 'A'}
                    </div>
                    <div className="flex flex-col min-w-0">
                        <span className="text-xs font-semibold text-gray-700 dark:text-gray-200 truncate">{user?.username || 'Admin'}</span>
                        <span className="text-[10px] text-gray-400 dark:text-gray-500 truncate">Pro Plan</span>
                    </div>
                </div>
                <button 
                    onClick={() => logout()} 
                    className="p-1.5 text-gray-400 hover:text-gray-700 dark:hover:text-gray-200 transition-colors"
                    title={t('common.logout')}
                >
                    <LogOut size={16} />
                </button>
            </div>
        </div>
      </aside>

      {/* Mobile Sidebar Overlay */}
      {isMobileMenuOpen && (
        <div className="fixed inset-0 z-40 bg-black/20 backdrop-blur-sm md:hidden" onClick={toggleMobileMenu} />
      )}

      {/* Sidebar - Mobile */}
      <aside
        className={`fixed inset-y-0 left-0 z-50 w-72 bg-white dark:bg-[#1a1a1a] shadow-float transform transition-transform duration-300 cubic-bezier(0.16, 1, 0.3, 1) md:hidden flex flex-col ${
          isMobileMenuOpen ? 'translate-x-0' : '-translate-x-full'
        }`}
      >
        <div className="flex items-center justify-between p-5 border-b border-gray-100 dark:border-gray-800">
           <div className="flex items-center space-x-3">
             <div className="w-8 h-8 bg-black dark:bg-white rounded-lg flex items-center justify-center text-white dark:text-black">
                <Zap size={18} fill="currentColor" />
              </div>
            <span className="text-lg font-bold text-gray-900 dark:text-white">Lumina</span>
          </div>
          <button onClick={toggleMobileMenu} className="text-gray-400 hover:text-gray-900 dark:hover:text-white transition-colors">
            <X size={20} />
          </button>
        </div>
        <nav className="flex-1 p-3 space-y-1 overflow-y-auto">
          {navItems.map((item) => (
            <button
              key={item.id}
              onClick={() => {
                onChangeView(item.id);
                toggleMobileMenu();
              }}
              className={`flex items-center w-full px-4 py-3 rounded-lg text-sm font-medium ${
                 currentView === item.id
                  ? 'bg-gray-100 dark:bg-gray-800 text-gray-900 dark:text-white'
                  : 'text-gray-500 dark:text-gray-400'
              }`}
            >
              <item.icon size={18} className="mr-3" />
              {item.label}
            </button>
          ))}
        </nav>
        <div className="p-4 border-t border-gray-100 dark:border-gray-800">
            <button 
                onClick={() => logout()}
                className="flex items-center w-full px-4 py-3 rounded-lg text-sm font-medium text-red-600 hover:bg-red-50 dark:hover:bg-red-900/10 transition-colors"
            >
                <LogOut size={18} className="mr-3" />
                {t('common.logout')}
            </button>
        </div>
      </aside>

      {/* Main Content Area */}
      <main className="flex-1 flex flex-col min-w-0 overflow-hidden relative z-10">
        {/* Mobile Header */}
        <header className="md:hidden flex items-center justify-between p-4 bg-white/80 dark:bg-[#0d0d0d]/80 backdrop-blur-md border-b border-gray-200 dark:border-gray-800 sticky top-0 z-30">
          <button onClick={toggleMobileMenu} className="text-gray-500 dark:text-gray-400">
            <Menu size={24} />
          </button>
          <span className="font-bold text-gray-900 dark:text-white">
            {navItems.find((i) => i.id === currentView)?.label}
          </span>
          <div className="w-6" />
        </header>

        {/* Scrollable Content */}
        <div className="flex-1 overflow-auto p-4 md:p-10 scroll-smooth custom-scrollbar">
          <div className="max-w-screen-2xl mx-auto w-full">
            <PageTransition key={currentView}>
                {children}
            </PageTransition>
          </div>
        </div>
      </main>
    </div>
  );
};