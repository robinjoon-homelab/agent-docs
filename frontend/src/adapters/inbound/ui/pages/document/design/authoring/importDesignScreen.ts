import type { DefaultTreeAdapterTypes as Tree } from 'parse5';
import {
  equalDesignValues,
  isDesignObject,
  type DesignJson,
  type DesignObject,
  type FlowDesignBlock,
} from '../../../../../../../domain/document/designBlock';
import { readLayerTree, type Resources } from './markupLayers';
import { stylesheet } from './markupStyle';
import {
  allowKeys,
  at,
  attributes,
  designEntries,
  elements,
  identifier,
  parseMarkup,
  plainText,
  requireMarkup,
  type Element,
} from './markupTree';

function modelId(obj: DesignObject): string {
  requireMarkup(typeof obj.id === 'string', '저장된 디자인에 ID가 없습니다.');
  return obj.id;
}
function byId(items: DesignObject[]): Map<string, DesignObject> {
  return new Map(items.map((o) => [modelId(o), o]));
}
// An import may refer to existing resources, but cannot redefine a component or token people already edited.
function mergeDefinitions(base: DesignObject[], added: DesignObject[]): DesignObject[] {
  const definitions = byId(base),
    inSource = new Set<string>();
  for (const item of added) {
    const id = modelId(item);
    requireMarkup(!inSource.has(id), '중복된 선언: ' + id);
    inSource.add(id);
    const old = definitions.get(id);
    requireMarkup(!old || equalDesignValues(old, item), '기존 정의와 충돌합니다: ' + id);
    definitions.set(id, item);
  }
  return [...definitions.values()];
}
function template(node: Element) {
  const a = attributes(node);
  allowKeys(a, ['data-component', 'data-name', 'data-props']);
  const id = identifier(a['data-component']);
  const props = (a['data-props'] ?? '').trim().split(/\s+/).filter(Boolean).map(identifier);
  requireMarkup(new Set(props).size === props.length, '중복된 컴포넌트 속성입니다.');
  const kids = elements((node as Tree.Template).content);
  requireMarkup(kids.length === 1 && kids[0], '컴포넌트에는 루트 상자 하나가 필요합니다.');
  return {
    id,
    name: a['data-name'] ?? id,
    props: props.map((prop) => ({ id: prop, name: prop, type: 'text' })),
    root: kids[0],
  };
}
function readSource(source: string) {
  const sheets: string[] = [],
    templates: ReturnType<typeof template>[] = [];
  let root: Element | undefined;
  for (const el of parseMarkup(source)) {
    if (el.tagName === 'style')
      sheets.push(
        at(el, () => {
          allowKeys(attributes(el), []);
          return plainText(el);
        }),
      );
    else if (el.tagName === 'template') templates.push(at(el, () => template(el)));
    else {
      requireMarkup(!root, '화면에는 루트 상자 하나만 둘 수 있습니다.');
      root = el;
    }
  }
  requireMarkup(root, '화면의 루트 상자가 필요합니다.');
  return { root, sheets, templates };
}
function checkCycles(components: readonly DesignObject[]): void {
  const defs = byId([...components]),
    done = new Set<string>(),
    active = new Set<string>();
  function traverse(value: DesignJson): void {
    if (!isDesignObject(value)) return;
    if (value.kind === 'instance' && typeof value.component === 'string') visit(value.component);
    for (const field of ['children', 'template', 'fallback', 'operands']) {
      for (const child of designEntries(value[field])) traverse(child);
    }
    if (isDesignObject(value.source)) traverse(value.source);
    if (isDesignObject(value.slots))
      for (const contents of Object.values(value.slots)) {
        for (const child of designEntries(contents)) traverse(child);
      }
  }
  function visit(id: string): void {
    if (done.has(id)) return;
    requireMarkup(!active.has(id), '순환 컴포넌트 참조: ' + id);
    active.add(id);
    const def = defs.get(id);
    if (def?.root) traverse(def.root);
    active.delete(id);
    done.add(id);
  }
  for (const id of defs.keys()) visit(id);
}
function nextScreen(settings: DesignObject, blocks: readonly FlowDesignBlock[]) {
  const prefix = settings.idPrefix;
  requireMarkup(
    typeof prefix === 'string' &&
      typeof settings.nextScreen === 'number' &&
      Number.isSafeInteger(settings.nextScreen) &&
      settings.nextScreen > 0,
    '화면 번호 설정을 읽을 수 없습니다.',
  );
  const codes = new Set(blocks.filter((b) => b.design.kind === 'screen').map((b) => b.design.code));
  let n = settings.nextScreen;
  let code: string;
  do {
    code = (prefix || 'S') + '-' + String(n++).padStart(2, '0');
  } while (codes.has(code));
  requireMarkup(Number.isSafeInteger(n), '다음 화면 번호가 너무 큽니다.');
  return { code, next: n };
}
export function importDesignScreen(
  source: string,
  blocks: readonly FlowDesignBlock[],
  screenName: string,
  makeBlockId: () => string,
): { blocks: FlowDesignBlock[]; screenBlockId: string } {
  const settings = blocks[0],
    name = screenName.trim();
  requireMarkup(
    settings?.design.kind === 'settings' && settings.design.modelVersion === 4,
    'v4 화면 흐름 문서를 먼저 열어 주세요.',
  );
  requireMarkup(name, '새 화면의 이름을 입력해 주세요.');
  const input = readSource(source);
  const library = blocks.find((b) => b.design.kind === 'tokens');
  const existingTokens = designEntries(library?.design.entries),
    existingStyles = designEntries(library?.design.styles);
  const tokens = new Set(existingTokens.filter((t) => t.type === 'color').map(modelId));
  const rules = stylesheet(input.sheets, tokens);
  const entries = mergeDefinitions(existingTokens, rules.tokens),
    styles = mergeDefinitions(existingStyles, rules.styles);
  const components = blocks.filter((b) => b.design.kind === 'component').map((b) => b.design);
  const catalog = new Map(components.map((c) => [modelId(c), byId(designEntries(c.props))]));
  for (const t of input.templates) catalog.set(t.id, byId(t.props));
  const context: Resources = { components: catalog, styles: new Set(styles.map(modelId)), tokens };
  const additions = input.templates.map((t) => ({
    kind: 'component',
    id: t.id,
    name: t.name,
    props: t.props,
    root: readLayerTree(t.root, context, new Set(t.props.map((p) => p.id))),
  }));
  const merged = mergeDefinitions(components, additions);
  checkCycles(merged);
  const root = readLayerTree(input.root, context),
    { code, next } = nextScreen(settings.design, blocks);
  const usedModel = new Set(blocks.map((b) => modelId(b.design))),
    usedBlock = new Set(blocks.map((b) => b.id));
  const result: FlowDesignBlock[] = blocks.map((b) =>
    b === settings ? { ...b, design: { ...b.design, nextScreen: next } } : b,
  );
  function add(design: DesignObject): string {
    const id = makeBlockId();
    requireMarkup(!usedBlock.has(id), '새 블록 ID가 이미 사용 중입니다. 다시 시도해 주세요.');
    const key = typeof design.id === 'string' ? design.id : id;
    requireMarkup(!usedModel.has(key), '디자인 ID가 이미 사용 중입니다: ' + key);
    usedBlock.add(id);
    usedModel.add(key);
    result.push({ type: 'flowDesign', id, design: { ...design, id: key } });
    return id;
  }
  if (rules.tokens.length || rules.styles.length) {
    const design: DesignObject = {
      ...library?.design,
      kind: 'tokens',
      entries,
      ...(styles.length ? { styles } : {}),
    };
    if (library) result[result.indexOf(library)] = { ...library, design };
    else add(design);
  }
  for (const component of merged.slice(components.length)) add(component);
  const screenBlockId = add({ kind: 'screen', name, code, root });
  return { blocks: result, screenBlockId };
}
