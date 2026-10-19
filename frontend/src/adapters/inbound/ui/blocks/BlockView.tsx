import type { Block, ColumnAlign, TableBlock } from '../../../../domain/document/block';
import { CALLOUT_LABEL } from '../../../../domain/document/block';
import { cellKey } from '../../../../domain/document/diffView';
import { listOrdinals } from '../../../../domain/document/listOrdinals';
import { useServices } from '../../../../config/ServicesContext';
import { InlineText } from '../common/InlineText';
import { MermaidPreview } from './MermaidPreview';
import './blocks.css';

const ALIGN_STYLE: Record<ColumnAlign, 'left' | 'center' | 'right' | undefined> = {
  none: undefined,
  left: 'left',
  center: 'center',
  right: 'right',
};

function TableView({ table, changedCells }: { table: TableBlock; changedCells?: ReadonlySet<string> }) {
  const [header, ...body] = table.rows;
  const cellClass = (rowId: string, columnId: string): string | undefined =>
    changedCells?.has(cellKey(rowId, columnId)) === true ? 'bv-cell-changed' : undefined;
  return (
    <div className="bv-table-wrap">
      <table className="bv-table">
        {header === undefined ? null : (
          <thead>
            <tr>
              {table.columns.map((column, index) => (
                <th
                  key={column.id}
                  className={cellClass(header.id, column.id)}
                  style={{ textAlign: ALIGN_STYLE[column.align] }}
                >
                  <InlineText markdown={header.cells[index] ?? ''} />
                </th>
              ))}
            </tr>
          </thead>
        )}
        <tbody>
          {body.map((row) => (
            <tr key={row.id}>
              {table.columns.map((column, index) => (
                <td
                  key={column.id}
                  className={cellClass(row.id, column.id)}
                  style={{ textAlign: ALIGN_STYLE[column.align] }}
                >
                  <InlineText markdown={row.cells[index] ?? ''} />
                </td>
              ))}
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  );
}

function ImageView({ assetId, alt, caption }: { assetId: string; alt: string; caption: string }) {
  const { assets } = useServices();
  return (
    <figure className="bv-image">
      <img src={assets.contentUrl(assetId)} alt={alt} loading="lazy" />
      {caption === '' ? null : <figcaption>{caption}</figcaption>}
    </figure>
  );
}

/** Read-only rendering of one block, used by history, conflict cards and other non-editing views. */
export function BlockView({
  block,
  changedCells,
  ordinal,
}: {
  block: Block;
  changedCells?: ReadonlySet<string>;
  /** Number shown for a numbered list item; BlockList computes it from the surrounding items. */
  ordinal?: number;
}) {
  switch (block.type) {
    case 'heading': {
      const Tag = `h${block.level}` as const;
      return (
        <Tag className={`bv-heading bv-h${block.level}`}>
          <InlineText markdown={block.text} />
        </Tag>
      );
    }
    case 'paragraph':
      return (
        <p className="bv-paragraph">
          <InlineText markdown={block.text} />
        </p>
      );
    case 'listItem':
      return (
        <div className="bv-list-item" style={{ paddingLeft: `${block.depth * 24 + 24}px` }}>
          <span className="bv-list-marker" aria-hidden={block.style !== 'task'}>
            {block.style === 'bullet' ? '•' : null}
            {block.style === 'numbered' ? `${ordinal ?? 1}.` : null}
            {block.style === 'task' ? (
              <input type="checkbox" checked={block.checked} disabled aria-label="체크리스트 항목" />
            ) : null}
          </span>
          <span className={block.style === 'task' && block.checked ? 'bv-task-done' : undefined}>
            <InlineText markdown={block.text} />
          </span>
        </div>
      );
    case 'quote':
      return (
        <blockquote className="bv-quote">
          <InlineText markdown={block.text} />
        </blockquote>
      );
    case 'callout':
      return (
        <div className={`bv-callout bv-callout-${block.kind}`}>
          <span className="bv-callout-label">{CALLOUT_LABEL[block.kind]}</span>
          <div>
            <InlineText markdown={block.text} />
          </div>
        </div>
      );
    case 'code':
      if (block.language === 'mermaid') {
        return (
          <div className="bv-mermaid">
            <MermaidPreview code={block.code} debounceMs={0} />
          </div>
        );
      }
      return (
        <div className="bv-code">
          {block.language === '' ? null : <span className="bv-code-language">{block.language}</span>}
          <pre>
            <code>{block.code}</code>
          </pre>
        </div>
      );
    case 'table':
      return <TableView table={block} changedCells={changedCells} />;
    case 'image':
      return <ImageView assetId={block.assetId} alt={block.alt} caption={block.caption} />;
    case 'divider':
      return <hr className="bv-divider" />;
  }
}

/** A run of blocks; consecutive list items read as one list and numbered items count up. */
export function BlockList({ blocks }: { blocks: readonly Block[] }) {
  const ordinals = listOrdinals(blocks);
  return (
    <div className="bv-blocks">
      {blocks.map((block) => (
        <BlockView key={block.id} block={block} ordinal={ordinals.get(block.id)} />
      ))}
    </div>
  );
}
