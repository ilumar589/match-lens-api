-- Enable pgvector extension
CREATE EXTENSION IF NOT EXISTS vector;

-- Create dedicated table for match embeddings
CREATE TABLE IF NOT EXISTS match_embedding (
    id BIGSERIAL PRIMARY KEY,
    match_id BIGINT NOT NULL REFERENCES fd_match(id),
    embedding vector(768) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT now(),
    UNIQUE(match_id)
);

-- Create index for vector similarity search using HNSW algorithm
CREATE INDEX match_embedding_vector_idx ON match_embedding USING hnsw (embedding vector_cosine_ops);
