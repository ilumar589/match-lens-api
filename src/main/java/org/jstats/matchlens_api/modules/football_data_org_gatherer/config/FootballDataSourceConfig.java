package org.jstats.matchlens_api.modules.football_data_org_gatherer.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.convert.DurationUnit;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;

import java.net.http.HttpClient;
import java.time.Duration;
import java.time.temporal.ChronoUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@ConfigurationProperties(prefix = "footballdata.api")
record FootballDataSourceProperties(
        String baseUrl,
        String key,
        @DurationUnit(ChronoUnit.MILLIS) Duration connectTimeout,
        @DurationUnit(ChronoUnit.MILLIS) Duration readTimeout,
        String userAgent) {}

@Configuration
@EnableConfigurationProperties(FootballDataSourceProperties.class)
public class FootballDataSourceConfig {

    private static final Logger log = LoggerFactory.getLogger(FootballDataSourceConfig.class);

    // Simple marker exception you can @Retryable on
    public static class TooManyRequestsException extends RuntimeException {
    }

    private static final Pattern PLACEHOLDER = Pattern.compile("^\\$\\{([^}]+)}$");
    private static volatile String lastSource = null; // for diagnostics only

    private static String resolveApiKey(String configured) {
        lastSource = null;
        // If Spring already resolved it, just return
        if (StringUtils.hasText(configured) && !configured.startsWith("${")) {
            lastSource = "spring-property";
            return configured;
        }
        if (!StringUtils.hasText(configured)) {
            return configured;
        }
        // Try to extract placeholder name and read from JVM system properties/env as a fallback
        Matcher m = PLACEHOLDER.matcher(configured.trim());
        if (m.matches()) {
            String var = m.group(1);
            // Support formats like ENV:NAME or just NAME
            int colon = var.indexOf(':');
            if (colon >= 0) {
                var = var.substring(colon + 1);
            }
            // 1) Check Java system property first (works reliably with Gradle/IDE)
            String fromSysProp = System.getProperty(var);
            if (!StringUtils.hasText(fromSysProp)) {
                // Also allow canonical property name
                fromSysProp = System.getProperty("footballdata.api.key");
                if (StringUtils.hasText(fromSysProp)) {
                    lastSource = "system-property:footballdata.api.key";
                    return fromSysProp;
                }
            } else {
                lastSource = "system-property:" + var;
                return fromSysProp;
            }
            // 2) Check environment variable
            String fromEnv = System.getenv(var);
            if (StringUtils.hasText(fromEnv)) {
                lastSource = "env:" + var;
                return fromEnv;
            }
        }
        // If still unresolved, return as-is (will fail validation below)
        return configured;
    }

    @Bean(name = "footballdataorg")
    RestClient footballDataSourceRestClient(RestClient.Builder builder,
                                            FootballDataSourceProperties p) {
        String apiKey = resolveApiKey(p.key());
        if (!StringUtils.hasText(apiKey) || apiKey.startsWith("${")) {
            throw new IllegalStateException("Football-Data.org API key missing. Provide via: 1) application property footballdata.api.key, 2) JVM system property -DFOOTBALL_DATA_API_KEY=..., or 3) environment variable FOOTBALL_DATA_API_KEY. Note: if using Gradle, the daemon may cache old env vars; try '--no-daemon' or pass -DFOOTBALL_DATA_API_KEY explicitly.");
        }
        // Log non-secret diagnostics
        try {
            int len = apiKey.length();
            String tail = len >= 2 ? apiKey.substring(len - 2) : "??";
            log.info("Football-Data API key resolved from {} (len={}, endsWith=**{})", lastSource, len, tail);
        } catch (Exception ignored) {
            // never fail boot because of diagnostics
        }

        // Connect timeout is configured on the underlying JDK HttpClient:
        var httpClientBuilder = HttpClient.newBuilder();
        if (p.connectTimeout() != null) {
            httpClientBuilder.connectTimeout(p.connectTimeout());
        }
        final var jdkClient = httpClientBuilder.build();

        final var factory = new JdkClientHttpRequestFactory(jdkClient);
        // Read timeout can be set on the Spring factory (applies as a default per request):
        if (p.readTimeout() != null) {
            factory.setReadTimeout(p.readTimeout());
        }

        return builder
                .baseUrl(p.baseUrl())
                .requestFactory(factory)
                .defaultHeader(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE)
                .defaultHeader("X-Auth-Token", apiKey)
                .defaultHeader("User-Agent", p.userAgent())
                .build();
    }

}
