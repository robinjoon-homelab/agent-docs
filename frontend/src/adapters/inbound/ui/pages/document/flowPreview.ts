/** Explicit dev:fake fixtures reproduce the supplied artboards without changing production behavior. */
export type FlowPreview = {
  screen: string;
  selection: string[];
  hover: string | null;
  expanded: string[];
  mode: string;
  tab: string;
  collapsed: boolean;
  markers: boolean;
  zoom: string;
  history: string[];
  confirm: string | null;
  snap: string | null;
  filter: string;
  active: string | null;
  suggest: string | null;
  guides: { axis: 'x' | 'y'; value: number }[];
  marquee: { x: number; y: number; w: number; h: number } | null;
  scrolled: boolean;
  drag: string | null;
  tall: boolean;
  blank: boolean;
  redo: boolean;
  saving: boolean;
};
export function flowPreview(): Partial<FlowPreview> {
  if (import.meta.env.VITE_FAKE_API !== 'true' || typeof window === 'undefined') return {};
  const raw = new URLSearchParams(window.location.search).get('flowUi');
  if (!raw) return {};
  try {
    const value: unknown = JSON.parse(raw);
    return value !== null && typeof value === 'object' ? value : {};
  } catch {
    return {};
  }
}
