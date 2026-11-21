import type {
  Block,
  FlowElementBlock,
  FlowFrame,
  FlowKind,
  FlowScreenBlock,
  FlowSettingsBlock,
} from '../document/block';
export const FRAMES: Record<FlowFrame, readonly [string, number, number]> = {
  phone: ['휴대폰', 390, 844],
  phoneSmall: ['작은 휴대폰', 360, 800],
  tabletP: ['태블릿 세로', 820, 1180],
  tabletL: ['태블릿 가로', 1180, 820],
  laptop: ['노트북', 1280, 800],
  desktop: ['데스크톱', 1440, 900],
  modal: ['팝업·모달', 480, 560],
};
export const KINDS: Record<FlowKind, string> = {
  title: '제목',
  text: '텍스트',
  field: '입력칸',
  select: '선택',
  area: '여러 줄 입력',
  button: '버튼',
  link: '링크',
  list: '목록',
  option: '선택 항목',
  notice: '알림',
  path: '값 표시',
  bar: '상단 바',
  crumb: '위치 표시',
  block: '본문 블록',
  box: '사각형',
  image: '이미지 자리',
};
export const NOTE_FRAME = ['글 페이지', 800, 600] as const;
/** A note page shows its description as wide text instead of a device and never holds elements. */
export function isNotePage(screen: FlowScreenBlock): boolean {
  return screen.frame === 'note';
}
const ASSET_VALUE = /^asset:([0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12})$/i;
/** An image placeholder whose visible text is exactly `asset:<id>` shows that uploaded image. */
export function imageAssetId(element: FlowElementBlock): string | null {
  return element.kind === 'image' ? (ASSET_VALUE.exec(element.value)?.[1] ?? null) : null;
}
export type FlowScreen = { screen: FlowScreenBlock; elements: FlowElementBlock[] };
export type Flow = { settings: FlowSettingsBlock; screens: FlowScreen[] };
export function readFlow(blocks: readonly Block[]): Flow {
  const settings = blocks[0];
  if (settings?.type !== 'flowSettings') throw new Error('화면 흐름 설정이 없습니다.');
  const screens: FlowScreen[] = [];
  for (const block of blocks.slice(1)) {
    if (block.type === 'flowScreen') screens.push({ screen: block, elements: [] });
    else if (block.type === 'flowElement' && screens.length)
      screens[screens.length - 1]?.elements.push(block);
    else throw new Error('화면 흐름 블록 순서가 올바르지 않습니다.');
  }
  if (!screens.length || new Set(screens.map((s) => s.screen.screenId)).size !== screens.length)
    throw new Error('화면 ID는 유일하고 화면은 하나 이상이어야 합니다.');
  return { settings, screens };
}
export function flowBlocks(flow: Flow): Block[] {
  return [flow.settings, ...flow.screens.flatMap((s) => [s.screen, ...s.elements])];
}
export function frameSize(flow: Flow, screen: FlowScreenBlock): readonly [string, number, number] {
  if (screen.frame === 'note') return NOTE_FRAME;
  return screen.frame === 'custom'
    ? ['직접 입력', screen.frameW ?? 390, screen.frameH ?? 844]
    : FRAMES[screen.frame ?? flow.settings.frame];
}
/** '맞춤' fits the whole frame into the measured preview area and never enlarges it past 100%. */
export function fitScale(frameW: number, frameH: number, area: { width: number; height: number }): number {
  const ratio = Math.min(1, area.width / frameW, area.height / frameH);
  return Math.max(0.1, Math.floor(ratio * 100) / 100);
}
export function nextScreenId(settings: FlowSettingsBlock): string {
  return `${settings.idPrefix || 'S'}-${String(settings.nextScreen).padStart(2, '0')}`;
}
export function numbered(elements: readonly FlowElementBlock[]): FlowElementBlock[] {
  return elements.filter((e) => e.annotated).sort((a, b) => a.y - b.y || a.x - b.x);
}
export function nextScreens(screen: FlowScreen): string[] {
  return [
    ...new Set(
      numbered(screen.elements).flatMap((e) => e.actions.flatMap((a) => (a.to === null ? [] : [a.to]))),
    ),
  ];
}
export function warnings(flow: Flow, screen: FlowScreen, element: FlowElementBlock): string[] {
  return [
    element.annotated && !element.desc.trim() ? '설명 없음' : '',
    element.actions.some((a) => a.to !== null && !flow.screens.some((s) => s.screen.screenId === a.to))
      ? '없는 화면'
      : '',
    element.x + element.w > frameSize(flow, screen.screen)[1] ? '틀 밖' : '',
  ].filter(Boolean);
}
export function fitElement(e: FlowElementBlock, width: number): FlowElementBlock {
  const w = Math.min(width, e.w);
  return { ...e, w, x: Math.max(0, Math.min(e.x, width - w)), y: Math.min(10000, Math.max(0, e.y)) };
}
export function newFlow(
  settingsId: string,
  screenId: string,
  frame: FlowFrame = 'phone',
  idPrefix = 'M',
): Block[] {
  const settings: FlowSettingsBlock = {
    id: settingsId,
    type: 'flowSettings',
    frame,
    idPrefix,
    nextScreen: 2,
  };
  return [
    settings,
    {
      id: screenId,
      type: 'flowScreen',
      screenId: nextScreenId({ ...settings, nextScreen: 1 }),
      name: '새 화면',
      entry: '',
      desc: '',
      frame: null,
      frameW: null,
      frameH: null,
    },
  ];
}
export function newElement(
  id: string,
  kind: FlowKind,
  width: number,
  height: number,
  after?: FlowElementBlock,
): FlowElementBlock {
  const defaults: Record<FlowKind, { w: number; h: number; label: string; value: string }> = {
    title: { w: 240, h: 30, label: '제목', value: '제목' },
    text: { w: 300, h: 40, label: '안내 문구', value: '안내 문구를 입력하세요' },
    field: { w: 354, h: 58, label: '입력칸', value: '' },
    select: { w: 354, h: 58, label: '선택', value: '항목을 고르세요' },
    area: { w: 354, h: 110, label: '여러 줄 입력', value: '' },
    button: { w: 354, h: 44, label: '버튼', value: '버튼' },
    link: { w: 160, h: 26, label: '링크', value: '링크' },
    list: { w: 354, h: 132, label: '목록', value: '' },
    option: { w: 354, h: 44, label: '선택 항목', value: '선택 항목' },
    notice: { w: 354, h: 44, label: '알림', value: '알림 문구' },
    path: { w: 354, h: 30, label: '값 표시', value: '값' },
    bar: { w: 390, h: 44, label: '상단 바', value: '' },
    crumb: { w: 300, h: 20, label: '위치 표시', value: '스페이스 · 경로' },
    block: { w: 354, h: 48, label: '본문 블록', value: '본문' },
    box: { w: 200, h: 120, label: '사각형', value: '' },
    image: { w: 354, h: 180, label: '이미지', value: '이미지' },
  };
  const d = defaults[kind],
    w = kind === 'bar' ? width : Math.min(d.w, width - 32);
  return {
    id,
    type: 'flowElement',
    kind,
    label: d.label,
    value: d.value,
    items: kind === 'list' ? ['항목 1', '항목 2', '항목 3'] : null,
    date: null,
    look:
      kind === 'button'
        ? 'primary'
        : kind === 'title'
          ? 'large'
          : kind === 'box'
            ? 'white'
            : ['field', 'select', 'area'].includes(kind)
              ? 'normal'
              : kind === 'notice'
                ? 'info'
                : null,
    x: kind === 'bar' ? 0 : after ? Math.min(after.x, Math.max(0, width - w)) : Math.round((width - w) / 2),
    y: kind === 'bar' ? 0 : after ? after.y + after.h + 12 : Math.max(16, Math.round(height / 3 - d.h / 2)),
    w,
    h: d.h,
    pinned: false,
    group: null,
    annotated: kind !== 'title' && kind !== 'box',
    desc: '',
    actions: [],
    states: [],
  };
}

/** The sequence survives undo, deletion, and a stale client attempting to lower it. */
export function normalizeFlow(blocks: readonly Block[], previous: readonly Block[] = []): Block[] {
  const flow = readFlow(blocks);
  const old = previous[0];
  const highest = Math.max(...flow.screens.map((s) => Number(s.screen.screenId.split('-').at(-1))));
  if (
    !/^[A-Z0-9]{0,4}$/.test(flow.settings.idPrefix) ||
    flow.screens.some((s) => !/^[A-Z0-9]{0,4}-[0-9]{2,}$/.test(s.screen.screenId))
  )
    throw new Error('화면 ID 형식이 올바르지 않습니다.');
  return flowBlocks({
    ...flow,
    settings: {
      ...flow.settings,
      nextScreen: Math.max(
        flow.settings.nextScreen,
        old?.type === 'flowSettings' ? old.nextScreen : 1,
        highest + 1,
      ),
    },
  });
}
export type Rect = { x: number; y: number; w: number; h: number };
export function bounds(elements: readonly Rect[]): Rect {
  const x = Math.min(...elements.map((e) => e.x)),
    y = Math.min(...elements.map((e) => e.y));
  return {
    x,
    y,
    w: Math.max(...elements.map((e) => e.x + e.w)) - x,
    h: Math.max(...elements.map((e) => e.y + e.h)) - y,
  };
}
export function snapMove(
  rect: Rect,
  dx: number,
  dy: number,
  others: readonly Rect[],
  width: number,
  alt = false,
): { x: number; y: number; guides: { axis: 'x' | 'y'; value: number }[] } {
  let x = alt ? Math.round(rect.x + dx) : Math.round((rect.x + dx) / 4) * 4,
    y = alt ? Math.round(rect.y + dy) : Math.round((rect.y + dy) / 4) * 4;
  const guides: { axis: 'x' | 'y'; value: number }[] = [];
  if (!alt)
    for (const axis of ['x', 'y'] as const) {
      const size = axis === 'x' ? 'w' : 'h';
      const origin = axis === 'x' ? x : y;
      const own = [0, rect[size] / 2, rect[size]];
      const targets = [
        ...(axis === 'x' ? [0, width / 2, width] : [0]),
        ...others.flatMap((e) => [e[axis], e[axis] + e[size] / 2, e[axis] + e[size]]),
      ];
      const matches = targets
        .flatMap((target) => own.map((offset) => ({ delta: target - origin - offset, target })))
        .filter((m) => Math.abs(m.delta) <= 4)
        .sort((a, b) => Math.abs(a.delta) - Math.abs(b.delta));
      const match = matches[0];
      if (match) {
        if (axis === 'x') x += match.delta;
        else y += match.delta;
        guides.push({ axis, value: match.target });
      }
    }
  return {
    x: Math.max(0, Math.min(width - rect.w, Math.round(x))),
    y: Math.min(10000, Math.max(0, Math.round(y))),
    guides,
  };
}
export function alignElements(
  elements: readonly FlowElementBlock[],
  ids: readonly string[],
  operation: 'left' | 'center' | 'right' | 'top' | 'middle' | 'bottom' | 'horizontal' | 'vertical',
): FlowElementBlock[] {
  const selected = elements.filter((e) => ids.includes(e.id) && !e.pinned);
  if (selected.length < 2) return [...elements];
  const box = bounds(selected);
  const horizontal = operation === 'horizontal';
  const sorted = [...selected].sort((a, b) => (horizontal ? a.x - b.x : a.y - b.y));
  let cursor = horizontal ? box.x : box.y;
  const gap =
    ((horizontal ? box.w : box.h) - selected.reduce((n, e) => n + (horizontal ? e.w : e.h), 0)) /
    (selected.length - 1);
  const spaced = new Map(
    sorted.map((e) => {
      const value = cursor;
      cursor += (horizontal ? e.w : e.h) + gap;
      return [e.id, Math.round(value)];
    }),
  );
  return elements.map((e) =>
    !selected.includes(e)
      ? e
      : {
          ...e,
          ...(operation === 'left'
            ? { x: box.x }
            : operation === 'center'
              ? { x: Math.round(box.x + (box.w - e.w) / 2) }
              : operation === 'right'
                ? { x: box.x + box.w - e.w }
                : operation === 'top'
                  ? { y: box.y }
                  : operation === 'middle'
                    ? { y: Math.round(box.y + (box.h - e.h) / 2) }
                    : operation === 'bottom'
                      ? { y: box.y + box.h - e.h }
                      : horizontal
                        ? { x: spaced.get(e.id) ?? e.x }
                        : { y: spaced.get(e.id) ?? e.y }),
        },
  );
}
export type FlowDifference = { readonly field: string; readonly before: string; readonly after: string };
type ComparedField = { label: string; value: unknown; text: string };
function elementFields(element: FlowElementBlock): ComparedField[] {
  const modes: Record<string, string> = {
    same: '같은 화면',
    page: '페이지 이동',
    sheet: '시트',
    sheetClose: '시트 닫힘',
    popup: '팝업',
    newWindow: '새 창',
    error: '오류 표시',
  };
  return [
    { label: '종류', value: element.kind, text: KINDS[element.kind] },
    { label: '이름', value: element.label, text: element.label },
    { label: '화면 글자', value: element.value, text: element.value || '(비어 있음)' },
    { label: '목록 항목', value: element.items, text: element.items?.join(', ') ?? '없음' },
    { label: '날짜', value: element.date, text: element.date ?? '없음' },
    { label: '모양', value: element.look, text: element.look ?? '기본' },
    { label: '설명', value: element.desc, text: element.desc || '(비어 있음)' },
    {
      label: '누르면',
      value: element.actions.map((a) => [a.when, a.to, a.mode]),
      text:
        element.actions.map((a) => `${a.when} → ${a.to ?? '이동 없음'} (${modes[a.mode]})`).join('\n') ||
        '이동 없음',
    },
    { label: '상태', value: element.states, text: element.states.join(', ') || '없음' },
    { label: '번호·설명', value: element.annotated, text: element.annotated ? '있음' : '없음' },
    {
      label: '위치·크기',
      value: [element.x, element.y, element.w, element.h],
      text: `x ${element.x}, y ${element.y} · ${element.w}×${element.h}`,
    },
  ];
}
function screenFields(screen: FlowScreenBlock): ComparedField[] {
  return [
    { label: '화면 이름', value: screen.name, text: screen.name },
    { label: '진입 경로', value: screen.entry, text: screen.entry },
    { label: '화면 설명', value: screen.desc, text: screen.desc },
    {
      label: '기기 틀',
      value: [
        screen.frame,
        screen.frame === 'custom' ? screen.frameW : null,
        screen.frame === 'custom' ? screen.frameH : null,
      ],
      text:
        screen.frame === null
          ? '문서 기본값'
          : screen.frame === 'custom'
            ? `직접 입력 ${screen.frameW}×${screen.frameH}`
            : screen.frame === 'note'
              ? NOTE_FRAME[0]
              : FRAMES[screen.frame][0],
    },
  ];
}
function fieldDifferences(before: ComparedField[], after: ComparedField[], prefix = ''): FlowDifference[] {
  return before.flatMap((field, index) => {
    const next = after[index];
    return next && JSON.stringify(field.value) !== JSON.stringify(next.value)
      ? [{ field: prefix + field.label, before: field.text, after: next.text }]
      : [];
  });
}
/** Shares the exact target-comparison fields with the history panel. Layer order is presentation only. */
export function flowDifferences(
  before: readonly Block[],
  now: readonly Block[],
  blockId: string,
): FlowDifference[] {
  const old = readFlow(before),
    current = readFlow(now);
  const parent = old.screens.find((s) => s.screen.id === blockId || s.elements.some((e) => e.id === blockId));
  if (!parent) return [];
  const next = current.screens.find((s) => s.screen.id === parent.screen.id);
  if (!next)
    return [{ field: '화면', before: `${parent.screen.screenId} ${parent.screen.name}`, after: '삭제됨' }];
  if (parent.screen.id !== blockId) {
    const prior = parent.elements.find((e) => e.id === blockId),
      after = next.elements.find((e) => e.id === blockId);
    return prior
      ? after
        ? fieldDifferences(elementFields(prior), elementFields(after))
        : [{ field: '요소', before: prior.label, after: '삭제됨' }]
      : [];
  }
  const changes = fieldDifferences(screenFields(parent.screen), screenFields(next.screen));
  for (const element of parent.elements) {
    const after = next.elements.find((e) => e.id === element.id);
    if (after)
      changes.push(
        ...fieldDifferences(elementFields(element), elementFields(after), `요소 · ${after.label} · `),
      );
    else changes.push({ field: `요소 · ${element.label}`, before: '있음', after: '삭제됨' });
  }
  for (const element of next.elements)
    if (!parent.elements.some((e) => e.id === element.id))
      changes.push({ field: `요소 · ${element.label}`, before: '없음', after: '추가됨' });
  return changes;
}
export function flowTarget(
  before: readonly Block[],
  now: readonly Block[],
  blockId: string,
): 'ok' | 'changed' | 'elementDeleted' | 'screenDeleted' {
  const old = readFlow(before),
    current = readFlow(now);
  const parent = old.screens.find((s) => s.screen.id === blockId || s.elements.some((e) => e.id === blockId));
  if (!parent) return 'ok';
  const next = current.screens.find((s) => s.screen.id === parent.screen.id);
  if (!next) return 'screenDeleted';
  if (parent.screen.id !== blockId && !next.elements.some((e) => e.id === blockId)) return 'elementDeleted';
  return flowDifferences(before, now, blockId).length ? 'changed' : 'ok';
}

export function cloneElements(
  elements: readonly FlowElementBlock[],
  options: { id: () => string; offset: number; copyName: boolean; width: number },
): FlowElementBlock[] {
  const groups = new Map<string, string>();
  return elements.map((element) => {
    if (element.group && !groups.has(element.group)) groups.set(element.group, options.id());
    return fitElement(
      {
        ...element,
        id: options.id(),
        label: (element.label + (options.copyName ? ' 사본' : '')).slice(0, 100),
        x: element.x + options.offset,
        y: element.y + options.offset,
        group: element.group ? (groups.get(element.group) ?? null) : null,
      },
      options.width,
    );
  });
}

export function resizeElement(
  element: FlowElementBlock,
  options: { dx: number; dy: number; handle: string; width: number; alt: boolean },
): FlowElementBlock {
  if (element.pinned) return element;
  const grid = (value: number) => (options.alt ? Math.round(value) : Math.round(value / 4) * 4);
  const right = options.handle.includes('e')
    ? Math.min(options.width, Math.max(element.x + 16, grid(element.x + element.w + options.dx)))
    : Math.min(options.width, element.x + element.w);
  const bottom = options.handle.includes('s')
    ? Math.min(20000, Math.max(element.y + 16, grid(element.y + element.h + options.dy)))
    : element.y + element.h;
  const x = options.handle.includes('w')
    ? Math.max(0, Math.min(right - 16, grid(element.x + options.dx)))
    : Math.min(element.x, right - 16);
  const y = options.handle.includes('n')
    ? Math.max(0, Math.min(bottom - 16, grid(element.y + options.dy)))
    : element.y;
  return { ...element, x, y, w: Math.max(16, right - x), h: Math.max(16, Math.min(10000, bottom - y)) };
}

/** A moved/deleted element's thread still belongs to its anchored screen. */
export function anchoredScreenId(
  blockId: string | null,
  screens: readonly FlowScreen[],
  originalScreenId?: string,
): string | null {
  if (blockId === null) return null;
  return (
    originalScreenId ??
    screens.find((s) => s.screen.id === blockId || s.elements.some((e) => e.id === blockId))?.screen.id ??
    null
  );
}
