package org.jstats.matchlens_api.modules.ai_prediction;

import org.jstats.matchlens_api.modules.ai_prediction.model.PredictionRequest;
import org.jstats.matchlens_api.modules.ai_prediction.model.PredictionResponse;
import org.jstats.matchlens_api.modules.ai_prediction.repository.MatchEmbeddingRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration tests for the AI prediction module using pgvector.
 * Note: Full integration with Ollama requires the Ollama container to be running.
 */
@org.springframework.boot.test.autoconfigure.jdbc.JdbcTest
@org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase(replace = org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase.Replace.NONE)
@Import({MatchEmbeddingRepository.class, AiPredictionModuleTests.TestContainersLocal.class})
class AiPredictionModuleTests {

    @TestConfiguration(proxyBeanMethods = false)
    static class TestContainersLocal {
        @Bean
        @ServiceConnection
        PostgreSQLContainer<?> postgresContainer() {
            return new PostgreSQLContainer<>(DockerImageName.parse("pgvector/pgvector:pg16"));
        }
    }

    @Autowired
    NamedParameterJdbcTemplate jdbcTemplate;

    @Autowired
    MatchEmbeddingRepository embeddingRepository;

    @Test
    void predictionRequest_shouldBeValidWithRequiredFields() {
        var request = new PredictionRequest(
                "Liverpool",
                "Manchester City",
                "PL",
                LocalDate.of(2024, 1, 15)
        );

        assertNotNull(request);
        assertEquals("Liverpool", request.homeTeam());
        assertEquals("Manchester City", request.awayTeam());
        assertEquals("PL", request.competition());
        assertEquals(LocalDate.of(2024, 1, 15), request.matchDate());
    }

    @Test
    void predictionResponse_shouldContainAllFields() {
        var response = new PredictionResponse(
                "HOME",
                0.65,
                "Liverpool has strong home form",
                List.of("Home advantage", "Recent form"),
                List.of(new PredictionResponse.HistoricalMatch(
                        "Liverpool", "Man City", "2-1", "PL", "2023-10-15"
                ))
        );

        assertNotNull(response);
        assertEquals("HOME", response.predictedWinner());
        assertEquals(0.65, response.confidence());
        assertEquals("Liverpool has strong home form", response.reasoning());
        assertEquals(2, response.keyFactors().size());
        assertEquals(1, response.relevantMatches().size());
    }

    @Test
    void pgvectorExtension_shouldBeEnabled() {
        // Check if the vector extension is available
        Boolean extensionExists = jdbcTemplate.queryForObject(
                "SELECT EXISTS(SELECT 1 FROM pg_extension WHERE extname = 'vector')",
                new org.springframework.jdbc.core.namedparam.MapSqlParameterSource(),
                Boolean.class
        );
        assertTrue(Boolean.TRUE.equals(extensionExists), "pgvector extension should be enabled");
    }

    @Test
    void matchEmbeddingTable_shouldExist() {
        // Check if the match_embedding table exists
        Boolean tableExists = jdbcTemplate.queryForObject("""
                SELECT EXISTS(
                    SELECT 1 FROM information_schema.tables 
                    WHERE table_name = 'match_embedding'
                )
                """,
                new org.springframework.jdbc.core.namedparam.MapSqlParameterSource(),
                Boolean.class
        );
        assertTrue(Boolean.TRUE.equals(tableExists), "match_embedding table should exist");
    }

    @Test
    void embeddingRepository_existsByMatchId_shouldReturnFalseForNonExistentMatch() {
        boolean exists = embeddingRepository.existsByMatchId(999999L);
        assertFalse(exists);
    }
}
