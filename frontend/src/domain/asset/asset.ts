export type Asset = {
  readonly id: string;
  readonly mediaType: string;
  readonly size: number;
  readonly fileName: string;
  readonly contentUrl: string;
};

export const MAX_UPLOAD_BYTES = 20 * 1024 * 1024;

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

export type UploadError = 'not_image' | 'too_large';

export function checkUpload(mediaType: string, size: number): UploadError | null {
  if (!isImageMediaType(mediaType)) return 'not_image';
  if (size > MAX_UPLOAD_BYTES) return 'too_large';
  return null;
}
