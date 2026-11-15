export interface DocumentEventsGateway {
  watch(documentId: string, invalidate: () => void): () => void;
}
