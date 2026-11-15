import { describe, expect, it } from 'vitest';
import type { WebhookDelivery, WebhookEndpoint } from './webhook';
import { deliveryStatus, normalizeWebhookDraft, validateWebhookDraft } from './webhook';

const endpoint: WebhookEndpoint = {
  id: 'wh1',
  url: 'http://192.168.0.20:8080/hook',
  events: ['document.changed'],
  active: true,
  createdAt: '2026-11-16T03:00:00Z',
  secretRotatedAt: null,
  stoppedAt: null,
  stopReason: null,
};

const delivery: WebhookDelivery = {
  id: 'd1',
  webhookId: 'msg_1',
  event: 'document.changed',
  activityId: 7,
  mentioned: null,
  occurredAt: '2026-11-16T03:00:00Z',
  state: 'pending',
  attempts: 0,
  maxAttempts: 8,
  nextAttemptAt: '2026-11-16T03:00:00Z',
  lastAttemptAt: null,
  lastStatus: null,
  lastError: null,
};

describe('webhook drafts', () => {
  it('accepts http and https addresses, homelab ones included, with at least one event', () => {
    expect(
      validateWebhookDraft({ url: ' http://192.168.0.20:8080/hook ', events: ['comment.created'] }),
    ).toEqual([]);
    expect(validateWebhookDraft({ url: 'https://hooks.example.com', events: ['document.changed'] })).toEqual(
      [],
    );
  });

  it('reports a missing or non-http address, credentials, a fragment, and no events', () => {
    expect(validateWebhookDraft({ url: ' ', events: [] })).toEqual(['url_required', 'no_events']);
    expect(validateWebhookDraft({ url: 'ftp://hooks.example.com', events: ['comment.created'] })).toEqual([
      'url_scheme',
    ]);
    expect(
      validateWebhookDraft({ url: 'https://user:pass@hooks.example.com/a#b', events: ['comment.created'] }),
    ).toEqual(['url_credentials', 'url_fragment']);
    expect(
      validateWebhookDraft({ url: 'https://hooks.example.com/a?to=me@x', events: ['comment.created'] }),
    ).toEqual([]);
  });

  it('trims the address and keeps the events in a fixed order without repeats', () => {
    expect(
      normalizeWebhookDraft({
        url: ' https://h.example ',
        events: ['comment.mentioned', 'document.changed'],
      }),
    ).toEqual({ url: 'https://h.example', events: ['document.changed', 'comment.mentioned'] });
  });
});

describe('deliveryStatus', () => {
  it('tells delivered, retrying, failed, and not-sent deliveries apart', () => {
    expect(
      deliveryStatus({ ...delivery, state: 'succeeded', attempts: 1, lastStatus: 204 }, endpoint),
    ).toEqual({
      tone: 'ok',
      text: '전달됨 · HTTP 204 · 1/8회',
    });
    expect(deliveryStatus({ ...delivery, attempts: 2, lastError: 'timeout' }, endpoint)).toEqual({
      tone: 'waiting',
      text: '재시도 대기 · 마지막 15초 안에 응답 없음 · 2/8회',
    });
    expect(
      deliveryStatus({ ...delivery, state: 'failed', attempts: 1, lastStatus: 410 }, endpoint).text,
    ).toBe('실패 · 수신처가 410으로 거부 · 1/8회');
    expect(deliveryStatus(delivery, { ...endpoint, active: false })).toEqual({
      tone: 'idle',
      text: '엔드포인트가 중지되어 보내지 않음',
    });
    expect(deliveryStatus(delivery, endpoint).tone).toBe('waiting');
  });
});
