import type { Block, FlowFrame, FlowKind } from '../../../../domain/document/block';
import { FRAMES, KINDS } from '../../../../domain/flow/flow';
import type { Json } from './json';
import { str, num, bool, optStr, optNum, oneOf, list, obj, strings } from './json';
export function toFlowBlock(s: Json): Block {
  const id = str(s, 'id');
  if (s.type === 'flowSettings')
    return {
      id,
      type: 'flowSettings',
      frame: oneOf(s, 'frame', Object.keys(FRAMES) as FlowFrame[]),
      idPrefix: str(s, 'idPrefix'),
      nextScreen: num(s, 'nextScreen'),
    };
  if (s.type === 'flowScreen')
    return {
      id,
      type: 'flowScreen',
      screenId: str(s, 'screenId'),
      name: str(s, 'name'),
      entry: str(s, 'entry'),
      desc: str(s, 'desc'),
      frame:
        s.frame == null
          ? null
          : oneOf(s, 'frame', [...Object.keys(FRAMES), 'custom'] as (FlowFrame | 'custom')[]),
      frameW: optNum(s, 'frameW'),
      frameH: optNum(s, 'frameH'),
    };
  return {
    id,
    type: 'flowElement',
    kind: oneOf(s, 'kind', Object.keys(KINDS) as FlowKind[]),
    label: str(s, 'label'),
    value: str(s, 'value'),
    items: s.items == null ? null : strings(s, 'items'),
    date: optStr(s, 'date'),
    look: optStr(s, 'look'),
    x: num(s, 'x'),
    y: num(s, 'y'),
    w: num(s, 'w'),
    h: num(s, 'h'),
    pinned: bool(s, 'pinned'),
    group: optStr(s, 'group'),
    annotated: bool(s, 'annotated'),
    desc: str(s, 'desc'),
    states: strings(s, 'states'),
    actions: list(s.actions, (v) => {
      const a = obj(v);
      return {
        when: str(a, 'when'),
        to: optStr(a, 'to'),
        mode: oneOf(a, 'mode', [
          'same',
          'page',
          'sheet',
          'sheetClose',
          'popup',
          'newWindow',
          'error',
        ] as const),
      };
    }),
  };
}
