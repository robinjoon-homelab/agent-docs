export type FolderEntry = {
  readonly path: string;
  readonly type: string;
  readonly bytes: ArrayBuffer;
};
export type ImportedFile = {
  readonly path: string;
  readonly status: 'created' | 'failed' | 'skipped';
  readonly documentId: string | null;
  readonly code: string | null;
  readonly relatedPath: string | null;
};
export type FolderImportResult = { readonly files: readonly ImportedFile[] };
