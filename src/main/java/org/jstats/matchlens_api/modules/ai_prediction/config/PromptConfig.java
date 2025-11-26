package org.jstats.matchlens_api.modules.ai_prediction.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * Configuration for prompt templates used in AI predictions.
 */
@Configuration
@ConfigurationProperties(prefix = "matchlens.ai.prediction")
public class PromptConfig {

    private int maxContextMatches = 15;
    private String cacheTtl = "1h";

    /**
     * Default prompt template for match predictions.
     */
    public static final String PREDICTION_PROMPT_TEMPLATE = """
            You are a football match prediction expert. Analyze the following data and predict the outcome.
            
            Match: %s vs %s
            Competition: %s
            Date: %s
            
            Historical Context:
            %s
            
            Based on the historical data provided, analyze the teams' past performances
            and provide your prediction.
            
            Respond ONLY with a valid JSON object in the following format (no markdown, no explanation outside JSON):
            {
                "predictedWinner": "HOME" or "AWAY" or "DRAW",
                "confidence": <number between 0.0 and 1.0>,
                "reasoning": "<your analysis based on the data>",
                "keyFactors": ["<factor1>", "<factor2>", ...]
            }
            """;

    public int getMaxContextMatches() {
        return maxContextMatches;
    }

    public void setMaxContextMatches(int maxContextMatches) {
        this.maxContextMatches = maxContextMatches;
    }

    public String getCacheTtl() {
        return cacheTtl;
    }

    public void setCacheTtl(String cacheTtl) {
        this.cacheTtl = cacheTtl;
    }
}
