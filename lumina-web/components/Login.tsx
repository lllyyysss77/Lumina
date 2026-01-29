import React, { useState } from 'react';
import { useAuth } from './AuthContext';
import { Zap, Loader2, AlertCircle } from 'lucide-react';
import { BrandLoader } from './Loading';

export const Login: React.FC = () => {
  const { login } = useAuth();
  const [username, setUsername] = useState('');
  const [password, setPassword] = useState('');
  const [isLoading, setIsLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!username || !password) {
      setError('Please enter both username and password.');
      return;
    }

    setIsLoading(true);
    setError(null);

    try {
      await login(username, password);
    } catch (err: any) {
      console.error("Login component caught error:", err);
      // Extract error message
      let msg = 'Login failed. Please check your credentials.';
      
      if (err instanceof Error) {
        if (err.message === 'Failed to fetch') {
           msg = 'Unable to connect to the server. Please check your network connection or CORS configuration.';
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

  return (
    <div className="min-h-screen bg-slate-50 flex flex-col justify-center py-12 sm:px-6 lg:px-8">
      <div className="sm:mx-auto sm:w-full sm:max-w-md">
        <div className="flex justify-center animate-in fade-in zoom-in-95 duration-500">
            <div className="w-12 h-12 bg-indigo-600 rounded-xl flex items-center justify-center text-white shadow-lg shadow-indigo-200">
                <Zap size={28} fill="currentColor" />
            </div>
        </div>
        <h2 className="mt-6 text-center text-3xl font-extrabold text-slate-900 tracking-tight animate-in fade-in slide-in-from-bottom duration-500" style={{ animationDelay: '100ms' }}>
          Lumina Gateway
        </h2>
        <p className="mt-2 text-center text-sm text-slate-600 animate-in fade-in slide-in-from-bottom duration-500" style={{ animationDelay: '200ms' }}>
          Sign in to manage your LLM aggregation
        </p>
      </div>

      <div className="mt-8 sm:mx-auto sm:w-full sm:max-w-md animate-in fade-in zoom-in-95 duration-500" style={{ animationDelay: '300ms' }}>
        <div className="bg-white py-8 px-4 shadow-xl shadow-slate-200/50 sm:rounded-xl sm:px-10 border border-slate-100">
          <form className="space-y-6" onSubmit={handleSubmit}>
            {error && (
              <div className="bg-red-50 border border-red-200 rounded-lg p-3 flex items-start">
                 <AlertCircle className="text-red-500 mt-0.5 mr-2 flex-shrink-0" size={16} />
                 <span className="text-sm text-red-700">{error}</span>
              </div>
            )}
            
            <div>
              <label htmlFor="username" className="block text-sm font-medium text-slate-700">
                Username
              </label>
              <div className="mt-1">
                <input
                  id="username"
                  name="username"
                  type="text"
                  autoComplete="username"
                  required
                  value={username}
                  onChange={(e) => setUsername(e.target.value)}
                  className="appearance-none block w-full px-3 py-2 border border-slate-300 rounded-lg shadow-sm placeholder-slate-400 focus:outline-none focus:ring-indigo-500 focus:border-indigo-500 sm:text-sm transition-colors"
                />
              </div>
            </div>

            <div>
              <label htmlFor="password" className="block text-sm font-medium text-slate-700">
                Password
              </label>
              <div className="mt-1">
                <input
                  id="password"
                  name="password"
                  type="password"
                  autoComplete="current-password"
                  required
                  value={password}
                  onChange={(e) => setPassword(e.target.value)}
                  className="appearance-none block w-full px-3 py-2 border border-slate-300 rounded-lg shadow-sm placeholder-slate-400 focus:outline-none focus:ring-indigo-500 focus:border-indigo-500 sm:text-sm transition-colors"
                />
              </div>
            </div>

            <div>
              <button
                type="submit"
                disabled={isLoading}
                className="w-full flex justify-center py-2.5 px-4 border border-transparent rounded-lg shadow-sm text-sm font-medium text-white bg-indigo-600 hover:bg-indigo-700 focus:outline-none focus:ring-2 focus:ring-offset-2 focus:ring-indigo-500 disabled:opacity-70 disabled:cursor-not-allowed transition-all"
              >
                {isLoading ? (
                  <>
                    <Loader2 className="animate-spin -ml-1 mr-2 h-4 w-4" />
                    Signing in...
                  </>
                ) : (
                  'Sign in'
                )}
              </button>
            </div>
          </form>
        </div>
        
        <p className="mt-6 text-center text-xs text-slate-400">
             &copy; {new Date().getFullYear()} Lumina LLM Gateway. All rights reserved.
        </p>
      </div>
    </div>
  );
};