package org.jstats.matchlens_api.modules.football_data_org_gatherer.ingest;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Recover;
import org.springframework.retry.annotation.Retryable;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.server.ResponseStatusException;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Optional;

import static org.springframework.http.HttpStatus.*;

@Component
public class FdOrgClient {

    private static final Logger log = LoggerFactory.getLogger(FdOrgClient.class);

    private final RestClient http;

    public FdOrgClient(
            @Qualifier("footballdataorg") RestClient http) {
        this.http = http;
    }

    /**
     * GET /competitions/{code}
     * - 200 JSON -> body
     * - 404 -> Optional.empty()
     * - 429/5xx/IO -> retry with exponential backoff
     * - other 4xx -> throw (no retry)
     * - 2xx non-JSON -> treated as upstream error (no retry)
     */
    @Retryable(
            // Use non-deprecated attributes to be compatible with newer Spring Retry
            retryFor = {
                    RateLimitedException.class,       // 429
                    Upstream5xxException.class,       // 5xx
                    ResourceAccessException.class     // I/O timeouts, connection issues
            },
            noRetryFor = {
                    UpstreamBadContentTypeException.class, // usually config/endpoint issue, don't spin
                    UpstreamJsonParseException.class,      // badly formatted / changed payload; fail fast
                    org.springframework.web.server.ResponseStatusException.class, // 4xx surfaced intentionally
                    org.springframework.web.client.HttpClientErrorException.class // any other 4xx from RestClient
            },
            maxAttempts = 3,
            backoff = @Backoff(delay = 1000, multiplier = 2.0, maxDelay = 8000)
    )
    public Optional<MatchPayload.Competition> getCompetitionInfo(String code) {
        try {
            if (log.isDebugEnabled()) {
                log.debug("Calling football-data.org GET /v4/competitions/{}", code);
            }

            var resp = http.get()
                    .uri(u -> u.path("/v4/competitions/{code}").build(code))
                    .accept(MediaType.APPLICATION_JSON)
                    .retrieve()
                    .onStatus(s -> s.value() == 404, (req, res) -> { throw new NotFoundException(); })
                    .onStatus(s -> s.value() == 429, (req, res) -> {
                        throw new RateLimitedException(parseRetryAfter(res.getHeaders()));
                    })
                    .onStatus(HttpStatusCode::is5xxServerError, (req, res) -> {
                        throw new Upstream5xxException(res.getStatusCode().value());
                    })
                    .toEntity(MatchPayload.Competition.class);

            var dto = resp.getBody();
            return Optional.ofNullable(dto);

        } catch (NotFoundException nf) {
            if (log.isDebugEnabled()) {
                log.debug("Competition not found at football-data.org: {}", code);
            }
            return Optional.empty();
        } catch (HttpClientErrorException ex) {
            // 4xx other than 404/429 – capture and surface clearly
            var status = ex.getStatusCode();
            var bodyBytes = ex.getResponseBodyAsByteArray();
            var preview = bodyBytes == null ? "" : new String(bodyBytes, 0, Math.min(bodyBytes.length, 500), StandardCharsets.UTF_8);

            if (log.isWarnEnabled()) {
                log.warn("football-data.org client error {} for competitions {}. Body: {}", status.value(), code, preview);
            }

            if (status.value() == 400) {
                throw new ResponseStatusException(BAD_REQUEST, problemMsg("Bad request to football-data.org (likely token/parameters)", preview));
            } else if (status.value() == 401) {
                throw new ResponseStatusException(UNAUTHORIZED, problemMsg("Unauthorized at football-data.org (check X-Auth-Token)", preview));
            } else if (status.value() == 403) {
                throw new ResponseStatusException(FORBIDDEN, problemMsg("Forbidden at football-data.org (token lacks permissions)", preview));
            } else {
                throw new ResponseStatusException(status, problemMsg("Upstream 4xx from football-data.org", preview));
            }
        } catch (org.springframework.http.converter.HttpMessageConversionException conv) {
            var cause = conv.getCause();
            var msg = (cause instanceof com.fasterxml.jackson.core.JsonProcessingException jp)
                    ? jp.getOriginalMessage()
                    : conv.getMessage();

            if (log.isErrorEnabled()) {
                log.error("Failed to parse football-data.org JSON for competitions {}: {}", code, msg);
            }
            throw new UpstreamJsonParseException(msg);
        }
    }

    // ---------- Retry helpers / exception types ----------
    public static final class NotFoundException extends RuntimeException {}

    public static final class RateLimitedException extends RuntimeException {
        public final Duration retryAfter;
        RateLimitedException(Duration ra) { this.retryAfter = ra; }
    }

    public static final class Upstream5xxException extends RuntimeException {
        public final int status;
        Upstream5xxException(int status) { this.status = status; }
    }

    public static final class UpstreamBadContentTypeException extends RuntimeException {
        public final String contentType;
        public final String preview;
        UpstreamBadContentTypeException(String ct, String preview) {
            super("Non-JSON response (" + ct + ")");
            this.contentType = ct; this.preview = preview;
        }
    }

    public static final class UpstreamJsonParseException extends RuntimeException {
        UpstreamJsonParseException(String msg) { super(msg); }
    }

    private static Duration parseRetryAfter(HttpHeaders headers) {
        var ra = headers.getFirst(HttpHeaders.RETRY_AFTER);
        if (ra == null || ra.isBlank()) return Duration.ofSeconds(2);
        try { return Duration.ofSeconds(Math.max(1, Long.parseLong(ra.trim()))); }
        catch (NumberFormatException ignore) { return Duration.ofSeconds(2); }
    }

    private static String problemMsg(String leading, String preview) {
        if (preview == null || preview.isBlank()) return leading;
        var safe = preview.length() > 500 ? preview.substring(0, 500) : preview;
        return leading + ": " + safe;
    }

    // ---------- @Recover handlers (run after final retry attempt fails) ----------
    @Recover
    public Optional<MatchPayload.Competition> recoverRateLimit(RateLimitedException ex, String code) {
        // surface as 429 Problem
        if (log.isWarnEnabled()) {
            log.warn("Recover after rate limit for competitions {}. Retry-After ~{}s", code, ex.retryAfter.toSeconds());
        }
        throw new ResponseStatusException(TOO_MANY_REQUESTS,
                "Rate limit reached at football-data.org; retry after ~" + ex.retryAfter.toSeconds() + "s");
    }

    @Recover
    public Optional<MatchPayload.Competition> recoverUpstream(Upstream5xxException ex, String code) {
        if (log.isErrorEnabled()) {
            log.error("Recover after upstream 5xx {} for competitions {}", ex.status, code);
        }
        throw new ResponseStatusException(BAD_GATEWAY,
                "Upstream error from football-data.org: HTTP " + ex.status);
    }

    @Recover
    public Optional<MatchPayload.Competition> recoverIo(ResourceAccessException ex, String code) {
        if (log.isErrorEnabled()) {
            log.error("Recover after IO error while calling football-data.org for competitions {}", code, ex);
        }
        throw new ResponseStatusException(GATEWAY_TIMEOUT,
                "Upstream timeout while calling football-data.org");
    }
}
