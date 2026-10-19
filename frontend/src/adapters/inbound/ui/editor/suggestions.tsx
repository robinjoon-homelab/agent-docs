import type { Editor, Range } from '@tiptap/core';
import { Extension } from '@tiptap/core';
import { PluginKey } from '@tiptap/pm/state';
import type { SuggestionKeyDownProps, SuggestionOptions, SuggestionProps } from '@tiptap/suggestion';
import { Suggestion } from '@tiptap/suggestion';
import { useSyncExternalStore } from 'react';

/*
 * Bridge between Tiptap suggestion plugins ("/", "[[", "@") and one React popup. The plugin writes
 * into a small store; the popup reads it with useSyncExternalStore.
 */

export type SuggestionItem = {
  readonly key: string;
  readonly label: string;
  readonly hint?: string;
  readonly run: (editor: Editor, range: Range) => void;
};

type View = {
  readonly open: boolean;
  readonly title: string;
  readonly items: readonly SuggestionItem[];
  readonly index: number;
  readonly rect: DOMRect | null;
  readonly loading: boolean;
};

const CLOSED: View = { open: false, title: '', items: [], index: 0, rect: null, loading: false };

export class SuggestionStore {
  private view: View = CLOSED;
  private command: ((item: SuggestionItem) => void) | null = null;
  private readonly listeners = new Set<() => void>();

  readonly subscribe = (listener: () => void): (() => void) => {
    this.listeners.add(listener);
    return () => this.listeners.delete(listener);
  };

  readonly getView = (): View => this.view;

  show(title: string, props: SuggestionProps<SuggestionItem, SuggestionItem>): void {
    this.command = props.command;
    const sameItems = this.view.open && this.view.items === props.items;
    this.update({
      open: true,
      title,
      items: props.items,
      index: sameItems ? this.view.index : 0,
      rect: props.clientRect?.() ?? null,
      loading: props.loading,
    });
  }

  close(): void {
    this.command = null;
    this.update(CLOSED);
  }

  choose(item: SuggestionItem | undefined): void {
    if (item !== undefined) this.command?.(item);
  }

  keyDown({ event }: SuggestionKeyDownProps): boolean {
    if (!this.view.open) return false;
    const count = this.view.items.length;
    if (event.key === 'ArrowDown' || event.key === 'ArrowUp') {
      if (count === 0) return true;
      const step = event.key === 'ArrowDown' ? 1 : -1;
      this.update({ ...this.view, index: (this.view.index + step + count) % count });
      return true;
    }
    if (event.key === 'Enter' || event.key === 'Tab') {
      if (count === 0) return false;
      this.choose(this.view.items[this.view.index]);
      return true;
    }
    if (event.key === 'Escape') {
      this.close();
      return true;
    }
    return false;
  }

  hover(index: number): void {
    this.update({ ...this.view, index });
  }

  private update(view: View): void {
    this.view = view;
    for (const listener of this.listeners) listener();
  }
}

export type SuggestionSource = {
  readonly name: string;
  readonly char: string;
  readonly title: string;
  readonly startOfLine?: boolean;
  readonly allowSpaces?: boolean;
  readonly allow?: SuggestionOptions['allow'];
  readonly items: (query: string) => SuggestionItem[] | Promise<SuggestionItem[]>;
};

/** One Tiptap extension per trigger character, all rendering into the shared store. */
export function suggestionExtension(source: SuggestionSource, store: SuggestionStore): Extension {
  return Extension.create({
    name: `suggestion-${source.name}`,
    addProseMirrorPlugins() {
      return [
        Suggestion<SuggestionItem, SuggestionItem>({
          editor: this.editor,
          pluginKey: new PluginKey(`suggestion-${source.name}`),
          char: source.char,
          startOfLine: source.startOfLine ?? false,
          allowSpaces: source.allowSpaces ?? false,
          allow: source.allow,
          items: ({ query }) => source.items(query),
          command: ({ editor, range, props }) => props.run(editor, range),
          render: () => ({
            onStart: (props) => store.show(source.title, props),
            onUpdate: (props) => store.show(source.title, props),
            onExit: () => store.close(),
            onKeyDown: (props) => store.keyDown(props),
          }),
        }),
      ];
    },
  });
}

export function SuggestionPopup({ store }: { store: SuggestionStore }) {
  const view = useSyncExternalStore(store.subscribe, store.getView);
  if (!view.open || view.rect === null) return null;
  const height = Math.min(320, 34 + Math.max(view.items.length, 1) * 34);
  const below = view.rect.bottom + 6;
  const top = below + height > window.innerHeight ? Math.max(8, view.rect.top - 6 - height) : below;
  const left = Math.max(8, Math.min(view.rect.left, window.innerWidth - 268));
  return (
    <div
      className="popover suggestion-popup"
      style={{ position: 'fixed', top, left, width: 260 }}
      role="listbox"
      aria-label={view.title}
    >
      <div className="suggestion-title">{view.title}</div>
      {view.items.length === 0 ? (
        <div className="suggestion-empty">{view.loading ? '찾는 중…' : '결과 없음'}</div>
      ) : (
        view.items.map((item, index) => (
          <button
            key={item.key}
            type="button"
            role="option"
            aria-selected={index === view.index}
            className="menu-item"
            onMouseDown={(event) => event.preventDefault()}
            onMouseEnter={() => store.hover(index)}
            onClick={() => store.choose(item)}
          >
            <span>{item.label}</span>
            {item.hint === undefined ? null : <span className="hint">{item.hint}</span>}
          </button>
        ))
      )}
    </div>
  );
}
