import type { NavigationGateway } from '../../../../application/navigation/ports/NavigationGateway';
import type {
  MemoCommand,
  MobilePreferences,
  NavigationDocument,
  NavigationPage,
} from '../../../../domain/navigation/navigation';

export type MemoryNavigationState = {
  readonly visits: Map<string, number>;
  readonly favorites: Map<string, number>;
  readonly preferences: Map<string, MobilePreferences>;
};
export type MemoryNavigationBackend = {
  readonly userId: () => string;
  readonly visible: () => readonly NavigationDocument[];
  readonly backlinks: (id: string) => readonly NavigationDocument[];
  readonly writeMemo: (command: MemoCommand) => Promise<string>;
  readonly now: () => number;
  readonly timeZone: string;
};

export class MemoryNavigationGateway implements NavigationGateway {
  constructor(
    private readonly state: MemoryNavigationState,
    private readonly backend: MemoryNavigationBackend,
  ) {}
  private key(id: string): string {
    return `${this.backend.userId()}:${id}`;
  }
  private readable(id: string): void {
    if (!this.backend.visible().some((entry) => entry.id === id)) throw new Error('문서를 찾을 수 없습니다.');
  }
  private personal(entries: ReadonlyMap<string, number>): NavigationPage {
    const documents = this.backend
      .visible()
      .filter((entry) => entries.has(this.key(entry.id)))
      .sort((a, b) => (entries.get(this.key(b.id)) ?? 0) - (entries.get(this.key(a.id)) ?? 0));
    return { documents: documents.slice(0, 20), total: documents.length };
  }
  isFavorite(id: string): Promise<boolean> {
    return Promise.resolve().then(() => {
      this.readable(id);
      return this.state.favorites.has(this.key(id));
    });
  }
  recent(): Promise<NavigationPage> {
    return Promise.resolve(this.personal(this.state.visits));
  }
  favorites(): Promise<NavigationPage> {
    return Promise.resolve(this.personal(this.state.favorites));
  }
  backlinks(id: string): Promise<NavigationPage> {
    return Promise.resolve().then(() => {
      this.readable(id);
      const visible = new Set(this.backend.visible().map((entry) => entry.id));
      const documents = this.backend.backlinks(id).filter((entry) => visible.has(entry.id));
      return { documents: documents.slice(0, 20), total: documents.length };
    });
  }
  recordView(id: string): Promise<void> {
    return Promise.resolve().then(() => {
      this.readable(id);
      this.state.visits.set(this.key(id), this.backend.now());
    });
  }
  favorite(id: string, enabled: boolean): Promise<void> {
    return Promise.resolve().then(() => {
      this.readable(id);
      if (enabled) this.state.favorites.set(this.key(id), this.backend.now());
      else this.state.favorites.delete(this.key(id));
    });
  }
  preferences(userId: string): MobilePreferences {
    return (
      this.state.preferences.get(userId) ?? {
        space: '',
        diaryPattern: '일기/{YYYY-MM-DD}',
        timeZone: this.backend.timeZone,
      }
    );
  }
  savePreferences(userId: string, value: MobilePreferences): void {
    this.state.preferences.set(userId, { ...value });
  }
  writeMemo(command: MemoCommand): Promise<string> {
    return this.backend.writeMemo(command);
  }
}
