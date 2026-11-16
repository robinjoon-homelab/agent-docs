import { describe, expect, it } from 'vitest';
import type { Asset } from '../../../../../domain/asset/asset';
import type { DrawingSaveSteps } from './drawingSave';
import { CANNOT_EDIT, saveDrawingPair } from './drawingSave';
import type { DrawingPair, DrawingSaveRequest } from './drawingTypes';

type Step = 'body' | 'finalize' | 'scene' | 'svg';

const oldPair: DrawingPair = { svgAssetId: 'svg-old', sceneAssetId: 'scene-old' };

const request: DrawingSaveRequest = {
  target: { kind: 'edit', key: 'dialog-1', blockId: 'draw000001', pair: oldPair },
  expected: oldPair,
  blockId: null,
  snapshot: {
    sceneJson: '{"type":"excalidraw","elements":[]}',
    svg: '<svg xmlns="http://www.w3.org/2000/svg"/>',
  },
  reservation: null,
};

/**
 * A server holding the stored files and the drawing block's pair, with one step that waits until resumed. Writes are
 * open until the test closes them, as a permission change would.
 */
function world(pause: Step | null) {
  const server = { assets: [] as Asset[], block: oldPair };
  let open = true;
  let entered: () => void = () => undefined;
  const paused = new Promise<void>((resolve) => {
    entered = resolve;
  });
  let resume: () => void = () => undefined;
  const waited = new Set<Step>();
  const wait = (step: Step): Promise<void> => {
    if (step !== pause || waited.has(step)) return Promise.resolve();
    waited.add(step);
    entered();
    return new Promise((resolve) => {
      resume = resolve;
    });
  };
  const steps: DrawingSaveSteps = {
    canWrite: () => open,
    saveBody: async () => {
      await wait('body');
      return 'saved';
    },
    finalize: async () => {
      await wait('finalize');
      return true;
    },
    upload: async (name, type, data) => {
      await wait(name.endsWith('.svg') ? 'svg' : 'scene');
      const asset = {
        id: `stored-${name}`,
        mediaType: type,
        size: data.size,
        fileName: name,
        contentUrl: '',
      };
      server.assets.push(asset);
      return asset;
    },
    place: (pair) => {
      server.block = pair;
      return 'draw000001';
    },
  };
  return {
    server,
    steps,
    paused,
    resume: () => {
      resume();
    },
    closeWrites: () => {
      open = false;
    },
  };
}

describe('saving a drawing while edit permission can be taken away', () => {
  it('uploads both files and puts the new pair into its block while writes stay open', async () => {
    const { server, steps } = world(null);

    const result = await saveDrawingPair(request, steps);

    const pair = { svgAssetId: 'stored-dialog-1.svg', sceneAssetId: 'stored-dialog-1.excalidraw' };
    expect(result).toEqual({ kind: 'saved', blockId: 'draw000001', pair });
    expect(server.assets.map((asset) => asset.fileName)).toEqual(['dialog-1.excalidraw', 'dialog-1.svg']);
    expect(server.block).toEqual(pair);
  });

  it.each<[Step, readonly string[]]>([
    ['body', []],
    ['finalize', []],
    ['scene', ['dialog-1.excalidraw']],
    ['svg', ['dialog-1.excalidraw', 'dialog-1.svg']],
  ])(
    'starts nothing new once writes close while the %s step waits, and the block keeps its old pair',
    async (step, stored) => {
      const { server, steps, paused, resume, closeWrites } = world(step);

      const saving = saveDrawingPair(request, steps);
      await paused;
      closeWrites();
      resume();

      expect(await saving).toEqual({ kind: 'kept', message: CANNOT_EDIT });
      expect(server.assets.map((asset) => asset.fileName)).toEqual(stored);
      expect(server.block).toEqual(oldPair);
    },
  );
});
