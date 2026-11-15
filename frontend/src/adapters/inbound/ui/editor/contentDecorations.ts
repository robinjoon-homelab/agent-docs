import { Extension } from '@tiptap/core';
import { Plugin } from '@tiptap/pm/state';
import { Decoration, DecorationSet } from '@tiptap/pm/view';
import katex from 'katex';
import type { GitHubContext } from '../../../../domain/document/githubReferences';
import { githubReferences } from '../../../../domain/document/githubReferences';
import { mathSpans } from '../../../../domain/document/math';
import 'katex/dist/katex.min.css';

export const ContentDecorations = Extension.create<{ github: () => GitHubContext }>({
  name: 'contentDecorations',
  addOptions: () => ({ github: () => ({ repositories: [] }) }),
  addProseMirrorPlugins() {
    const options = this.options;
    return [
      new Plugin({
        props: {
          decorations(state) {
            const decorations: Decoration[] = [];
            state.doc.descendants((node, position, parent) => {
              if (
                !node.isText ||
                parent?.type.name === 'codeBlock' ||
                node.marks.some((mark) => ['code', 'link'].includes(mark.type.name))
              )
                return;
              const text = node.text ?? '';
              const math = mathSpans(text);
              for (const span of math) {
                decorations.push(
                  Decoration.inline(position + span.from, position + span.to, { class: 'math-source' }),
                );
                decorations.push(
                  Decoration.widget(
                    position + span.to,
                    () => {
                      const preview = document.createElement('span');
                      preview.className = 'math-decoration';
                      preview.contentEditable = 'false';
                      katex.render(span.source, preview, {
                        displayMode: span.display,
                        trust: false,
                        throwOnError: false,
                        maxSize: 20,
                        maxExpand: 1000,
                        strict: 'error',
                      });
                      return preview;
                    },
                    { side: 1 },
                  ),
                );
              }
              for (const span of githubReferences(text, options.github())) {
                if (math.some((formula) => span.from < formula.to && formula.from < span.to)) continue;
                decorations.push(
                  Decoration.inline(position + span.from, position + span.to, {
                    nodeName: 'a',
                    href: span.href,
                    rel: 'noopener noreferrer',
                    class: 'github-reference',
                  }),
                );
              }
            });
            return DecorationSet.create(state.doc, decorations);
          },
        },
      }),
    ];
  },
});
