import { useRef, useState } from 'react';
import type { DocumentEditorSession } from '../../../../../../application/document/editorSession';
import { equalDesignValues, type FlowDesignBlock } from '../../../../../../domain/document/designBlock';
import { restoreDesignEdit } from '../../../../../../domain/flow/editDesignUndo';
export type DesignMutation = (
  change: (blocks: readonly FlowDesignBlock[]) => FlowDesignBlock[],
) => string | null;
type EditRecord = { before: readonly FlowDesignBlock[]; after: readonly FlowDesignBlock[] };
const sameBlocks = (a: readonly FlowDesignBlock[], b: readonly FlowDesignBlock[]) =>
  a.length === b.length && a.every((x, i) => x.id === b[i]?.id && equalDesignValues(x.design, b[i].design));
export function useDesignEditHistory(
  session: DocumentEditorSession,
  canWrite: boolean,
  onInput: () => void,
  setMessage: (value: string) => void,
) {
  const [historySizes, setHistorySizes] = useState({ undo: 0, redo: 0 });
  const journal = useRef<{ undo: EditRecord[]; redo: EditRecord[] }>({ undo: [], redo: [] });
  function write(next: readonly FlowDesignBlock[]) {
    onInput();
    session.markEdited(next);
    void session.saveContent(next);
    setHistorySizes({ undo: journal.current.undo.length, redo: journal.current.redo.length });
    setMessage('');
  }
  const mutate: DesignMutation = (change) => {
    if (!canWrite || !session.isWritable() || session.getState().status === 'conflict') {
      const reason = '현재 편집할 수 없습니다.';
      setMessage(reason);
      return reason;
    }
    try {
      const current = session.currentBlocks();
      const model = current.filter((b): b is FlowDesignBlock => b.type === 'flowDesign');
      if (model.length !== current.length) throw Error('문서 형식이 바뀌었습니다. 다시 열어 주세요.');
      const next = change(model);
      if (sameBlocks(model, next)) return null;
      journal.current.undo = [...journal.current.undo.slice(-59), { before: model, after: next }];
      journal.current.redo = [];
      write(next);
      return null;
    } catch (error) {
      const msg = error instanceof Error ? error.message : '변경을 적용하지 못했습니다.';
      setMessage(msg);
      return msg;
    }
  };
  const undo = (back: boolean) => {
    if (!canWrite || !session.isWritable() || session.getState().status === 'conflict') return;
    const from = back ? journal.current.undo : journal.current.redo,
      to = back ? journal.current.redo : journal.current.undo,
      entry = from.at(-1);
    if (!entry) return;
    const current = session.currentBlocks().filter((b): b is FlowDesignBlock => b.type === 'flowDesign');
    try {
      const next = restoreDesignEdit(
        back ? entry.after : entry.before,
        back ? entry.before : entry.after,
        current,
      );
      from.pop();
      to.push(entry);
      write(next);
      return next;
    } catch (error) {
      setMessage(error instanceof Error ? error.message : '실행 취소를 적용하지 못했습니다.');
    }
  };
  return { mutate, undo, historySizes };
}
