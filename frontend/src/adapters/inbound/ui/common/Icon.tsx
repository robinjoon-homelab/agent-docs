export type IconName =
  | 'search'
  | 'inbox'
  | 'settings'
  | 'plus'
  | 'history'
  | 'comment'
  | 'link'
  | 'menu'
  | 'chevron-down'
  | 'chevron-right'
  | 'close'
  | 'drag'
  | 'trash'
  | 'agent'
  | 'sidebar'
  | 'check'
  | 'warning'
  | 'home'
  | 'doc'
  | 'folder'
  | 'image'
  | 'more';

const PATHS: Record<IconName, string> = {
  search:
    'M11 4a7 7 0 1 0 4.4 12.4l4.1 4.1 1.4-1.4-4.1-4.1A7 7 0 0 0 11 4Zm0 2a5 5 0 1 1 0 10 5 5 0 0 1 0-10Z',
  inbox: 'M4 4h16l1 10v6H3v-6L4 4Zm1.8 2-.7 7H9l1 2h4l1-2h3.9l-.7-7H5.8Z',
  settings:
    'M12 8a4 4 0 1 0 0 8 4 4 0 0 0 0-8Zm8.4 5.5-.1-3 2-1.6-2-3.4-2.4.8-2.4-1.5L15 2h-4l-.5 2.8-2.4 1.5-2.4-.8-2 3.4 2 1.6-.1 3-2 1.6 2 3.4 2.4-.8 2.4 1.5L11 22h4l.5-2.8 2.4-1.5 2.4.8 2-3.4-1.9-1.6Z',
  plus: 'M11 5h2v6h6v2h-6v6h-2v-6H5v-2h6V5Z',
  history:
    'M13 3a9 9 0 0 0-9 9H1l4 4 4-4H6a7 7 0 1 1 2 4.9l-1.4 1.4A9 9 0 1 0 13 3Zm-1 5v5l4.2 2.5.8-1.3-3.5-2.1V8H12Z',
  comment: 'M4 4h16v12H8l-4 4V4Zm2 2v9.2L7.2 14H18V6H6Z',
  link: 'M10.6 13.4a1 1 0 0 0 1.4 0l4-4a3 3 0 0 0-4.2-4.2l-1.5 1.5 1.4 1.4 1.5-1.5a1 1 0 0 1 1.4 1.4l-4 4a1 1 0 0 0 0 1.4Zm2.8-2.8a1 1 0 0 0-1.4 0l-4 4a3 3 0 1 0 4.2 4.2l1.5-1.5-1.4-1.4-1.5 1.5a1 1 0 0 1-1.4-1.4l4-4a1 1 0 0 0 0-1.4Z',
  menu: 'M3 6h18v2H3V6Zm0 5h18v2H3v-2Zm0 5h18v2H3v-2Z',
  'chevron-down': 'm7 9 5 5 5-5H7Z',
  'chevron-right': 'm9 7 5 5-5 5V7Z',
  close: 'm6.4 5 5.6 5.6L17.6 5 19 6.4 13.4 12l5.6 5.6-1.4 1.4-5.6-5.6L6.4 19 5 17.6l5.6-5.6L5 6.4 6.4 5Z',
  drag: 'M9 5a1.5 1.5 0 1 0 0 3 1.5 1.5 0 0 0 0-3Zm6 0a1.5 1.5 0 1 0 0 3 1.5 1.5 0 0 0 0-3ZM9 10.5a1.5 1.5 0 1 0 0 3 1.5 1.5 0 0 0 0-3Zm6 0a1.5 1.5 0 1 0 0 3 1.5 1.5 0 0 0 0-3ZM9 16a1.5 1.5 0 1 0 0 3 1.5 1.5 0 0 0 0-3Zm6 0a1.5 1.5 0 1 0 0 3 1.5 1.5 0 0 0 0-3Z',
  trash: 'M9 3h6l1 2h4v2H4V5h4l1-2Zm-3 6h12l-1 12H7L6 9Zm3 2v8h2v-8H9Zm4 0v8h2v-8h-2Z',
  agent: 'M12 2 9.5 7H4l4.5 3.5L7 16l5-3 5 3-1.5-5.5L20 7h-5.5L12 2Zm-7 17h14v2H5v-2Z',
  sidebar: 'M3 4h18v16H3V4Zm2 2v12h4V6H5Zm6 0v12h8V6h-8Z',
  check: 'm9.5 16.2-4.2-4.2-1.4 1.4 5.6 5.6L20.5 8 19 6.6l-9.5 9.6Z',
  warning: 'M12 2 1 21h22L12 2Zm0 4.5 7.5 12.5h-15L12 6.5ZM11 10v4h2v-4h-2Zm0 5v2h2v-2h-2Z',
  home: 'M12 3 2 11h3v9h6v-6h2v6h6v-9h3L12 3Z',
  doc: 'M6 2h8l5 5v15H6V2Zm7 1.5V8h4.5L13 3.5ZM8 12v2h8v-2H8Zm0 4v2h8v-2H8Z',
  folder: 'M3 5h7l2 2h9v12H3V5Z',
  image: 'M3 4h18v16H3V4Zm2 2v9.6l3.5-3.5 3 3 4-4L19 14.6V6H5Zm4 1.5a1.5 1.5 0 1 1 0 3 1.5 1.5 0 0 1 0-3Z',
  more: 'M6 10.5a1.5 1.5 0 1 0 0 3 1.5 1.5 0 0 0 0-3Zm6 0a1.5 1.5 0 1 0 0 3 1.5 1.5 0 0 0 0-3Zm6 0a1.5 1.5 0 1 0 0 3 1.5 1.5 0 0 0 0-3Z',
};

export function Icon({ name, size = 16, title }: { name: IconName; size?: number; title?: string }) {
  return (
    <svg
      width={size}
      height={size}
      viewBox="0 0 24 24"
      fill="currentColor"
      aria-hidden={title === undefined}
      role={title === undefined ? undefined : 'img'}
    >
      {title === undefined ? null : <title>{title}</title>}
      <path d={PATHS[name]} />
    </svg>
  );
}
