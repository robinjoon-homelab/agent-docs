import { UPLOAD_ERROR_TEXT, type AssetUseCases } from '../../../../../application/asset/assetUseCases';

export async function uploadFlowImage(assets: AssetUseCases, space: string, file: File): Promise<string> {
  if (!file.type.startsWith('image/')) throw new Error('이미지 파일을 골라 주세요.');
  const uploaded = await assets.upload(space, {
    name: file.name,
    type: file.type,
    size: file.size,
    data: file,
  });
  if (!uploaded.ok) throw new Error(UPLOAD_ERROR_TEXT[uploaded.error]);
  return uploaded.value.id;
}
