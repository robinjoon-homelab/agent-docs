import { useEffect, useRef } from 'react';

export type Hotkey = {
  readonly key: string;
  readonly mod?: boolean;
  readonly alt?: boolean;
  readonly shift?: boolean;
};

/** True for Cmd on macOS and Ctrl elsewhere. */
export function hasMod(event: { readonly metaKey: boolean; readonly ctrlKey: boolean }): boolean {
  return event.metaKey || event.ctrlKey;
}

export function matchesHotkey(event: KeyboardEvent, hotkey: Hotkey): boolean {
  // Option on macOS changes event.key (e.g. "µ"), so compare the physical key code for letters.
  const key = hotkey.key.length === 1 ? `Key${hotkey.key.toUpperCase()}` : hotkey.key;
  const pressed = hotkey.key.length === 1 ? event.code : event.key;
  return (
    pressed === key &&
    hasMod(event) === (hotkey.mod ?? false) &&
    event.altKey === (hotkey.alt ?? false) &&
    event.shiftKey === (hotkey.shift ?? false)
  );
}

export function useHotkey(hotkey: Hotkey, handler: (event: KeyboardEvent) => void): void {
  const latest = useRef(handler);
  useEffect(() => {
    latest.current = handler;
  });
  const { key, mod, alt, shift } = hotkey;
  useEffect(() => {
    const listener = (event: KeyboardEvent): void => {
      if (!matchesHotkey(event, { key, mod, alt, shift })) return;
      event.preventDefault();
      latest.current(event);
    };
    window.addEventListener('keydown', listener);
    return () => window.removeEventListener('keydown', listener);
  }, [key, mod, alt, shift]);
}
