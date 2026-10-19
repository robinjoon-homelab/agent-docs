import type { SpaceGateway, SpacePatch } from '../../../../application/space/ports/SpaceGateway';
import type { Space, SpaceDraft, SpaceLink } from '../../../../domain/space/space';
import { SPACE_LINK_KINDS } from '../../../../domain/space/space';
import type { HttpClient } from '../shared/httpClient';
import { segment } from '../shared/httpClient';
import { list, num, obj, oneOf, optStr, str, strOr } from '../shared/json';

function toLink(value: unknown): SpaceLink {
  const source = obj(value, 'link');
  return { kind: oneOf(source, 'kind', SPACE_LINK_KINDS), value: str(source, 'value') };
}

export function toSpace(value: unknown): Space {
  const source = obj(value, 'space');
  return {
    id: str(source, 'id'),
    slug: str(source, 'slug'),
    name: str(source, 'name'),
    description: strOr(source, 'description', ''),
    links: source.links === undefined || source.links === null ? [] : list(source.links, toLink),
    createdAt: str(source, 'createdAt'),
    lastActivityAt: optStr(source, 'lastActivityAt'),
    documentCount: typeof source.documentCount === 'number' ? num(source, 'documentCount') : 0,
  };
}

export class HttpSpaceGateway implements SpaceGateway {
  private readonly http: HttpClient;

  constructor(http: HttpClient) {
    this.http = http;
  }

  async list(): Promise<Space[]> {
    return list(await this.http.get('/api/spaces'), toSpace);
  }

  async create(draft: SpaceDraft): Promise<Space> {
    return toSpace(await this.http.post('/api/spaces', draft));
  }

  async get(slug: string): Promise<Space> {
    return toSpace(await this.http.get(`/api/spaces/${segment(slug)}`));
  }

  async update(slug: string, patch: SpacePatch): Promise<Space> {
    return toSpace(await this.http.patch(`/api/spaces/${segment(slug)}`, patch));
  }
}
