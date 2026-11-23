import type { CSSProperties } from 'react';
import type { DesignJson, DesignObject } from '../../../../../../domain/document/designBlock';
import { asObject, asObjects, asText } from '../../../../../../domain/flow/designValue';
import { designTextStyle } from './designStyle';

type AssetUrl = (id: string) => string;
function Runs({ runs, assetUrl }: { runs: readonly DesignObject[]; assetUrl: AssetUrl }) {
  return runs.map((run) => (
    <span key={asText(run.id)} style={designTextStyle(asObject(run.style), assetUrl)}>
      {asText(run.text)}
    </span>
  ));
}

export function DesignCanvasText({ text, assetUrl }: { text: DesignJson | undefined; assetUrl: AssetUrl }) {
  if (typeof text === 'string') return text;
  const value = asObject(text);
  if (!value.paragraphs) return <Runs runs={asObjects(value.runs)} assetUrl={assetUrl} />;
  return asObjects(value.paragraphs).map((paragraph) => {
    const list = asObject(paragraph.list);
    const style: CSSProperties = { margin: 0, ...designTextStyle(asObject(paragraph.style), assetUrl) };
    const runs = <Runs runs={asObjects(paragraph.runs)} assetUrl={assetUrl} />;
    if (list.kind === 'bullet' || list.kind === 'ordered') {
      const List = list.kind === 'ordered' ? 'ol' : 'ul';
      return (
        <List
          key={asText(paragraph.id)}
          style={{ ...style, paddingInlineStart: `${1.5 + Number(list.level ?? 0)}em` }}
          start={typeof list.start === 'number' ? list.start : undefined}
        >
          <li>{runs}</li>
        </List>
      );
    }
    return (
      <p key={asText(paragraph.id)} style={style}>
        {runs}
      </p>
    );
  });
}
