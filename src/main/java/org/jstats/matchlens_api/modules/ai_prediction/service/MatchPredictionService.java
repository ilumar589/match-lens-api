package org.jstats.matchlens_api.modules.ai_prediction.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import org.jstats.matchlens_api.modules.ai_prediction.config.PromptConfig;
import org.jstats.matchlens_api.modules.ai_prediction.model.MatchContext;
import org.jstats.matchlens_api.modules.ai_prediction.model.PredictionRequest;
import org.jstats.matchlens_api.modules.ai_prediction.model.PredictionResponse;
import org.jstats.matchlens_api.modules.ai_prediction.repository.MatchEmbeddingRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

/**
 * Main service for AI-powered match predictions using RAG.
 */
@Service
public class MatchPredictionService {

    private static final Logger log = LoggerFactory.getLogger(MatchPredictionService.class);

    /** Default confidence score when parsing fails or confidence is not provided */
    private static final double DEFAULT_CONFIDENCE = 0.5;

    /** Fallback confidence score when LLM prediction fails entirely */
    private static final double FALLBACK_CONFIDENCE = 0.33;

    private final ChatClient chatClient;
    private final VectorStore vectorStore;
    private final MatchContextBuilder contextBuilder;
    private final EmbeddingService embeddingService;
    private final MatchEmbeddingRepository embeddingRepository;
    private final PromptConfig promptConfig;
    private final ObjectMapper objectMapper;

    public MatchPredictionService(
            ChatClient chatClient,
            VectorStore vectorStore,
            MatchContextBuilder contextBuilder,
            EmbeddingService embeddingService,
            MatchEmbeddingRepository embeddingRepository,
            PromptConfig promptConfig,
            ObjectMapper objectMapper) {
        this.chatClient = chatClient;
        this.vectorStore = vectorStore;
        this.contextBuilder = contextBuilder;
        this.embeddingService = embeddingService;
        this.embeddingRepository = embeddingRepository;
        this.promptConfig = promptConfig;
        this.objectMapper = objectMapper;
    }

    /**
     * Predicts the outcome of a match using RAG-based AI.
     * <p>
     * This method is protected by a circuit breaker to handle failures in the
     * Ollama AI service gracefully. When the circuit is open, the fallback
     * method will return a degraded response.
     *
     * @param request the prediction request containing match details
     * @return the prediction response with winner, confidence, and reasoning
     */
    @CircuitBreaker(name = "ollamaService", fallbackMethod = "predictFallback")
    @Retry(name = "ollamaService")
    public PredictionResponse predict(PredictionRequest request) {
        log.info("Predicting match: {} vs {} in {} on {}",
                request.homeTeam(), request.awayTeam(),
                request.competition(), request.matchDate());

        // 1. Build search query
        String query = buildQuery(request);

        // 2. Retrieve similar historical matches (RAG)
        List<Document> similarMatches = retrieveSimilarMatches(query);

        // 3. Build context from retrieved matches
        MatchContext context = contextBuilder.build(similarMatches, request.homeTeam(), request.awayTeam());

        // 4. Create prompt with context
        String prompt = createPrompt(request, context);

        // 5. Get LLM prediction
        try {
            String response = chatClient.prompt()
                    .user(prompt)
                    .call()
                    .content();

            log.debug("LLM response: {}", response);

            // Parse the JSON response
            return parseResponse(response, context.relevantMatches());
        } catch (Exception e) {
            log.error("Failed to get prediction from LLM: {}", e.getMessage());
            return createFallbackResponse(request, context);
        }
    }

    private String buildQuery(PredictionRequest request) {
        return String.format("%s vs %s %s football match",
                request.homeTeam(), request.awayTeam(), request.competition());
    }

    private List<Document> retrieveSimilarMatches(String query) {
        try {
            SearchRequest searchRequest = SearchRequest.builder()
                    .query(query)
                    .topK(promptConfig.getMaxContextMatches())
                    .build();

            final var result = vectorStore.similaritySearch(searchRequest);

            if (result == null)
                return List.of();

            return result;

        } catch (Exception e) {
            log.warn("Vector store search failed: {}", e.getMessage());
            return List.of();
        }
    }

    private String createPrompt(PredictionRequest request, MatchContext context) {
        return String.format(
                PromptConfig.PREDICTION_PROMPT_TEMPLATE,
                request.homeTeam(),
                request.awayTeam(),
                request.competition(),
                request.matchDate(),
                context.summary()
        );
    }

    private PredictionResponse parseResponse(String response, List<PredictionResponse.HistoricalMatch> relevantMatches) {
        try {
            // Clean up the response - remove any markdown formatting
            String cleanedResponse = response.trim();
            if (cleanedResponse.startsWith("```json")) {
                cleanedResponse = cleanedResponse.substring(7);
            }
            if (cleanedResponse.startsWith("```")) {
                cleanedResponse = cleanedResponse.substring(3);
            }
            if (cleanedResponse.endsWith("```")) {
                cleanedResponse = cleanedResponse.substring(0, cleanedResponse.length() - 3);
            }
            cleanedResponse = cleanedResponse.trim();

            // Parse the JSON response
            Map<String, Object> parsed = objectMapper.readValue(cleanedResponse, Map.class);

            String predictedWinner = (String) parsed.get("predictedWinner");
            Double confidence = parsed.get("confidence") instanceof Number n ? n.doubleValue() : DEFAULT_CONFIDENCE;
            String reasoning = (String) parsed.get("reasoning");
            @SuppressWarnings("unchecked")
            List<String> keyFactors = parsed.get("keyFactors") instanceof List<?> list
                    ? list.stream().map(Object::toString).toList()
                    : List.of();

            return new PredictionResponse(predictedWinner, confidence, reasoning, keyFactors, relevantMatches);
        } catch (JsonProcessingException e) {
            log.warn("Failed to parse LLM response as JSON: {}", e.getMessage());
            // Try to extract key information from the response text
            return extractFromText(response, relevantMatches);
        }
    }

    private PredictionResponse extractFromText(String response, List<PredictionResponse.HistoricalMatch> relevantMatches) {
        String predictedWinner = "DRAW"; // Default
        if (response.toUpperCase().contains("HOME")) {
            predictedWinner = "HOME";
        } else if (response.toUpperCase().contains("AWAY")) {
            predictedWinner = "AWAY";
        }

        return new PredictionResponse(
                predictedWinner,
                DEFAULT_CONFIDENCE,
                response,
                List.of("Unable to parse structured response"),
                relevantMatches
        );
    }

    private PredictionResponse createFallbackResponse(PredictionRequest request, MatchContext context) {
        return new PredictionResponse(
                "DRAW",
                FALLBACK_CONFIDENCE,
                "Unable to generate prediction due to LLM error. Based on limited historical data.",
                List.of("Fallback prediction", "Insufficient data"),
                context.relevantMatches()
        );
    }

    /**
     * Circuit breaker fallback method for the predict method.
     * Called when the Ollama AI service is unavailable or the circuit breaker is open.
     *
     * @param request the original prediction request
     * @param ex the exception that triggered the fallback
     * @return a degraded prediction response indicating service unavailability
     */
    private PredictionResponse predictFallback(PredictionRequest request, Exception ex) {
        log.warn("Prediction service fallback triggered for {} vs {}: {}",
                request.homeTeam(), request.awayTeam(), ex.getMessage());

        return new PredictionResponse(
                "UNAVAILABLE",
                0.0,
                "Prediction service temporarily unavailable: " + ex.getMessage(),
                List.of("Service degraded", "Circuit breaker active"),
                List.of()
        );
    }
}
