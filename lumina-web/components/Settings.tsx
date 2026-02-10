import React from 'react';
import { useLanguage } from './LanguageContext';
import { useAuth } from './AuthContext';
import { SlideInItem } from './Animations';

import { LanguageSection } from './LanguageSection';
import { AccountSection } from './AccountSection';
import { SecuritySection } from './SecuritySection';
import { CircuitBreakerSection } from './CircuitBreakerSection';
import { AppearanceSection } from './AppearanceSection';

export const Settings: React.FC = () => {
    const { t } = useLanguage();
    const { user } = useAuth();

    return (
        <div className="max-w-6xl space-y-8 relative">

            <SlideInItem>
                <div>
                    <h1 className="text-3xl font-extrabold text-slate-900 dark:text-white tracking-tight">{t('settings.title')}</h1>
                    <p className="text-slate-500 dark:text-slate-400 mt-2 text-lg">{t('settings.subtitle')}</p>
                </div>
            </SlideInItem>

            <div className="space-y-6">
                <SlideInItem index={1}>
                    <LanguageSection />
                </SlideInItem>

                <SlideInItem index={2}>
                    <AccountSection />
                </SlideInItem>

                <SlideInItem index={3}>
                    <SecuritySection />
                </SlideInItem>

                <SlideInItem index={4}>
                    <CircuitBreakerSection username={user?.username || ''} />
                </SlideInItem>

                <SlideInItem index={5}>
                    <AppearanceSection />
                </SlideInItem>
            </div>
        </div>
    );
};