import { describe, expect, it } from 'vitest';
import type { Asset } from '../../domain/asset/asset';
import { createAssetUseCases } from './assetUseCases';
import type { AssetGateway } from './ports/AssetGateway';

const gateway: AssetGateway = {
  upload: (_slug, file): Promise<Asset> =>
    Promise.resolve({
      id: 'as-1',
      mediaType: file.type,
      size: file.size,
      fileName: file.name,
      contentUrl: '/x',
    }),
  importUrl: () => Promise.reject(new Error('unused')),
  contentUrl: (id) => `/api/assets/${id}/content`,
};

describe('asset uploads', () => {
  it('rejects non-images and uploads images', async () => {
    const useCases = createAssetUseCases(gateway);
    const pdf = { name: 'a.pdf', type: 'application/pdf', size: 10, data: new Blob() };
    expect(await useCases.upload('team', pdf)).toEqual({ ok: false, error: 'not_image' });
    const png = { name: 'a.png', type: 'image/png', size: 10, data: new Blob() };
    const result = await useCases.upload('team', png);
    expect(result.ok && result.value.id).toBe('as-1');
  });
});
