import type { Asset } from '../../../domain/asset/asset';

export type UploadFile = {
  readonly name: string;
  readonly type: string;
  readonly size: number;
  readonly data: Blob;
};

export interface AssetGateway {
  upload(spaceSlug: string, file: UploadFile): Promise<Asset>;
  importUrl(spaceSlug: string, url: string): Promise<Asset>;
  /** URL usable as an <img src> for a stored asset. */
  contentUrl(assetId: string): string;
}
