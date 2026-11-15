import type { SpaceGateway, SpacePatch } from '../../../../application/space/ports/SpaceGateway';
import type {
  Space,
  SpaceDraft,
  SpaceGuide,
  SpaceGuideIssue,
  SpaceLink,
} from '../../../../domain/space/space';
import { SPACE_LINK_KINDS } from '../../../../domain/space/space';
import type { HttpClient } from '../shared/httpClient';
import { segment } from '../shared/httpClient';
import { list, num, obj, oneOf, optOneOf, optStr, str, strOr } from '../shared/json';

function toLink(value: unknown): SpaceLink {
  const source = obj(value, 'link');
  return { kind: oneOf(source, 'kind', SPACE_LINK_KINDS), value: str(source, 'value') };
}

const GUIDE_ISSUES: readonly SpaceGuideIssue[] = ['trashed', 'unavailable'];

function toGuide(value: unknown): SpaceGuide | null {
  if (value === undefined || value === null) return null;
  const source = obj(value, 'agentGuide');
  return { documentId: str(source, 'documentId'), title: str(source, 'title'), path: str(source, 'path') };
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
    archivedAt: optStr(source, 'archivedAt'),
    lastActivityAt: optStr(source, 'lastActivityAt'),
    documentCount: typeof source.documentCount === 'number' ? num(source, 'documentCount') : 0,
    agentGuide: toGuide(source.agentGuide),
    agentGuideIssue: optOneOf(source, 'agentGuideIssue', GUIDE_ISSUES),
    defaultRepository: optStr(source, 'defaultRepository'),
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
