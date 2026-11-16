import type { DocumentEventHandlers } from '../../../domain/document/documentEvents';
export interface DocumentEventsGateway {
  watch(documentId: string, invalidate: () => void, handlers?: DocumentEventHandlers): () => void;
}
