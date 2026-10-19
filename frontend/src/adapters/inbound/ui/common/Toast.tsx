import type { ReactNode } from 'react';
import { createContext, useCallback, useContext, useState } from 'react';

type ToastItem = { readonly id: number; readonly message: string };

const ToastContext = createContext<(message: string) => void>(() => undefined);

export function ToastProvider({ children }: { children: ReactNode }) {
  const [items, setItems] = useState<readonly ToastItem[]>([]);
  const show = useCallback((message: string) => {
    const id = Date.now() + Math.random();
    setItems((current) => [...current, { id, message }]);
    setTimeout(() => setItems((current) => current.filter((item) => item.id !== id)), 2600);
  }, []);
  return (
    <ToastContext value={show}>
      {children}
      <div className="toasts" aria-live="polite">
        {items.map((item) => (
          <div key={item.id} className="toast">
            {item.message}
          </div>
        ))}
      </div>
    </ToastContext>
  );
}

export function useToast(): (message: string) => void {
  return useContext(ToastContext);
}
