package org.jstats.matchlens_api.modules.football_data_org_gatherer.ingest;

import com.fasterxml.jackson.annotation.*;

import java.net.URI;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;

public final class MatchPayload {

    @JsonInclude(JsonInclude.Include.NON_NULL)
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Competition(
            Area area,
            long id,
            String name,
            String code,
            CompetitionType type,
            URI emblem,
            Season currentSeason,
            List<Season> seasons,
            OffsetDateTime lastUpdated
    ) {
        @JsonInclude(JsonInclude.Include.NON_NULL)
        @JsonIgnoreProperties(ignoreUnknown = true)
        public record Area(
                long id,
                String name,
                String code,
                URI flag
        ) {
        }

        @JsonInclude(JsonInclude.Include.NON_NULL)
        @JsonIgnoreProperties(ignoreUnknown = true)
        public record Season(
                long id,
                LocalDate startDate,
                LocalDate endDate,
                Integer currentMatchday,
                Team winner,
                List<Stage> stages
        ) {
        }

        @JsonInclude(JsonInclude.Include.NON_NULL)
        @JsonIgnoreProperties(ignoreUnknown = true)
        public record Team(
                long id,
                String name,
                String shortName,
                String tla,
                URI crest,
                String address,
                URI website,
                Integer founded,
                String clubColors,
                String venue,
                OffsetDateTime lastUpdated
        ) {
        }

        @JsonFormat(shape = JsonFormat.Shape.STRING)
        public enum CompetitionType {
            LEAGUE,
            CUP,
            PLAYOFFS,
            @JsonEnumDefaultValue UNKNOWN
        }

        @JsonFormat(shape = JsonFormat.Shape.STRING)
        public enum Stage {
            REGULAR_SEASON,
            // Handles unexpected values (including the literal "null" seen in 1948–49 season)
            @JsonEnumDefaultValue UNKNOWN
        }
    }
}
