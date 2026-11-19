// @vitest-environment jsdom
import { cleanup, render, screen } from '@testing-library/react';
import { afterEach, expect, it } from 'vitest';
import { MemoryStore } from '../../../../adapters/outbound/memory/shared/store';
import { fakeServicesOf } from '../../../../config/fakeServices';
import { ServicesProvider } from '../../../../config/ServicesContext';
import { BlockView } from './BlockView';

afterEach(cleanup);

it('renders existing alt read-only for image and drawing blocks without missing-alt warnings', () => {
  const services = fakeServicesOf(
    new MemoryStore({ userId: 'u1', login: 'owner', name: 'Owner', avatarUrl: '', owner: true }),
    'UTC',
  );
  render(
    <ServicesProvider services={services}>
      <BlockView block={{ id: 'img1', type: 'image', assetId: 'a1', alt: '아키텍처 이미지', caption: '' }} />
      <BlockView
        block={{
          id: 'drw1',
          type: 'drawing',
          svgAssetId: 's1',
          sceneAssetId: 'sc1',
          alt: '손그림 다이어그램',
        }}
      />
      <BlockView block={{ id: 'img2', type: 'image', assetId: 'a2', alt: '', caption: '' }} />
    </ServicesProvider>,
  );

  expect(screen.getByText('아키텍처 이미지')).toBeDefined();
  expect(screen.getByText('손그림 다이어그램')).toBeDefined();
  expect(screen.queryByText('대체 텍스트 없음')).toBeNull();
});
