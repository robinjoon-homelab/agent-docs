import type { DocumentEventHandlers } from '../../domain/document/documentEvents';
import type { DocumentEventsGateway } from './ports/DocumentEventsGateway';

export interface DocumentEventsUseCases {
  watch(documentId: string, invalidate: () => void, handlers?: DocumentEventHandlers): () => void;
}

export function createDocumentEventsUseCases(gateway: DocumentEventsGateway): DocumentEventsUseCases {
  return { watch: (documentId, invalidate, handlers) => gateway.watch(documentId, invalidate, handlers) };
}
