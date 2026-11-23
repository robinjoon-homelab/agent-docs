import type { Block, FlowFrame } from '../document/block';
import type { FlowDesignBlock } from '../document/designBlock';

export function newDesignFlow(
  settingsId: string,
  screenId: string,
  frame: FlowFrame = 'phone',
  idPrefix = 'M',
): FlowDesignBlock[] {
  return [
    {
      type: 'flowDesign',
      id: settingsId,
      design: { kind: 'settings', id: settingsId, modelVersion: 4, frame, idPrefix, nextScreen: 2 },
    },
    {
      type: 'flowDesign',
      id: screenId,
      design: {
        kind: 'screen',
        id: screenId,
        code: `${idPrefix || 'S'}-01`,
        name: '새 화면',
        root: { kind: 'box', id: 'canvas', name: '화면', layout: { mode: 'free' }, children: [] },
      },
    },
  ];
}

export function isDesignFlow(blocks: readonly Block[]): blocks is readonly FlowDesignBlock[] {
  return (
    blocks[0]?.type === 'flowDesign' &&
    blocks[0].design.kind === 'settings' &&
    blocks[0].design.modelVersion === 4 &&
    blocks.every((block) => block.type === 'flowDesign')
  );
}
