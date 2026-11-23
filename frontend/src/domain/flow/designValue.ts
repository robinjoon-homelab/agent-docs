import type { DesignJson, DesignObject } from '../document/designBlock';

import { isDesignObject } from '../document/designBlock';
export { isDesignObject } from '../document/designBlock';
export const asObject = (value: DesignJson | undefined): DesignObject => (isDesignObject(value) ? value : {});
export const asObjects = (value: DesignJson | undefined): DesignObject[] =>
  Array.isArray(value) ? (value as readonly DesignJson[]).filter(isDesignObject) : [];
export const asText = (value: DesignJson | undefined): string | undefined =>
  typeof value === 'string' ? value : undefined;
export const asNumber = (value: DesignJson | undefined): number | undefined =>
  typeof value === 'number' && Number.isFinite(value) ? value : undefined;

function isBinding(value: DesignObject): boolean {
  return 'prop' in value || 'repeat' in value || 'token' in value;
}

/** References and array values are atomic; unrelated authored fields still inherit. */
export function mergeDesignFields(base: DesignObject, patch: DesignObject): DesignObject {
  return Object.fromEntries([
    ...Object.entries(base),
    ...Object.entries(patch).map(([key, value]): [string, DesignJson] => {
      const earlier = base[key];
      return [
        key,
        isDesignObject(earlier) && isDesignObject(value) && !isBinding(earlier) && !isBinding(value)
          ? mergeDesignFields(earlier, value)
          : value,
      ];
    }),
  ]);
}

export function editDesignValue(
  source: DesignObject,
  path: readonly string[],
  edit: (value: DesignJson | undefined) => DesignJson | undefined,
): DesignObject {
  const [head, ...tail] = path;
  if (!head || ['constructor', 'prototype', '__proto__'].includes(head)) return source;
  const prior = source[head];
  const next = tail.length ? editDesignValue(asObject(prior), tail, edit) : edit(prior);
  if (next === undefined) return Object.fromEntries(Object.entries(source).filter(([k]) => k !== head));
  return { ...source, [head]: next };
}
