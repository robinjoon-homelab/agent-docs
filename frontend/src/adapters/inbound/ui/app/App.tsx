import { lazy, Suspense } from 'react';
import { BrowserRouter, Route, Routes } from 'react-router';
import { Loading, NotFound } from '../common/States';
import { ToastProvider } from '../common/Toast';
import { DocumentRedirect } from '../pages/document/DocumentRedirect';
import { HomePage } from '../pages/home/HomePage';
import { InboxPage } from '../pages/inbox/InboxPage';
import { LoginPage } from '../pages/login/LoginPage';
import { ConnectionApprovalPage } from '../pages/oauth/ConnectionApprovalPage';
import { SearchPage } from '../pages/search/SearchPage';
import { AgentConnectionsPage } from '../pages/settings/AgentConnectionsPage';
import { SpaceHomePage } from '../pages/space/SpaceHomePage';
import { SpaceSettingsPage } from '../pages/space/SpaceSettingsPage';
import { AppShell } from './AppShell';
import { RequireSession } from './RequireSession';

// The editor (Tiptap/ProseMirror) and the diff view are the heaviest screens; load them on demand.
const DocumentPage = lazy(() =>
  import('../pages/document/DocumentPage').then((module) => ({ default: module.DocumentPage })),
);
const HistoryPage = lazy(() =>
  import('../pages/history/HistoryPage').then((module) => ({ default: module.HistoryPage })),
);

export function App() {
  return (
    <ToastProvider>
      <BrowserRouter>
        <Suspense fallback={<Loading />}>
          <Routes>
            <Route path="/login" element={<LoginPage />} />
            <Route element={<RequireSession />}>
              <Route path="/oauth/authorize" element={<ConnectionApprovalPage />} />
              <Route element={<AppShell />}>
                <Route index element={<HomePage />} />
                <Route path="/inbox" element={<InboxPage />} />
                <Route path="/search" element={<SearchPage />} />
                <Route path="/settings/agents" element={<AgentConnectionsPage />} />
                <Route path="/d/:docId" element={<DocumentRedirect />} />
                <Route path="/s/:space" element={<SpaceHomePage />} />
                <Route path="/s/:space/settings" element={<SpaceSettingsPage />} />
                <Route path="/s/:space/d/:docId" element={<DocumentPage />} />
                <Route path="/s/:space/d/:docId/history" element={<HistoryPage />} />
                <Route path="*" element={<NotFound />} />
              </Route>
            </Route>
          </Routes>
        </Suspense>
      </BrowserRouter>
    </ToastProvider>
  );
}
