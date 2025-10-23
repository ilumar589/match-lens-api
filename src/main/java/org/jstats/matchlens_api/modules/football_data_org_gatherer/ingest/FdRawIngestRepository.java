package org.jstats.matchlens_api.modules.football_data_org_gatherer.ingest;

import org.jspecify.annotations.NullMarked;
import org.postgresql.util.PGobject;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.time.OffsetDateTime;
import java.util.Optional;

@Repository
@NullMarked
public class FdRawIngestRepository {

    private final NamedParameterJdbcTemplate jdbc;

    public FdRawIngestRepository(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public boolean wasFetchedSince(String source, String endpoint, String externalKey, OffsetDateTime since) {
        var sql = """
                
                    SELECT 1
                FROM fd_raw_ingest
                WHERE source = :source
                  AND endpoint = :endpoint
                  AND external_key = :externalKey
                  AND fetched_at >= :since
                LIMIT 1
                """;
        var params = new MapSqlParameterSource()
                .addValue("source", source)
                .addValue("endpoint", endpoint)
                .addValue("externalKey", externalKey)
                .addValue("since", since);
        final var exists = jdbc.query(sql, params, rs -> rs.next() ? Boolean.TRUE : Boolean.FALSE);
        return Boolean.TRUE.equals(exists);
    }

    public Optional<Long> insertRaw(
            String source,
            String endpoint,
            String externalKey,
            OffsetDateTime lastModified,
            String jsonBody
    ) {

        PGobject jsonb = null;
        if (!jsonBody.isEmpty()) {
            jsonb = new PGobject();
            jsonb.setType("jsonb");
            try {
                jsonb.setValue(jsonBody);
            } catch (Exception e) {
                throw new IllegalArgumentException("Failed to set JSONB payload", e);
            }
        }

        final var sql = """
                INSERT INTO fd_raw_ingest
                  (source, endpoint, external_key, last_modified, payload)
                VALUES
                  (:source, :endpoint, :externalKey, :lastModified, :payload)
                ON CONFLICT (source, endpoint, external_key)
                DO NOTHING
                RETURNING id
                """;

        final var params = new MapSqlParameterSource()
                .addValue("source", source)
                .addValue("endpoint", endpoint)
                .addValue("externalKey", externalKey)
                .addValue("lastModified", lastModified)
                .addValue("payload", jsonb);

        final var id = jdbc.query(sql, params, rs -> rs.next() ? rs.getLong(1) : null);
        return Optional.ofNullable(id);
    }
}
