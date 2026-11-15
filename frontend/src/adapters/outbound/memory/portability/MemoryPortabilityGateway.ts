import type { PortabilityGateway } from '../../../../application/portability/ports/PortabilityGateway';
import type { FolderImportResult } from '../../../../domain/portability/portability';

export class MemoryPortabilityGateway implements PortabilityGateway {
  importFolder(): Promise<FolderImportResult> {
    return Promise.reject(new Error('폴더 가져오기는 서버 연결이 필요합니다.'));
  }
  exportZip(): Promise<ArrayBuffer> {
    return Promise.reject(new Error('ZIP 내보내기는 서버 연결이 필요합니다.'));
  }
}
