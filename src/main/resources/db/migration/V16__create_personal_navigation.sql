CREATE TABLE personal_documents (
    user_id UUID NOT NULL,
    document_id UUID NOT NULL REFERENCES documents(id) ON DELETE CASCADE,
    viewed_at TIMESTAMP WITH TIME ZONE,
    favorite_at TIMESTAMP WITH TIME ZONE,
    PRIMARY KEY (user_id, document_id)
);
