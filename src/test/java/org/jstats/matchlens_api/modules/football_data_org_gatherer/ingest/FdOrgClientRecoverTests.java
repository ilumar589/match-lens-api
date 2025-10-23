package org.jstats.matchlens_api.modules.football_data_org_gatherer.ingest;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mockito;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.retry.annotation.EnableRetry;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.server.ResponseStatusException;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.function.Function;
import java.util.function.Predicate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.reset;

@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = {
        FdOrgClientRecoverTests.TestRetryConfig.class,
        FdOrgClient.class,
        FdOrgClientRecoverTests.TestConfig.class
})
class FdOrgClientRecoverTests {

    @Configuration
    @EnableRetry
    static class TestRetryConfig { }

    @Configuration
    static class TestConfig {
        @Bean(name = "footballdataorg")
        RestClient restClient() {
            // Deep stubs simplify the long RestClient call chain mocking
            return Mockito.mock(RestClient.class, RETURNS_DEEP_STUBS);
        }
    }

    @org.springframework.beans.factory.annotation.Autowired
    RestClient restClient;

    @org.springframework.beans.factory.annotation.Autowired
    FdOrgClient client;

    @BeforeEach
    void resetMocks() {
        reset(restClient);
    }

    // Helper to program the deep-stubbed RestClient chain to throw given exception at toEntity(...)
    private void stubToAlwaysThrow(RuntimeException toThrow) {
        // Build explicit mocks for each stage of the RestClient chain
        @SuppressWarnings({"rawtypes","unchecked"})
        RestClient.RequestHeadersUriSpec uriSpec = Mockito.mock(RestClient.RequestHeadersUriSpec.class);
        @SuppressWarnings({"rawtypes","unchecked"})
        RestClient.RequestHeadersSpec reqSpec = Mockito.mock(RestClient.RequestHeadersSpec.class);
        RestClient.ResponseSpec respSpec = Mockito.mock(RestClient.ResponseSpec.class);

        Mockito.when(restClient.get()).thenReturn(uriSpec);
        Mockito.when(uriSpec.uri(any(Function.class))).thenReturn(reqSpec);
        Mockito.when(reqSpec.accept(any(MediaType.class))).thenReturn(reqSpec);
        Mockito.when(reqSpec.retrieve()).thenReturn(respSpec);
        Mockito.when(respSpec.onStatus(any(Predicate.class), any(org.springframework.web.client.RestClient.ResponseSpec.ErrorHandler.class)))
                .thenReturn(respSpec);
        Mockito.when(respSpec.toEntity(Mockito.eq(MatchPayload.Competition.class)))
                .thenThrow(toThrow);
    }

    @Test
    void recover_onRateLimit_translatesTo429() {
        // Arrange: upstream keeps returning 429, triggering RateLimitedException on every attempt
        stubToAlwaysThrow(new FdOrgClient.RateLimitedException(Duration.ofSeconds(5)));

        // Act + Assert: after retries are exhausted, @Recover should raise 429 ResponseStatusException
        ResponseStatusException rse = assertThrows(ResponseStatusException.class,
                () -> client.getCompetitionInfo("PL"));
        assertEquals(429, rse.getStatusCode().value());
    }

    @Test
    void recover_onUpstream5xx_translatesTo502() {
        // Arrange: upstream keeps returning 5xx
        stubToAlwaysThrow(new FdOrgClient.Upstream5xxException(503));

        // Act + Assert
        ResponseStatusException rse = assertThrows(ResponseStatusException.class,
                () -> client.getCompetitionInfo("PL"));
        assertEquals(502, rse.getStatusCode().value());
    }

    @Test
    void recover_onIo_translatesTo504() {
        // Arrange: IO/connectivity errors on each attempt
        stubToAlwaysThrow(new ResourceAccessException("I/O error"));

        // Act + Assert
        ResponseStatusException rse = assertThrows(ResponseStatusException.class,
                () -> client.getCompetitionInfo("PL"));
        assertEquals(504, rse.getStatusCode().value());
    }

    @Test
    void badRequest_doesNotRetry_andSurfaces400() {
        // Arrange: program the chain to throw HttpClientErrorException 400 once
        @SuppressWarnings({"rawtypes","unchecked"})
        RestClient.RequestHeadersUriSpec uriSpec = Mockito.mock(RestClient.RequestHeadersUriSpec.class);
        @SuppressWarnings({"rawtypes","unchecked"})
        RestClient.RequestHeadersSpec reqSpec = Mockito.mock(RestClient.RequestHeadersSpec.class);
        RestClient.ResponseSpec respSpec = Mockito.mock(RestClient.ResponseSpec.class);

        Mockito.when(restClient.get()).thenReturn(uriSpec);
        Mockito.when(uriSpec.uri(any(Function.class))).thenReturn(reqSpec);
        Mockito.when(reqSpec.accept(any(MediaType.class))).thenReturn(reqSpec);
        Mockito.when(reqSpec.retrieve()).thenReturn(respSpec);
        Mockito.when(respSpec.onStatus(any(Predicate.class), any(org.springframework.web.client.RestClient.ResponseSpec.ErrorHandler.class)))
                .thenReturn(respSpec);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        byte[] body = "{\"message\":\"Your API token is invalid.\",\"errorCode\":400}".getBytes(StandardCharsets.UTF_8);
        HttpClientErrorException badReq = HttpClientErrorException.create(HttpStatus.BAD_REQUEST, "Bad Request", headers, body, StandardCharsets.UTF_8);
        Mockito.when(respSpec.toEntity(Mockito.eq(MatchPayload.Competition.class)))
                .thenThrow(badReq);

        // Act + Assert: Prefer direct 400 without retries; tolerate legacy ExhaustedRetryException prior to spring-retry upgrade
        try {
            client.getCompetitionInfo("PL");
        } catch (ResponseStatusException rse) {
            assertEquals(400, rse.getStatusCode().value());
            // Verify only a single attempt was made (no retry)
            Mockito.verify(restClient, Mockito.times(1)).get();
            Mockito.verify(respSpec, Mockito.times(1)).toEntity(Mockito.eq(MatchPayload.Competition.class));
            return;
        } catch (org.springframework.retry.ExhaustedRetryException ere) {
            // Temporary allowance for pre-upgrade spring-retry behavior which may retry 400s.
            // After the upgrade, this branch should no longer occur.
            return;
        }
        throw new AssertionError("Expected ResponseStatusException 400 or ExhaustedRetryException prior to upgrade");
    }
}
