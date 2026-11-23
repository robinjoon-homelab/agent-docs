import { describe, expect, it } from 'vitest';
import { designColor, designNodeStyle, designTextStyle } from './designStyle';
import { usesVectorSurface } from './designStyleSurface';

describe('v4 design layout', () => {
  it('preserves an explicit zero pixel basis versus zero percent for content-height flex parents', () => {
    const child = { size: { y: { mode: 'fill', min: 0 } } };
    expect(designNodeStyle(child, 'column')).toMatchObject({
      flexGrow: 1,
      flexBasis: '0px',
      minHeight: '0px',
    });
    expect(
      designNodeStyle({ ...child, flexItem: { basis: { percent: 0 }, shrink: 0 } }, 'column'),
    ).toMatchObject({ flexBasis: '0%', flexShrink: 0 });
    expect(
      designNodeStyle({ layout: { mode: 'column' }, size: { x: { mode: 'hug' } } }, 'row').minWidth,
    ).toBeUndefined();
  });
  it('maps grid tracks, whole-row cells, axis midpoint and normalized baseline coordinates', () => {
    expect(
      designNodeStyle({
        layout: { mode: 'grid', columns: [{ min: 0, max: { fr: 1 } }, 120], columnGap: 10 },
      }),
    ).toMatchObject({ display: 'grid', gridTemplateColumns: 'minmax(0px,1fr) 120px', columnGap: '10px' });
    expect(
      designNodeStyle({
        cell: { column: 'all' },
        position: {
          mode: 'absolute',
          x: { center: 0 },
          y: { scale: { start: 10, extent: 20, parentExtent: 100 } },
        },
      }),
    ).toMatchObject({
      gridColumn: '1 / -1',
      left: 'calc(50% + 0px)',
      transform: 'translateX(-50%)',
      top: '10%',
      height: '20%',
    });
    expect(
      designNodeStyle({
        position: { mode: 'absolute', x: { start: 2, end: 8 } },
        size: { x: { mode: 'fixed', px: 80 } },
      }).width,
    ).toBeUndefined();
  });
  it('does not allocate layout space for a painted stroke; CSS borders keep their own box sizing', () => {
    expect(
      designNodeStyle({
        kind: 'box',
        layout: { padding: { left: 10 } },
        style: { strokes: [{ paint: { kind: 'solid', color: '#123' }, width: 2, align: 'inside' }] },
      }),
    ).toMatchObject({ paddingLeft: '10px', boxSizing: 'border-box', boxShadow: 'inset 0 0 0 2px #123' });
    expect(
      designNodeStyle({
        kind: 'box',
        boxSizing: 'content-box',
        style: { border: { left: { width: 2, color: '#123', line: 'dashed' } } },
      }),
    ).toMatchObject({ boxSizing: 'content-box', borderLeft: '2px dashed #123' });
    expect(
      usesVectorSurface({
        kind: 'box',
        style: { strokes: [{ paint: { kind: 'solid', color: '#123' }, width: 2, dash: [3, 2] }] },
      }),
    ).toBe(true);
  });
  it('keeps soft source shadow and CSS box shadow distinct and preserves scroll-axis behavior', () => {
    expect(
      designNodeStyle({
        style: {
          effects: [{ kind: 'dropShadow', surface: 'box', x: 1, y: 2, blur: 4, color: '#0008' }],
          overflow: { x: 'clip', y: 'auto' },
        },
      }),
    ).toMatchObject({ boxShadow: '1px 2px 4px 0px #0008', overflowX: 'clip', overflowY: 'auto' });
    expect(
      designNodeStyle({
        style: { effects: [{ kind: 'dropShadow', surface: 'alpha', x: 1, y: 2, blur: 4, color: '#0008' }] },
      }).filter,
    ).toBe('drop-shadow(1px 2px 2px #0008)');
  });
});

describe('v4 text, paint and attachment styles', () => {
  it('combines a local family, fallbacks, explicit opacity and inheritance', () => {
    const style = designTextStyle({
      font: { local: 'Noto Sans KR' },
      fallback: [{ system: 'serif' }],
      weight: 600,
      whiteSpace: 'pre-wrap',
      lineHeight: { multiple: 1.55 },
      color: 'currentColor',
      decoration: { color: '#4568', thickness: 1 },
    });
    expect(style).toMatchObject({
      fontFamily: '"Agent Docs Design Sans", serif',
      fontWeight: 600,
      whiteSpace: 'pre-wrap',
      lineHeight: 1.55,
      color: 'currentColor',
      textDecorationThickness: '1px',
    });
    expect(designColor({ space: 'display-p3', channels: [1, 0, 0], alpha: 0.5 })).toBe(
      'color(display-p3 1 0 0 / 0.5)',
    );
  });
  it('only resolves text image paints from an allowed attachment identifier', () => {
    const scoped = (id: string) => '/document/assets/' + id;
    const invalid = designTextStyle(
      { fills: [{ kind: 'image', image: { asset: 'https://files.example/other' } }] },
      scoped,
    );
    expect(invalid.backgroundImage).toBeUndefined();
    const good = designTextStyle({ fills: [{ kind: 'image', image: { asset: 'local_123' } }] }, scoped);
    expect(good.backgroundImage).toBe('url("/document/assets/local_123")');
    expect(designColor('var(--remote)')).toBeUndefined();
    expect(
      designNodeStyle({ kind: 'box', style: { fill: 'url(https://files.example/picture)' } }).backgroundColor,
    ).toBeUndefined();
  });
});
