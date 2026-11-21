import type { NavigationGateway } from '../../../../application/navigation/ports/NavigationGateway';
import type {
  MemoCommand,
  MobilePreferences,
  NavigationPage,
} from '../../../../domain/navigation/navigation';
import type { HttpClient } from '../shared/httpClient';
import { ProblemError, segment } from '../shared/httpClient';
import { list, num, obj, str } from '../shared/json';

type PreferencesStorage = { getItem(key: string): string | null; setItem(key: string, value: string): void };
type PendingWrite = {
  readonly endpoint: string;
  readonly body: unknown;
  readonly method: 'post' | 'put';
  readonly documentId: string | null;
};
function page(value: unknown): NavigationPage {
  const source = obj(value);
  return {
    total: num(source, 'total'),
    documents: list(source.documents, (value) => {
      const entry = obj(value);
      return {
        id: str(entry, 'id'),
        spaceId: str(entry, 'spaceId'),
        spaceSlug: str(entry, 'spaceSlug'),
        spaceName: str(entry, 'spaceName'),
        path: str(entry, 'path'),
        type: entry.type === 'flow' ? 'flow' : 'doc',
        title: str(entry, 'title'),
      };
    }),
  };
}

export class HttpNavigationGateway implements NavigationGateway {
  private readonly writes = new Map<string, PendingWrite>();
  constructor(
    private readonly http: HttpClient,
    private readonly storage: PreferencesStorage,
    private readonly defaultTimeZone: string,
  ) {}
  async isFavorite(id: string): Promise<boolean> {
    return obj(await this.http.get(`/api/navigation/documents/${segment(id)}/favorite`)).enabled === true;
  }
  async recent(): Promise<NavigationPage> {
    return page(await this.http.get('/api/navigation/recent'));
  }
  async favorites(): Promise<NavigationPage> {
    return page(await this.http.get('/api/navigation/favorites'));
  }
  async backlinks(id: string): Promise<NavigationPage> {
    return page(await this.http.get(`/api/navigation/documents/${segment(id)}/backlinks`));
  }
  async recordView(id: string): Promise<void> {
    await this.http.post(`/api/navigation/documents/${segment(id)}/view`);
  }
  async favorite(id: string, enabled: boolean): Promise<void> {
    await this.http.put(`/api/navigation/documents/${segment(id)}/favorite`, { enabled });
  }
  preferences(userId: string): MobilePreferences {
    const raw = this.storage.getItem(`mobile-preferences:${userId}`);
    if (raw === null) return { space: '', diaryPattern: '일기/{YYYY-MM-DD}', timeZone: this.defaultTimeZone };
    try {
      const source = obj(JSON.parse(raw) as unknown);
      return {
        space: str(source, 'space'),
        diaryPattern: str(source, 'diaryPattern'),
        timeZone: str(source, 'timeZone'),
      };
    } catch {
      return { space: '', diaryPattern: '일기/{YYYY-MM-DD}', timeZone: this.defaultTimeZone };
    }
  }
  savePreferences(userId: string, value: MobilePreferences): void {
    this.storage.setItem(`mobile-preferences:${userId}`, JSON.stringify(value));
  }
  async writeMemo(command: MemoCommand): Promise<string> {
    const write = this.writes.get(command.opId) ?? (await this.prepare(command));
    this.writes.set(command.opId, write);
    try {
      const response = obj(await this.http[write.method](write.endpoint, write.body));
      this.writes.delete(command.opId);
      return write.documentId ?? str(response, 'id');
    } catch (error) {
      if (
        error instanceof ProblemError &&
        ['legacy_operation', 'result_removed'].includes(String(error.body.code))
      ) {
        throw new Error(
          '이미 처리된 작업의 결과를 되돌려 받을 수 없습니다. 현재 문서와 이력을 확인하세요. 같은 입력을 새 작업으로 다시 보내지 마세요.',
          { cause: error },
        );
      }
      if (error instanceof ProblemError && [400, 403, 404, 409, 422].includes(error.status))
        this.writes.delete(command.opId);
      throw error;
    }
  }
  private async prepare(command: MemoCommand): Promise<PendingWrite> {
    const query = new URLSearchParams({ space: command.space, path: command.path });
    const target = await this.http.get(`/api/navigation/mobile-target?${query.toString()}`);
    const block = {
      id: command.blockId,
      type: 'paragraph',
      text: command.text.trim().replaceAll('\n', '<br>'),
    };
    if (target === null || target === undefined)
      return {
        endpoint: `/api/spaces/${segment(command.space)}/documents`,
        method: 'post',
        body: {
          opId: command.opId,
          path: command.path,
          title: command.path.split('/').at(-1),
          blocks: [block],
          inputTrace: command.inputTrace,
        },
        documentId: null,
      };
    const current = obj(target);
    const id = str(current, 'id');
    const blocks = list(current.blocks, (value) => obj(value));
    const last = blocks.at(-1);
    const position = last === undefined ? { at: 'end' } : { after: str(last, 'id') };
    return {
      endpoint: `/api/documents/${segment(id)}/edits`,
      method: 'post',
      documentId: id,
      body: {
        opId: command.opId,
        operations: [{ op: 'insert', ...position, markdown: `<!-- b:${command.blockId} -->\n${block.text}` }],
        inputTrace: command.inputTrace,
      },
    };
  }
}
