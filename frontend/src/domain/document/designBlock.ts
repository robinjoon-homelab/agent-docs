/** A design block has a revision-stable storage ID and a separate ID in the portable design. */
export type DesignJson = null | boolean | number | string | readonly DesignJson[] | DesignObject;
export type DesignObject = { readonly [key: string]: DesignJson };
export type FlowDesignBlock = {
  readonly id: string;
  readonly type: 'flowDesign';
  readonly design: DesignObject;
};

export function isDesignObject(value: unknown): value is DesignObject {
  return value !== null && typeof value === 'object' && !Array.isArray(value);
}

export function equalDesignValues(left: DesignJson, right: DesignJson): boolean {
  if (left === right) return true;
  if (Array.isArray(left) && Array.isArray(right)) {
    return (
      left.length === right.length &&
      left.every((v: DesignJson, i: number) => equalDesignValues(v, right[i] as DesignJson))
    );
  }
  if (!isDesignObject(left) || !isDesignObject(right)) return false;
  const keys = Object.keys(left);
  return (
    keys.length === Object.keys(right).length &&
    keys.every(
      (key) =>
        Object.hasOwn(right, key) && equalDesignValues(left[key] as DesignJson, right[key] as DesignJson),
    )
  );
}
