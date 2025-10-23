package org.jstats.matchlens_api.modules.football_data_org_gatherer.ingest;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.jspecify.annotations.NullMarked;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Optional;

@Service
@NullMarked
public class FdRawIngestService {

    private static final String SRC = "football-data.org";
    private static final String EP  = "/v4/competitions/{code}";

    private static final Logger log = LoggerFactory.getLogger(FdRawIngestService.class);


    private final FdRawIngestRepository fdRawIngestRepository;
    private final FdOrgClient client;
    private final ObjectMapper mapper;
    private final Clock clock;

    public FdRawIngestService(
            FdRawIngestRepository fdRawIngestRepository,
            FdOrgClient client,
            ObjectMapper mapper,
            Clock clock) {
        this.fdRawIngestRepository = fdRawIngestRepository;
        this.client = client;
        this.mapper = mapper;
        this.clock = clock;
    }

    public Optional<Long> fetchAndStoreCompetitionRaw(String code) {

        try {
            var nowUtc = OffsetDateTime.now(clock).withOffsetSameInstant(ZoneOffset.UTC);
            var monthAgo = nowUtc.minusMonths(1);

            // Guard: if we fetched this key in the last month, skip remote call
            if (fdRawIngestRepository.wasFetchedSince(SRC, EP, code, monthAgo)) {
                return Optional.empty();
            }

            return client.getCompetitionInfo(code).flatMap(competition -> {
                try {
                    final var json = mapper.writeValueAsString(competition);
                    return fdRawIngestRepository.insertRaw(
                            SRC,
                            EP,
                            code,
                            nowUtc,
                            json);
                } catch (JsonProcessingException jpe) {
                    if (log.isErrorEnabled()) {
                        log.error("Failed to serialize competition {} to JSON: {}", code, jpe.getMessage());
                    }
                    throw new FdOrgClient.UpstreamJsonParseException(jpe.getMessage());
                }
            });
        } catch (Exception ex) {
            if (log.isErrorEnabled()) {
                log.error("Failed to fetch competition {} from football-data.org: {}", code, ex.getMessage());
            }
            throw ex;
        }
    }
}
