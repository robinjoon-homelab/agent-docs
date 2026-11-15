import type { MemoCommand, MobilePreferences, NavigationPage } from '../../../domain/navigation/navigation';
export interface NavigationGateway {
  isFavorite(id: string): Promise<boolean>;
  recent(): Promise<NavigationPage>;
  favorites(): Promise<NavigationPage>;
  backlinks(id: string): Promise<NavigationPage>;
  recordView(id: string): Promise<void>;
  favorite(id: string, enabled: boolean): Promise<void>;
  writeMemo(command: MemoCommand): Promise<string>;
  preferences(userId: string): MobilePreferences;
  savePreferences(userId: string, preferences: MobilePreferences): void;
}
