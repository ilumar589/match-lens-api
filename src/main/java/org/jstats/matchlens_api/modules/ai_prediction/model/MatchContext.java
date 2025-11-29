package org.jstats.matchlens_api.modules.ai_prediction.model;

import java.util.List;

/**
 * Represents the context built from similar historical matches for RAG-based prediction.
 *
 * @param summary          summarized context from historical matches
 * @param relevantMatches  list of relevant historical matches
 * @param homeTeamStats    statistics for the home team
 * @param awayTeamStats    statistics for the away team
 */
public record MatchContext(
        String summary,
        List<PredictionResponse.HistoricalMatch> relevantMatches,
        TeamStats homeTeamStats,
        TeamStats awayTeamStats
) {
    /**
     * Represents aggregated statistics for a team.
     */
    public record TeamStats(
            int totalMatches,
            int wins,
            int draws,
            int losses,
            int goalsScored,
            int goalsConceded
    ) {
        public static TeamStats empty() {
            return new TeamStats(0, 0, 0, 0, 0, 0);
        }
    }
}
