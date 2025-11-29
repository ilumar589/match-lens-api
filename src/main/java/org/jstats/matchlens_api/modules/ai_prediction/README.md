# AI Prediction Module

This module provides AI-powered match prediction capabilities using Retrieval-Augmented Generation (RAG) with Spring AI, Ollama, and PostgreSQL pgvector.

## Architecture

### RAG (Retrieval-Augmented Generation) Flow

1. **Embedding Generation**: Historical match data is converted into vector embeddings using Ollama's `nomic-embed-text` model
2. **Vector Storage**: Embeddings are stored in PostgreSQL with the pgvector extension for efficient similarity search
3. **Retrieval**: When a prediction is requested, similar historical matches are retrieved using cosine similarity
4. **Augmentation**: Retrieved matches provide context for the LLM
5. **Generation**: Ollama's `llama3.2` model generates predictions based on the context

### Components

```
ai_prediction/
├── config/
│   ├── OllamaConfig.java       # ChatClient configuration
│   ├── PromptConfig.java       # Prompt templates and settings
│   └── package-info.java
├── service/
│   ├── MatchPredictionService.java   # Main prediction orchestration
│   ├── EmbeddingService.java         # Embedding generation
│   ├── MatchContextBuilder.java      # Context building from matches
│   └── package-info.java
├── model/
│   ├── PredictionRequest.java        # Input DTO
│   ├── PredictionResponse.java       # Output DTO
│   ├── MatchContext.java             # RAG context model
│   └── package-info.java
├── controller/
│   ├── PredictionController.java     # REST endpoints
│   └── package-info.java
└── repository/
    ├── MatchEmbeddingRepository.java # Vector operations
    └── package-info.java
```

## API Endpoints

### Predict Match Outcome

```http
POST /api/predictions
Content-Type: application/json

{
    "homeTeam": "Liverpool",
    "awayTeam": "Manchester City",
    "competition": "PL",
    "matchDate": "2024-01-15"
}
```

**Response:**
```json
{
    "predictedWinner": "HOME",
    "confidence": 0.65,
    "reasoning": "Liverpool has shown strong home form...",
    "keyFactors": [
        "Liverpool's home advantage",
        "Recent head-to-head results"
    ],
    "relevantMatches": [
        {
            "homeTeam": "Liverpool",
            "awayTeam": "Manchester City",
            "result": "2-1",
            "competition": "Premier League",
            "date": "2023-10-15"
        }
    ]
}
```

### Generate Embeddings

```http
POST /api/predictions/embeddings/generate?limit=100
```

Triggers batch generation of embeddings for finished matches.

### Health Check

```http
GET /api/predictions/health
```

## Configuration

### Application Properties

```properties
# Ollama Configuration
spring.ai.ollama.base-url=http://localhost:11434
spring.ai.ollama.chat.options.model=llama3.2
spring.ai.ollama.chat.options.temperature=0.7
spring.ai.ollama.embedding.options.model=nomic-embed-text

# Vector Store Configuration
spring.ai.vectorstore.pgvector.index-type=HNSW
spring.ai.vectorstore.pgvector.distance-type=COSINE_DISTANCE
spring.ai.vectorstore.pgvector.dimensions=768

# AI Prediction Settings
matchlens.ai.prediction.max-context-matches=15
matchlens.ai.prediction.cache-ttl=1h
```

## Model Selection Guide

### Chat Models (for predictions)

| Model | Size | Pros | Cons |
|-------|------|------|------|
| `llama3.2` | 2-3B | Fast, good quality | May lack nuance |
| `mistral` | 7B | Better reasoning | Slower |
| `llama3.1:8b` | 8B | Best quality | Requires more resources |

### Embedding Models

| Model | Dimensions | Best For |
|-------|------------|----------|
| `nomic-embed-text` | 768 | General text, default choice |
| `mxbai-embed-large` | 1024 | Better semantic understanding |

## Database Schema

The module uses the `match_embedding` table:

```sql
CREATE TABLE match_embedding (
    id BIGSERIAL PRIMARY KEY,
    match_id BIGINT NOT NULL REFERENCES fd_match(id),
    embedding vector(768) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT now(),
    UNIQUE(match_id)
);

CREATE INDEX match_embedding_vector_idx 
    ON match_embedding USING hnsw (embedding vector_cosine_ops);
```

## Performance Considerations

1. **Initial Setup**: Run embedding generation for historical matches before predictions
2. **Batch Processing**: Use the batch endpoint to generate embeddings in chunks
3. **Index Type**: HNSW index provides fast approximate nearest neighbor search
4. **Model Warmup**: First prediction may be slower due to model loading

## Running Locally

### Prerequisites

1. Docker and Docker Compose
2. Sufficient RAM for Ollama models (8GB+ recommended)

### Start Services

```bash
docker compose up -d
```

This starts:
- PostgreSQL with pgvector extension
- Ollama with auto-pulled models

### Pull Models (if not auto-pulled)

```bash
docker exec -it <ollama-container> ollama pull llama3.2
docker exec -it <ollama-container> ollama pull nomic-embed-text
```

## Testing

Integration tests use Testcontainers with:
- `pgvector/pgvector:pg16` for PostgreSQL with vector support
- `ollama/ollama:latest` for LLM capabilities

Note: Integration tests may take longer due to model loading and inference.

## Troubleshooting

### Ollama Connection Issues
- Verify Ollama is running: `curl http://localhost:11434/api/tags`
- Check if models are pulled: `ollama list`

### Vector Store Issues
- Verify pgvector extension: `SELECT * FROM pg_extension WHERE extname = 'vector';`
- Check embedding dimensions match configuration (768)

### Slow Predictions
- Consider using a smaller model
- Ensure sufficient CPU/RAM for Ollama
- Check if embeddings are properly indexed

## Circuit Breaker (Resilience4j)

The prediction service is protected by a circuit breaker to handle failures gracefully when the Ollama AI service is unavailable.

### Configuration

Circuit breaker settings are defined in `application-resilience.yml`:

```yaml
resilience4j:
  circuitbreaker:
    instances:
      ollamaService:
        slidingWindowSize: 20
        minimumNumberOfCalls: 10
        waitDurationInOpenState: 30s
        failureRateThreshold: 60
```

### Circuit Breaker States

1. **CLOSED**: Normal operation, requests pass through
2. **OPEN**: After failure threshold is reached, requests are rejected immediately with fallback response
3. **HALF_OPEN**: After wait duration, allows limited requests to test service recovery

### Fallback Behavior

When the circuit breaker is open or the Ollama service fails, a fallback response is returned:

```json
{
  "predictedWinner": "UNAVAILABLE",
  "confidence": 0.0,
  "reasoning": "Prediction service temporarily unavailable: <error details>",
  "keyFactors": ["Service degraded", "Circuit breaker active"],
  "relevantMatches": []
}
```

### Monitoring

Circuit breaker status is available via Spring Boot Actuator:

- `/actuator/health` - Overall health including circuit breaker status
- `/actuator/circuitbreakers` - Detailed circuit breaker information
- `/actuator/circuitbreakerevents` - Recent circuit breaker events
