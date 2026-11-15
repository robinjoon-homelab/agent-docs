import { useCallback, useRef, useState } from 'react';
import type { ThreadUseCases } from '../../../../../application/thread/threadUseCases';
import type { Anchor, Comment, Thread } from '../../../../../domain/thread/thread';
import { useToast } from '../../common/Toast';
import { errorMessage } from '../../common/States';
import type { ThreadActions } from './ThreadCard';

function replaceThread(threads: readonly Thread[], updated: Thread): Thread[] {
  return threads.map((thread) => (thread.id === updated.id ? updated : thread));
}

export function useThreadState(
  documentId: string,
  initial: readonly Thread[],
  service: ThreadUseCases,
  onDocumentChanged?: () => Promise<void>,
) {
  const toast = useToast();
  const [threads, setThreads] = useState<readonly Thread[]>(initial);
  const mutationGeneration = useRef(0);
  const listGeneration = useRef(0);
  const commit = (change: (current: readonly Thread[]) => readonly Thread[]) => {
    mutationGeneration.current += 1;
    setThreads(change);
  };
  const guard = useCallback(
    async <T>(work: () => Promise<T>): Promise<T | null> => {
      try {
        return await work();
      } catch (error) {
        toast(errorMessage(error));
        return null;
      }
    },
    [toast],
  );

  const start = async (anchor: Anchor, body: string): Promise<Thread | null> => {
    const result = await guard(() => service.start(documentId, anchor, body));
    if (result === null || !result.ok) return null;
    commit((current) => [...current, result.value]);
    return result.value;
  };

  const update = async (work: () => Promise<Thread>): Promise<boolean> => {
    const updated = await guard(work);
    if (updated === null) return false;
    commit((current) => replaceThread(current, updated));
    return true;
  };

  const actions = (startReanchor: (thread: Thread) => void): ThreadActions => ({
    react: async (thread, emoji, add) => {
      await update(() => service.react(thread.id, emoji, add));
    },
    suggest: async (thread, before, after) =>
      update(() => service.suggest(thread.id, { anchor: thread.anchor, before, after })),
    decide: async (commentId, accept) => {
      const done = await update(() => service.decide(commentId, accept));
      if (done && accept) await guard(async () => onDocumentChanged?.());
    },
    reply: async (thread: Thread, body: string) => {
      const result = await guard(() => service.reply(thread, body));
      if (result === null || !result.ok) return false;
      commit((current) => replaceThread(current, result.value));
      return true;
    },
    resolve: async (thread: Thread) => {
      await update(() => service.resolve(thread.id));
    },
    reopen: async (thread: Thread) => {
      await update(() => service.reopen(thread.id));
    },
    editComment: async (thread: Thread, comment: Comment, body: string) => {
      const result = await guard(() => service.editComment(thread, comment.id, body));
      if (result === null || !result.ok) return false;
      commit((current) => replaceThread(current, result.value));
      return true;
    },
    deleteComment: async (thread: Thread, comment: Comment) => {
      await update(() => service.deleteComment(thread, comment.id));
    },
    startReanchor,
  });

  const reload = useCallback(
    async function reloadLatest(): Promise<void> {
      const mutation = mutationGeneration.current;
      const request = ++listGeneration.current;
      const current = await guard(() => service.list(documentId));
      if (current === null || request !== listGeneration.current) return;
      if (mutation !== mutationGeneration.current) {
        await reloadLatest();
        return;
      }
      setThreads(current);
    },
    [documentId, guard, service],
  );

  return { threads, start, update, actions, reload };
}
