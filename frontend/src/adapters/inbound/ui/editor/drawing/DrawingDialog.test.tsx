// @vitest-environment jsdom
import { act, cleanup, fireEvent, render, screen } from '@testing-library/react';
import { useEffect, type ReactNode } from 'react';
import { afterEach, describe, expect, it, vi } from 'vitest';
import type { DrawingSaveRequest, DrawingSaveResult } from './drawingTypes';

const mockCanvasState = {
  elements: [{ id: 'e1', version: 1 }] as { id: string; version: number }[],
  onChange: undefined as
    ((elements: readonly unknown[], appState: { viewBackgroundColor: string }) => void) | undefined,
};

vi.mock('@excalidraw/excalidraw', () => {
  const Excalidraw = ({
    excalidrawAPI,
    viewModeEnabled,
    onChange,
    children,
  }: {
    excalidrawAPI?: (api: unknown) => void;
    viewModeEnabled?: boolean;
    onChange?: (elements: readonly unknown[], appState: { viewBackgroundColor: string }) => void;
    children?: ReactNode;
  }) => {
    useEffect(() => {
      mockCanvasState.onChange = onChange;
      excalidrawAPI?.({
        getSceneElements: () => mockCanvasState.elements,
        getSceneElementsIncludingDeleted: () => mockCanvasState.elements,
        getAppState: () => ({ viewBackgroundColor: '#ffffff' }),
        getFiles: () => ({}),
      });
    }, [excalidrawAPI, onChange]);

    return (
      <div data-testid="excalidraw-canvas" data-view-mode={String(Boolean(viewModeEnabled))}>
        <button
          type="button"
          onClick={() => {
            if (viewModeEnabled) return;
            mockCanvasState.elements = [{ id: 'e1', version: 2 }];
            onChange?.(mockCanvasState.elements, { viewBackgroundColor: '#ffffff' });
          }}
        >
          모의 그리기
        </button>
        {children}
      </div>
    );
  };
  const MainMenu = ({ children }: { children?: ReactNode }) => <div>{children}</div>;
  MainMenu.DefaultItems = {
    Export: () => null,
    SearchMenu: () => null,
    ClearCanvas: () => null,
    ChangeCanvasBackground: () => null,
  };
  return {
    Excalidraw,
    MainMenu,
    MIME_TYPES: { excalidraw: 'application/vnd.excalidraw+json' },
    hashElementsVersion: (elements: readonly { version?: number }[]) =>
      elements.map((item) => String(item.version ?? 0)).join(','),
    loadFromBlob: () => Promise.resolve({ elements: [], appState: {}, files: {} }),
    serializeAsJSON: (elements: readonly unknown[]) => JSON.stringify({ type: 'excalidraw', elements }),
    exportToSvg: () => {
      const svg = document.createElementNS('http://www.w3.org/2000/svg', 'svg');
      return Promise.resolve(svg);
    },
  };
});

import DrawingDialog from './DrawingDialog';

afterEach(() => {
  cleanup();
  mockCanvasState.elements = [{ id: 'e1', version: 1 }];
  mockCanvasState.onChange = undefined;
});

function installMediaQuery(initialNarrow: boolean) {
  let matches = initialNarrow;
  const listeners = new Set<() => void>();
  Object.defineProperty(window, 'matchMedia', {
    configurable: true,
    value: () => ({
      get matches() {
        return matches;
      },
      addEventListener: (_type: string, listener: () => void) => {
        listeners.add(listener);
      },
      removeEventListener: (_type: string, listener: () => void) => {
        listeners.delete(listener);
      },
    }),
  });
  return (nextNarrow: boolean) => {
    matches = nextNarrow;
    for (const listener of listeners) listener();
  };
}

describe('DrawingDialog viewport and permission draft retention', () => {
  it('switches to read-only and disables saving below 1024px while retaining the draft until wide again', async () => {
    const setNarrow = installMediaQuery(false);
    let draftOpen = false;
    const savedSnapshots: DrawingSaveRequest[] = [];
    const onSave = (request: DrawingSaveRequest): Promise<DrawingSaveResult> => {
      savedSnapshots.push(request);
      return Promise.resolve({
        kind: 'saved',
        blockId: 'draw000001',
        pair: { svgAssetId: 'svg-2', sceneAssetId: 'scene-2' },
      });
    };

    render(
      <DrawingDialog
        target={{ kind: 'new', key: 'k1', anchorBlockId: null }}
        editable
        theme="light"
        loadScene={() => Promise.resolve(null)}
        onMeaningfulInput={() => undefined}
        reserveInput={() => null}
        onSave={onSave}
        onClose={() => undefined}
        onDraftChange={(dirty) => {
          draftOpen = dirty;
        }}
      />,
    );

    const canvas = await screen.findByTestId('excalidraw-canvas');
    // Initial normalize event establishes the baseline without dirtying.
    act(() => {
      mockCanvasState.onChange?.(mockCanvasState.elements, { viewBackgroundColor: '#ffffff' });
    });
    expect(draftOpen).toBe(false);

    fireEvent.click(screen.getByRole('button', { name: '모의 그리기' }));
    expect(draftOpen).toBe(true);
    expect(canvas.getAttribute('data-view-mode')).toBe('false');

    act(() => {
      setNarrow(true);
    });
    expect(canvas.getAttribute('data-view-mode')).toBe('true');
    expect(screen.getByRole<HTMLButtonElement>('button', { name: '저장' }).disabled).toBe(true);
    expect(screen.getByRole<HTMLButtonElement>('button', { name: '장면 파일로 내려받기' }).disabled).toBe(
      false,
    );
    expect(screen.getByRole('alert').textContent).toContain('1023px');
    expect(draftOpen).toBe(true);

    act(() => {
      setNarrow(false);
    });
    expect(canvas.getAttribute('data-view-mode')).toBe('false');
    const saveButton = screen.getByRole<HTMLButtonElement>('button', { name: '저장' });
    expect(saveButton.disabled).toBe(false);

    await act(async () => {
      fireEvent.click(saveButton);
      await Promise.resolve();
    });
    expect(savedSnapshots).toHaveLength(1);
    expect(savedSnapshots[0]?.snapshot.sceneJson).toContain('"version":2');
  });

  it('retains the draft when permission is lost or save fails', async () => {
    installMediaQuery(false);
    let draftOpen = false;
    const onSave = (): Promise<DrawingSaveResult> =>
      Promise.resolve({ kind: 'kept', message: '권한이 없어 저장하지 못했습니다.' });

    const view = render(
      <DrawingDialog
        target={{ kind: 'new', key: 'k2', anchorBlockId: null }}
        editable
        theme="light"
        loadScene={() => Promise.resolve(null)}
        onMeaningfulInput={() => undefined}
        reserveInput={() => null}
        onSave={onSave}
        onClose={() => undefined}
        onDraftChange={(dirty) => {
          draftOpen = dirty;
        }}
      />,
    );

    const canvas = await screen.findByTestId('excalidraw-canvas');
    act(() => {
      mockCanvasState.onChange?.(mockCanvasState.elements, { viewBackgroundColor: '#ffffff' });
    });
    fireEvent.click(screen.getByRole('button', { name: '모의 그리기' }));
    expect(draftOpen).toBe(true);

    await act(async () => {
      fireEvent.click(screen.getByRole('button', { name: '저장' }));
      await Promise.resolve();
    });
    expect(screen.getByRole('status').textContent).toContain('권한이 없어');
    expect(draftOpen).toBe(true);

    view.rerender(
      <DrawingDialog
        target={{ kind: 'new', key: 'k2', anchorBlockId: null }}
        editable={false}
        theme="light"
        loadScene={() => Promise.resolve(null)}
        onMeaningfulInput={() => undefined}
        reserveInput={() => null}
        onSave={onSave}
        onClose={() => undefined}
        onDraftChange={(dirty) => {
          draftOpen = dirty;
        }}
      />,
    );
    expect(canvas.getAttribute('data-view-mode')).toBe('true');
    expect(screen.getByRole<HTMLButtonElement>('button', { name: '저장' }).disabled).toBe(true);
    expect(screen.getByRole<HTMLButtonElement>('button', { name: '장면 파일로 내려받기' }).disabled).toBe(
      false,
    );
    expect(draftOpen).toBe(true);
  });
});
