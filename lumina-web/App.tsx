import React, { useState } from 'react';
import { Layout } from './components/Layout';
import { Dashboard } from './components/Dashboard';
import { Providers } from './components/Providers';
import { Groups } from './components/Groups';
import { Settings } from './components/Settings';
import { Logs } from './components/Logs';
import { Pricing } from './components/Pricing';
import { Login } from './components/Login';
import { AuthProvider, useAuth } from './components/AuthContext';
import { ViewState } from './types';
import { LanguageProvider, useLanguage } from './components/LanguageContext';
import { ThemeProvider } from './components/ThemeContext';
import { Loader2 } from 'lucide-react';

const AppContent: React.FC = () => {
  const [currentView, setCurrentView] = useState<ViewState>('dashboard');
  const { isAuthenticated, isLoading } = useAuth();

  if (isLoading) {
    return (
      <div className="h-screen w-screen flex items-center justify-center bg-slate-50 dark:bg-slate-900">
        <Loader2 className="w-8 h-8 text-indigo-600 animate-spin" />
      </div>
    );
  }

  if (!isAuthenticated) {
    return <Login />;
  }

  const renderView = () => {
    switch (currentView) {
      case 'dashboard':
        return <Dashboard />;
      case 'providers':
        return <Providers />;
      case 'groups':
        return <Groups />;
      case 'pricing':
        return <Pricing />;
      case 'logs':
        return <Logs />;
      case 'settings':
        return <Settings />;
      default:
        return <Dashboard />;
    }
  };

  return (
    <Layout currentView={currentView} onChangeView={setCurrentView}>
      {renderView()}
    </Layout>
  );
};

const App: React.FC = () => {
    return (
        <LanguageProvider>
          <ThemeProvider>
            <AuthProvider>
              <AppContent />
            </AuthProvider>
          </ThemeProvider>
        </LanguageProvider>
    );
};

export default App;