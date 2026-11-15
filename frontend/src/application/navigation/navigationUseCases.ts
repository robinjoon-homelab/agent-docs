import { isValidPath } from '../../domain/document/document';
import type { NavigationGateway } from './ports/NavigationGateway';

export type NavigationUseCases = NavigationGateway;
export function createNavigationUseCases(gateway: NavigationGateway): NavigationUseCases {
  return {
    isFavorite: (id) => gateway.isFavorite(id),
    recent: () => gateway.recent(),
    favorites: () => gateway.favorites(),
    backlinks: (id) => gateway.backlinks(id),
    recordView: (id) => gateway.recordView(id),
    favorite: (id, enabled) => gateway.favorite(id, enabled),
    preferences: (id) => gateway.preferences(id),
    savePreferences: (id, value) => gateway.savePreferences(id, value),
    writeMemo(command) {
      if (!isValidPath(command.path)) throw new Error('저장 경로를 확인하세요.');
      if (command.text.trim() === '') throw new Error('메모를 입력하세요.');
      return gateway.writeMemo(command);
    },
  };
}
