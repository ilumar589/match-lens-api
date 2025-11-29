package org.jstats.matchlens_api.modules.football_data_org_gatherer.ingest;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Pattern;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "FootballData.org Ingest", description = "Fetch competition info & fixtures.")
@Validated
@RestController
@RequestMapping("/ingest/footballdataorg")
public class IngestFootballDataOrgController {

    private final FdRawIngestService ingestService ;

    public IngestFootballDataOrgController(FdRawIngestService ingestService) {
        this.ingestService = ingestService;
    }

    /**
     * Example:
     * GET /ingest/footballdataorg/fixtures?competition=PL
     */
    @Operation(
            summary = "Get competition fixtures/info",
            description = "Fetches competition details by code (e.g., PL, CL, PD).",
            responses = {
                    @ApiResponse(responseCode = "200", description = "OK"),
                    @ApiResponse(responseCode = "400", description = "Bad Request",
                            content = @Content(mediaType = "application/problem+json")),
                    @ApiResponse(responseCode = "404", description = "Not Found",
                            content = @Content(mediaType = "application/problem+json")),
                    @ApiResponse(responseCode = "429", description = "Too Many Requests",
                            content = @Content(mediaType = "application/problem+json")),
                    @ApiResponse(responseCode = "500", description = "Internal Server Error",
                            content = @Content(mediaType = "application/problem+json"))
            }
    )
    @GetMapping("/fixtures")
    public Long fixtures(
            @RequestParam("competition")
            @Pattern(regexp = "^[A-Z0-9]{2,5}$", message = "competition must be an uppercase code like PL, CL, etc.")
            String competitionCode) {

        return ingestService.storeCompetitionRaw(competitionCode)
                .orElseThrow(() -> new org.springframework.web.server.ResponseStatusException(
                        org.springframework.http.HttpStatus.NOT_FOUND,
                        "Competition %s not found or already stored".formatted(competitionCode)));
    }
}
