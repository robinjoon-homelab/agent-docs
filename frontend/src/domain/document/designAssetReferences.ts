import { isDesignObject, type DesignJson, type DesignObject, type FlowDesignBlock } from './designBlock';

const uuid = /^[0-9a-f]{8}(?:-[0-9a-f]{4}){3}-[0-9a-f]{12}$/i;

function keyOf(value: DesignJson | undefined): string {
  return typeof value === 'string' ? value : '';
}

function list(value: DesignJson | undefined): DesignObject[] {
  return Array.isArray(value) ? value.filter(isDesignObject) : [];
}

/** Property and slot names are user-defined: a text input named "asset" is not a document attachment. */
function walk(root: DesignObject, visit: (key: string, node: DesignObject) => void): void {
  const queue: [string, DesignJson][] = [['', root]];
  while (queue.length) {
    const entry = queue.pop();
    if (!entry) break;
    const [key, value] = entry;
    if (value !== null && typeof value === 'object' && !isDesignObject(value)) {
      for (const child of value) queue.push([key, child]);
      continue;
    }
    if (!isDesignObject(value)) continue;
    visit(key, value);
    for (const [field, child] of Object.entries(value)) {
      if (field === 'inputs' || field === 'initial' || (field === 'fields' && 'itemKey' in value)) continue;
      if (field === 'value' && (value.op === 'input' || value.op === 'itemSet')) continue;
      if (field === 'slots' && isDesignObject(child)) {
        for (const children of Object.values(child)) queue.push(['children', children]);
      } else if (field === 'value' && (value.op === 'leaf' || value.op === 'detailSet')) {
        queue.push([
          keyOf(value.path ?? value.field)
            .split('.')
            .at(-1) ?? '',
          child,
        ]);
      } else queue.push([field, child]);
    }
  }
}

export function designAssetReferences(blocks: readonly FlowDesignBlock[]): Set<string> {
  const ids = new Set<string>();
  const components = new Map<string, DesignObject[]>();
  const instances = new Map<string, Map<string, string>>();
  function add(value: DesignJson | undefined) {
    if (typeof value === 'string' && uuid.test(value)) ids.add(value.toLowerCase());
  }
  for (const { design } of blocks) {
    const owner = keyOf(design.id);
    if (design.kind === 'component') components.set(owner, list(design.props));
    const nodes = new Map<string, string>();
    instances.set(owner, nodes);
    walk(design, (key, node) => {
      if (node.kind === 'instance' && typeof node.component === 'string')
        nodes.set(keyOf(node.id), node.component);
      if (node.type === 'asset') add(node.initial);
      if (node.kind === 'image' || ['fields', 'set', 'image', 'font', 'fallback'].includes(key))
        add(node.asset);
      add(node.svgAsset);
      if (node.op === 'leaf' || node.op === 'detailSet') {
        const field = keyOf(node.path ?? node.field)
          .split('.')
          .at(-1);
        if (field === 'asset' || field === 'svgAsset') add(node.value);
      }
    });
  }
  function declared(prop: DesignObject | undefined, value: DesignJson | undefined) {
    if (prop?.type === 'asset') add(value);
    if (prop?.type === 'list') for (const row of list(value)) fields(list(prop.fields), row.fields);
  }
  function fields(props: DesignObject[], values: DesignJson | undefined) {
    if (!isDesignObject(values)) return;
    for (const p of props) declared(p, values[keyOf(p.id)]);
  }
  for (const { design } of blocks) {
    walk(design, (_, node) => {
      if (typeof node.component === 'string') fields(components.get(node.component) ?? [], node.inputs);
      if (!isDesignObject(node.target)) return;
      const key = instances.get(keyOf(node.target.owner))?.get(keyOf(node.target.node));
      const props = components.get(key ?? '') ?? [];
      if (isDesignObject(node.set)) fields(props, node.set.inputs);
      if (isDesignObject(node.fields)) fields(props, node.fields.inputs);
      let prop = props.find((p) => p.id === node.prop);
      for (const step of list(node.within)) prop = list(prop?.fields).find((p) => p.id === step.field);
      if (node.op === 'input') declared(prop, node.value);
      if (node.op === 'itemSet')
        declared(
          list(prop?.fields).find((p) => p.id === node.field),
          node.value,
        );
      if (node.op === 'itemInsert' && isDesignObject(node.item)) fields(list(prop?.fields), node.item.fields);
    });
  }
  return ids;
}
