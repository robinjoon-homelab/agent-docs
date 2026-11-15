import type { PortabilityGateway } from '../../../../application/portability/ports/PortabilityGateway';
import type { FolderEntry, FolderImportResult } from '../../../../domain/portability/portability';
import type { HttpClient, FetchLike } from '../shared/httpClient';
import { problemFrom, segment } from '../shared/httpClient';
import { list, obj, str, optStr } from '../shared/json';

export class HttpPortabilityGateway implements PortabilityGateway {
  constructor(
    private readonly http: HttpClient,
    private readonly fetch: FetchLike,
  ) {}
  async importFolder(
    space: string,
    tool: string,
    files: readonly FolderEntry[],
  ): Promise<FolderImportResult> {
    const form = new FormData();
    form.set('tool', tool);
    for (const file of files) {
      form.append('paths', file.path);
      form.append(
        'files',
        new Blob([file.bytes], { type: file.type }),
        file.path.split('/').at(-1) ?? 'file',
      );
    }
    const result = obj(await this.http.postForm(`/api/spaces/${segment(space)}/imports/folder`, form));
    return {
      files: list(result.files, (value) => {
        const row = obj(value);
        const status = str(row, 'status');
        if (status !== 'created' && status !== 'failed' && status !== 'skipped')
          throw new Error('가져오기 결과가 올바르지 않습니다.');
        return {
          path: str(row, 'path'),
          status,
          documentId: optStr(row, 'documentId'),
          code: optStr(row, 'code'),
          relatedPath: optStr(row, 'relatedPath'),
        };
      }),
    };
  }
  async exportZip(space: string | null): Promise<ArrayBuffer> {
    const url = space === null ? '/api/exports/workspace.zip' : `/api/spaces/${segment(space)}/export.zip`;
    const response = await this.fetch(url, { credentials: 'same-origin', redirect: 'manual' });
    if (!response.ok) throw await problemFrom(response);
    return response.arrayBuffer();
  }
}
