import React, { ReactNode } from 'react';

interface AnimationProps {
  children: ReactNode;
  className?: string;
  delay?: number; // ms
}

export const FadeIn: React.FC<AnimationProps> = ({ children, className = '', delay = 0 }) => {
  return (
    <div 
      className={`animate-fade-in ${className}`}
      style={{ animationDelay: `${delay}ms`, animationFillMode: 'both' }}
    >
      {children}
    </div>
  );
};

export const SlideInItem: React.FC<AnimationProps & { index?: number }> = ({ children, className = '', index = 0, delay }) => {
  // Base delay + staggered index delay
  const finalDelay = delay !== undefined ? delay : index * 50; 
  
  return (
    <div 
      className={`opacity-0 animate-slide-up ${className}`}
      style={{ animationDelay: `${finalDelay}ms`, animationFillMode: 'forwards' }}
    >
      {children}
    </div>
  );
};

export const PageTransition: React.FC<AnimationProps> = ({ children, className = '' }) => {
  return (
    <div className={`animate-fade-in ${className}`}>
      {children}
    </div>
  );
};