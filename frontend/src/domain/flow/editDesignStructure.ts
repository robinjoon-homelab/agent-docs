import type { DesignObject, FlowDesignBlock } from '../document/designBlock';
import type { DesignAddress } from './designAddress';
import { editDesignValue } from './designValue';
import {
  findDesignNode,
  object,
  records,
  sourceBlock,
  stringValue,
  editDesignCollection,
} from './editDesign';

const block = (id: string, design: DesignObject): FlowDesignBlock => ({ id, type: 'flowDesign', design });
const box = (id: string): DesignObject => ({
  kind: 'box',
  id,
  layout: { mode: 'column', gap: 12 },
  children: [],
});
export function createDesignBlock(
  blocks: readonly FlowDesignBlock[],
  kind: 'screen' | 'state' | 'component',
  value: string,
  id: string,
  screenId: string,
) {
  const name = value.trim();
  if (!name) throw Error('이름을 입력하세요.');
  if (blocks.some((b) => b.id === id || b.design.id === id)) throw Error('이미 사용 중인 ID입니다.');
  if (kind === 'screen') {
    const settings = blocks.find((b) => b.design.kind === 'settings');
    if (!settings) throw Error('문서 설정을 찾을 수 없습니다.');
    const prefix = stringValue(settings.design.idPrefix) || 'S';
    let number = typeof settings.design.nextScreen === 'number' ? settings.design.nextScreen : 1;
    const codes = new Set(blocks.filter((b) => b.design.kind === 'screen').map((b) => b.design.code));
    let code: string;
    do {
      code = prefix + '-' + String(number++).padStart(2, '0');
    } while (codes.has(code));
    return [
      ...blocks.map((b) => (b === settings ? { ...b, design: { ...b.design, nextScreen: number } } : b)),
      block(id, { id, kind, name, code, root: box('root') }),
    ];
  }
  if (kind === 'state') {
    const screen = sourceBlock(blocks, screenId, 'screen');
    if (!screen || screen.design.frame === 'note') throw Error('디자인 화면을 선택하세요.');
    return [...blocks, block(id, { id, kind, name, screen: screenId, changes: [] })];
  }
  const root = {
    ...box('root'),
    style: { fill: '#ffffff' },
    children: [{ id: 'label', kind: 'text', text: { prop: 'caption' } }],
  };
  return [
    ...blocks,
    block(id, {
      id,
      kind,
      name,
      props: [{ id: 'caption', name: '문구', type: 'text', initial: name }],
      root,
    }),
  ];
}

export function addDesignChild(
  blocks: readonly FlowDesignBlock[],
  screen: string,
  state: string | undefined,
  parent: DesignAddress,
  newNode: DesignObject,
) {
  const parentNode = findDesignNode(blocks, parent);
  if (!parentNode || !['box', 'group'].includes(stringValue(parentNode.kind)))
    throw Error('요소를 추가할 상자를 선택하세요.');
  const free = object(parentNode.layout)?.mode === 'free' || parentNode.kind === 'group';
  const offset = 16 + records(parentNode.children).length * 12;
  const child =
    free && newNode.position === undefined
      ? {
          ...newNode,
          position: { mode: 'absolute', x: { start: offset }, y: { start: offset } },
          size: {
            x: { mode: 'fixed', px: 160 },
            ...(newNode.kind === 'box' ? { y: { mode: 'fixed', px: 64 } } : {}),
          },
        }
      : newNode;
  if (state) {
    const current = sourceBlock(blocks, state, 'state');
    if (current?.design.screen !== screen) throw Error('상태가 다른 화면에 속합니다.');
    const insert = { op: 'insert', at: { parent: { ...parent }, edge: 'last' }, node: child };
    return blocks.map((b) =>
      b === current ? { ...b, design: { ...b.design, changes: [...records(b.design.changes), insert] } } : b,
    );
  }
  if (parent.owner !== screen || parent.via?.length) throw Error('이 화면의 상자를 선택하세요.');
  return editDesignCollection(blocks, parent, (n) => {
    if (n.kind !== 'box' && n.kind !== 'group') throw Error('요소를 추가할 상자를 선택하세요.');
    return { ...n, children: [...records(n.children), child] };
  });
}

export function makeDesignChild(
  kind: 'text' | 'box' | 'instance',
  id: string,
  value: string,
  blocks: readonly FlowDesignBlock[],
): DesignObject {
  if (kind === 'box')
    return {
      ...box(id),
      name: value || '상자',
      size: { y: { mode: 'fixed', px: 64 } },
      style: { fill: '#eeeeee' },
    };
  if (kind === 'text') return { id, kind, text: value || '텍스트' };
  if (!sourceBlock(blocks, value, 'component')) throw Error('컴포넌트를 선택하세요.');
  return { id, kind, component: value };
}

export function addDesignAnnotation(
  blocks: readonly FlowDesignBlock[],
  screen: string,
  state: string | undefined,
  a: DesignAddress,
  id: string,
  name: string,
  description: string,
) {
  if (
    !sourceBlock(blocks, screen, 'screen') ||
    (state && sourceBlock(blocks, state, 'state')?.design.screen !== screen)
  )
    throw Error('화면을 찾을 수 없습니다.');
  if (!findDesignNode(blocks, a)) throw Error('주석을 달 요소를 찾을 수 없습니다.');
  if (!name.trim()) throw Error('주석 이름을 입력하세요.');
  return [
    ...blocks,
    block(id, {
      id,
      kind: 'annotation',
      screen,
      ...(state ? { state } : {}),
      target: { ...a },
      name: name.trim(),
      description,
    }),
  ];
}

export function setDesignFrame(
  blocks: readonly FlowDesignBlock[],
  screen: string,
  state: string | undefined,
  width: number,
  height: number,
  changed?: 'width' | 'height',
) {
  if (!Number.isFinite(width) || width <= 0 || !Number.isFinite(height) || height <= 0)
    throw Error('가로와 세로는 0보다 커야 합니다.');
  const target = sourceBlock(blocks, state ?? screen, state ? 'state' : 'screen');
  if (!target || (state && target.design.screen !== screen)) throw Error('화면을 찾을 수 없습니다.');
  const updated =
    state && changed
      ? editDesignValue(target.design, ['frame', changed], () => (changed === 'width' ? width : height))
      : { ...target.design, frame: { width, height } };
  return blocks.map((b) => (b === target ? { ...b, design: updated } : b));
}
