import { useEffect } from 'react';

export function usePageTitle(title: string | null): void {
  useEffect(() => {
    document.title = title === null || title === '' ? 'Agent Docs' : `${title} · Agent Docs`;
  }, [title]);
}
