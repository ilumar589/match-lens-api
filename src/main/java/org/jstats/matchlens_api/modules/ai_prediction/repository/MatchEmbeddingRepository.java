package org.jstats.matchlens_api.modules.ai_prediction.repository;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * Repository for vector operations on match embeddings.
 */
@Repository
public class MatchEmbeddingRepository {

    private static final Logger log = LoggerFactory.getLogger(MatchEmbeddingRepository.class);

    private final NamedParameterJdbcTemplate jdbcTemplate;

    public MatchEmbeddingRepository(NamedParameterJdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /**
     * Converts a List of Doubles to PostgreSQL vector format string.
     *
     * @param embedding the embedding vector as a list of doubles
     * @return the PostgreSQL vector format string, e.g., "[0.1,0.2,0.3]"
     */
    private String toVectorString(List<Double> embedding) {
        return embedding.stream()
                .map(Object::toString)
                .reduce((a, b) -> a + "," + b)
                .map(s -> "[" + s + "]")
                .orElse("[]");
    }

    /**
     * Saves an embedding for a match.
     *
     * @param matchId   the ID of the match
     * @param embedding the embedding vector
     * @return the ID of the saved embedding, or empty if already exists
     */
    public Optional<Long> save(Long matchId, List<Double> embedding) {
        String vectorString = toVectorString(embedding);

        String sql = """
                INSERT INTO match_embedding (match_id, embedding)
                VALUES (:matchId, :embedding::vector)
                ON CONFLICT (match_id) DO NOTHING
                RETURNING id
                """;

        var params = new MapSqlParameterSource()
                .addValue("matchId", matchId)
                .addValue("embedding", vectorString);

        try {
            List<Long> ids = jdbcTemplate.queryForList(sql, params, Long.class);
            return ids.isEmpty() ? Optional.empty() : Optional.of(ids.getFirst());
        } catch (Exception e) {
            log.error("Failed to save embedding for match {}: {}", matchId, e.getMessage());
            return Optional.empty();
        }
    }

    /**
     * Finds similar matches based on vector similarity.
     *
     * @param queryEmbedding the query embedding vector
     * @param limit          maximum number of results
     * @return list of match IDs ordered by similarity
     */
    public List<Long> findSimilarMatches(List<Double> queryEmbedding, int limit) {
        String vectorString = toVectorString(queryEmbedding);

        String sql = """
                SELECT match_id
                FROM match_embedding
                ORDER BY embedding <=> :queryEmbedding::vector
                LIMIT :limit
                """;

        var params = new MapSqlParameterSource()
                .addValue("queryEmbedding", vectorString)
                .addValue("limit", limit);

        return jdbcTemplate.queryForList(sql, params, Long.class);
    }

    /**
     * Checks if an embedding exists for a match.
     *
     * @param matchId the match ID
     * @return true if embedding exists
     */
    public boolean existsByMatchId(Long matchId) {
        String sql = "SELECT EXISTS(SELECT 1 FROM match_embedding WHERE match_id = :matchId)";
        var params = new MapSqlParameterSource().addValue("matchId", matchId);
        Boolean exists = jdbcTemplate.queryForObject(sql, params, Boolean.class);
        return Boolean.TRUE.equals(exists);
    }

    /**
     * Deletes an embedding by match ID.
     *
     * @param matchId the match ID
     * @return number of rows deleted
     */
    public int deleteByMatchId(Long matchId) {
        String sql = "DELETE FROM match_embedding WHERE match_id = :matchId";
        var params = new MapSqlParameterSource().addValue("matchId", matchId);
        return jdbcTemplate.update(sql, params);
    }
}
