package org.jstats.matchlens_api.modules.football_data_org_gatherer.ingest;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@org.springframework.boot.test.autoconfigure.jdbc.JdbcTest
@org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase(replace = org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase.Replace.NONE)
@org.springframework.context.annotation.Import(FdRawIngestRepository.class)
class FdRawIngestRepositoryIntegrationTests {

    @TestConfiguration(proxyBeanMethods = false)
    static class TestContainersLocal {
        @Bean
        @ServiceConnection
        PostgreSQLContainer<?> postgresContainer() {
            return new PostgreSQLContainer<>(DockerImageName.parse("pgvector/pgvector:pg16"));
        }
    }

    @Autowired
    NamedParameterJdbcTemplate jdbc;

    @Autowired
    FdRawIngestRepository repository;

    private static final String SRC = "football-data.org";
    private static final String EP = "/v4/competitions/{code}";
    private static final String KEY = "PL";

    @BeforeEach
    void clean() {
        jdbc.update("DELETE FROM fd_raw_ingest WHERE source=:s AND endpoint=:e AND external_key=:k",
                new org.springframework.jdbc.core.namedparam.MapSqlParameterSource(Map.of(
                        "s", SRC,
                        "e", EP,
                        "k", KEY
                )));
    }

    @Test
    void insertRaw_returnsId_andPersistsRow() {
        var now = OffsetDateTime.now(ZoneOffset.UTC);
        var idOpt = repository.insertRaw(SRC, EP, KEY, now, "{\"ok\":true}");
        assertTrue(idOpt.isPresent());

        var count = jdbc.getJdbcTemplate().queryForObject(
                "SELECT count(*) FROM fd_raw_ingest WHERE id = ? AND source = ? AND endpoint = ? AND external_key = ?",
                Integer.class,
                idOpt.get(), SRC, EP, KEY);
        assertNotNull(count);
        assertEquals(1, count);
    }

    @Test
    void insertRaw_duplicateKey_returnsEmptyOptional_dueToOnConflictDoNothing() {
        var now = OffsetDateTime.now(ZoneOffset.UTC);
        var id1 = repository.insertRaw(SRC, EP, KEY, now, "{\"n\":1}");
        assertTrue(id1.isPresent());

        var id2 = repository.insertRaw(SRC, EP, KEY, now.plusMinutes(1), "{\"n\":2}");
        assertTrue(id2.isEmpty(), "Expected empty Optional on conflict DO NOTHING");

        // Still exactly one row for the triplet
        var count = jdbc.getJdbcTemplate().queryForObject(
                "SELECT count(*) FROM fd_raw_ingest WHERE source = ? AND endpoint = ? AND external_key = ?",
                Integer.class,
                SRC, EP, KEY);
        assertEquals(1, count);
    }

    @Test
    void wasFetchedSince_respectsFetchedAtCutoff() {
        var now = OffsetDateTime.now(ZoneOffset.UTC);
        // Insert a row with fetched_at defaulting to now()
        var id = repository.insertRaw(SRC, EP, KEY, now, "{\"x\":1}");
        assertTrue(id.isPresent());

        // The table uses fetched_at DEFAULT now(); ensure queries see the row as fetched recently
        var monthAgo = now.minusMonths(1);
        assertTrue(repository.wasFetchedSince(SRC, EP, KEY, monthAgo));

        // A future cutoff after now should also return true (still since before cutoff)
        var aMinuteAgo = now.minusMinutes(1);
        assertTrue(repository.wasFetchedSince(SRC, EP, KEY, aMinuteAgo));

        // For a far-future cutoff (e.g., now + 1 day), the row shouldn't qualify
        var tomorrow = now.plusDays(1);
        assertFalse(repository.wasFetchedSince(SRC, EP, KEY, tomorrow));
    }
}
