package org.jstats.matchlens_api.modules.ai_prediction.service;

import org.jstats.matchlens_api.modules.ai_prediction.model.MatchContext;
import org.jstats.matchlens_api.modules.ai_prediction.model.PredictionResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.document.Document;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Service for building context from similar historical matches.
 */
@Service
public class MatchContextBuilder {

    private static final Logger log = LoggerFactory.getLogger(MatchContextBuilder.class);

    private final NamedParameterJdbcTemplate jdbcTemplate;

    public MatchContextBuilder(NamedParameterJdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /**
     * Builds a MatchContext from a list of similar match documents.
     *
     * @param similarMatches documents retrieved from vector store
     * @param homeTeam       name of the home team
     * @param awayTeam       name of the away team
     * @return the match context with summary and statistics
     */
    public MatchContext build(List<Document> similarMatches, String homeTeam, String awayTeam) {
        List<PredictionResponse.HistoricalMatch> historicalMatches = new ArrayList<>();
        MatchContext.TeamStats homeStats = MatchContext.TeamStats.empty();
        MatchContext.TeamStats awayStats = MatchContext.TeamStats.empty();

        StringBuilder summaryBuilder = new StringBuilder();

        for (Document doc : similarMatches) {
            Map<String, Object> metadata = doc.getMetadata();

            String docHomeTeam = getStringValue(metadata, "home_team");
            String docAwayTeam = getStringValue(metadata, "away_team");
            String result = getStringValue(metadata, "result");
            String competition = getStringValue(metadata, "competition");
            String date = getStringValue(metadata, "date");

            historicalMatches.add(new PredictionResponse.HistoricalMatch(
                    docHomeTeam, docAwayTeam, result, competition, date
            ));

            summaryBuilder.append(String.format("- %s vs %s: %s (%s, %s)%n",
                    docHomeTeam, docAwayTeam, result, competition, date));
        }

        // If no documents found, try to get stats from database
        if (similarMatches.isEmpty()) {
            log.info("No similar matches found from vector store, fetching from database");
            return buildFromDatabase(homeTeam, awayTeam);
        }

        String summary = summaryBuilder.isEmpty() ? "No historical data available" : summaryBuilder.toString();

        return new MatchContext(summary, historicalMatches, homeStats, awayStats);
    }

    /**
     * Builds context directly from the database when no vector matches are found.
     */
    private MatchContext buildFromDatabase(String homeTeam, String awayTeam) {
        List<PredictionResponse.HistoricalMatch> historicalMatches = new ArrayList<>();
        StringBuilder summaryBuilder = new StringBuilder();

        String sql = """
                SELECT
                    ht.name AS home_team,
                    at.name AS away_team,
                    m.score_json::text AS score,
                    c.name AS competition,
                    m.utc_date::date::text AS match_date
                FROM fd_match m
                JOIN fd_team ht ON m.home_team_id = ht.id
                JOIN fd_team at ON m.away_team_id = at.id
                JOIN fd_competition c ON m.competition_id = c.id
                WHERE (ht.name ILIKE :homeTeam OR at.name ILIKE :homeTeam
                    OR ht.name ILIKE :awayTeam OR at.name ILIKE :awayTeam)
                    AND m.status = 'FINISHED'
                ORDER BY m.utc_date DESC
                LIMIT 15
                """;

        var params = new MapSqlParameterSource()
                .addValue("homeTeam", "%" + homeTeam + "%")
                .addValue("awayTeam", "%" + awayTeam + "%");

        try {
            List<Map<String, Object>> rows = jdbcTemplate.queryForList(sql, params);

            for (Map<String, Object> row : rows) {
                String docHomeTeam = (String) row.get("home_team");
                String docAwayTeam = (String) row.get("away_team");
                String score = row.get("score") != null ? row.get("score").toString() : "N/A";
                String competition = (String) row.get("competition");
                String date = row.get("match_date") != null ? row.get("match_date").toString() : "N/A";

                historicalMatches.add(new PredictionResponse.HistoricalMatch(
                        docHomeTeam, docAwayTeam, score, competition, date
                ));

                summaryBuilder.append(String.format("- %s vs %s: %s (%s, %s)%n",
                        docHomeTeam, docAwayTeam, score, competition, date));
            }
        } catch (Exception e) {
            log.warn("Failed to fetch historical matches from database: {}", e.getMessage());
        }

        String summary = summaryBuilder.isEmpty() ? "No historical data available" : summaryBuilder.toString();
        return new MatchContext(summary, historicalMatches,
                MatchContext.TeamStats.empty(), MatchContext.TeamStats.empty());
    }

    private String getStringValue(Map<String, Object> metadata, String key) {
        Object value = metadata.get(key);
        return value != null ? value.toString() : "";
    }
}
