import { Fragment } from 'react';
import { richBlocks } from '../../../../../domain/flow/richText';
import { InlineText } from '../../common/InlineText';

/** Screen and element descriptions: paragraphs keep their line breaks; lists, tables and headings get structure. */
export function FlowRichText({
  text,
  className = '',
  assetUrl,
}: {
  text: string;
  className?: string;
  assetUrl?: (id: string) => string;
}) {
  return (
    <div className={`flow-rich ${className}`.trim()}>
      {richBlocks(text).map((block, index) => {
        switch (block.kind) {
          case 'paragraph':
            return (
              <p key={index}>
                {block.lines.map((line, i) => (
                  <Fragment key={i}>
                    {i > 0 && <br />}
                    <InlineText assetUrl={assetUrl} markdown={line} />
                  </Fragment>
                ))}
              </p>
            );
          case 'heading':
            return (
              <h4 key={index}>
                <InlineText assetUrl={assetUrl} markdown={block.text} />
              </h4>
            );
          case 'list': {
            const List = block.ordered ? 'ol' : 'ul';
            return (
              <List key={index}>
                {block.items.map((item, i) => (
                  <li key={i}>
                    <InlineText assetUrl={assetUrl} markdown={item} />
                  </li>
                ))}
              </List>
            );
          }
          case 'table':
            return (
              <div key={index} className="flow-rich-table">
                <table>
                  {block.header.length > 0 && (
                    <thead>
                      <tr>
                        {block.header.map((cell, i) => (
                          <th key={i}>
                            <InlineText assetUrl={assetUrl} markdown={cell} />
                          </th>
                        ))}
                      </tr>
                    </thead>
                  )}
                  <tbody>
                    {block.rows.map((row, r) => (
                      <tr key={r}>
                        {row.map((cell, i) => (
                          <td key={i}>
                            <InlineText assetUrl={assetUrl} markdown={cell} />
                          </td>
                        ))}
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>
            );
        }
      })}
    </div>
  );
}
