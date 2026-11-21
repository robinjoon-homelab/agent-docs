export function FlowIcon({
  name,
  size = 16,
}: {
  name:
    | 'flow'
    | 'comment'
    | 'left'
    | 'right'
    | 'panel'
    | 'plus'
    | 'eye'
    | 'edit'
    | 'menu'
    | 'search'
    | 'image'
    | 'info';
  size?: number;
}) {
  const paths = {
    flow: 'M4 6.5v5h5.5',
    comment: 'M2.5 3.5h11V11H7l-3 2.5V11H2.5z',
    left: 'M10 4L6 8l4 4',
    right: 'M6 4l4 4-4 4',
    panel: 'M10 2.5v11',
    plus: 'M8 3v10M3 8h10',
    eye: 'M1.5 8s2.5-4.5 6.5-4.5S14.5 8 14.5 8 12 12.5 8 12.5 1.5 8 1.5 8z',
    edit: 'M10.5 2.5l3 3L6 13H3v-3z',
    menu: 'M2.5 4.5h11M2.5 8h11M2.5 11.5h11',
    search: 'M10.5 10.5L14 14',
    image: 'M2 12l4-4 3 3 2-2 3 3',
    info: 'M8 7v5M8 4h.01',
  };
  return (
    <svg
      width={size}
      height={size}
      viewBox="0 0 16 16"
      fill="none"
      stroke="currentColor"
      strokeWidth={name === 'comment' ? 2 : name === 'info' ? 2.4 : 1.6}
      strokeLinecap="round"
      strokeLinejoin="round"
      aria-hidden="true"
    >
      <path d={paths[name]} />
      {name === 'flow' && (
        <>
          <rect x="1.5" y="2.5" width="5" height="4" rx="1" />
          <rect x="9.5" y="9.5" width="5" height="4" rx="1" />
        </>
      )}
      {(name === 'panel' || name === 'image') && <rect x="1.5" y="2.5" width="13" height="11" rx="1.5" />}
      {name === 'search' && <circle cx="7" cy="7" r="4.5" />}
      {name === 'eye' && <circle cx="8" cy="8" r="2" />}
      {name === 'image' && <circle cx="5.5" cy="6" r="1.2" />}
    </svg>
  );
}
export function FlowCommentBadge({ count }: { count: number }) {
  return count > 0 ? (
    <span className="flow-comment-badge">
      <FlowIcon name="comment" size={11} />
      {count}
    </span>
  ) : null;
}

export function FlowToolIcon({ name }: { name: 'front' | 'back' | 'copy' | 'delete' }) {
  return (
    <svg
      width="14"
      height="14"
      viewBox="0 0 16 16"
      fill="none"
      stroke="currentColor"
      strokeWidth="1.6"
      strokeLinecap="round"
      strokeLinejoin="round"
      aria-hidden="true"
    >
      {name === 'front' ? (
        <>
          <rect x="5.5" y="5.5" width="8" height="8" rx="1.2" fill="currentColor" />
          <path d="M2.5 10.5v-7a1 1 0 0 1 1-1h7" />
        </>
      ) : name === 'back' ? (
        <>
          <rect x="2.5" y="2.5" width="8" height="8" rx="1.2" fill="currentColor" />
          <path d="M13.5 5.5v7a1 1 0 0 1-1 1h-7" />
        </>
      ) : name === 'copy' ? (
        <>
          <rect x="5" y="5" width="8.5" height="8.5" rx="1.5" />
          <path d="M3 11V3.5A1 1 0 0 1 4 2.5h7" />
        </>
      ) : (
        <path d="M3 4.5h10M6.5 4.5V3h3v1.5M4.5 4.5l.7 8.5h5.6l.7-8.5" />
      )}
    </svg>
  );
}
