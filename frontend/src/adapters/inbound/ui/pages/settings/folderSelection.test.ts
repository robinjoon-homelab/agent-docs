import { describe, expect, it } from 'vitest';
import { folderEntries } from './folderSelection';

describe('folder selection', () => {
  it('sends paths relative to the selected folder and keeps original bytes', async () => {
    const file = new File(['hello'], 'a.md', { type: 'text/markdown' });
    Object.defineProperty(file, 'webkitRelativePath', { value: 'private-folder/notes/a.md' });
    const result = await folderEntries([file]);
    expect(result[0]?.path).toBe('notes/a.md');
    expect(new TextDecoder().decode(result[0]?.bytes)).toBe('hello');
  });
  it('rejects absolute paths before upload', async () => {
    const file = new File(['secret'], 'a.md');
    Object.defineProperty(file, 'webkitRelativePath', { value: '/private/a.md' });
    await expect(folderEntries([file])).rejects.toThrow('상대 경로');
  });
});
