import type { FolderEntry } from '../../../../../domain/portability/portability';

export async function folderEntries(files: readonly File[]): Promise<readonly FolderEntry[]> {
  return Promise.all(
    files.map(async (file) => {
      const path = file.webkitRelativePath;
      if (!path.includes('/') || path.startsWith('/') || path.includes('\\'))
        throw new Error('폴더의 상대 경로가 필요합니다.');
      return {
        path: path.substring(path.indexOf('/') + 1),
        type: file.type,
        bytes: await file.arrayBuffer(),
      };
    }),
  );
}
