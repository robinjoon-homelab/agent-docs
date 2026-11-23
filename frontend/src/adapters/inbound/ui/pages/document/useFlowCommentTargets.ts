import { useEffect, useState } from 'react';
import { useServices } from '../../../../../config/ServicesContext';
import type { Block, StoredBlock } from '../../../../../domain/document/block';
import type { Thread } from '../../../../../domain/thread/thread';
import { flowAnchorTarget } from '../../../../../domain/flow/flowAnchorComparison';
import { flowAnchorLocation } from '../../../../../domain/flow/flowAnchorLocation';
export function useFlowCommentTargets(
  threads: readonly Thread[],
  blocks: readonly Block[] | undefined,
  currentVersion?: number,
) {
  const { documents } = useServices();
  const [snapshots, setSnapshots] = useState<Record<string, readonly Block[]>>({});
  const revisions = blocks
    ? threads
        .filter(
          (t) =>
            t.anchor.kind !== 'document' &&
            t.anchoredRevision !== undefined &&
            (currentVersion === undefined ||
              t.targetVersion !== currentVersion ||
              !t.target ||
              t.target === 'screenDeleted' ||
              t.target === 'elementDeleted'),
        )
        .map((t) => `${t.documentId}:${t.anchoredRevision}`)
        .sort()
        .join(',')
    : '';
  useEffect(() => {
    let active = true;
    const keys = [...new Set(revisions.split(',').filter(Boolean))];
    void Promise.all(
      keys.map(async (key) => {
        const index = key.lastIndexOf(':');
        try {
          return [
            key,
            (await documents.revision(key.slice(0, index), Number(key.slice(index + 1)))).blocks,
          ] as const;
        } catch {
          return [key, null] as const;
        }
      }),
    ).then((entries) => {
      if (active)
        setSnapshots(
          Object.fromEntries(
            entries.filter((entry): entry is readonly [string, readonly StoredBlock[]] => entry[1] !== null),
          ),
        );
    });
    return () => {
      active = false;
    };
  }, [documents, revisions]);
  const targets = new Map(
    threads.map((thread) => {
      const past = snapshots[`${thread.documentId}:${thread.anchoredRevision}`];
      const saved =
        currentVersion !== undefined && thread.targetVersion === currentVersion ? thread.target : undefined;
      const target =
        saved ??
        (past && blocks && thread.anchor.kind !== 'document'
          ? flowAnchorTarget(past, blocks, thread.anchor.blockId, thread)
          : 'ok');
      return [thread.id, target] as const;
    }),
  );
  const parents = new Map(
    threads.map((thread) => {
      const past = snapshots[`${thread.documentId}:${thread.anchoredRevision}`];
      return [
        thread.id,
        past && thread.anchor.kind !== 'document'
          ? flowAnchorLocation(past, thread.anchor.blockId)?.screenBlockId
          : undefined,
      ] as const;
    }),
  );
  return Object.assign(targets, { parents, snapshots });
}
