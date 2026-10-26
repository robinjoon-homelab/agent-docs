import { describe, expect, it } from 'vitest';
import { MAX_SCALE, MIN_SCALE, diagramSize, fitView, zoomAt } from './diagramZoom';

describe('diagram zoom', () => {
  it('fits a wide diagram to the frame width and centers it vertically', () => {
    const view = fitView({ width: 1000, height: 100 }, { width: 1240, height: 800 }, 20);

    expect(view.scale).toBeCloseTo(1.2);
    expect(view.x).toBeCloseTo(20);
    expect(view.y).toBeCloseTo((800 - 120) / 2);
  });

  it('fits a tall diagram to the frame height and centers it horizontally', () => {
    const view = fitView({ width: 100, height: 2000 }, { width: 1000, height: 1040 }, 20);

    expect(view.scale).toBeCloseTo(0.5);
    expect(view.x).toBeCloseTo((1000 - 50) / 2);
    expect(view.y).toBeCloseTo(20);
  });

  it('keeps the point under the cursor in place while zooming', () => {
    const before = { scale: 1, x: 40, y: 60 };
    const point = { x: 300, y: 200 };
    const contentX = (point.x - before.x) / before.scale;
    const contentY = (point.y - before.y) / before.scale;

    const after = zoomAt(before, 2, point);

    expect(after.scale).toBe(2);
    expect(after.x + contentX * after.scale).toBeCloseTo(point.x);
    expect(after.y + contentY * after.scale).toBeCloseTo(point.y);
  });

  it('stops zooming at the smallest and largest scale', () => {
    expect(zoomAt({ scale: MAX_SCALE, x: 0, y: 0 }, 2, { x: 10, y: 10 })).toEqual({
      scale: MAX_SCALE,
      x: 0,
      y: 0,
    });
    expect(zoomAt({ scale: 0.15, x: 0, y: 0 }, 0.5, { x: 0, y: 0 }).scale).toBe(MIN_SCALE);
  });
});

describe('diagram size', () => {
  it('reads the drawing size from the viewBox mermaid writes', () => {
    expect(
      diagramSize('<svg id="m1" width="100%" viewBox="-8 -8 812.5 70" style="max-width: 812.5px;">'),
    ).toEqual({
      width: 812.5,
      height: 70,
    });
  });

  it('has no size when the drawing does not say', () => {
    expect(diagramSize('<svg width="100%">')).toBeNull();
    expect(diagramSize('<svg viewBox="0 0 0 40">')).toBeNull();
  });
});
