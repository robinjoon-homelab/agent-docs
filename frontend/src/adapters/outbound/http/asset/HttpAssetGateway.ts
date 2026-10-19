import type { AssetGateway, UploadFile } from '../../../../application/asset/ports/AssetGateway';
import type { Asset } from '../../../../domain/asset/asset';
import type { HttpClient } from '../shared/httpClient';
import { segment } from '../shared/httpClient';
import { num, obj, str, strOr } from '../shared/json';

/** The stored file of an asset; only this adapter knows the HTTP route. */
function contentPath(assetId: string): string {
  return `/api/assets/${segment(assetId)}/content`;
}

export function toAsset(value: unknown): Asset {
  const source = obj(value, 'asset');
  const id = str(source, 'id');
  return {
    id,
    mediaType: str(source, 'mediaType'),
    size: num(source, 'size'),
    fileName: strOr(source, 'fileName', ''),
    contentUrl: strOr(source, 'contentUrl', contentPath(id)),
  };
}

export class HttpAssetGateway implements AssetGateway {
  private readonly http: HttpClient;

  constructor(http: HttpClient) {
    this.http = http;
  }

  async upload(spaceSlug: string, file: UploadFile): Promise<Asset> {
    const form = new FormData();
    form.append('file', file.data, file.name);
    return toAsset(await this.http.postForm(`/api/spaces/${segment(spaceSlug)}/assets`, form));
  }

  async importUrl(spaceSlug: string, url: string): Promise<Asset> {
    return toAsset(await this.http.post(`/api/spaces/${segment(spaceSlug)}/assets/import`, { url }));
  }

  contentUrl(assetId: string): string {
    return contentPath(assetId);
  }
}
