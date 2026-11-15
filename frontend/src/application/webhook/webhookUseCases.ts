import type {
  IssuedWebhook,
  WebhookDelivery,
  WebhookDraft,
  WebhookDraftError,
  WebhookEndpoint,
} from '../../domain/webhook/webhook';
import { normalizeWebhookDraft, validateWebhookDraft } from '../../domain/webhook/webhook';
import type { Result } from '../shared/result';
import { failure, ok } from '../shared/result';
import type { WebhookGateway } from './ports/WebhookGateway';

/** The owner's webhook settings. There is no delete: a stopped endpoint stays with its delivery records. */
export interface WebhookUseCases {
  /** Active endpoints first, each group newest first. */
  list(): Promise<WebhookEndpoint[]>;
  register(draft: WebhookDraft): Promise<Result<IssuedWebhook, WebhookDraftError[]>>;
  stop(endpointId: string): Promise<WebhookEndpoint>;
  rotateSecret(endpointId: string): Promise<IssuedWebhook>;
  deliveries(endpointId: string): Promise<WebhookDelivery[]>;
}

export const DELIVERY_PAGE = 50;

export function createWebhookUseCases(webhooks: WebhookGateway): WebhookUseCases {
  return {
    async list() {
      const all = await webhooks.list();
      return [...all].sort(
        (a, b) => Number(b.active) - Number(a.active) || b.createdAt.localeCompare(a.createdAt),
      );
    },
    async register(draft) {
      const normalized = normalizeWebhookDraft(draft);
      const errors = validateWebhookDraft(normalized);
      if (errors.length > 0) return failure(errors);
      return ok(await webhooks.register(normalized));
    },
    stop: (endpointId) => webhooks.stop(endpointId),
    rotateSecret: (endpointId) => webhooks.rotateSecret(endpointId),
    deliveries: (endpointId) => webhooks.deliveries(endpointId, DELIVERY_PAGE),
  };
}
