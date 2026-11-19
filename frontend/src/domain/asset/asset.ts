export type Asset = {
  readonly id: string;
  readonly mediaType: string;
  readonly size: number;
  readonly fileName: string;
  readonly contentUrl: string;
};

export const MAX_UPLOAD_BYTES = 20 * 1024 * 1024;

/** The stored original of a hand-drawn diagram. */
export const EXCALIDRAW_SCENE_MEDIA_TYPE = 'application/vnd.excalidraw+json';

/** Media types the server accepts (it checks the file signature too). */
export const IMAGE_MEDIA_TYPES: readonly string[] = [
  'image/png',
  'image/jpeg',
  'image/gif',
  'image/webp',
  'image/svg+xml',
];

export function isImageMediaType(mediaType: string): boolean {
  return IMAGE_MEDIA_TYPES.includes(mediaType.toLowerCase());
}

export function isExternalImageUrl(text: string): boolean {
  return /^https?:\/\/\S+\.(png|jpe?g|gif|webp|svg)(\?\S*)?$/i.test(text.trim());
}

/** A single standalone http(s) URL without spaces, offered as an explicit import candidate when extensionless. */
export function standaloneHttpUrl(text: string): string | null {
  const trimmed = text.trim();
  if (trimmed === '' || /\s/.test(trimmed) || !/^https?:\/\//i.test(trimmed)) return null;
  try {
    const parsed = new URL(trimmed);
    return parsed.protocol === 'http:' || parsed.protocol === 'https:' ? trimmed : null;
  } catch {
    return null;
  }
}

export type UploadError = 'not_image' | 'too_large' | 'empty';

export function checkUpload(mediaType: string, size: number): UploadError | null {
  if (mediaType.startsWith('image/') && !isImageMediaType(mediaType)) return 'not_image';
  if (size <= 0) return 'empty';
  if (size > MAX_UPLOAD_BYTES) return 'too_large';
  return null;
}
