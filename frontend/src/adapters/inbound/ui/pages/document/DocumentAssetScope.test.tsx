// @vitest-environment jsdom
import { cleanup, render, screen } from '@testing-library/react';
import { afterEach, expect, it } from 'vitest';
import { MemoryRouter } from 'react-router';
import type { Services } from '../../../../../config/ServicesContext';
import { ServicesProvider } from '../../../../../config/ServicesContext';
import { BlockView } from '../../blocks/BlockView';
import { CommentBody } from '../../common/InlineText';
import { DocumentAssetScope } from './DocumentAssetScope';

afterEach(cleanup);

it('renders images and comment attachment links using the current document binding', () => {
  const services: Partial<Services> = {
    assets: {
      upload: () => Promise.reject(new Error('read only')),
      importUrl: () => Promise.reject(new Error('read only')),
      contentUrl: (assetId: string, documentId?: string) =>
        `https://assets.test/${documentId ?? 'standalone'}/${assetId}`,
      htmlSource: () => Promise.resolve('<p>preview</p>'),
    },
  };
  render(
    <MemoryRouter>
      <ServicesProvider services={services as Services}>
        <DocumentAssetScope documentId="moved-document">
          <BlockView
            block={{
              id: 'image',
              type: 'image',
              assetId: 'immutable-reference',
              alt: 'attachment',
              caption: '',
            }}
          />
          <CommentBody markdown="[원문](asset:immutable-reference)" />
        </DocumentAssetScope>
      </ServicesProvider>
    </MemoryRouter>,
  );
  expect(screen.getByRole('img').getAttribute('src')).toBe(
    'https://assets.test/moved-document/immutable-reference',
  );
  expect(screen.getByRole('link', { name: '원문' }).getAttribute('href')).toBe(
    'https://assets.test/moved-document/immutable-reference',
  );
});
