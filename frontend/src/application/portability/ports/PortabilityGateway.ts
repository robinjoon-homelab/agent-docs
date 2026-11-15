import type { FolderEntry, FolderImportResult } from '../../../domain/portability/portability';
export interface PortabilityGateway {
  importFolder(space: string, tool: string, files: readonly FolderEntry[]): Promise<FolderImportResult>;
  exportZip(space: string | null): Promise<ArrayBuffer>;
}
