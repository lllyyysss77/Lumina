import React from 'react';

interface SkeletonProps {
  className?: string;
}

export const Skeleton: React.FC<SkeletonProps> = ({ className = '' }) => {
  return (
    <div className={`animate-pulse bg-slate-200 dark:bg-slate-700 rounded ${className}`} />
  );
};

export const DashboardSkeleton: React.FC = () => {
  return (
    <div className="space-y-6 animate-pulse">
      {/* Title */}
      <div className="space-y-2">
        <Skeleton className="h-8 w-48" />
        <Skeleton className="h-4 w-64" />
      </div>

      {/* KPI Cards */}
      <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-4 gap-6">
        {[1, 2, 3, 4].map((i) => (
          <div key={i} className="bg-white dark:bg-slate-800 rounded-xl p-6 border border-slate-100 dark:border-slate-700 h-32">
            <div className="flex justify-between items-center mb-4">
              <Skeleton className="h-10 w-10 rounded-lg" />
              <Skeleton className="h-4 w-16" />
            </div>
            <Skeleton className="h-4 w-24 mb-2" />
            <Skeleton className="h-8 w-32" />
          </div>
        ))}
      </div>

      {/* Charts */}
      <div className="grid grid-cols-1 lg:grid-cols-3 gap-6">
        <div className="lg:col-span-2 bg-white dark:bg-slate-800 p-6 rounded-xl border border-slate-100 dark:border-slate-700 h-80">
          <Skeleton className="h-6 w-32 mb-6" />
          <div className="h-60 w-full flex items-end space-x-2">
             <Skeleton className="w-full h-full rounded-b" />
          </div>
        </div>
        <div className="bg-white dark:bg-slate-800 p-6 rounded-xl border border-slate-100 dark:border-slate-700 h-80">
          <Skeleton className="h-6 w-32 mb-6" />
          <div className="space-y-3">
            {[1, 2, 3, 4, 5].map(i => (
                <div key={i} className="flex items-center space-x-2">
                    <Skeleton className="w-20 h-4" />
                    <Skeleton className="flex-1 h-4" />
                </div>
            ))}
          </div>
        </div>
      </div>

      {/* Table */}
      <div className="bg-white dark:bg-slate-800 rounded-xl border border-slate-100 dark:border-slate-700 h-64 p-6">
         <Skeleton className="h-6 w-48 mb-4" />
         <div className="space-y-3">
             {[1, 2, 3].map(i => <Skeleton key={i} className="h-10 w-full" />)}
         </div>
      </div>
    </div>
  );
};

export const CardGridSkeleton: React.FC<{ count?: number }> = ({ count = 8 }) => {
  return (
    <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 2xl:grid-cols-4 gap-6">
      {Array.from({ length: count }).map((_, i) => (
        <div key={i} className="bg-white dark:bg-slate-800 rounded-xl border border-slate-200 dark:border-slate-700 p-6 h-48 flex flex-col justify-between">
          <div className="flex justify-between items-start">
             <div className="flex gap-3">
                 <Skeleton className="w-10 h-10 rounded-lg" />
                 <div className="space-y-2">
                     <Skeleton className="w-32 h-5" />
                     <Skeleton className="w-20 h-3" />
                 </div>
             </div>
             <Skeleton className="w-8 h-8 rounded" />
          </div>
          <div className="space-y-2">
             <Skeleton className="w-full h-3" />
             <Skeleton className="w-2/3 h-3" />
          </div>
          <div className="flex justify-between items-center mt-2">
             <Skeleton className="w-16 h-4" />
             <div className="flex gap-2">
                 <Skeleton className="w-8 h-8 rounded" />
                 <Skeleton className="w-8 h-8 rounded" />
             </div>
          </div>
        </div>
      ))}
    </div>
  );
};

export const TableSkeleton: React.FC<{ rows?: number }> = ({ rows = 10 }) => {
    return (
        <div className="bg-white dark:bg-slate-800 rounded-xl shadow-sm border border-slate-200 dark:border-slate-700 overflow-hidden">
            <div className="p-4 border-b border-slate-200 dark:border-slate-700 flex justify-between">
                 <Skeleton className="w-48 h-8" />
                 <Skeleton className="w-24 h-8" />
            </div>
            <div className="p-4 space-y-4">
                {/* Header */}
                <div className="flex justify-between gap-4 mb-2">
                    <Skeleton className="w-1/6 h-4" />
                    <Skeleton className="w-1/6 h-4" />
                    <Skeleton className="w-1/6 h-4" />
                    <Skeleton className="w-1/6 h-4" />
                    <Skeleton className="w-1/6 h-4" />
                </div>
                {/* Rows */}
                {Array.from({ length: rows }).map((_, i) => (
                    <div key={i} className="flex justify-between gap-4 py-2 border-t border-slate-100 dark:border-slate-800">
                        <Skeleton className="w-1/6 h-4" />
                        <Skeleton className="w-1/6 h-4" />
                        <Skeleton className="w-1/6 h-4" />
                        <Skeleton className="w-1/6 h-4" />
                        <Skeleton className="w-1/6 h-4" />
                    </div>
                ))}
            </div>
        </div>
    );
}