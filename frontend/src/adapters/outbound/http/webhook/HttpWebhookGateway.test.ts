import { describe, expect, it } from 'vitest';
import { isApiError } from '../../../../application/shared/ports/errors';
import { HttpClient } from '../shared/httpClient';
import { HttpWebhookGateway } from './HttpWebhookGateway';

type Seen = { url: string; method: string; body: string | null };

function gateway(body: unknown, status = 200): { webhooks: HttpWebhookGateway; seen: Seen[] } {
  const seen: Seen[] = [];
  const fetch = (input: string, init?: RequestInit): Promise<Response> => {
    seen.push({
      url: input,
      method: init?.method ?? 'GET',
      body: typeof init?.body === 'string' ? init.body : null,
    });
    return Promise.resolve(
      new Response(JSON.stringify(body), { status, headers: { 'content-type': 'application/json' } }),
    );
  };
  return { webhooks: new HttpWebhookGateway(new HttpClient({ fetch, cookies: () => '' })), seen };
}

const endpointJson = {
  id: 'wh1',
  url: 'http://192.168.0.20:8080/hook',
  events: ['comment.mentioned', 'document.changed'],
  active: false,
  createdAt: '2026-11-16T03:00:00Z',
  secretRotatedAt: null,
  stoppedAt: '2026-11-16T04:00:00Z',
  stopReason: 'gone',
};

describe('HttpWebhookGateway', () => {
  it('posts the draft and reads the one-time secret', async () => {
    const { webhooks, seen } = gateway({ endpoint: endpointJson, secret: 'whsec_abc' }, 201);
    const issued = await webhooks.register({
      url: 'http://192.168.0.20:8080/hook',
      events: ['document.changed'],
    });
    expect(issued.secret).toBe('whsec_abc');
    expect(issued.endpoint).toMatchObject({ active: false, stopReason: 'gone', events: endpointJson.events });
    expect(seen[0]).toEqual({
      url: '/api/webhooks',
      method: 'POST',
      body: '{"url":"http://192.168.0.20:8080/hook","events":["document.changed"]}',
    });
  });

  it('reads deliveries with their retry state and asks for a page', async () => {
    const { webhooks, seen } = gateway([
      {
        id: 'd1',
        webhookId: 'msg_1',
        event: 'comment.mentioned',
        activityId: 9,
        mentioned: 'alice',
        occurredAt: '2026-11-16T03:00:00Z',
        state: 'pending',
        attempts: 2,
        maxAttempts: 8,
        nextAttemptAt: '2026-11-16T03:00:35Z',
        lastAttemptAt: '2026-11-16T03:00:05Z',
        lastStatus: null,
        lastError: 'timeout',
      },
    ]);
    const deliveries = await webhooks.deliveries('wh 1', 50);
    expect(deliveries[0]).toMatchObject({
      mentioned: 'alice',
      lastStatus: null,
      lastError: 'timeout',
      attempts: 2,
    });
    expect(seen[0]?.url).toBe('/api/webhooks/wh%201/deliveries?limit=50');
  });

  it('turns an owner-only refusal and an unknown event into errors', async () => {
    const forbidden = gateway({ code: 'forbidden', detail: 'listing webhooks is for the owner only' }, 403);
    await expect(forbidden.webhooks.list()).rejects.toSatisfy((error) => isApiError(error, 'forbidden'));
    const unknown = gateway([{ ...endpointJson, events: ['document.revised'] }]);
    await expect(unknown.webhooks.list()).rejects.toSatisfy((error) => isApiError(error, 'unknown'));
  });
});
