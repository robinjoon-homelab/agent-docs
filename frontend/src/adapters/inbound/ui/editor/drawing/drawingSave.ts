import type { ContentSaveOptions, SaveOutcome } from '../../../../../application/document/editorSession';
import { isNotVisible } from '../../../../../application/shared/ports/errors';
import type { Asset } from '../../../../../domain/asset/asset';
import { EXCALIDRAW_SCENE_MEDIA_TYPE } from '../../../../../domain/asset/asset';
import { errorMessage } from '../../common/States';
import type { DrawingPair, DrawingSaveRequest, DrawingSaveResult } from './drawingTypes';
import { drawingFileNames } from './drawingTypes';

export const CANNOT_EDIT =
  '지금은 이 문서를 편집할 수 없어 저장하지 않았습니다. 그림은 이 창에 남아 있습니다.';

/** What saving a drawing needs from the editor and the session around it. */
export type DrawingSaveSteps = {
  /** Edit permission and the session's write gate as they are right now. */
  readonly canWrite: () => boolean;
  /** Saves the editor's current body; drawing input goes along only once the pair is in its block. */
  readonly saveBody: (options?: ContentSaveOptions) => Promise<SaveOutcome>;
  /** Confirms the saved input; false when confirming failed. */
  readonly finalize: () => Promise<boolean>;
  /** Uploads one file and returns the stored asset; fails when it was not stored with that type. */
  readonly upload: (name: string, type: string, data: Blob) => Promise<Asset>;
  /** Puts the pair into its block and returns the block id, or null when the block no longer holds what was expected. */
  readonly place: (pair: DrawingPair, svg: Blob) => string | null;
};

/**
 * The same order as replacing a mockup: save pending input first, upload both files, check the block, then save the
 * pair with the drawing input frozen when the user pressed save. Permission is read again after every wait, so once
 * it is gone no further upload or write starts. A request already sent cannot be taken back: a file it stored stays,
 * and the dialog keeps the scene while the block keeps its old pair.
 */
export async function saveDrawingPair(
  request: DrawingSaveRequest,
  steps: DrawingSaveSteps,
): Promise<DrawingSaveResult> {
  const kept = (message: string): DrawingSaveResult => ({ kind: 'kept', message });
  if (!steps.canWrite()) return kept(CANNOT_EDIT);
  const body = await steps.saveBody();
  if (!steps.canWrite()) return kept(CANNOT_EDIT);
  if (body !== 'saved')
    return kept('먼저 문서의 다른 입력을 저장해야 합니다. 저장 상태를 확인한 뒤 다시 저장하세요.');
  const confirmed = await steps.finalize();
  if (!steps.canWrite()) return kept(CANNOT_EDIT);
  if (!confirmed) return kept('기존 입력 기록을 확정하지 못했습니다. 다시 저장하세요.');
  const names = drawingFileNames(request.target.key);
  const svgBlob = new Blob([request.snapshot.svg], { type: 'image/svg+xml' });
  let pair: DrawingPair;
  try {
    const scene = await steps.upload(
      names.scene,
      EXCALIDRAW_SCENE_MEDIA_TYPE,
      new Blob([request.snapshot.sceneJson]),
    );
    if (!steps.canWrite()) return kept(CANNOT_EDIT);
    const svg = await steps.upload(names.svg, 'image/svg+xml', svgBlob);
    pair = { svgAssetId: svg.id, sceneAssetId: scene.id };
  } catch (error) {
    return kept(isNotVisible(error) ? CANNOT_EDIT : errorMessage(error));
  }
  if (!steps.canWrite()) return kept(CANNOT_EDIT);
  const blockId = steps.place(pair, svgBlob);
  if (blockId === null)
    return kept(
      '다른 쪽이 이 그림을 바꾸거나 지웠습니다. 그림은 이 창에 남아 있으니 내려받거나 닫은 뒤 다시 열어 주세요.',
    );
  const options: ContentSaveOptions =
    request.reservation === null
      ? {}
      : { drawingInput: { reservation: request.reservation, placement: { blockId, ...pair } } };
  if ((await steps.saveBody(options)) !== 'saved')
    return {
      kind: 'applied',
      blockId,
      pair,
      message: '그림을 문서에 넣었지만 아직 저장하지 못했습니다. 다음 저장 때 함께 저장됩니다.',
    };
  await steps.finalize();
  return { kind: 'saved', blockId, pair };
}
