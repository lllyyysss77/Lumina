import React, { createContext, useContext, useState, useCallback, ReactNode } from 'react';
import { CheckCircle2, AlertTriangle, Activity, AlertCircle } from 'lucide-react';

type ToastType = 'success' | 'error' | 'info';

interface ToastState {
    show: boolean;
    message: string;
    type: ToastType;
}

interface ToastContextType {
    showToast: (message: string, type?: ToastType) => void;
}

const ToastContext = createContext<ToastContextType | undefined>(undefined);

export const ToastProvider: React.FC<{ children: ReactNode }> = ({ children }) => {
    const [toast, setToast] = useState<ToastState>({ show: false, message: '', type: 'success' });

    const showToast = useCallback((message: string, type: ToastType = 'success') => {
        setToast({ show: true, message, type });
        setTimeout(() => setToast(prev => ({ ...prev, show: false })), 3000);
    }, []);

    const getIcon = () => {
        switch (toast.type) {
            case 'success': return <CheckCircle2 size={18} className="mr-2" />;
            case 'error': return <AlertCircle size={18} className="mr-2" />;
            case 'info': return <Activity size={18} className="mr-2" />;
        }
    };

    const getStyle = () => {
        switch (toast.type) {
            case 'success': return 'bg-white/90 border-green-200 text-green-700 dark:bg-slate-800/90 dark:border-green-900 dark:text-green-400';
            case 'error': return 'bg-white/90 border-red-200 text-red-700 dark:bg-slate-800/90 dark:border-red-900 dark:text-red-400';
            case 'info': return 'bg-white/90 border-blue-200 text-blue-700 dark:bg-slate-800/90 dark:border-blue-900 dark:text-blue-400';
        }
    };

    return (
        <ToastContext.Provider value={{ showToast }}>
            {children}
            {toast.show && (
                <div className={`fixed top-4 right-4 z-[100] px-4 py-3 rounded-xl shadow-lg border flex items-center animate-in slide-in-from-right duration-300 backdrop-blur-md ${getStyle()}`}>
                    {getIcon()}
                    <span className="text-sm font-medium">{toast.message}</span>
                </div>
            )}
        </ToastContext.Provider>
    );
};

export const useToast = () => {
    const context = useContext(ToastContext);
    if (!context) {
        throw new Error('useToast must be used within a ToastProvider');
    }
    return context;
};
