package org.jstats.matchlens_api.core.config;

import org.jstats.matchlens_api.modules.football_data_org_gatherer.ingest.FdOrgClient;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.server.ResponseStatusException;

import java.net.URI;
import java.time.Duration;

import static org.springframework.http.HttpStatus.BAD_REQUEST;
import static org.springframework.http.HttpStatus.NOT_FOUND;

@RestControllerAdvice
public class ProblemHandler {
    // Anything thrown as ResponseStatusException becomes a Problem
    @ExceptionHandler(ResponseStatusException.class)
    public ProblemDetail handle(ResponseStatusException ex) {
        ProblemDetail pd = ProblemDetail.forStatusAndDetail(ex.getStatusCode(), ex.getReason());
        pd.setType(URI.create("https://api.jstats.org/problems/" + ex.getStatusCode().value()));
        pd.setTitle(switch (ex.getStatusCode()) {
            case NOT_FOUND -> "Resource Not Found";
            case BAD_REQUEST -> "Bad Request";
            default -> "Request Failed";
        });
        // Optional: include an "instance" pointer to the endpoint
        // pd.setInstance(URI.create("...current request URI if you have it..."));
        return pd; // Spring sets Content-Type: application/problem+json
    }

    // Example: your client throws a TooManyRequestsException when the upstream returns 429
    @ExceptionHandler(org.jstats.matchlens_api.modules.football_data_org_gatherer.config.FootballDataSourceConfig.TooManyRequestsException.class)
    public org.springframework.http.ResponseEntity<ProblemDetail> handleTooManyRequests(RuntimeException ex) {
        ProblemDetail pd = ProblemDetail.forStatusAndDetail(HttpStatus.TOO_MANY_REQUESTS,
                "Rate limit reached for football-data.org. Please retry later.");
        pd.setType(URI.create("https://api.jstats.org/problems/rate-limit"));
        pd.setTitle("Too Many Requests");

        var headers = new HttpHeaders();
        // If you know the backoff duration, advertise it
        headers.add(HttpHeaders.RETRY_AFTER, String.valueOf(Duration.ofSeconds(30).toSeconds()));

        return new org.springframework.http.ResponseEntity<>(pd, headers, HttpStatus.TOO_MANY_REQUESTS);
    }

    @ExceptionHandler(FdOrgClient.RateLimitedException.class)
    public ResponseEntity<ProblemDetail> rateLimited(FdOrgClient.RateLimitedException ex) {
        var pd = ProblemDetail.forStatusAndDetail(HttpStatus.TOO_MANY_REQUESTS,
                "Rate limit reached at football-data.org. Please retry later.");
        var headers = new HttpHeaders();
        headers.add(HttpHeaders.RETRY_AFTER, String.valueOf(ex.retryAfter.toSeconds()));
        return new ResponseEntity<>(pd, headers, HttpStatus.TOO_MANY_REQUESTS);
    }

    @ExceptionHandler(FdOrgClient.Upstream5xxException.class)
    public ProblemDetail upstream(FdOrgClient.Upstream5xxException ex) {
        var pd = ProblemDetail.forStatus(HttpStatus.BAD_GATEWAY);
        pd.setDetail("Upstream error from football-data.org: " + ex.status);
        return pd;
    }

    @ExceptionHandler(FdOrgClient.UpstreamBadContentTypeException.class)
    public ProblemDetail badContent(FdOrgClient.UpstreamBadContentTypeException ex) {
        var pd = ProblemDetail.forStatus(HttpStatus.BAD_GATEWAY);
        pd.setTitle("Upstream returned non-JSON");
        pd.setDetail("Content-Type: " + ex.contentType + "; Preview: " + ex.preview);
        return pd;
    }

    @ExceptionHandler(FdOrgClient.UpstreamJsonParseException.class)
    public ProblemDetail parse(FdOrgClient.UpstreamJsonParseException ex) {
        var pd = ProblemDetail.forStatus(HttpStatus.BAD_GATEWAY);
        pd.setTitle("Failed to parse upstream JSON");
        pd.setDetail(ex.getMessage());
        return pd;
    }

    // Catch any other unexpected exception as a 500 Problem (avoid leaking internals)
    @ExceptionHandler(Exception.class)
    public ProblemDetail handleGeneric(Exception ex) {
        ProblemDetail pd = ProblemDetail.forStatusAndDetail(HttpStatus.INTERNAL_SERVER_ERROR,
                "Unexpected error. If this persists, contact support.");
        pd.setType(URI.create("https://api.jstats.org/problems/internal-error"));
        pd.setTitle("Internal Server Error");
        return pd;
    }
}
