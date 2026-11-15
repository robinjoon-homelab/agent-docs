import type { DocumentEventsGateway } from './ports/DocumentEventsGateway';

export interface DocumentEventsUseCases {
  watch(documentId: string, invalidate: () => void): () => void;
}

export function createDocumentEventsUseCases(gateway: DocumentEventsGateway): DocumentEventsUseCases {
  return { watch: (documentId, invalidate) => gateway.watch(documentId, invalidate) };
}
