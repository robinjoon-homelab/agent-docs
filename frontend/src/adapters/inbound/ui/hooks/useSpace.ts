import { useEffect } from 'react';
import { useLocation, useNavigate } from 'react-router';
import type { Space } from '../../../../domain/space/space';
import { useServices } from '../../../../config/ServicesContext';
import type { LoadResult } from './useLoad';
import { useLoad } from './useLoad';

/**
 * Loads the space named in the URL. A former slug still resolves on the server; the URL is then
 * replaced with the current slug so bookmarks heal themselves.
 */
export function useSpace(slug: string): LoadResult<Space> {
  const services = useServices();
  const navigate = useNavigate();
  const location = useLocation();
  const space = useLoad(`space:${slug}`, () => services.spaces.get(slug));
  const currentSlug = space.state === 'ready' ? space.data.slug : null;

  useEffect(() => {
    if (currentSlug === null || currentSlug === slug) return;
    const prefix = `/s/${encodeURIComponent(slug)}`;
    const rest = location.pathname.startsWith(prefix) ? location.pathname.slice(prefix.length) : '';
    void navigate(`/s/${encodeURIComponent(currentSlug)}${rest}${location.search}${location.hash}`, {
      replace: true,
    });
  }, [currentSlug, slug, location.pathname, location.search, location.hash, navigate]);

  return space;
}
