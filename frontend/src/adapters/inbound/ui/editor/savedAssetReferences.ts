import { useSyncExternalStore } from 'react';
import type { DocumentEditorSession } from '../../../../application/document/editorSession';
import { assetReferences } from '../../../../domain/document/assetReferences';

export type SavedAssetReferences = {
  readonly subscribe: (listener: () => void) => () => void;
  readonly contains: (assetId: string) => boolean;
};

export function savedAssetReferences(
  session: Pick<DocumentEditorSession, 'subscribe' | 'baseBlocks'>,
): SavedAssetReferences {
  return {
    subscribe: session.subscribe,
    contains: (assetId) => assetReferences(session.baseBlocks()).includes(assetId),
  };
}

const noSavedReferenceUpdates = () => () => undefined;

/** Only server-confirmed references may use the document-scoped attachment routes. */
export function useSavedAssetReference(assetId: string, references?: SavedAssetReferences): boolean {
  return useSyncExternalStore(
    references?.subscribe ?? noSavedReferenceUpdates,
    () => references?.contains(assetId) ?? true,
  );
}
