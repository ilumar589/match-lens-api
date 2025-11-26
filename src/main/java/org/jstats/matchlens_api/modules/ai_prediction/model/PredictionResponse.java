package org.jstats.matchlens_api.modules.ai_prediction.model;

import java.util.List;

/**
 * Response DTO for match prediction containing AI-generated prediction details.
 *
 * @param predictedWinner  predicted outcome: "HOME", "AWAY", or "DRAW"
 * @param confidence       confidence score between 0.0 and 1.0
 * @param reasoning        explanation based on historical data
 * @param keyFactors       list of key factors influencing the prediction
 * @param relevantMatches  list of historical matches used for context
 */
public record PredictionResponse(
        String predictedWinner,
        Double confidence,
        String reasoning,
        List<String> keyFactors,
        List<HistoricalMatch> relevantMatches
) {
    /**
     * Represents a historical match used for prediction context.
     */
    public record HistoricalMatch(
            String homeTeam,
            String awayTeam,
            String result,
            String competition,
            String date
    ) {}
}
