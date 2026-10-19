import type { SpaceLink } from '../../../../../domain/space/space';
import { SPACE_LINK_LABEL, linkHref } from '../../../../../domain/space/space';
import { copyText } from '../../common/CopyButton';
import { Icon } from '../../common/Icon';
import { useToast } from '../../common/Toast';

/** External link chips: GitHub and URLs open in a new tab, a local path can only be copied. */
export function SpaceLinks({ links }: { links: readonly SpaceLink[] }) {
  const toast = useToast();
  if (links.length === 0) return null;
  return (
    <ul className="space-links" aria-label="외부 링크">
      {links.map((link) => {
        const href = linkHref(link);
        return (
          <li key={`${link.kind}:${link.value}`}>
            {href === null ? (
              <span className="chip" title={SPACE_LINK_LABEL[link.kind]}>
                <Icon name="folder" size={13} />
                <span className="mono space-link-value">{link.value}</span>
                <button
                  type="button"
                  className="space-link-copy"
                  aria-label={`${link.value} 경로 복사`}
                  onClick={() => {
                    void copyText(link.value).then((copied) =>
                      toast(copied ? '경로를 복사했습니다' : '복사하지 못했습니다'),
                    );
                  }}
                >
                  복사
                </button>
              </span>
            ) : (
              <a
                className="chip"
                href={href}
                target="_blank"
                rel="noreferrer noopener"
                title={SPACE_LINK_LABEL[link.kind]}
              >
                <Icon name="link" size={13} />
                <span className="space-link-kind">{SPACE_LINK_LABEL[link.kind]}</span>
                <span className="space-link-value">{link.value}</span>
              </a>
            )}
          </li>
        );
      })}
    </ul>
  );
}
