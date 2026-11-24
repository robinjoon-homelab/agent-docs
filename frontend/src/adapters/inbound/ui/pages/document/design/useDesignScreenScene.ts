import { useMemo } from 'react';
import type { FlowDesignBlock } from '../../../../../../domain/document/designBlock';
import { designAnnotationIndex } from '../../../../../../domain/flow/designAnnotations';
import { resolveDesignScene } from '../../../../../../domain/flow/designScene';
import { designRows } from './designLocation';
export function useDesignScreenScene(blocks: readonly FlowDesignBlock[], sc: string, st?: string) {
  const scene = useMemo(() => resolveDesignScene(blocks, sc, st), [blocks, sc, st]);
  const allRows = useMemo(() => designRows(scene.root, blocks), [scene.root, blocks]);
  const scopedAnnotations = useMemo(() => designAnnotationIndex(blocks, sc, st), [blocks, sc, st]);
  return { scene, allRows, scopedAnnotations };
}
