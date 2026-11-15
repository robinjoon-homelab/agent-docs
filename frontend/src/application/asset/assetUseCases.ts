import type { Asset, UploadError } from '../../domain/asset/asset';
import { checkUpload } from '../../domain/asset/asset';
import type { Result } from '../shared/result';
import { failure, ok } from '../shared/result';
import type { AssetGateway, UploadFile } from './ports/AssetGateway';

export const UPLOAD_ERROR_TEXT: Record<UploadError, string> = {
  not_image: 'PNG, JPEG, GIF, WebP, SVG 이미지만 올릴 수 있습니다.',
  empty: '빈 파일은 올릴 수 없습니다.',
  too_large: '20MB보다 큰 파일은 올릴 수 없습니다.',
};

export interface AssetUseCases {
  upload(spaceSlug: string, file: UploadFile): Promise<Result<Asset, UploadError>>;
  /** Copies an external image into the space so the document does not depend on the remote host. */
  importUrl(spaceSlug: string, url: string): Promise<Asset>;
  contentUrl(assetId: string, documentId?: string): string;
  htmlSource(assetId: string, documentId?: string): Promise<string>;
}

export function createAssetUseCases(gateway: AssetGateway): AssetUseCases {
  return {
    async upload(spaceSlug, file) {
      const error = checkUpload(file.type, file.size);
      if (error !== null) return failure(error);
      return ok(await gateway.upload(spaceSlug, file));
    },
    importUrl: (spaceSlug, url) => gateway.importUrl(spaceSlug, url.trim()),
    htmlSource: (assetId, documentId) =>
      gateway.htmlSource?.(assetId, documentId) ?? Promise.reject(new Error('HTML 원문을 읽을 수 없습니다.')),
    contentUrl: (assetId, documentId) => gateway.contentUrl(assetId, documentId),
  };
}
