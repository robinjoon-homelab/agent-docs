import type { ReactNodeViewProps } from '@tiptap/react';
import { NodeViewContent, NodeViewWrapper, ReactNodeViewRenderer } from '@tiptap/react';
import { useState } from 'react';
import { MermaidPreview } from '../blocks/MermaidPreview';
import { CodeBlockNode, ImageNode } from './schema';

export const CODE_LANGUAGES: readonly { value: string; label: string }[] = [
  { value: '', label: '일반 텍스트' },
  { value: 'mermaid', label: '다이어그램 (mermaid)' },
  { value: 'bash', label: 'Bash' },
  { value: 'kotlin', label: 'Kotlin' },
  { value: 'java', label: 'Java' },
  { value: 'typescript', label: 'TypeScript' },
  { value: 'javascript', label: 'JavaScript' },
  { value: 'json', label: 'JSON' },
  { value: 'yaml', label: 'YAML' },
  { value: 'sql', label: 'SQL' },
  { value: 'python', label: 'Python' },
  { value: 'go', label: 'Go' },
  { value: 'rust', label: 'Rust' },
  { value: 'html', label: 'HTML' },
  { value: 'css', label: 'CSS' },
  { value: 'markdown', label: 'Markdown' },
];

function languageOptions(current: string) {
  const known = CODE_LANGUAGES.some((language) => language.value === current);
  return known ? CODE_LANGUAGES : [...CODE_LANGUAGES, { value: current, label: current }];
}

function CodeBlockView({ node, updateAttributes, editor }: ReactNodeViewProps) {
  const language = String(node.attrs.language ?? '');
  const mermaid = language === 'mermaid';
  const editable = editor.isEditable;
  const [showCode, setShowCode] = useState(false);
  const codeVisible = !mermaid || editable || showCode;
  const label = languageOptions(language).find((option) => option.value === language)?.label ?? language;

  return (
    <NodeViewWrapper
      className={`code-block${mermaid ? ' code-block-mermaid' : ''}`}
      data-block-id={node.attrs.id as string}
    >
      <div className="code-block-bar" contentEditable={false}>
        {editable ? (
          <select
            aria-label="코드 언어"
            value={language}
            onChange={(event) => updateAttributes({ language: event.target.value })}
          >
            {languageOptions(language).map((option) => (
              <option key={option.value} value={option.value}>
                {option.label}
              </option>
            ))}
          </select>
        ) : (
          <span>{label}</span>
        )}
        {mermaid && !editable ? (
          <button
            type="button"
            className="btn btn-ghost btn-small"
            onClick={() => setShowCode((value) => !value)}
          >
            {showCode ? '코드 숨기기' : '코드 보기'}
          </button>
        ) : null}
      </div>
      <div className="code-block-body">
        <pre className={codeVisible ? undefined : 'code-hidden'} spellCheck={false}>
          <NodeViewContent<'code'> as="code" />
        </pre>
        {mermaid ? (
          <div className="code-block-preview" contentEditable={false}>
            <MermaidPreview code={node.textContent} />
          </div>
        ) : null}
      </div>
    </NodeViewWrapper>
  );
}

export type ImageOptions = { contentUrl: (assetId: string) => string };

function ImageView({ node, updateAttributes, editor, extension, selected }: ReactNodeViewProps) {
  const options = extension.options as ImageOptions;
  const assetId = String(node.attrs.assetId ?? '');
  const alt = String(node.attrs.alt ?? '');
  const caption = String(node.attrs.caption ?? '');
  const editable = editor.isEditable;

  return (
    <NodeViewWrapper
      as="figure"
      className={`image-block${selected ? ' is-selected' : ''}`}
      data-block-id={node.attrs.id as string}
    >
      <div className="image-frame" data-drag-handle="">
        <img src={options.contentUrl(assetId)} alt={alt} draggable={false} />
        {editable && alt.trim() === '' ? (
          <span
            className="badge badge-warning image-alt-warning"
            title="화면 낭독기 사용자를 위해 대체 텍스트를 적어 주세요"
          >
            대체 텍스트 없음
          </span>
        ) : null}
      </div>
      {editable ? (
        <div className="image-fields" contentEditable={false}>
          <input
            className="input image-alt"
            aria-label="대체 텍스트"
            placeholder="대체 텍스트: 이미지가 보여 주는 내용"
            value={alt}
            onChange={(event) => updateAttributes({ alt: event.target.value })}
          />
          <input
            className="input image-caption"
            aria-label="캡션"
            placeholder="캡션"
            value={caption}
            onChange={(event) => updateAttributes({ caption: event.target.value })}
          />
        </div>
      ) : caption === '' ? null : (
        <figcaption>{caption}</figcaption>
      )}
    </NodeViewWrapper>
  );
}

export const CodeBlockWithView = CodeBlockNode.extend({
  addNodeView: () => ReactNodeViewRenderer(CodeBlockView),
});

export const ImageWithView = ImageNode.extend<ImageOptions>({
  // The real URL comes from the asset use case (configured in DocumentEditor); the editor knows no routes.
  addOptions: () => ({ contentUrl: () => '' }),
  addNodeView: () => ReactNodeViewRenderer(ImageView),
});
