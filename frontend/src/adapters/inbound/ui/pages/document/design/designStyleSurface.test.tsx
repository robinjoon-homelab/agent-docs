import { describe, expect, it } from 'vitest';
import { renderToStaticMarkup } from 'react-dom/server';
import { DesignBoxSurface } from './DesignBoxSurface';

describe('frame paints', () => {
  it('uses editable transformed gradient stops in a rounded frame without an additional layout box', () => {
    const html = renderToStaticMarkup(
      <DesignBoxSurface
        uid="card"
        node={{
          kind: 'box',
          size: { x: { mode: 'fixed', px: 60 }, y: { mode: 'fixed', px: 40 } },
          style: {
            radius: { topLeft: 6 },
            fills: [
              {
                id: 'wash',
                kind: 'linear',
                matrix: [0, 1, -1, 0, 1, 0],
                stops: [
                  { id: 'a', at: 0, color: '#FC0' },
                  { id: 'b', at: 1, color: '#06C' },
                ],
              },
            ],
          },
        }}
      />,
    );
    expect(html).toContain('<linearGradient');
    expect(html).toContain('matrix(0 1 -1 0 1 0)');
    expect(html).toContain('A6 6');
    expect(html).toContain('position:absolute');
  });

  it('uses native CSS for a frame with a single solid paint', () => {
    expect(
      renderToStaticMarkup(
        <DesignBoxSurface
          uid="plain"
          node={{ kind: 'box', style: { fills: [{ id: 'a', kind: 'solid', color: '#123' }] } }}
        />,
      ),
    ).toBe('');
  });
});
