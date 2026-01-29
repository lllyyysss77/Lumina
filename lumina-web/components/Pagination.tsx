import React from 'react';
import { ChevronLeft, ChevronRight, ChevronsLeft, ChevronsRight } from 'lucide-react';

interface PaginationProps {
  current: number;
  size: number;
  total: number;
  onChange: (page: number, size: number) => void;
  className?: string;
}

export const Pagination: React.FC<PaginationProps> = ({ 
  current, 
  size, 
  total, 
  onChange,
  className = ''
}) => {
  const totalPages = Math.ceil(total / size) || 1;

  const handlePageChange = (page: number) => {
    if (page >= 1 && page <= totalPages && page !== current) {
      onChange(page, size);
    }
  };

  // Generate page numbers to display (e.g., 1 ... 4 5 6 ... 10)
  const getPageNumbers = () => {
    const delta = 1; // Number of pages to show on each side of current
    const range = [];
    const rangeWithDots = [];

    for (let i = 1; i <= totalPages; i++) {
      if (i === 1 || i === totalPages || (i >= current - delta && i <= current + delta)) {
        range.push(i);
      }
    }

    let l;
    for (let i of range) {
      if (l) {
        if (i - l === 2) {
          rangeWithDots.push(l + 1);
        } else if (i - l !== 1) {
          rangeWithDots.push('...');
        }
      }
      rangeWithDots.push(i);
      l = i;
    }

    return rangeWithDots;
  };

  return (
    <div className={`flex flex-col sm:flex-row items-center justify-between gap-4 py-4 ${className}`}>
      {/* Info Text */}
      <div className="text-sm text-slate-500 dark:text-slate-400">
        Showing <span className="font-medium text-slate-900 dark:text-slate-200">{Math.min((current - 1) * size + 1, total)}</span> to <span className="font-medium text-slate-900 dark:text-slate-200">{Math.min(current * size, total)}</span> of <span className="font-medium text-slate-900 dark:text-slate-200">{total}</span> entries
      </div>

      <div className="flex items-center gap-2">
        {/* Pagination Controls */}
        <nav className="isolate inline-flex -space-x-px rounded-md shadow-sm bg-white dark:bg-slate-800" aria-label="Pagination">
          <button
            onClick={() => handlePageChange(1)}
            disabled={current === 1}
            className="relative inline-flex items-center rounded-l-md px-2 py-2 text-slate-400 ring-1 ring-inset ring-slate-300 dark:ring-slate-700 hover:bg-slate-50 dark:hover:bg-slate-700 focus:z-20 focus:outline-offset-0 disabled:opacity-50 disabled:cursor-not-allowed"
          >
            <span className="sr-only">First</span>
            <ChevronsLeft className="h-4 w-4" aria-hidden="true" />
          </button>
          <button
            onClick={() => handlePageChange(current - 1)}
            disabled={current === 1}
            className="relative inline-flex items-center px-2 py-2 text-slate-400 ring-1 ring-inset ring-slate-300 dark:ring-slate-700 hover:bg-slate-50 dark:hover:bg-slate-700 focus:z-20 focus:outline-offset-0 disabled:opacity-50 disabled:cursor-not-allowed"
          >
            <span className="sr-only">Previous</span>
            <ChevronLeft className="h-4 w-4" aria-hidden="true" />
          </button>
          
          {/* Page Numbers - Hidden on very small screens */}
          <div className="hidden sm:flex">
             {getPageNumbers().map((page, idx) => (
                <React.Fragment key={idx}>
                    {page === '...' ? (
                        <span className="relative inline-flex items-center px-4 py-2 text-sm font-semibold text-slate-700 dark:text-slate-400 ring-1 ring-inset ring-slate-300 dark:ring-slate-700 focus:outline-offset-0">
                            ...
                        </span>
                    ) : (
                        <button
                            onClick={() => handlePageChange(page as number)}
                            className={`relative inline-flex items-center px-4 py-2 text-sm font-semibold focus:z-20 focus:outline-offset-0 ${
                                page === current
                                ? 'z-10 bg-indigo-600 text-white focus-visible:outline focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-indigo-600'
                                : 'text-slate-900 dark:text-slate-200 ring-1 ring-inset ring-slate-300 dark:ring-slate-700 hover:bg-slate-50 dark:hover:bg-slate-700'
                            }`}
                        >
                            {page}
                        </button>
                    )}
                </React.Fragment>
             ))}
          </div>
          
          {/* Mobile Current Page Indicator (Visible only on small screens) */}
          <span className="relative inline-flex items-center px-4 py-2 text-sm font-semibold text-slate-700 dark:text-slate-200 ring-1 ring-inset ring-slate-300 dark:ring-slate-700 sm:hidden">
              {current} / {totalPages}
          </span>

          <button
            onClick={() => handlePageChange(current + 1)}
            disabled={current === totalPages}
            className="relative inline-flex items-center px-2 py-2 text-slate-400 ring-1 ring-inset ring-slate-300 dark:ring-slate-700 hover:bg-slate-50 dark:hover:bg-slate-700 focus:z-20 focus:outline-offset-0 disabled:opacity-50 disabled:cursor-not-allowed"
          >
            <span className="sr-only">Next</span>
            <ChevronRight className="h-4 w-4" aria-hidden="true" />
          </button>
           <button
            onClick={() => handlePageChange(totalPages)}
            disabled={current === totalPages}
            className="relative inline-flex items-center rounded-r-md px-2 py-2 text-slate-400 ring-1 ring-inset ring-slate-300 dark:ring-slate-700 hover:bg-slate-50 dark:hover:bg-slate-700 focus:z-20 focus:outline-offset-0 disabled:opacity-50 disabled:cursor-not-allowed"
          >
            <span className="sr-only">Last</span>
            <ChevronsRight className="h-4 w-4" aria-hidden="true" />
          </button>
        </nav>
      </div>
    </div>
  );
};