import type { DesignJson, DesignObject, FlowDesignBlock } from '../document/designBlock';
import { FRAMES, NOTE_FRAME } from './flow';
import { atOccurrence, designAddress, designAddressKey } from './designAddress';
import type { DesignAddress, DesignHop, DesignOccurrence } from './designAddress';
import { applyNodeChange } from './designChanges';
import { asNumber, asObject, asObjects, asText, isDesignObject, mergeDesignFields } from './designValue';

export { designAddressKey } from './designAddress';
export type { DesignAddress, DesignHop, DesignOccurrence } from './designAddress';
export type DesignFrame = { width: number; height: number };
export type DesignScene = {
  root: DesignOccurrence | null;
  frame: DesignFrame;
  warnings: string[];
  emptySlots: readonly string[];
  inputs: ReadonlyMap<string, DesignObject>;
};
type Occurrence = {
  node: DesignObject;
  address: DesignAddress;
  mounts: DesignAddress[];
  children: Occurrence[];
  maskSource?: Occurrence;
};
type Scope = {
  owner: string;
  via: readonly DesignHop[];
  props: DesignObject;
  types: DesignObject;
  rows: DesignObject;
  rowTypes: DesignObject;
  slots: ReadonlyMap<string, Slot>;
  theme?: string;
  ancestors: readonly string[];
  mountDepth: number;
};
type Slot = { nodes: readonly DesignObject[]; scope: Scope };
type Change = { value: DesignObject; key?: string; scope: Scope };
function environment(owner: string, theme?: string): Scope {
  return {
    owner,
    via: [],
    props: {},
    types: {},
    rows: {},
    rowTypes: {},
    slots: new Map(),
    theme,
    ancestors: [],
    mountDepth: 0,
  };
}
function idMap(values: readonly DesignObject[]): Map<string, DesignObject> {
  return new Map(values.flatMap((v) => (typeof v.id === 'string' ? [[v.id, v] as const] : [])));
}
function scopedChange(value: DesignObject, scope: Scope, via = scope.via): Change {
  const a = designAddress(value.target);
  return { value, scope, key: a ? designAddressKey(atOccurrence(a, via)) : undefined };
}
function valuesFor(props: readonly DesignObject[]): DesignObject {
  return Object.fromEntries(
    props.flatMap((p) => {
      if (typeof p.id !== 'string') return [];
      const fallback =
        p.type === 'boolean' ? false : p.type === 'list' ? [] : p.type === 'text' ? '' : undefined;
      const v = p.initial ?? fallback;
      return v === undefined ? [] : [[p.id, v]];
    }),
  );
}
function frameFor(settings: DesignObject, screen: DesignObject, state: DesignObject): DesignFrame {
  const f = screen.frame ?? settings.frame;
  const preset =
    f === 'note' ? NOTE_FRAME : (Object.entries(FRAMES).find(([name]) => name === f)?.[1] ?? FRAMES.desktop);
  const base = asObject(f),
    delta = asObject(state.frame);
  return {
    width: asNumber(delta.width) ?? asNumber(base.width) ?? preset[1],
    height: asNumber(delta.height) ?? asNumber(base.height) ?? preset[2],
  };
}
const placement = [
  'position',
  'size',
  'aspectRatio',
  'boxSizing',
  'cell',
  'flexItem',
  'alignSelf',
  'margin',
  'themeMode',
  'sharedStyle',
  'style',
  'visible',
  'locked',
  'role',
  'accessibleName',
  'localTransform',
];
class SceneBuilder {
  private readonly blocks: Map<string, DesignObject>;
  private readonly tokens: Map<string, DesignObject>;
  private readonly styles: Map<string, DesignObject>;
  private readonly index = new Map<string, Occurrence>();
  private readonly parents = new Map<Occurrence, Occurrence>();
  private readonly warnings = new Set<string>();
  private readonly emptySlots: string[] = [];
  private readonly inputs = new Map<string, DesignObject>();
  private readonly applied = new Set<Change>();

  constructor(blocks: readonly FlowDesignBlock[]) {
    const data = blocks.map((b) => b.design);
    this.blocks = idMap(data);
    const tokenBlock = data.find((b) => b.kind === 'tokens');
    this.tokens = idMap(asObjects(tokenBlock?.entries));
    this.styles = idMap(asObjects(tokenBlock?.styles));
  }
  private reference(value: DesignJson | undefined, scope: Scope, chain: readonly string[] = []): DesignJson {
    if (value === undefined) return null;
    if (Array.isArray(value))
      return (value as readonly DesignJson[]).map((v) => this.reference(v, scope, chain));
    if (!isDesignObject(value)) return value;
    if (typeof value.prop === 'string' && Object.keys(value).length === 1)
      return Object.hasOwn(scope.props, value.prop) ? (scope.props[value.prop] ?? null) : null;
    if (typeof value.repeat === 'string' && typeof value.field === 'string')
      return asObject(scope.rows[value.repeat])[value.field] ?? null;
    if (value.token !== undefined && !value.kind && Object.keys(value).length === 1) {
      const id = asText(value.token),
        token = id ? this.tokens.get(id) : undefined;
      if (!token || !id || chain.includes(id)) {
        this.warnings.add(
          '토큰을 해석할 수 없습니다: ' + (id ?? asText(asObject(value.token).unresolvedName) ?? ''),
        );
        return null;
      }
      const mode = asObjects(token.modes).find((m) => m.mode === scope.theme);
      return this.reference(mode?.value ?? token.value, scope, [...chain, id]);
    }
    if (value.kind === 'token') {
      const paint = this.reference({ token: value.token ?? null }, scope, chain);
      if (!isDesignObject(paint)) return null;
      return mergeDesignFields(
        paint,
        Object.fromEntries(Object.entries(value).filter(([k]) => k !== 'kind' && k !== 'token')),
      );
    }
    const result = Object.fromEntries(
      Object.entries(value).map(([k, v]) => [k, this.reference(v, scope, chain)]),
    );
    if ((value.text !== undefined || value.runs !== undefined) && isDesignObject(result.typography))
      result.style = mergeDesignFields(result.typography, asObject(result.style));
    return result;
  }
  private repeat(node: DesignObject, scope: Scope, changes: readonly Change[]): Occurrence[] {
    const bound = asObject(node.items),
      statement =
        typeof bound.prop === 'string'
          ? asObject(scope.types[bound.prop])
          : asObject(asObject(scope.rowTypes[asText(bound.repeat) ?? ''])[asText(bound.field) ?? '']);
    const fields = asObjects(statement.fields),
      defaults = valuesFor(fields),
      id = asText(node.id) ?? '';
    const rowTypes = { ...scope.rowTypes, [id]: Object.fromEntries(idMap(fields)) };
    return asObjects(this.reference(node.items, scope)).flatMap((row) => {
      const itemKey = asText(row.itemKey);
      if (!itemKey) return [];
      const via = [...scope.via, { owner: scope.owner, repeat: id, itemKey }];
      const rows = { ...scope.rows, [id]: { ...defaults, ...asObject(row.fields) } };
      return asObjects(node.template).flatMap((c) =>
        this.expand(c, { ...scope, via, rows, rowTypes }, changes),
      );
    });
  }
  private children(values: DesignJson | undefined, scope: Scope, changes: readonly Change[]): Occurrence[] {
    return asObjects(values).flatMap((n) => this.expand(n, scope, changes));
  }
  private expand(
    src: DesignObject,
    scope: Scope,
    changes: readonly Change[],
    mount: DesignObject = {},
  ): Occurrence[] {
    if (src.kind === 'slot') {
      const slot = scope.slots.get(asText(src.prop) ?? '');
      const repeatedLocation = scope.via.slice(scope.mountDepth).filter((h) => 'repeat' in h);
      const expanded = slot
        ? slot.nodes.flatMap((n) =>
            this.expand(n, { ...slot.scope, via: [...slot.scope.via, ...repeatedLocation] }, changes),
          )
        : this.children(src.fallback, scope, changes);
      if (!expanded.length)
        this.emptySlots.push(
          designAddressKey({ owner: scope.owner, node: asText(src.id) ?? '', via: scope.via }),
        );
      return expanded;
    }
    if (src.kind === 'repeat') return this.repeat(src, scope, changes);
    const id = asText(src.id);
    if (!id) return [];
    const address: DesignAddress = {
        owner: scope.owner,
        node: id,
        ...(scope.via.length ? { via: scope.via } : {}),
      },
      key = designAddressKey(address);
    const own = changes.filter((c) => c.key === key);
    own.forEach((c) => this.applied.add(c));
    let node = own.reduce((n, c) => applyNodeChange(n, c.value, (v) => this.reference(v, scope)), src);
    node = mergeDesignFields(node, mount);
    if (node.kind === 'instance') return this.instance(node, scope, changes, own, address);
    const local = { ...scope, theme: asText(node.themeMode) ?? scope.theme };
    const fields = Object.fromEntries(
      Object.entries(node).filter(([k]) => !['children', 'operands', 'source'].includes(k)),
    );
    const data = asObject(this.reference(fields, local));
    const merged = asObject(this.reference(this.styles.get(asText(node.sharedStyle) ?? '')?.value, local));
    let style = mergeDesignFields(merged, asObject(data.style));
    if (isDesignObject(style.typography))
      style = { ...style, text: mergeDesignFields(style.typography, asObject(style.text)) };
    const layer: Occurrence = {
      node: { ...data, style },
      address,
      mounts: [],
      children: this.children(node.kind === 'boolean' ? node.operands : node.children, local, changes),
    };
    if (node.kind === 'mask' && isDesignObject(node.source))
      layer.maskSource = this.expand(node.source, local, changes)[0];
    for (const child of layer.children) this.parents.set(child, layer);
    this.index.set(key, layer);
    return [layer];
  }
  private instance(
    n: DesignObject,
    scope: Scope,
    changes: readonly Change[],
    own: readonly Change[],
    address: DesignAddress,
  ): Occurrence[] {
    const id = asText(n.component),
      component = id ? this.blocks.get(id) : undefined;
    if (!id || component?.kind !== 'component' || scope.ancestors.includes(id)) {
      this.warnings.add(
        '컴포넌트를 해석할 수 없습니다: ' + (id ?? asText(asObject(n.component).unresolvedName) ?? ''),
      );
      return [{ node: { ...n, kind: 'unresolved' }, address, mounts: [], children: [] }];
    }
    const defs = asObjects(component.props),
      props = { ...valuesFor(defs), ...asObject(this.reference(n.inputs, scope)) };
    this.inputs.set(designAddressKey(address), props);
    const slots = new Map<string, Slot>(
      Object.entries(asObject(n.slots)).map(([k, v]) => [k, { nodes: asObjects(v), scope }]),
    );
    for (const c of own.filter((c) => c.value.op === 'slot')) {
      const k = asText(c.value.prop),
        assign = asObject(c.value.assignment);
      if (k && assign.fallback === true) slots.delete(k);
      else if (k) slots.set(k, { nodes: asObjects(assign.children), scope: c.scope });
    }
    const child: Scope = {
      owner: id,
      via: [...scope.via, { owner: scope.owner, instance: address.node, component: id }],
      props,
      types: Object.fromEntries(idMap(defs)),
      rows: {},
      rowTypes: {},
      slots,
      theme: asText(n.themeMode) ?? scope.theme,
      ancestors: [...scope.ancestors, id],
      mountDepth: scope.via.length + 1,
    };
    const rules = asObjects(component.variants)
      .filter((v) => {
        const w = asObject(v.when);
        return typeof w.prop === 'string' && props[w.prop] === w.choice;
      })
      .map((v) => scopedChange({ op: 'variant', target: v.target ?? null, fields: v.set ?? {} }, child));
    const overrides = asObjects(n.overrides).map((v) => scopedChange(v, scope, child.via));
    const constraints = Object.fromEntries(
      placement.filter((k) => n[k] !== undefined).map((k) => [k, this.reference(n[k], scope)]),
    );
    const layers = this.expand(
      asObject(component.root),
      child,
      [...rules, ...overrides, ...changes],
      constraints,
    );
    for (const layer of layers) {
      layer.mounts.push(address);
      this.index.set(designAddressKey(address), layer);
    }
    return layers;
  }
  private find(value: DesignJson | undefined): Occurrence | undefined {
    const a = designAddress(value);
    return a ? this.index.get(designAddressKey(a)) : undefined;
  }
  private arrange(c: Change, changes: readonly Change[]) {
    const op = c.value,
      at = asObject(op.at),
      parent = this.find(at.parent);
    if (!parent) return;
    const sibling = this.find(at.before ?? at.after);
    if ((at.before || at.after) && (!sibling || !parent.children.includes(sibling))) return;
    let items: Occurrence[];
    if (op.op === 'insert') items = this.expand(asObject(op.node), c.scope, changes);
    else {
      const node = this.find(op.target);
      if (!node || node === sibling) return;
      let current: Occurrence | undefined = parent;
      while (current) {
        if (current === node) return;
        current = this.parents.get(current);
      }
      const prev = this.parents.get(node);
      if (!prev) return;
      prev.children = prev.children.filter((n) => n !== node);
      items = [node];
    }
    const pos = sibling
      ? parent.children.indexOf(sibling) + (at.after ? 1 : 0)
      : at.edge === 'first'
        ? 0
        : parent.children.length;
    parent.children.splice(pos, 0, ...items);
    for (const item of items) this.parents.set(item, parent);
    this.applied.add(c);
  }
  scene(screenId: string, stateId?: string | null): DesignScene {
    const screen = this.blocks.get(screenId),
      settings = Array.from(this.blocks.values()).find((b) => b.kind === 'settings') ?? {};
    const selected = stateId ? this.blocks.get(stateId) : undefined;
    const state = selected?.kind === 'state' && selected.screen === screenId ? selected : {};
    const frame = frameFor(settings, screen ?? {}, state);
    if (!screen || screen.kind !== 'screen')
      return {
        root: null,
        frame,
        emptySlots: [],
        inputs: this.inputs,
        warnings: ['화면을 찾을 수 없습니다.'],
      };
    const scope = environment(screenId, asText(screen.themeMode)),
      stateScope = environment(asText(state.id) ?? screenId, scope.theme);
    const changes = asObjects(state.changes).map((c) => scopedChange(c, stateScope, []));
    const root = this.expand(asObject(screen.root), scope, changes)[0] ?? null;
    for (const c of changes) if (c.value.op === 'insert' || c.value.op === 'move') this.arrange(c, changes);
    for (const c of changes)
      if (!this.applied.has(c))
        this.warnings.add(
          '변경 대상을 찾을 수 없습니다: ' +
            (asText(asObject(c.value.target).node) ?? asText(c.value.op) ?? ''),
        );
    return {
      root,
      frame,
      emptySlots: this.emptySlots,
      warnings: Array.from(this.warnings),
      inputs: this.inputs,
    };
  }
}
export function resolveDesignScene(
  blocks: readonly FlowDesignBlock[],
  screen: string,
  state?: string | null,
): DesignScene {
  return new SceneBuilder(blocks).scene(screen, state);
}
