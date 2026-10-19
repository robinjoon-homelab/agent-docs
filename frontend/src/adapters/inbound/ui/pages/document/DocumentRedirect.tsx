import { Navigate, useLocation, useParams } from 'react-router';
import { isNotVisible } from '../../../../../application/shared/ports/errors';
import { documentHref } from '../../../../../domain/document/document';
import { useServices } from '../../../../../config/ServicesContext';
import { ErrorBox, Loading, NoAccessDocument } from '../../common/States';
import { useLoad } from '../../hooks/useLoad';

/** Target of `[title](doc:<id>)` links: finds the document's space and opens it there. */
export function DocumentRedirect() {
  const { docId = '' } = useParams();
  const location = useLocation();
  const services = useServices();
  const document = useLoad(`doc-redirect:${docId}`, () => services.documents.get(docId));
  if (document.state === 'loading') return <Loading />;
  if (document.state === 'error') {
    return isNotVisible(document.error) ? (
      <NoAccessDocument />
    ) : (
      <ErrorBox error={document.error} onRetry={document.reload} />
    );
  }
  return (
    <Navigate to={`${documentHref(document.data.space.slug, document.data.id)}${location.hash}`} replace />
  );
}
