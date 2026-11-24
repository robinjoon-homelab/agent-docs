import type { ReactNode } from 'react';
import type { FlowKind } from '../../../../../../domain/document/block';
import type { FlowDesignBlock } from '../../../../../../domain/document/designBlock';
import { designAddressKey, type DesignAddress } from '../../../../../../domain/flow/designAddress';
import { KINDS, type Rect } from '../../../../../../domain/flow/flow';
import { stringValue } from '../../../../../../domain/flow/editDesign';
import { addDesignElement } from '../../../../../../domain/flow/addDesignElement';
import { randomBlockId } from '../../../editor/ids';
import { FlowIcon } from '../FlowIcon';
import type { DesignMutation } from './useDesignEditHistory';
import type { DesignLocation } from './designLocation';

function selectedRect(
  anchor: Element,
  selected: DesignAddress | undefined,
  frameWidth: number,
): Rect | undefined {
  const canvas = anchor.closest('.design-document')?.querySelector<HTMLElement>('.design-canvas');
  if (!canvas || !selected) return;
  const key = designAddressKey(selected);
  const layer = [...canvas.querySelectorAll<HTMLElement>('[data-design-node]')].find(
    (el) =>
      el.dataset.designAddress === key ||
      el.dataset.designSourceAddress === key ||
      el.dataset.designMountKeys?.split('\n').includes(key),
  );
  const root = canvas.querySelector<HTMLElement>('[data-design-node]');
  if (!layer || !root || layer === root) return;
  const bounds = root.getBoundingClientRect(),
    area = layer.getBoundingClientRect(),
    scale = canvas.getBoundingClientRect().width / frameWidth;
  if (!scale) return;
  return {
    x: Math.round((area.x - bounds.x) / scale),
    y: Math.round((area.y - bounds.y) / scale),
    w: Math.round(area.width / scale),
    h: Math.round(area.height / scale),
  };
}

export function DesignElementPalette({
  screen,
  state,
  selected,
  frame,
  mutate,
  go,
  children,
}: {
  screen: FlowDesignBlock;
  state?: FlowDesignBlock;
  selected?: DesignAddress;
  frame: { width: number; height: number };
  mutate: DesignMutation;
  go: (location: DesignLocation) => void;
  children?: ReactNode;
}) {
  function add(kind: FlowKind, button: HTMLElement) {
    const placement = selectedRect(button, selected, frame.width);
    let address: DesignAddress | undefined;
    const error = mutate((bs) => {
      const result = addDesignElement(
        bs,
        { screen: stringValue(screen.design.id), state: state ? stringValue(state.design.id) : undefined },
        kind,
        frame,
        placement,
        randomBlockId,
      );
      address = result.target;
      return result.blocks;
    });
    if (!error && address) go({ screen: screen.id, state: state?.id, address });
  }
  return (
    <aside className="flow-palette" aria-label="요소 추가">
      <strong>요소 추가</strong>
      {(Object.entries(KINDS) as [FlowKind, string][]).map(([kind, label]) => (
        <button type="button" key={kind} onClick={(e) => add(kind, e.currentTarget)}>
          <FlowIcon name="plus" size={13} />
          {label}
        </button>
      ))}
      <p>
        선택한 요소 아래 12px에, 없으면 가로 가운데·세로 1/3에 놓입니다. 상단 바는 맨 위. 놓은 뒤 끌어서
        옮기세요.
      </p>
      {children}
    </aside>
  );
}
