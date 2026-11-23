import type { DesignAddress, DesignOccurrence } from '../../../../../../domain/flow/designAddress';
import { designAddressKey } from '../../../../../../domain/flow/designAddress';
import { readAddress, stringValue } from '../../../../../../domain/flow/editDesign';
import type { FlowDesignBlock } from '../../../../../../domain/document/designBlock';

export { designQuery, type DesignLocation } from '../../../../../../domain/flow/flowAnchorLocation';
export function resolveDesignLocation(blocks: readonly FlowDesignBlock[], search: string) {
  const q = new URLSearchParams(search),
    screens = blocks.filter((b) => b.design.kind === 'screen');
  const screen = q.has('flowScreen') ? screens.find((b) => b.id === q.get('flowScreen')) : screens[0];
  const states = blocks.filter((b) => b.design.kind === 'state' && b.design.screen === screen?.design.id);
  const state = states.find((b) => b.id === q.get('flowState'));
  let address: DesignAddress | undefined,
    invalidNode = false;
  if (q.has('flowElement')) {
    let via: unknown;
    try {
      if (q.has('flowVia')) via = JSON.parse(q.get('flowVia') ?? '');
    } catch {
      invalidNode = true;
    }
    address = readAddress({
      owner: q.get('flowOwner') ?? screen?.design.id,
      node: q.get('flowElement'),
      ...(q.has('flowVia') ? { via } : {}),
    });
    if (!address) invalidNode = true;
  }
  return { screens, screen, states, state, address, invalidNode, invalidState: q.has('flowState') && !state };
}

export type DesignRow = {
  address: DesignAddress;
  occurrence: DesignOccurrence;
  depth: number;
  visible: boolean;
  parent?: DesignAddress;
  label: string;
};
export function designRows(root: DesignOccurrence | null, blocks: readonly FlowDesignBlock[]): DesignRow[] {
  const rows: DesignRow[] = [];
  function visit(n: DesignOccurrence, depth: number, parent?: DesignAddress, parentVisible = true) {
    const visible = parentVisible && n.node.visible !== false;
    let at = depth,
      ancestor = parent;
    for (const mount of [...n.mounts].reverse()) {
      const component = (n.address.via ?? []).find(
        (s) => 'instance' in s && s.owner === mount.owner && s.instance === mount.node,
      );
      const name =
        component && 'component' in component
          ? blocks.find((b) => b.design.id === component.component)?.design.name
          : undefined;
      rows.push({
        address: mount,
        occurrence: n,
        depth: at,
        visible,
        parent: ancestor,
        label: stringValue(name, mount.node) + ' · 인스턴스',
      });
      at++;
      ancestor = mount;
    }
    rows.push({
      address: n.address,
      occurrence: n,
      depth: at,
      visible,
      parent: ancestor,
      label: nodeLabel(n),
    });
    const children = n.maskSource ? [n.maskSource, ...n.children] : n.children;
    for (const c of children) visit(c, at + 1, n.address, visible);
  }
  if (root) visit(root, 0);
  return rows;
}
const kindNames: Record<string, string> = {
  box: '상자',
  text: '텍스트',
  instance: '인스턴스',
  group: '그룹',
  shape: '도형',
  vector: '벡터',
  boolean: '불리언',
  mask: '마스크',
  icon: '아이콘',
  image: '이미지',
};
function nodeLabel(n: DesignOccurrence) {
  const v = n.node;
  return (
    stringValue(v.name) ||
    stringValue(v.alias) ||
    stringValue(v.accessibleName) ||
    stringValue(v.text).slice(0, 36) ||
    kindNames[stringValue(v.kind)] ||
    n.address.node
  );
}
export function lookupRow(rows: readonly DesignRow[], a: DesignAddress | undefined) {
  if (!a) return undefined;
  const key = designAddressKey(a);
  return rows.find((r) => designAddressKey(r.address) === key);
}
