import { describe, expect, it } from 'vitest';
import { HttpClient } from '../shared/httpClient';
import { HttpNavigationGateway } from './HttpNavigationGateway';

function bodyText(body: BodyInit | null | undefined): string {
  if (typeof body !== 'string') throw new Error('JSON body required');
  return body;
}

function storage() {
  const values = new Map<string, string>();
  return {
    getItem: (key: string) => values.get(key) ?? null,
    setItem: (key: string, value: string) => {
      values.set(key, value);
    },
  };
}
const command = {
  opId: 'same-op',
  blockId: 'newblock01',
  space: 'notes',
  path: '일기/2026-11-16',
  text: '오늘 기록',
};

describe('HttpNavigationGateway', () => {
  it('retries a lost successful creation as the same operation without appending again', async () => {
    const operations = new Map<string, string>();
    const documents: string[] = [];
    let loseResponse = true;
    const http = new HttpClient({
      cookies: () => '',
      fetch: (url, init) => {
        if (url.startsWith('/api/navigation/mobile-target'))
          return Promise.resolve(new Response('', { status: 200 }));
        const body = JSON.parse(bodyText(init?.body)) as { opId: string; path: string };
        let id = operations.get(body.opId);
        if (id === undefined) {
          id = 'created-id';
          documents.push(body.path);
          operations.set(body.opId, id);
        }
        if (loseResponse) {
          loseResponse = false;
          throw new Error('response lost');
        }
        return Promise.resolve(Response.json({ id }));
      },
    });
    const gateway = new HttpNavigationGateway(http, storage(), 'Asia/Seoul');
    await expect(gateway.writeMemo(command)).rejects.toThrow();
    expect(await gateway.writeMemo(command)).toBe('created-id');
    expect(documents).toEqual(['일기/2026-11-16']);
  });

  it('preserves all current blocks while adding a single new stable block', async () => {
    let stored = [{ id: 'existing01', type: 'paragraph', text: '원래 글' }];
    const http = new HttpClient({
      cookies: () => '',
      fetch: (url, init) => {
        if (url.startsWith('/api/navigation/mobile-target'))
          return Promise.resolve(Response.json({ id: 'diary', version: 7, blocks: stored }));
        const body = JSON.parse(bodyText(init?.body)) as {
          operations: { op: string; after: string; markdown: string }[];
        };
        const operation = body.operations[0];
        if (operation?.op !== 'insert' || !stored.some((block) => block.id === operation.after))
          return Promise.resolve(Response.json({ code: 'conflict' }, { status: 409 }));
        const match = /<!-- b:(\w+) -->\n([\s\S]*)/.exec(operation.markdown);
        if (match) stored = [...stored, { id: match[1] ?? '', type: 'paragraph', text: match[2] ?? '' }];
        return Promise.resolve(Response.json({ id: 'diary', version: 8 }));
      },
    });
    const gateway = new HttpNavigationGateway(http, storage(), 'Asia/Seoul');
    expect(await gateway.writeMemo(command)).toBe('diary');
    expect(stored).toEqual([
      { id: 'existing01', type: 'paragraph', text: '원래 글' },
      { id: 'newblock01', type: 'paragraph', text: '오늘 기록' },
    ]);
  });

  it.each(['legacy_operation', 'result_removed'])(
    'keeps %s from becoming a new write attempt',
    async (code) => {
      const http = new HttpClient({
        cookies: () => '',
        fetch: (url) =>
          Promise.resolve(
            url.startsWith('/api/navigation/mobile-target')
              ? new Response('', { status: 200 })
              : Response.json({ code }, { status: 409 }),
          ),
      });
      const gateway = new HttpNavigationGateway(http, storage(), 'Asia/Seoul');
      await expect(gateway.writeMemo(command)).rejects.toThrow('같은 입력을 새 작업으로 다시 보내지 마세요');
    },
  );

  it('keeps preferences under each user and starts with browser timezone', () => {
    const http = new HttpClient({ cookies: () => '', fetch: () => Promise.resolve(Response.json({})) });
    const gateway = new HttpNavigationGateway(http, storage(), 'Asia/Seoul');
    gateway.savePreferences('a', {
      space: 'notes',
      diaryPattern: 'diary/{YYYY-MM-DD}',
      timeZone: 'America/New_York',
    });
    expect(gateway.preferences('b')).toEqual({
      space: '',
      diaryPattern: '일기/{YYYY-MM-DD}',
      timeZone: 'Asia/Seoul',
    });
    expect(gateway.preferences('a').timeZone).toBe('America/New_York');
  });
});
