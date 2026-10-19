import { useEffect, useState } from 'react';
import { useServices } from '../../../../config/ServicesContext';

/** Current time from the app clock, refreshed every `intervalMs` so relative times stay fresh. */
export function useNow(intervalMs = 60_000): Date {
  const { clock } = useServices();
  const [now, setNow] = useState(() => clock.now());
  useEffect(() => {
    const timer = setInterval(() => setNow(clock.now()), intervalMs);
    return () => clearInterval(timer);
  }, [clock, intervalMs]);
  return now;
}
