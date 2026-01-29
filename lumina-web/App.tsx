import React, { useState, lazy, Suspense } from 'react';
import { Layout } from './components/Layout';
import { Login } from './components/Login';
import { AuthProvider, useAuth } from './components/AuthContext';
import { ViewState } from './types';
import { LanguageProvider, useLanguage } from './components/LanguageContext';
import { ThemeProvider } from './components/ThemeContext';
import { Loader2 } from 'lucide-react';
import { FullPageLoader } from './components/Loading';

// Lazy load page components for code splitting
const Dashboard = lazy(() => import('./components/Dashboard').then(m => ({ default: m.Dashboard })));
const Providers = lazy(() => import('./components/Providers').then(m => ({ default: m.Providers })));
const Groups = lazy(() => import('./components/Groups').then(m => ({ default: m.Groups })));
const Settings = lazy(() => import('./components/Settings').then(m => ({ default: m.Settings })));
const Logs = lazy(() => import('./components/Logs').then(m => ({ default: m.Logs })));
const Pricing = lazy(() => import('./components/Pricing').then(m => ({ default: m.Pricing })));

// Page transition wrapper component
const PageTransition: React.FC<{ children: React.ReactNode; key: string }> = ({ children, key }) => (
  <div
    key={key}
    className="animate-in fade-in slide-in-from-bottom duration-300 ease-out-smooth"
  >
    {children}
  </div>
);

const AppContent: React.FC = () => {
  const [currentView, setCurrentView] = useState<ViewState>('dashboard');
  const { isAuthenticated, isLoading } = useAuth();

  if (isLoading) {
    return <FullPageLoader message="Loading..." />;
  }

  if (!isAuthenticated) {
    return <Login />;
  }

  const renderView = () => {
    switch (currentView) {
      case 'dashboard':
        return <PageTransition key="dashboard"><Dashboard /></PageTransition>;
      case 'providers':
        return <PageTransition key="providers"><Providers /></PageTransition>;
      case 'groups':
        return <PageTransition key="groups"><Groups /></PageTransition>;
      case 'pricing':
        return <PageTransition key="pricing"><Pricing /></PageTransition>;
      case 'logs':
        return <PageTransition key="logs"><Logs /></PageTransition>;
      case 'settings':
        return <PageTransition key="settings"><Settings /></PageTransition>;
      default:
        return <PageTransition key="dashboard"><Dashboard /></PageTransition>;
    }
  };

  return (
    <Layout currentView={currentView} onChangeView={setCurrentView}>
      <Suspense fallback={<FullPageLoader message="Loading page..." />}>
        {renderView()}
      </Suspense>
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