import { NodeViewWrapper, ReactNodeViewRenderer } from '@tiptap/react';
import type { ReactNodeViewProps } from '@tiptap/react';
import { useState } from 'react';
import { DrawingNode } from '../schema';
import type { SavedAssetReferences } from '../savedAssetReferences';
import { useSavedAssetReference } from '../savedAssetReferences';
import { useMediaQuery } from '../../hooks/useMediaQuery';

export type DrawingOptions = {
  readonly contentUrl: (assetId: string) => string;
  readonly savedReferences?: SavedAssetReferences;
  /** The SVG this tab just uploaded, shown until the server confirms the save. */
  readonly localPreview: (assetId: string) => string | null;
  readonly openDrawing: (blockId: string) => void;
};

/** Narrow screens read drawings; drawing needs room, so editing is offered on wide screens only. */
export const NARROW_DRAWING_QUERY = '(max-width: 1023px)';

// A node view gets its new attributes a microtask after the change; keep the typed text locally so Korean IME input
// is not cut short (the same reason as the image alt field).
function AltField({ value, onChange }: { value: string; onChange: (text: string) => void }) {
  const [draft, setDraft] = useState(value);
  const [source, setSource] = useState(value);
  if (source !== value) {
    setSource(value);
    setDraft(value);
  }
  return (
    <input
      className="input image-alt"
      aria-label="대체 텍스트"
      placeholder="대체 텍스트: 그림이 보여 주는 내용"
      value={draft}
      onChange={(event) => {
        setDraft(event.target.value);
        onChange(event.target.value);
      }}
    />
  );
}

/** A saved drawing loads through the document; before the save is confirmed only this tab's own copy can show. */
export function DrawingPicture({
  svgAssetId,
  alt,
  options,
}: {
  svgAssetId: string;
  alt: string;
  options: DrawingOptions;
}) {
  const saved = useSavedAssetReference(svgAssetId, options.savedReferences);
  const [failed, setFailed] = useState(false);
  const source = saved ? options.contentUrl(svgAssetId) : options.localPreview(svgAssetId);
  if (source === null) return <p role="status">문서 저장 후 그림을 불러옵니다.</p>;
  return (
    <>
      <img
        className="drawing-picture"
        src={source}
        alt={alt}
        draggable={false}
        onError={() => setFailed(true)}
      />
      {failed ? <p role="alert">그림을 불러오지 못했습니다.</p> : null}
    </>
  );
}

function DrawingView({ node, updateAttributes, editor, extension, selected }: ReactNodeViewProps) {
  const options = extension.options as DrawingOptions;
  const blockId = String(node.attrs.id);
  const svgAssetId = String(node.attrs.svgAssetId ?? '');
  const alt = String(node.attrs.alt ?? '');
  const editable = editor.isEditable;
  const narrow = useMediaQuery(NARROW_DRAWING_QUERY);
  return (
    <NodeViewWrapper
      as="figure"
      className={`image-block drawing-block${selected ? ' is-selected' : ''}`}
      data-block-id={blockId}
    >
      <div className="image-frame" data-drag-handle="">
        <DrawingPicture key={svgAssetId} svgAssetId={svgAssetId} alt={alt} options={options} />
        {editable && alt.trim() === '' ? (
          <span
            className="badge badge-warning image-alt-warning"
            title="에이전트와 화면 낭독기를 위해 대체 텍스트를 적어 주세요"
          >
            대체 텍스트 없음
          </span>
        ) : null}
      </div>
      {editable ? (
        <div className="image-fields drawing-fields" contentEditable={false}>
          <AltField value={alt} onChange={(text) => updateAttributes({ alt: text })} />
          {narrow ? (
            <p className="drawing-narrow-note">손그림 편집은 넓은 화면에서 할 수 있습니다.</p>
          ) : (
            <button type="button" className="btn btn-small" onClick={() => options.openDrawing(blockId)}>
              편집
            </button>
          )}
        </div>
      ) : alt.trim() === '' ? null : (
        <p className="image-alt-readonly">
          <span className="image-alt-label">대체 텍스트</span>
          <span>{alt}</span>
        </p>
      )}
    </NodeViewWrapper>
  );
}

export const DrawingWithView = DrawingNode.extend<DrawingOptions>({
  // Routes and the dialog come from DocumentEditor; the node view only asks for them.
  addOptions: () => ({
    contentUrl: () => '',
    localPreview: () => null,
    openDrawing: () => undefined,
  }),
  addNodeView: () => ReactNodeViewRenderer(DrawingView),
});
