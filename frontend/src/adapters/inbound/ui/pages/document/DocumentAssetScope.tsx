import type { ReactNode } from 'react';
import { useMemo } from 'react';
import { ServicesProvider, useServices } from '../../../../../config/ServicesContext';

/** Keep immutable attachment references tied to the document after a transfer. */
export function DocumentAssetScope({ documentId, children }: { documentId: string; children: ReactNode }) {
  const services = useServices();
  const scoped = useMemo(
    () => ({
      ...services,
      assets: {
        ...services.assets,
        contentUrl: (assetId: string) => services.assets.contentUrl(assetId, documentId),
        htmlSource: (assetId: string) => services.assets.htmlSource(assetId, documentId),
        sceneSource: (assetId: string) => services.assets.sceneSource(assetId, documentId),
      },
    }),
    [services, documentId],
  );
  return <ServicesProvider services={scoped}>{children}</ServicesProvider>;
}
