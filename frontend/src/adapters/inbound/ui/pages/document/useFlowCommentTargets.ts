import { useEffect, useState } from 'react';
import { useServices } from '../../../../../config/ServicesContext';
import type { Block, StoredBlock } from '../../../../../domain/document/block';
import type { Thread } from '../../../../../domain/thread/thread';
import { flowTarget, readFlow } from '../../../../../domain/flow/flow';
export function useFlowCommentTargets(threads: readonly Thread[], blocks: readonly Block[] | undefined) {
  const { documents } = useServices();
  const [snapshots, setSnapshots] = useState<Record<string, readonly Block[]>>({});
  const revisions = blocks
    ? threads
        .filter((t) => t.anchoredRevision !== undefined)
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
    threads.map((t) => {
      const past = snapshots[`${t.documentId}:${t.anchoredRevision}`];
      return [
        t.id,
        blocks && past && t.anchor.kind !== 'document' ? flowTarget(past, blocks, t.anchor.blockId) : 'ok',
      ];
    }),
  );
  const parents = new Map(
    threads.map((thread) => {
      const past = snapshots[`${thread.documentId}:${thread.anchoredRevision}`];
      const parent =
        past && thread.anchor.kind !== 'document'
          ? readFlow(past).screens.find(
              (screen) =>
                thread.anchor.kind !== 'document' &&
                (screen.screen.id === thread.anchor.blockId ||
                  screen.elements.some(
                    (e) => thread.anchor.kind !== 'document' && e.id === thread.anchor.blockId,
                  )),
            )
          : undefined;
      return [thread.id, parent?.screen.id];
    }),
  );
  return Object.assign(targets, { parents });
}
