import { StrictMode } from 'react';
import { createRoot } from 'react-dom/client';
import { App } from './adapters/inbound/ui/app/App';
import './adapters/inbound/ui/styles/global.css';
import { createHttpServices } from './config/httpServices';
import type { Services } from './config/services';
import { ServicesProvider } from './config/ServicesContext';

async function servicesForEnvironment(): Promise<Services> {
  if (import.meta.env.VITE_FAKE_API === 'true') {
    const { createFakeServices } = await import('./config/fakeServices');
    return createFakeServices();
  }
  return createHttpServices();
}

async function boot(): Promise<void> {
  const root = document.getElementById('root');
  if (root === null) throw new Error('#root element is missing');
  const services = await servicesForEnvironment();
  createRoot(root).render(
    <StrictMode>
      <ServicesProvider services={services}>
        <App />
      </ServicesProvider>
    </StrictMode>,
  );
}

void boot();
