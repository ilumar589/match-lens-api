package org.jstats.matchlens_api.modules.ai_prediction.service;

import org.jstats.matchlens_api.modules.ai_prediction.repository.MatchEmbeddingRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

/**
 * Service for generating and storing embeddings for matches.
 */
@Service
public class EmbeddingService {

    private static final Logger log = LoggerFactory.getLogger(EmbeddingService.class);

    private final EmbeddingModel embeddingModel;
    private final MatchEmbeddingRepository repository;
    private final NamedParameterJdbcTemplate jdbcTemplate;

    public EmbeddingService(
            EmbeddingModel embeddingModel,
            MatchEmbeddingRepository repository,
            NamedParameterJdbcTemplate jdbcTemplate) {
        this.embeddingModel = embeddingModel;
        this.repository = repository;
        this.jdbcTemplate = jdbcTemplate;
    }

    /**
     * Converts a float array embedding to a List of Doubles.
     *
     * @param embedding the float array embedding
     * @return the embedding as a List of Doubles
     */
    private List<Double> toDoubleList(float[] embedding) {
        List<Double> result = new java.util.ArrayList<>(embedding.length);
        for (float f : embedding) {
            result.add((double) f);
        }
        return result;
    }

    /**
     * Generates and stores an embedding for a match by ID.
     *
     * @param matchId the match ID
     * @return true if embedding was successfully generated and stored
     */
    public boolean generateAndStoreEmbedding(Long matchId) {
        // Check if embedding already exists
        if (repository.existsByMatchId(matchId)) {
            log.debug("Embedding already exists for match {}", matchId);
            return false;
        }

        // Fetch match details
        String matchText = buildMatchText(matchId);
        if (matchText == null) {
            log.warn("Match {} not found or has no data", matchId);
            return false;
        }

        try {
            // Generate embedding and convert to List<Double>
            float[] embedding = embeddingModel.embed(matchText);
            List<Double> embeddingList = toDoubleList(embedding);

            // Store in database
            repository.save(matchId, embeddingList);
            log.info("Generated and stored embedding for match {}", matchId);
            return true;
        } catch (Exception e) {
            log.error("Failed to generate embedding for match {}: {}", matchId, e.getMessage());
            return false;
        }
    }

    /**
     * Generates embeddings for all finished matches that don't have embeddings yet.
     *
     * @param limit maximum number of matches to process
     * @return number of embeddings generated
     */
    public int generateBatchEmbeddings(int limit) {
        String sql = """
                SELECT m.id
                FROM fd_match m
                LEFT JOIN match_embedding me ON m.id = me.match_id
                WHERE me.id IS NULL
                    AND m.status = 'FINISHED'
                ORDER BY m.utc_date DESC
                LIMIT :limit
                """;

        var params = new MapSqlParameterSource().addValue("limit", limit);
        List<Long> matchIds = jdbcTemplate.queryForList(sql, params, Long.class);

        int count = 0;
        for (Long matchId : matchIds) {
            if (generateAndStoreEmbedding(matchId)) {
                count++;
            }
        }

        log.info("Generated {} embeddings in batch", count);
        return count;
    }

    /**
     * Generates an embedding for a search query.
     *
     * @param query the search query text
     * @return the embedding as a list of doubles
     */
    public List<Double> generateQueryEmbedding(String query) {
        float[] embedding = embeddingModel.embed(query);
        return toDoubleList(embedding);
    }

    /**
     * Builds a text representation of a match for embedding.
     *
     * @param matchId the match ID
     * @return the text representation, or null if match not found
     */
    private String buildMatchText(Long matchId) {
        String sql = """
                SELECT
                    ht.name AS home_team,
                    at.name AS away_team,
                    m.status,
                    m.score_json::text AS score,
                    c.name AS competition,
                    m.utc_date::date::text AS match_date
                FROM fd_match m
                JOIN fd_team ht ON m.home_team_id = ht.id
                JOIN fd_team at ON m.away_team_id = at.id
                JOIN fd_competition c ON m.competition_id = c.id
                WHERE m.id = :matchId
                """;

        var params = new MapSqlParameterSource().addValue("matchId", matchId);

        try {
            Map<String, Object> row = jdbcTemplate.queryForMap(sql, params);

            String homeTeam = (String) row.get("home_team");
            String awayTeam = (String) row.get("away_team");
            String status = (String) row.get("status");
            String score = row.get("score") != null ? row.get("score").toString() : "";
            String competition = (String) row.get("competition");
            String date = row.get("match_date") != null ? row.get("match_date").toString() : "";

            return String.format(
                    "%s vs %s, %s, Score: %s, Competition: %s, Date: %s",
                    homeTeam, awayTeam, status, score, competition, date
            );
        } catch (Exception e) {
            log.warn("Failed to build match text for match {}: {}", matchId, e.getMessage());
            return null;
        }
    }
}
