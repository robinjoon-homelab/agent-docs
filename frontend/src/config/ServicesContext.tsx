import type { ReactNode } from 'react';
import { createContext, useContext } from 'react';
import type { Services } from './services';

export type { Services } from './services';

const ServicesContext = createContext<Services | null>(null);

export function ServicesProvider({ services, children }: { services: Services; children: ReactNode }) {
  return <ServicesContext value={services}>{children}</ServicesContext>;
}

/** The only door from the UI into the application layer. */
export function useServices(): Services {
  const services = useContext(ServicesContext);
  if (services === null) throw new Error('useServices must be used inside ServicesProvider');
  return services;
}
