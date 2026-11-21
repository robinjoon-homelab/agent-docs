import { useCallback, useRef, useState } from 'react';
import type { Block } from '../../../../../domain/document/block';
export function useFlowUndo(initial: { past: Block[][]; future: Block[][] } = { past: [], future: [] }) {
  const past = useRef<Block[][]>(initial.past),
    future = useRef<Block[][]>(initial.future),
    last = useRef<{ key: string; at: number } | null>(null);
  const [sizes, setSizes] = useState({ undo: initial.past.length, redo: initial.future.length });
  const record = useCallback((blocks: readonly Block[], key?: string) => {
    const now = Date.now();
    if (!key || last.current?.key !== key || now - last.current.at > 1200)
      past.current = [...past.current, structuredClone([...blocks])].slice(-60);
    last.current = key ? { key, at: now } : null;
    future.current = [];
    setSizes({ undo: past.current.length, redo: 0 });
  }, []);
  const travel = useCallback((blocks: readonly Block[], back: boolean) => {
    const source = back ? past : future,
      dest = back ? future : past;
    const next = source.current.pop();
    if (next) {
      dest.current.push(structuredClone([...blocks]));
      last.current = null;
      setSizes({ undo: past.current.length, redo: future.current.length });
    }
    return next;
  }, []);
  return { record, travel, ...sizes };
}
