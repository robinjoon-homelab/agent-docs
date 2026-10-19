-- An inbox item becomes its reader's by user id the next time they open their inbox, so a GitHub login change keeps it.
ALTER TABLE inbox_items ADD COLUMN recipient_user_id UUID;
CREATE INDEX ix_inbox_items_recipient_user ON inbox_items (recipient_user_id, delivered_at);
