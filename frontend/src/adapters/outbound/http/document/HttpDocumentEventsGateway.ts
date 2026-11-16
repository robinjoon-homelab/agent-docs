import type { DocumentEventsGateway } from '../../../../application/document/ports/DocumentEventsGateway';
import type { DocumentEventHandlers } from '../../../../domain/document/documentEvents';
import { permissionOf } from '../../../../domain/space/permission';
export interface DocumentEventSource {
  addEventListener(type: string, listener: (event: { readonly data?: string }) => void): void;
  close(): void;
}
export type DocumentEventSourceFactory = (url: string) => DocumentEventSource;
function payload(data: string | undefined): Record<string, unknown> {
  try {
    const value: unknown = JSON.parse(data ?? '{}');
    return typeof value === 'object' && value !== null ? (value as Record<string, unknown>) : {};
  } catch {
    return {};
  }
}
/** Revocation is terminal: native EventSource must not keep reconnecting to a hidden document. */
export class HttpDocumentEventsGateway implements DocumentEventsGateway {
  constructor(private readonly source: DocumentEventSourceFactory) {}
  watch(documentId: string, invalidate: () => void, handlers?: DocumentEventHandlers): () => void {
    const stream = this.source(`/api/documents/${encodeURIComponent(documentId)}/events`);
    let active = true;
    const stop = () => {
      active = false;
      stream.close();
    };
    stream.addEventListener('invalidate', () => {
      if (active) invalidate();
    });
    stream.addEventListener('permission', (event) => {
      if (!active) return;
      const permission = permissionOf(payload(event.data).permission) ?? 'read';
      handlers?.permission?.(permission);
    });
    stream.addEventListener('revoked', (event) => {
      if (!active) return;
      const reason = payload(event.data).reason === 'unauthenticated' ? 'unauthenticated' : 'not_found';
      stop();
      handlers?.revoked?.(reason);
    });
    return stop;
  }
}
