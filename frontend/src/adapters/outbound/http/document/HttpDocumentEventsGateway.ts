import type { DocumentEventsGateway } from '../../../../application/document/ports/DocumentEventsGateway';

export interface DocumentEventSource {
  addEventListener(type: string, listener: () => void): void;
  close(): void;
}

export type DocumentEventSourceFactory = (url: string) => DocumentEventSource;

/** Native EventSource reconnects itself; each connection receives a fresh invalidation. */
export class HttpDocumentEventsGateway implements DocumentEventsGateway {
  constructor(private readonly source: DocumentEventSourceFactory) {}

  watch(documentId: string, invalidate: () => void): () => void {
    const stream = this.source(`/api/documents/${encodeURIComponent(documentId)}/events`);
    let active = true;
    stream.addEventListener('invalidate', () => {
      if (active) invalidate();
    });
    return () => {
      active = false;
      stream.close();
    };
  }
}
