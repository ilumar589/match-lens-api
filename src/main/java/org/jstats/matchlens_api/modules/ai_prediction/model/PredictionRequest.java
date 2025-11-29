package org.jstats.matchlens_api.modules.ai_prediction.model;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;

/**
 * Request DTO for match prediction.
 *
 * @param homeTeam     name of the home team
 * @param awayTeam     name of the away team
 * @param competition  competition code (e.g., PL, CL)
 * @param matchDate    date of the match
 */
public record PredictionRequest(
        @NotBlank String homeTeam,
        @NotBlank String awayTeam,
        @NotBlank String competition,
        @NotNull LocalDate matchDate
) {}
