// @vitest-environment jsdom
import { cleanup, render } from '@testing-library/react';
import { afterEach, describe, expect, it } from 'vitest';
import type { DesignObject } from '../../../../../../domain/document/designBlock';
import { DesignGeometry, geometryViewport } from './DesignGeometry';
import { geometryPaths } from './DesignGeometryPaths';

afterEach(cleanup);
const solid = (id: string, color: string) => ({ id, kind: 'solid', color });
const rect = (id: string, x: number, width: number): DesignObject => ({
  id,
  kind: 'shape',
  geometry: { kind: 'rect', origin: { x, y: 0 }, width, height: 20 },
});
const curve: DesignObject = {
  kind: 'vector',
  fillRule: 'nonzero',
  contours: [
    {
      id: 'outer',
      closed: false,
      vertices: [
        { id: 'p', x: 0, y: 0, out: { x: 0, y: 120 } },
        { id: 'q', x: 100, y: 0, in: { x: 0, y: 120 } },
      ],
    },
  ],
};

describe('design geometry', () => {
  it('measures Bézier extrema independently of control point bounds', () => {
    expect(geometryViewport(curve)).toEqual({ x: 0, y: 0, width: 100, height: 90 });
    expect(geometryPaths(curve).edges).toBe('M0 0 C0 120 100 120 100 0');
  });
  it('keeps a complete ring and its hole for a 360° sector', () => {
    const ring = {
      kind: 'shape',
      geometry: {
        kind: 'arc',
        closure: 'sector',
        center: { x: 10, y: 12 },
        radii: { x: 8, y: 4 },
        startAngle: 0,
        sweepAngle: 360,
        innerRatio: 0.5,
      },
    };
    const g = geometryPaths(ring);
    expect(g.edges.match(/A/g)).toHaveLength(4);
    expect(g.edges).toContain('A4 2 0 0 0');
    expect(g.bounds?.x).toBeCloseTo(2);
    expect(g.bounds?.height).toBeCloseTo(8);
  });
  it('paints individually rounded corners scaled proportionally to the available width', () => {
    const n = {
      kind: 'shape',
      geometry: {
        kind: 'rect',
        origin: { x: 0, y: 0 },
        width: 20,
        height: 20,
        corners: { topLeft: { x: 20, y: 10 }, topRight: { x: 20, y: 14 } },
      },
    };
    const d = geometryPaths(n).edges;
    expect(d).toContain('M10 0');
    expect(d).toContain('A10 7');
    expect(d).toContain('A10 5');
  });
  it('reverses both relative handles on a shared edge and only strokes it once', () => {
    const fills = [solid('base', '#123456')],
      stroke = { id: 'edge', paint: solid('ink', '#000000'), width: 2 };
    const n: DesignObject = {
      kind: 'vector',
      style: { fills, strokes: [stroke] },
      network: {
        vertices: [
          { id: 'a', x: 0, y: 0 },
          { id: 'b', x: 10, y: 0 },
          { id: 'c', x: 10, y: 10 },
          { id: 'd', x: 0, y: 10 },
        ],
        segments: [
          { id: 'ab', from: 'a', to: 'b' },
          { id: 'bc', from: 'b', to: 'c' },
          { id: 'ca', from: 'c', to: 'a', out: { x: -2, y: 3 }, in: { x: 5, y: 4 } },
          { id: 'cd', from: 'c', to: 'd' },
          { id: 'da', from: 'd', to: 'a' },
        ],
        regions: [
          {
            id: 'one',
            fillRule: 'nonzero',
            loops: [[{ segment: 'ab' }, { segment: 'bc' }, { segment: 'ca' }]],
          },
          {
            id: 'two',
            fillRule: 'evenodd',
            loops: [[{ segment: 'ca', reverse: true }, { segment: 'cd' }, { segment: 'da' }]],
            fills: [solid('other', '#abcdef')],
          },
        ],
      },
    };
    const { container } = render(
      <svg>
        <DesignGeometry node={n} uid="network" />
      </svg>,
    );
    expect(container.querySelector('path[fill="#abcdef"]')?.getAttribute('d')).toContain('C5 4 8 13 10 10');
    expect(container.querySelectorAll('path[stroke="#000000"]')).toHaveLength(1);
    expect(
      container
        .querySelector('path[stroke="#000000"]')
        ?.getAttribute('d')
        ?.match(/C8 13 5 4 0 0/g),
    ).toHaveLength(1);
  });
  it('renders independent paint servers, layer order, inside clipping, and safe asset references', () => {
    const node = {
      ...curve,
      style: {
        fills: [
          {
            id: 'g',
            kind: 'linear',
            stops: [
              { id: 'a', at: 0, color: '#fff' },
              { id: 'b', at: 1, color: '#123' },
            ],
          },
          solid('wash', '#abc'),
        ],
        strokes: [
          { paint: solid('line', '#678'), width: 2, align: 'inside', dash: [2, 4], nonScaling: true },
        ],
      },
    };
    const { container } = render(
      <svg>
        <DesignGeometry uid="sample" node={node} />
      </svg>,
    );
    expect(container.querySelectorAll('linearGradient stop')).toHaveLength(2);
    expect([...container.querySelectorAll('g > path')].map((p) => p.getAttribute('fill'))).toEqual([
      'url(#sample-f0)',
      '#abc',
      'none',
    ]);
    const stroke = container.querySelector('path[stroke="#678"]');
    expect(stroke?.getAttribute('stroke-width')).toBe('4');
    expect(stroke?.getAttribute('vector-effect')).toBe('non-scaling-stroke');
    expect(stroke?.getAttribute('clip-path')).toBe('url(#sample-s0-in)');
  });
  it('never treats a paint string or an attachment name as an external SVG address', () => {
    const n = {
      ...rect('p', 0, 20),
      style: {
        fills: [
          solid('bad', 'url(https://invalid.example/image)'),
          {
            id: 'badAsset',
            kind: 'image',
            image: { asset: 'https://invalid.example/image' },
            placement: { kind: 'fit', mode: 'cover' },
          },
        ],
      },
    };
    const { container } = render(
      <svg>
        <DesignGeometry uid="u" node={n} assetUrl={(id) => '/asset/' + id} />
      </svg>,
    );
    expect(container.innerHTML).not.toContain('invalid.example');
    expect(container.querySelector('image')).toBeNull();
  });
  it('preserves operand order and masks only the first shape when subtracting', () => {
    const n = {
      kind: 'boolean',
      operation: 'subtract',
      operands: [rect('left', 0, 20), rect('right', 10, 20)],
      style: { fills: [solid('base', '#fff')] },
    };
    const { container } = render(
      <svg>
        <DesignGeometry uid="cut" node={n} />
      </svg>,
    );
    expect(container.querySelector('#cut-expression-step1 > use')?.getAttribute('mask')).toBe(
      'url(#cut-expression-gate1)',
    );
    expect(container.querySelector('#cut-expression-gate1 > use')?.getAttribute('color')).toBe('black');
    expect(container.querySelector('#cut-expression-step1 > use')?.getAttribute('href')).toBe(
      '#cut-expression-step0',
    );
  });
});
