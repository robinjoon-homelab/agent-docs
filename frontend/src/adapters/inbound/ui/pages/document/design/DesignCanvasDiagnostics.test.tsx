// @vitest-environment jsdom
import { it, expect } from 'vitest';
import { inspectDesignCanvas } from './DesignCanvasDiagnostics';
import { designAddressKey } from '../../../../../../domain/flow/designAddress';

function extent(el: HTMLElement, x: number, y: number, width: number, height: number) {
  el.getBoundingClientRect = () => new DOMRect(x, y, width, height);
  for (const [k, v] of Object.entries({
    offsetWidth: width,
    offsetHeight: height,
    clientWidth: width,
    clientHeight: height,
    scrollWidth: width,
    scrollHeight: height,
  }))
    Object.defineProperty(el, k, { value: v, configurable: true });
}
function layer(id: string, parent: HTMLElement, x: number, y: number, w: number, h: number) {
  const el = document.createElement('div');
  el.dataset.designNode = id;
  el.dataset.designOwner = 'screen';
  el.dataset.designAddress = designAddressKey({ owner: 'screen', node: id });
  el.dataset.designSourceAddress = el.dataset.designAddress;
  el.dataset.designKind = 'box';
  extent(el, x, y, w, h);
  parent.append(el);
  return el;
}
it('returns canvas-relative bounding boxes, clipping by a scroll port, overlaps and a zero-range diagnostic', async () => {
  const canvas = document.createElement('div');
  canvas.dataset.designScreen = 'screen';
  canvas.dataset.designMode = 'viewport';
  canvas.dataset.designFrameWidth = '100';
  canvas.dataset.designFrameHeight = '100';
  extent(canvas, 20, 30, 100, 100);
  canvas.style.overflowX = 'auto';
  canvas.style.overflowY = 'auto';
  document.body.append(canvas);
  const root = layer('root', canvas, 20, 30, 100, 100);
  root.style.overflowX = 'hidden';
  root.style.overflowY = 'hidden';
  const target = layer('target', root, 40, 100, 40, 50);
  target.style.overflow = 'auto';
  target.dataset.designScrollY = 'end';
  layer('overlaps', root, 50, 98, 25, 15);
  try {
    const res = await inspectDesignCanvas(canvas, { owner: 'screen', node: 'target' });
    expect(res.layers).toHaveLength(1);
    expect(res.layers[0]).toMatchObject({
      node: 'target',
      rect: { x: 20, y: 70, width: 40, height: 50 },
      visibleRect: { x: 20, y: 70, width: 40, height: 30 },
      scroll: { x: 0, y: 0 },
      overlappingSibling: designAddressKey({ owner: 'screen', node: 'overlaps' }),
    });
    expect(res.layers[0]?.issues.toSorted()).toEqual([
      'clipped',
      'outsideFrame',
      'overlapsSibling',
      'zeroScrollRangeY',
    ]);
  } finally {
    canvas.remove();
  }
});
