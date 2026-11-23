import { toFlowBlock } from './flowMapper';
import { isDesignObject } from '../../../../domain/document/designBlock';
export { permissionOf as toPermission } from '../../../../domain/space/permission';
import type {
  Block,
  CalloutKind,
  ColumnAlign,
  HeadingLevel,
  ListStyle,
  StoredBlock,
  TableColumn,
  TableRow,
} from '../../../../domain/document/block';
import { CALLOUT_KINDS, HEADING_LEVELS, clampDepth } from '../../../../domain/document/block';
import type { DocumentStatus, DocumentSummary, SpaceRef } from '../../../../domain/document/document';
import { DOCUMENT_STATUSES } from '../../../../domain/document/document';
import type { Actor } from '../../../../domain/shared/actor';
import type { Json } from './json';
import { bool, contractError, list, num, obj, oneOf, optStr, str, strOr, strings } from './json';

export function toActor(value: unknown): Actor {
  const source = obj(value, 'actor');
  const kind = oneOf(source, 'kind', ['human', 'agent'] as const);
  const agentSource = source.agent;
  const agent =
    kind === 'agent' && typeof agentSource === 'object' && agentSource !== null
      ? {
          connectionId: strOr(obj(agentSource), 'connectionId', ''),
          clientName: strOr(obj(agentSource), 'clientName', '에이전트'),
        }
      : null;
  return { kind, login: str(source, 'login'), agent };
}

export function toSpaceRef(value: unknown): SpaceRef {
  const source = obj(value, 'space');
  return { id: strOr(source, 'id', ''), slug: str(source, 'slug'), name: str(source, 'name') };
}

export function toSpaceName(value: unknown): { slug: string; name: string } {
  const source = obj(value, 'space');
  return { slug: str(source, 'slug'), name: str(source, 'name') };
}

export function toStatus(source: Json): DocumentStatus {
  return oneOf(source, 'status', DOCUMENT_STATUSES);
}

export function toDocumentSummary(value: unknown): DocumentSummary {
  const source = obj(value, 'document');
  return {
    type: source.type === 'flow' ? 'flow' : 'doc',
    id: str(source, 'id'),
    path: str(source, 'path'),
    title: str(source, 'title'),
    status: toStatus(source),
    tags: strings(source, 'tags'),
    assignee: optStr(source, 'assignee'),
    version: num(source, 'version'),
    revision: num(source, 'revision'),
    updatedAt: str(source, 'updatedAt'),
    updatedBy: toActor(source.updatedBy),
  };
}

const ALIGNS: readonly ColumnAlign[] = ['none', 'left', 'center', 'right'];
const LIST_STYLES: readonly ListStyle[] = ['bullet', 'numbered', 'task'];

function toColumn(value: unknown): TableColumn {
  const source = obj(value, 'column');
  return {
    id: str(source, 'id'),
    align: ALIGNS.includes(source.align as ColumnAlign) ? (source.align as ColumnAlign) : 'none',
  };
}

function toRow(value: unknown): TableRow {
  const source = obj(value, 'row');
  return { id: str(source, 'id'), cells: strings(source, 'cells') };
}

function toHeadingLevel(source: Json): HeadingLevel {
  const level = num(source, 'level');
  const supported = HEADING_LEVELS.find((candidate) => candidate === level);
  if (supported === undefined) throw contractError('level');
  return supported;
}

/**
 * Unknown block types are rejected rather than mapped to something else: saving a guessed block would
 * silently overwrite content this client does not understand.
 */
export function toBlock(value: unknown): Block {
  const source = obj(value, 'block');
  const id = str(source, 'id');
  const type = str(source, 'type');
  switch (type) {
    case 'flowDesign': {
      const design = source.design;
      if (!isDesignObject(design) || typeof design.id !== 'string' || typeof design.kind !== 'string')
        throw contractError('flowDesign.design');
      return { id, type, design };
    }
    case 'flowSettings':
    case 'flowScreen':
    case 'flowElement':
      return toFlowBlock(source);
    case 'heading':
      return { id, type, level: toHeadingLevel(source), text: str(source, 'text') };
    case 'paragraph':
    case 'quote':
      return { id, type, text: str(source, 'text') };
    case 'listItem':
      return {
        id,
        type,
        style: oneOf(source, 'style', LIST_STYLES),
        depth: clampDepth(num(source, 'depth')),
        checked: bool(source, 'checked'),
        text: str(source, 'text'),
      };
    case 'callout':
      return { id, type, kind: oneOf<CalloutKind>(source, 'kind', CALLOUT_KINDS), text: str(source, 'text') };
    case 'code':
      return {
        id,
        type,
        language: strOr(source, 'language', ''),
        code: str(source, 'code'),
        // Only a $$ display formula carries the flag; older math blocks keep their fence and hash without it.
        ...(source.displayMath === true ? { displayMath: true } : {}),
      };
    case 'table':
      return { id, type, columns: list(source.columns, toColumn), rows: list(source.rows, toRow) };
    case 'image':
      return {
        id,
        type,
        assetId: str(source, 'assetId'),
        alt: strOr(source, 'alt', ''),
        caption: strOr(source, 'caption', ''),
      };
    case 'drawing':
      return {
        id,
        type,
        svgAssetId: str(source, 'svgAssetId'),
        sceneAssetId: str(source, 'sceneAssetId'),
        alt: strOr(source, 'alt', ''),
      };
    case 'divider':
      return { id, type };
    default:
      throw contractError(`block type ${type}`);
  }
}

export function toStoredBlock(value: unknown): StoredBlock {
  return { ...toBlock(value), hash: strOr(obj(value), 'hash', '') };
}

export function toOptionalBlock(value: unknown): Block | null {
  return value === null || value === undefined ? null : toBlock(value);
}
