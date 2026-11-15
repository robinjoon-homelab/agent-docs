import type { FolderEntry, FolderImportResult } from '../../domain/portability/portability';
import type { PortabilityGateway } from './ports/PortabilityGateway';
export interface PortabilityUseCases {
  importFolder(space: string, tool: string, files: readonly FolderEntry[]): Promise<FolderImportResult>;
  exportZip(space: string | null): Promise<ArrayBuffer>;
}
export function createPortabilityUseCases(gateway: PortabilityGateway): PortabilityUseCases {
  return {
    importFolder: (space, tool, files) => gateway.importFolder(space, tool.trim(), files),
    exportZip: (space) => gateway.exportZip(space),
  };
}
