import type { InputReservation } from '../../../../../application/document/editorSession';

/** The SVG and scene a drawing block points at. */
export type DrawingPair = { readonly svgAssetId: string; readonly sceneAssetId: string };

/**
 * What the dialog edits: a new drawing, placed at the block where the slash menu was opened (a block id, because a
 * position would shift while the dialog is open), or an existing block.
 */
export type DrawingTarget =
  | { readonly kind: 'new'; readonly key: string; readonly anchorBlockId: string | null }
  | { readonly kind: 'edit'; readonly key: string; readonly blockId: string; readonly pair: DrawingPair };

/** Scene JSON and SVG made from one and the same moment of the canvas. */
export type DrawingSnapshot = { readonly sceneJson: string; readonly svg: string };

export type DrawingSaveResult =
  | { readonly kind: 'saved'; readonly blockId: string; readonly pair: DrawingPair }
  /** The document did not change; the draft stays in the dialog. */
  | { readonly kind: 'kept'; readonly message: string }
  /** The block holds the new pair but the save did not go through; it rides with the next save. */
  | {
      readonly kind: 'applied';
      readonly blockId: string;
      readonly pair: DrawingPair;
      readonly message: string;
    };

export type DrawingSaveRequest = {
  readonly target: DrawingTarget;
  /** The block's pair this dialog last opened or applied; anything else means someone else changed it. */
  readonly expected: DrawingPair | null;
  readonly blockId: string | null;
  readonly snapshot: DrawingSnapshot;
  readonly reservation: InputReservation | null;
};

export type DrawingDialogProps = {
  readonly target: DrawingTarget;
  /** Live permission: the dialog stays open when it turns false, keeps the draft and stops saving. */
  readonly editable: boolean;
  readonly theme: 'light' | 'dark';
  /** Read once when the dialog opens; null for a new drawing. */
  readonly loadScene: () => Promise<string | null>;
  readonly onMeaningfulInput: (at: Date) => void;
  /** Freezes the input recorded so far, at the same moment as the canvas snapshot. */
  readonly reserveInput: () => InputReservation | null;
  readonly onSave: (request: DrawingSaveRequest) => Promise<DrawingSaveResult>;
  /** 'keep' after a pair was put into the block without a confirmed save; 'discard' otherwise. */
  readonly onClose: (input: 'discard' | 'keep') => void;
  /** True while the dialog holds something the server has not accepted. */
  readonly onDraftChange: (draft: boolean) => void;
};

/** Fixed attachment names, so no user file name or document title ends up in storage or exports. */
export function drawingFileNames(key: string): { readonly scene: string; readonly svg: string } {
  const safe = key.replace(/[^a-z0-9-]/gi, '').slice(0, 40) || 'drawing';
  return { scene: `${safe}.excalidraw`, svg: `${safe}.svg` };
}
