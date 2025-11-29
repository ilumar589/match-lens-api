package org.jstats.matchlens_api.modules.football_data_org_gatherer.ingest;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class FdRawIngestServiceTests {

    FdRawIngestRepository repo;
    FdOrgClient client;
    ObjectMapper mapper;
    Clock clock;

    FdRawIngestService service;

    private static final String SRC = "football-data.org";
    private static final String EP = "/v4/competitions/{code}"; // used internally by service

    @BeforeEach
    void setUp() {
        repo = mock(FdRawIngestRepository.class);
        client = mock(FdOrgClient.class);
        mapper = mock(ObjectMapper.class);
        clock = Clock.fixed(Instant.parse("2024-10-01T12:34:56.000Z"), ZoneId.of("UTC"));
        service = new FdRawIngestService(repo, client, mapper, clock);
    }

    @Test
    void whenRecentlyFetched_serviceSkipsAndReturnsEmpty() {
        when(repo.wasFetchedSince(eq(SRC), eq(EP), eq("PL"), any(OffsetDateTime.class))).thenReturn(true);

        var result = service.storeCompetitionRaw("PL");
        assertTrue(result.isEmpty());

        verifyNoInteractions(client);
        verify(repo, never()).insertRaw(anyString(), anyString(), anyString(), any(), anyString());
    }

    @Test
    void happyPath_insertsSerializedPayload_andReturnsId() throws Exception {
        // Not fetched recently
        when(repo.wasFetchedSince(eq(SRC), eq(EP), eq("PL"), any(OffsetDateTime.class))).thenReturn(false);
        // Client returns DTO
        MatchPayload.Competition dto = new MatchPayload.Competition(null, 2021, "Premier League", "PL", null, null, null, null, OffsetDateTime.now(ZoneOffset.UTC));
        when(client.getCompetitionInfo("PL")).thenReturn(Optional.of(dto));
        // Mapper serializes
        when(mapper.writeValueAsString(dto)).thenReturn("{\"id\":2021}");
        // Repo insert returns id
        when(repo.insertRaw(eq(SRC), eq(EP), eq("PL"), any(OffsetDateTime.class), eq("{\"id\":2021}")))
                .thenReturn(Optional.of(123L));

        var result = service.storeCompetitionRaw("PL");
        assertEquals(Optional.of(123L), result);

        // Verify the timestamp passed equals now(clock) in UTC
        ArgumentCaptor<OffsetDateTime> tsCaptor = ArgumentCaptor.forClass(OffsetDateTime.class);
        verify(repo).insertRaw(eq(SRC), eq(EP), eq("PL"), tsCaptor.capture(), eq("{\"id\":2021}"));
        var ts = tsCaptor.getValue();
        assertEquals(Instant.parse("2024-10-01T12:34:56Z"), ts.toInstant());
        assertEquals(ZoneOffset.UTC, ts.getOffset());
    }

    @Test
    void clientReturnsEmpty_serviceReturnsEmpty_andDoesNotInsert() {
        when(repo.wasFetchedSince(eq(SRC), eq(EP), eq("PL"), any(OffsetDateTime.class))).thenReturn(false);
        when(client.getCompetitionInfo("PL")).thenReturn(Optional.empty());

        var result = service.storeCompetitionRaw("PL");
        assertTrue(result.isEmpty());

        verify(repo, never()).insertRaw(anyString(), anyString(), anyString(), any(), anyString());
    }

    @Test
    void whenJsonSerializationFails_translatesToUpstreamJsonParseException() throws Exception {
        when(repo.wasFetchedSince(eq(SRC), eq(EP), eq("PL"), any(OffsetDateTime.class))).thenReturn(false);
        MatchPayload.Competition dto = new MatchPayload.Competition(null, 1, "X", "PL", null, null, null, null, OffsetDateTime.now(ZoneOffset.UTC));
        when(client.getCompetitionInfo("PL")).thenReturn(Optional.of(dto));
        when(mapper.writeValueAsString(dto)).thenThrow(new JsonProcessingException("boom") {});

        var ex = assertThrows(FdOrgClient.UpstreamJsonParseException.class,
                () -> service.storeCompetitionRaw("PL"));
        assertTrue(ex.getMessage().contains("boom"));

        verify(repo, never()).insertRaw(anyString(), anyString(), anyString(), any(), anyString());
    }
}
