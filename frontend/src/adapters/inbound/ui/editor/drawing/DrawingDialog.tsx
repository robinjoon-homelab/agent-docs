import * as excalidraw from '@excalidraw/excalidraw';
import {
  Excalidraw,
  MIME_TYPES,
  MainMenu,
  hashElementsVersion,
  loadFromBlob,
  serializeAsJSON,
} from '@excalidraw/excalidraw';
import type { ClipboardData } from '@excalidraw/excalidraw/clipboard';
import type { ExcalidrawElement, NonDeleted } from '@excalidraw/excalidraw/element/types';
import type {
  AppState,
  BinaryFiles,
  ExcalidrawImperativeAPI,
  ExcalidrawInitialDataState,
} from '@excalidraw/excalidraw/types';
import type { RefObject } from 'react';
import { useEffect, useRef, useState } from 'react';
import { createPortal } from 'react-dom';
import { DrawingEditState } from '../../../../../domain/document/drawingInput';
import { externalReferences } from '../../../../../domain/document/drawingSvg';
import { useMediaQuery } from '../../hooks/useMediaQuery';
import { NARROW_DRAWING_QUERY } from './DrawingView';
import type { DrawingDialogProps, DrawingPair } from './drawingTypes';
import { drawingFileNames } from './drawingTypes';
import { hasRemoteImage } from './pasteGuard';
import './drawing.css';

const FONT_MISSING =
  '손글씨 폰트를 이 앱에서 불러오지 못해 저장하지 않았습니다. 새로고침한 뒤 다시 저장하세요. 그림은 이 창에 남아 있습니다.';
const READ_ONLY =
  '지금은 이 문서를 편집할 수 없어 저장하지 않습니다. 그림은 이 창에만 있으니 장면 파일로 내려받아 보관하세요.';
const NARROW_VIEWPORT =
  '화면 폭이 1023px 이하라 지금은 편집하고 저장할 수 없습니다. 화면을 다시 넓히면 편집·저장할 수 있으며, 작업 중인 그림은 이 창에 남아 있습니다.';
const NO_REMOTE_IMAGES =
  '외부 주소의 이미지는 가져오지 않습니다. 이미지는 도구 막대의 이미지 도구로 넣어 주세요.';
const NO_FILE_DROP = '파일은 끌어놓을 수 없습니다. 이미지는 도구 막대의 이미지 도구로 넣어 주세요.';
const NO_PUBLISH = '라이브러리 게시는 외부 서비스로 보내는 기능이라 막았습니다. 아무것도 보내지 않았습니다.';
const NO_AI_PASTE = 'AI가 만든 HTML 프레임이나 주소로 된 이미지가 든 그림은 붙여 넣을 수 없습니다.';

// 0.18.1's index.d.ts re-exports exportToSvg from "@excalidraw/utils/export", a package it does not ship, so the
// type is lost. This is the signature in its dist/types/utils/export.d.ts, narrowed to the options used here.
type ExportToSvg = (options: {
  readonly elements: readonly NonDeleted<ExcalidrawElement>[];
  readonly appState?: Partial<AppState>;
  readonly files: BinaryFiles | null;
  readonly renderEmbeddables?: boolean;
}) => Promise<SVGSVGElement>;
const exportToSvg = excalidraw.exportToSvg as unknown as ExportToSvg;

type Phase = 'editing' | 'saving' | 'applied';
type Loaded = { readonly data: ExcalidrawInitialDataState } | { readonly error: string };

function fingerprintOf(elements: readonly ExcalidrawElement[], appState: AppState): string {
  return `${hashElementsVersion(elements)}|${appState.viewBackgroundColor}`;
}

/** Elements Excalidraw would render unsafely, or images it would fetch, as the server refuses them too. */
function unsafePaste(data: ClipboardData): boolean {
  const frames = (data.elements ?? []).some((element) => element.type === 'iframe' && !element.isDeleted);
  const remote = Object.values(data.files ?? {}).some((file) => !file.dataURL.startsWith('data:'));
  return frames || remote;
}

function openLink(link: string | null): void {
  if (link !== null && /^https?:\/\//i.test(link)) window.open(link, '_blank', 'noopener,noreferrer');
}

async function restored(source: string | null): Promise<ExcalidrawInitialDataState> {
  if (source === null) return { elements: [], appState: {} };
  const data = await loadFromBlob(new Blob([source], { type: MIME_TYPES.excalidraw }), null, null);
  return { elements: data.elements, appState: data.appState, files: data.files };
}

function download(api: ExcalidrawImperativeAPI, key: string): void {
  const json = serializeAsJSON(api.getSceneElements(), api.getAppState(), api.getFiles(), 'local');
  const url = URL.createObjectURL(new Blob([json], { type: MIME_TYPES.excalidraw }));
  const anchor = document.createElement('a');
  anchor.href = url;
  anchor.download = drawingFileNames(key).scene;
  anchor.click();
  URL.revokeObjectURL(url);
}

/**
 * Stops paste, drop and library-publish paths that would reach outside before Excalidraw handles them. The page CSP
 * blocks the network either way; these guards also say why and keep the canvas as it was.
 */
function useOutsideGuards(
  wrapper: RefObject<HTMLDivElement | null>,
  notify: (message: string) => void,
): void {
  useEffect(() => {
    const inside = (target: EventTarget | null) =>
      target instanceof Node && wrapper.current?.contains(target) === true;
    const stop = (event: Event, message: string) => {
      event.preventDefault();
      event.stopImmediatePropagation();
      notify(message);
    };
    const paste = (event: ClipboardEvent) => {
      if (inside(event.target) && hasRemoteImage(event.clipboardData?.getData('text/html') ?? ''))
        stop(event, NO_REMOTE_IMAGES);
    };
    const drop = (event: DragEvent) => {
      if (inside(event.target) && event.dataTransfer?.types.includes('Files') === true)
        stop(event, NO_FILE_DROP);
    };
    const submit = (event: SubmitEvent) => {
      if (event.target instanceof Element && event.target.closest('.publish-library') !== null)
        stop(event, NO_PUBLISH);
    };
    document.addEventListener('paste', paste, true);
    document.addEventListener('drop', drop, true);
    document.addEventListener('submit', submit, true);
    return () => {
      document.removeEventListener('paste', paste, true);
      document.removeEventListener('drop', drop, true);
      document.removeEventListener('submit', submit, true);
    };
  }, [wrapper, notify]);
}

export default function DrawingDialog(props: DrawingDialogProps) {
  const {
    target,
    editable,
    theme,
    loadScene,
    onMeaningfulInput,
    reserveInput,
    onSave,
    onClose,
    onDraftChange,
  } = props;
  const wrapper = useRef<HTMLDivElement>(null);
  const edits = useRef(new DrawingEditState());
  const started = useRef(false);
  const [loaded, setLoaded] = useState<Loaded | null>(null);
  const [api, setApi] = useState<ExcalidrawImperativeAPI | null>(null);
  const [phase, setPhase] = useState<Phase>('editing');
  const [dirty, setDirty] = useState(false);
  const [message, setMessage] = useState<string | null>(null);
  const [expected, setExpected] = useState<DrawingPair | null>(target.kind === 'edit' ? target.pair : null);
  const [blockId, setBlockId] = useState<string | null>(target.kind === 'edit' ? target.blockId : null);
  const narrow = useMediaQuery(NARROW_DRAWING_QUERY);
  const canEdit = editable && !narrow;
  useOutsideGuards(wrapper, setMessage);

  // The original is read once; a later permission change never makes the dialog fetch it again.
  useEffect(() => {
    if (started.current) return;
    started.current = true;
    void loadScene()
      .then(restored)
      .then(
        (data) => setLoaded({ data }),
        (error: unknown) =>
          setLoaded({ error: error instanceof Error ? error.message : '그림을 불러오지 못했습니다.' }),
      );
  }, [loadScene]);

  useEffect(() => {
    onDraftChange(dirty || phase !== 'editing');
  }, [dirty, phase, onDraftChange]);

  const save = async () => {
    if (api === null || phase === 'saving' || !canEdit) return;
    if (!edits.current.isDirty()) {
      onClose(phase === 'applied' ? 'keep' : 'discard');
      return;
    }
    // Content and input are frozen together: what is drawn from here on belongs to the next save.
    const elements = api.getSceneElements();
    const appState = api.getAppState();
    const files = api.getFiles();
    const fingerprint = fingerprintOf(api.getSceneElementsIncludingDeleted(), appState);
    const reservation = reserveInput();
    const before = phase;
    setPhase('saving');
    setMessage(null);
    try {
      const sceneJson = serializeAsJSON(elements, appState, files, 'local');
      const svg = new XMLSerializer().serializeToString(
        await exportToSvg({
          elements,
          appState: {
            viewBackgroundColor: appState.viewBackgroundColor,
            exportBackground: true,
            exportWithDarkMode: false,
            exportEmbedScene: false,
          },
          files,
          renderEmbeddables: false,
        }),
      );
      if (externalReferences(svg).length > 0) {
        setPhase(before);
        setMessage(FONT_MISSING);
        return;
      }
      const result = await onSave({ target, expected, blockId, snapshot: { sceneJson, svg }, reservation });
      if (result.kind === 'kept') {
        setPhase(before);
        setMessage(result.message);
        return;
      }
      edits.current.saved(fingerprint);
      setDirty(edits.current.isDirty());
      setExpected(result.pair);
      setBlockId(result.blockId);
      if (result.kind === 'applied') {
        setPhase('applied');
        setMessage(result.message);
      } else if (edits.current.isDirty()) {
        setPhase('editing');
        setMessage('저장했습니다. 저장을 누른 뒤 그린 내용은 아직 저장하지 않았습니다.');
      } else {
        onClose('discard');
      }
    } catch {
      setPhase(before);
      setMessage('그림을 저장하지 못했습니다. 그림은 이 창에 남아 있습니다.');
    }
  };

  const close = () => {
    if (phase === 'saving') return;
    if (edits.current.isDirty() && !window.confirm('저장하지 않은 그림을 버리고 닫을까요?')) return;
    onClose(phase === 'applied' ? 'keep' : 'discard');
  };

  return createPortal(
    <div className="drawing-dialog-backdrop">
      <div
        ref={wrapper}
        className="drawing-dialog"
        role="dialog"
        aria-modal="true"
        aria-label="손그림"
        // The app's own shortcuts (search, comment) must not fire while drawing.
        onKeyDown={(event) => event.stopPropagation()}
      >
        <div className="drawing-dialog-header">
          <h2>손그림</h2>
          <div className="drawing-dialog-actions">
            <button
              type="button"
              className="btn btn-small"
              disabled={api === null}
              onClick={() => api !== null && download(api, target.key)}
            >
              장면 파일로 내려받기
            </button>
            <button type="button" className="btn btn-small" onClick={close} disabled={phase === 'saving'}>
              닫기
            </button>
            <button
              type="button"
              className="btn btn-small btn-primary"
              onClick={() => void save()}
              disabled={api === null || phase === 'saving' || !canEdit}
            >
              {phase === 'saving' ? '저장 중…' : '저장'}
            </button>
          </div>
        </div>
        {!editable ? (
          <p className="drawing-dialog-notice" role="alert">
            {READ_ONLY}
          </p>
        ) : null}
        {narrow ? (
          <p className="drawing-dialog-notice" role="alert">
            {NARROW_VIEWPORT}
          </p>
        ) : null}
        {message !== null ? (
          <p className="drawing-dialog-notice" role="status">
            {message}
          </p>
        ) : null}
        <div className="drawing-dialog-canvas">
          {loaded === null ? <p role="status">그림을 불러오는 중…</p> : null}
          {loaded !== null && 'error' in loaded ? <p role="alert">{loaded.error}</p> : null}
          {loaded !== null && 'data' in loaded ? (
            <Excalidraw
              excalidrawAPI={setApi}
              initialData={loaded.data}
              theme={theme}
              viewModeEnabled={!canEdit}
              langCode="ko-KR"
              aiEnabled={false}
              validateEmbeddable={false}
              libraryReturnUrl={window.location.origin}
              onLinkOpen={(element, event) => {
                event.preventDefault();
                openLink(element.link);
              }}
              onPaste={(data) => {
                if (!unsafePaste(data)) return true;
                setMessage(NO_AI_PASTE);
                return false;
              }}
              onChange={(elements, appState) => {
                if (edits.current.observe(fingerprintOf(elements, appState))) onMeaningfulInput(new Date());
                setDirty(edits.current.isDirty());
              }}
              UIOptions={{
                canvasActions: {
                  loadScene: false,
                  saveToActiveFile: false,
                  saveAsImage: false,
                  toggleTheme: false,
                  export: { saveFileToDisk: true },
                  changeViewBackgroundColor: true,
                  clearCanvas: true,
                },
              }}
            >
              <MainMenu>
                <MainMenu.DefaultItems.Export />
                <MainMenu.DefaultItems.SearchMenu />
                <MainMenu.DefaultItems.ClearCanvas />
                <MainMenu.DefaultItems.ChangeCanvasBackground />
              </MainMenu>
            </Excalidraw>
          ) : null}
        </div>
      </div>
    </div>,
    document.body,
  );
}
