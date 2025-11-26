package org.jstats.matchlens_api.modules.ai_prediction.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.jstats.matchlens_api.modules.ai_prediction.model.PredictionRequest;
import org.jstats.matchlens_api.modules.ai_prediction.model.PredictionResponse;
import org.jstats.matchlens_api.modules.ai_prediction.service.EmbeddingService;
import org.jstats.matchlens_api.modules.ai_prediction.service.MatchPredictionService;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * REST controller for AI-powered match predictions.
 */
@Tag(name = "AI Predictions", description = "AI-powered match outcome predictions using RAG")
@Validated
@RestController
@RequestMapping("/api/predictions")
public class PredictionController {

    private final MatchPredictionService predictionService;
    private final EmbeddingService embeddingService;

    public PredictionController(
            MatchPredictionService predictionService,
            EmbeddingService embeddingService) {
        this.predictionService = predictionService;
        this.embeddingService = embeddingService;
    }

    @Operation(
            summary = "Predict match outcome using AI",
            description = "Uses RAG-based AI to predict the outcome of a football match based on historical data",
            responses = {
                    @ApiResponse(
                            responseCode = "200",
                            description = "Prediction generated successfully",
                            content = @Content(
                                    mediaType = "application/json",
                                    schema = @Schema(implementation = PredictionResponse.class)
                            )
                    ),
                    @ApiResponse(
                            responseCode = "400",
                            description = "Invalid request",
                            content = @Content(mediaType = "application/problem+json")
                    ),
                    @ApiResponse(
                            responseCode = "500",
                            description = "Internal server error",
                            content = @Content(mediaType = "application/problem+json")
                    )
            }
    )
    @PostMapping
    public PredictionResponse predict(@RequestBody @Valid PredictionRequest request) {
        return predictionService.predict(request);
    }

    @Operation(
            summary = "Generate embeddings for existing matches",
            description = "Triggers batch generation of embeddings for finished matches that don't have embeddings yet",
            responses = {
                    @ApiResponse(
                            responseCode = "200",
                            description = "Embeddings generated successfully"
                    )
            }
    )
    @PostMapping("/embeddings/generate")
    public ResponseEntity<Map<String, Object>> generateEmbeddings(
            @RequestParam(defaultValue = "100") int limit) {
        int count = embeddingService.generateBatchEmbeddings(limit);
        return ResponseEntity.ok(Map.of(
                "message", "Batch embedding generation completed",
                "embeddingsGenerated", count,
                "limit", limit
        ));
    }

    @Operation(
            summary = "Health check for AI prediction service",
            description = "Checks if the AI prediction service is operational"
    )
    @GetMapping("/health")
    public ResponseEntity<Map<String, String>> health() {
        return ResponseEntity.ok(Map.of(
                "status", "UP",
                "service", "ai-prediction"
        ));
    }
}
