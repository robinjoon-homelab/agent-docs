// @vitest-environment jsdom
import { Editor } from '@tiptap/core';
import { describe, expect, it } from 'vitest';
import { ContentDecorations } from './contentDecorations';
import { schemaExtensions } from './schema';

describe('GitHub display references', () => {
  it('decorates plain references without changing document JSON or code and explicit links', () => {
    const content = {
      type: 'doc',
      content: [
        {
          type: 'paragraph',
          content: [
            { type: 'text', text: '#123 src/main.kt ' },
            { type: 'text', text: '#456', marks: [{ type: 'code' }] },
            { type: 'text', text: ' ' },
            { type: 'text', text: '#789', marks: [{ type: 'link', attrs: { href: 'https://example.com' } }] },
          ],
        },
        { type: 'codeBlock', content: [{ type: 'text', text: '#999' }] },
      ],
    };
    const element = document.createElement('div');
    const editor = new Editor({
      element,
      extensions: [
        ...schemaExtensions,
        ContentDecorations.configure({ github: () => ({ repositories: ['owner/repo'] }) }),
      ],
      content,
    });
    try {
      const before = editor.getJSON();
      expect(
        Array.from(element.querySelectorAll('a.github-reference'), (node) => node.getAttribute('href')),
      ).toEqual([
        'https://github.com/owner/repo/issues/123',
        'https://github.com/owner/repo/blob/HEAD/src/main.kt',
      ]);
      editor.commands.setTextSelection(2);
      expect(editor.getJSON()).toEqual(before);
    } finally {
      editor.destroy();
    }
  });
});
