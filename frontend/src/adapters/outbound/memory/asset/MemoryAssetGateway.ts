import type { AssetGateway, UploadFile } from '../../../../application/asset/ports/AssetGateway';
import type { Asset } from '../../../../domain/asset/asset';
import type { MemoryStore } from '../shared/store';
import { delay } from '../shared/store';

export class MemoryAssetGateway implements AssetGateway {
  private readonly store: MemoryStore;

  constructor(store: MemoryStore) {
    this.store = store;
  }

  upload(spaceSlug: string, file: UploadFile): Promise<Asset> {
    this.store.space(spaceSlug);
    return this.save(file.name, file.type, file.size, URL.createObjectURL(file.data));
  }

  importUrl(spaceSlug: string, url: string): Promise<Asset> {
    this.store.space(spaceSlug);
    return this.save(url.split('/').pop() ?? 'image', 'image/*', 0, url);
  }

  async htmlSource(assetId: string, documentId?: string): Promise<string> {
    const url = this.contentUrl(assetId, documentId);
    return (await fetch(url)).text();
  }

  async sceneSource(assetId: string, documentId: string): Promise<string> {
    return (await fetch(this.contentUrl(assetId, documentId))).text();
  }

  contentUrl(assetId: string, documentId?: string): string {
    if (documentId !== undefined) this.store.document(documentId);
    return this.store.assets.get(assetId)?.url ?? '';
  }

  private save(fileName: string, mediaType: string, size: number, url: string): Promise<Asset> {
    const id = this.store.nextId('asset');
    const asset: Asset = { id, mediaType, size, fileName, contentUrl: url };
    this.store.assets.set(id, { asset, url });
    return delay(asset);
  }
}
