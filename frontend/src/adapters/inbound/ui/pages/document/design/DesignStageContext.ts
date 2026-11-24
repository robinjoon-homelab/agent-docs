import { createContext } from 'react';

/** Keep diagnostic controls outside the measured device viewport. */
export const DesignStageContext = createContext<{ diagnostics: HTMLElement | null; editing: boolean } | null>(
  null,
);
