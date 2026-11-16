import { createContext } from 'react';
/** Only a successfully opened document provides this boundary; it never supplies cached data for a new document. */
export type RetainedDocument = {
  readonly documentId: string;
  readonly revoked: boolean;
  readonly canLoad: () => boolean;
  readonly unauthenticated: () => void;
};
export const RetainedDocumentContext = createContext<RetainedDocument | null>(null);
