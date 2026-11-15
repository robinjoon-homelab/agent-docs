-- The owner usage metrics are removed. document_views held only the per-day opens behind the "came back to it on
-- another day" figure, so it goes with them. The other figures were counted from documents, revisions, threads, and
-- comments when asked, and those tables stay as they are.
DROP TABLE document_views;
