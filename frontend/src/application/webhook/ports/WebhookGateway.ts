import type {
  IssuedWebhook,
  WebhookDelivery,
  WebhookDraft,
  WebhookEndpoint,
} from '../../../domain/webhook/webhook';

export interface WebhookGateway {
  list(): Promise<WebhookEndpoint[]>;
  register(draft: WebhookDraft): Promise<IssuedWebhook>;
  stop(endpointId: string): Promise<WebhookEndpoint>;
  rotateSecret(endpointId: string): Promise<IssuedWebhook>;
  deliveries(endpointId: string, limit: number): Promise<WebhookDelivery[]>;
}
