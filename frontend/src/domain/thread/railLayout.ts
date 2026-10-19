export type RailItem = {
  readonly id: string;
  /** Vertical position of the anchor in the document, or null for document-level/detached threads. */
  readonly anchorTop: number | null;
  readonly height: number;
};

export type RailPlacement = { readonly id: string; readonly top: number };

/**
 * Places thread cards next to their anchors without overlap. Cards keep document order; a card that
 * would overlap the previous one is pushed down. Cards without an anchor position go to the end.
 */
export function layoutRail(items: readonly RailItem[], gap: number): RailPlacement[] {
  const anchored = items
    .filter((item) => item.anchorTop !== null)
    .sort((a, b) => (a.anchorTop ?? 0) - (b.anchorTop ?? 0));
  const floating = items.filter((item) => item.anchorTop === null);
  const placements: RailPlacement[] = [];
  let cursor = 0;
  for (const item of [...anchored, ...floating]) {
    const top = Math.max(cursor, item.anchorTop ?? cursor);
    placements.push({ id: item.id, top });
    cursor = top + item.height + gap;
  }
  return placements;
}
