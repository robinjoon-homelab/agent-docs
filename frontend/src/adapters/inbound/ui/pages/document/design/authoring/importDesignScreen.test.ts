import { describe, expect, it } from 'vitest';
import {
  isDesignObject,
  type DesignObject,
  type FlowDesignBlock,
} from '../../../../../../../domain/document/designBlock';
import { importDesignScreen } from './importDesignScreen';
import button from './fixtures/save-button.txt?raw';
import buttonModel from './fixtures/save-button.expected.json';
import dialog from './fixtures/new-document.txt?raw';
import dialogModel from './fixtures/new-document.expected.json';
import comment from './fixtures/comment-card.txt?raw';
import commentModel from './fixtures/comment-card.expected.json';

const wrap = (id: string, design: DesignObject): FlowDesignBlock => ({ type: 'flowDesign', id, design });
const original = () => [
  wrap('settings01', {
    kind: 'settings',
    id: 'settings',
    modelVersion: 4,
    frame: 'desktop',
    idPrefix: 'F',
    nextScreen: 2,
  }),
  wrap('screen0001', {
    kind: 'screen',
    id: 'original',
    code: 'F-01',
    name: '기존 화면',
    root: { kind: 'box', id: 'root', layout: { mode: 'free' }, children: [] },
  }),
];
function idGenerator() {
  let i = 0;
  return () => 'new' + String(++i).padStart(7, '0');
}
const snippets = [
  { name: '체크 아이콘 버튼과 재사용 컴포넌트', source: button, expected: buttonModel },
  { name: '모달의 가변 열과 라디오', source: dialog, expected: dialogModel },
  { name: '한글 혼합 서식과 공백', source: comment, expected: commentModel },
];
describe('화면 마크업 가져오기', () => {
  it.each(snippets)('$name 원본을 수동으로 작성한 모델과 같은 레이어로 저장한다', ({ source, expected }) => {
    const blocks = original();
    const { blocks: result, screenBlockId } = importDesignScreen(source, blocks, '새 화면', idGenerator());
    const models = expected.blocks.map((block: unknown) => {
      if (!isDesignObject(block)) throw Error('Invalid reference fixture');
      return block;
    });
    expect(result[0]?.design.nextScreen).toBe(3);
    expect(result[1]).toBe(blocks[1]);
    expect(result.at(-1)).toEqual(
      wrap(screenBlockId, {
        kind: 'screen',
        id: screenBlockId,
        code: 'F-02',
        name: '새 화면',
        root: models.find((m) => m.kind === 'screen')?.root as DesignObject,
      }),
    );
    const expectedTokens = models.find((m) => m.kind === 'tokens');
    if (expectedTokens)
      expect(result.find((b) => b.design.kind === 'tokens')?.design).toEqual({
        ...expectedTokens,
        id: 'new0000001',
      });
    expect(result.filter((b) => b.design.kind === 'component').map((b) => b.design)).toEqual(
      models.filter((m) => m.kind === 'component'),
    );
    expect(blocks).toEqual(original());
  });
  it('다른 화면의 토큰과 컴포넌트를 다시 쓰고 같은 정의를 중복 저장하지 않는다', () => {
    const generate = idGenerator();
    const first = importDesignScreen(button, original(), '첫 화면', generate);
    const again = importDesignScreen(button, first.blocks, '다음', generate);
    expect(again.blocks.length).toBe(first.blocks.length + 1);
    const reuse = importDesignScreen(
      '<div><flow-use component="button" data-input-caption="다시 저장"></flow-use></div>',
      again.blocks,
      '재사용',
      generate,
    );
    expect(reuse.blocks.length).toBe(again.blocks.length + 1);
    expect(reuse.blocks.at(-1)?.design).toMatchObject({
      code: 'F-04',
      root: { children: [{ component: 'button', inputs: { caption: '다시 저장' } }] },
    });
    expect(() =>
      importDesignScreen(button.replace('#6542C1', '#112233'), first.blocks, '충돌', generate),
    ).toThrow('기존 정의와 충돌');
  });
  it('직선과 곡선의 지정된 꼭짓점 ID와 상대 베지어 손잡이를 보존한다', () => {
    const source =
      '<div><svg viewBox="0 0 20 20"><path data-key="curve" data-points="start tip" d="M0 0C2 3 8 8 10 10" fill="#445566"/></svg></div>';
    expect(importDesignScreen(source, original(), '곡선', idGenerator()).blocks.at(-1)?.design).toMatchObject(
      {
        root: {
          children: [
            {
              children: [
                {
                  id: 'curve',
                  contours: [
                    {
                      vertices: [
                        { id: 'start', out: { x: 2, y: 3 } },
                        { id: 'tip', in: { x: -2, y: -2 } },
                      ],
                    },
                  ],
                },
              ],
            },
          ],
        },
      },
    );
  });
  it('SVG 그룹과 경로의 회전을 편집 가능한 변환 행렬로 보존한다', () => {
    const source =
      '<div><svg viewBox="0 0 2 2" transform="rotate(0)"><path transform="rotate(90)" d="M0 0L1 1"/></svg></div>';
    const result = importDesignScreen(source, original(), '회전', idGenerator());
    expect(result.blocks.at(-1)?.design).toMatchObject({
      root: {
        children: [
          {
            localTransform: { matrix: [1, 0, -0, 1, 0, 0] },
            children: [{ localTransform: { matrix: [expect.closeTo(0), 1, -1, expect.closeTo(0), 0, 0] } }],
          },
        ],
      },
    });
  });
  it.each([
    '<div><svg viewBox="0 0 2 2"><path transform="scale(2)" d="M0 0L1 1"/></svg></div>',
    '<div onclick="doSomething()"></div>',
    '<div><img src="https://example.test/photo.png"></div>',
    '<style>@import "theme.css";</style><div></div>',
    '<div style="background-color:url(theme.png)"></div>',
    '<div style="unknown-property:1"></div>',
    '<div style="gap:"></div>',
    '<div><script>noop()</script></div>',
    '<div>표시할 글자를 잃으면 안 됨</div>',
    '<div><p>닫지 않은 글</div>',
    '<div><p data-key="x">1</p><p data-key="x">2</p></div>',
    '<div><svg viewBox="0 0 1 2"><path d="M0 0Q1 1 2 2"/></svg></div>',
    '<div style="color:var(--missing)"></div>',
    '<div><flow-use component="absent"></flow-use></div>',
    '<template data-component="cycle"><div><flow-use component="cycle"></flow-use></div></template><div></div>',
  ])('지원하지 않는 입력을 무시하지 않고 원래 블록을 유지한다: %s', (source) => {
    const blocks = original();
    expect(() => importDesignScreen(source, blocks, '새 화면', idGenerator())).toThrow();
    expect(blocks).toEqual(original());
  });
  it('명시한 노드 키를 먼저 예약하고 코드 충돌을 건너뛴다', () => {
    const existing = original();
    existing[1] = wrap('screen0001', { ...existing[1]?.design, code: 'F-02' });
    expect(
      importDesignScreen(
        '<div><span data-key="n1">문구</span></div>',
        existing,
        '복사',
        idGenerator(),
      ).blocks.at(-1)?.design,
    ).toMatchObject({
      code: 'F-03',
      root: { id: 'n2', children: [{ id: 'n1' }] },
    });
  });
});
