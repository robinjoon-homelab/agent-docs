import { flushSync } from 'react-dom';
import { createRoot } from 'react-dom/client';
import { MemoryRouter } from 'react-router';
import type { FlowDesignBlock } from '../../../domain/document/designBlock';
import type { DesignAddress } from '../../../domain/flow/designAddress';
import { DesignCanvasView } from '../ui/pages/document/design/DesignCanvas';
import { inspectDesignCanvas } from '../ui/pages/document/design/DesignCanvasDiagnostics';
import '../ui/pages/document/flow.css';

type RenderInput = {
  blocks: FlowDesignBlock[];
  screen: string;
  state?: string;
  target?: DesignAddress;
  mode: 'full' | 'viewport';
  assets: Record<string, string>;
};

async function render() {
  const source = document.getElementById('design-input');
  if (!source?.textContent) throw Error('Missing design input');
  const input = JSON.parse(source.textContent) as RenderInput;
  const host = document.createElement('div');
  document.body.append(host);
  document.body.style.margin = '0';
  const root = createRoot(host);
  flushSync(() => {
    root.render(
      <MemoryRouter>
        <DesignCanvasView
          blocks={input.blocks}
          screen={input.screen}
          state={input.state}
          mode={input.mode}
          assetUrl={(id) => input.assets[id] ?? ''}
        />
      </MemoryRouter>,
    );
  });
  const canvas = host.querySelector<HTMLElement>('.design-canvas');
  if (!canvas) throw Error('Canvas failed to mount');
  const result = await inspectDesignCanvas(canvas, input.target);
  const capture = input.target
    ? result.layers[0]?.visibleRect
    : { x: 0, y: 0, ...(input.mode === 'full' ? result.natural : result.viewport) };
  const issues = result.layers.filter((layer) => layer.issues.length > 0);
  const relevant = input.target ? result.layers : issues;
  const layers = relevant.slice(0, 24);
  return {
    ...result,
    layers,
    measuredCount: result.layers.length,
    issueCount: issues.length,
    omittedLayers: relevant.length - layers.length,
    capture: capture ?? null,
  };
}

function publish(result: unknown) {
  const output = document.createElement('textarea');
  output.id = 'design-result';
  output.hidden = true;
  output.textContent = JSON.stringify(result);
  document.body.append(output);
}
void render().then(publish, (error: unknown) =>
  publish({ error: error instanceof Error ? error.message : 'Render failed' }),
);
