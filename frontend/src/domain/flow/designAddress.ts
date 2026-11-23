import type { DesignJson, DesignObject } from '../document/designBlock';
import { asObject, asObjects, asText } from './designValue';

export type DesignHop =
  | { readonly owner: string; readonly instance: string; readonly component: string }
  | { readonly owner: string; readonly repeat: string; readonly itemKey: string };
export type DesignAddress = {
  readonly owner: string;
  readonly node: string;
  readonly via?: readonly DesignHop[];
};
export type DesignOccurrence = {
  readonly node: DesignObject;
  readonly address: DesignAddress;
  readonly mounts: readonly DesignAddress[];
  readonly children: readonly DesignOccurrence[];
  readonly maskSource?: DesignOccurrence;
};

export function designAddress(value: DesignJson | undefined): DesignAddress | undefined {
  const obj = asObject(value),
    owner = asText(obj.owner),
    node = asText(obj.node);
  if (!owner || !node) return undefined;
  const via: DesignHop[] = [];
  for (const hop of asObjects(obj.via)) {
    const at = asText(hop.owner),
      instance = asText(hop.instance),
      component = asText(hop.component),
      repeat = asText(hop.repeat),
      itemKey = asText(hop.itemKey);
    if (at && instance && component) via.push({ owner: at, instance, component });
    else if (at && repeat && itemKey) via.push({ owner: at, repeat, itemKey });
    else return undefined;
  }
  return via.length ? { owner, node, via } : { owner, node };
}
export function designAddressKey(a: DesignAddress): string {
  return JSON.stringify([
    a.owner,
    a.node,
    ...(a.via ?? []).map((h) =>
      'instance' in h
        ? [h.owner, 'instance', h.instance, h.component]
        : [h.owner, 'repeat', h.repeat, h.itemKey],
    ),
  ]);
}
export function atOccurrence(a: DesignAddress, via: readonly DesignHop[]): DesignAddress {
  return via.length ? { ...a, via: [...via, ...(a.via ?? [])] } : a;
}
