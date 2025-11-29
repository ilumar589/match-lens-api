Sites with apis to gather data:
1. https://www.football-data.org/pricing
   example apis:
   1. curl -X GET http://api.football-data.org/v4/competitions/PL
   2. response: https://www.football-data.org/documentation/quickstart


Configuration
-------------

FOOTBALL_DATA_API_KEY
- The application reads the Football-Data.org API key from either a JVM system property or an environment variable named `FOOTBALL_DATA_API_KEY`.
- In application.properties it is referenced as: `footballdata.api.key=${FOOTBALL_DATA_API_KEY}`.
- At runtime, if that placeholder is not resolved by Spring, the app will fall back to:
  1) Java system property `-DFOOTBALL_DATA_API_KEY=...`, then
  2) environment variable `FOOTBALL_DATA_API_KEY`.
- Do NOT commit the key to source control.

How to provide the key
- Windows (PowerShell):
  - `$env:FOOTBALL_DATA_API_KEY = "your-real-key"`
  - Then run the app in the same terminal (e.g., `./gradlew bootRun`).
- macOS/Linux (bash/zsh):
  - `export FOOTBALL_DATA_API_KEY=your-real-key`
  - Then run the app in the same shell (e.g., `./gradlew bootRun`).
- As a JVM system property (works reliably with Gradle/IDE):
  - `./gradlew bootRun --args="" -DFOOTBALL_DATA_API_KEY=your-real-key`
  - or in IntelliJ: Run Configuration → VM options: `-DFOOTBALL_DATA_API_KEY=your-real-key`.
- IntelliJ IDEA Run Configuration:
  - Edit Configuration → Environment → Environment variables → add `FOOTBALL_DATA_API_KEY=your-real-key`.
- Docker Compose:
  - Create a `.env` file next to `compose.yaml` with `FOOTBALL_DATA_API_KEY=your-real-key`.
  - If/when you add an app service to compose, pass it through under `environment:` (example):
    
    services:
      app:
        image: your-app-image
        environment:
          - FOOTBALL_DATA_API_KEY=${FOOTBALL_DATA_API_KEY}

Troubleshooting (Gradle Daemon on Windows)
- The Gradle Daemon may cache environment variables from when it first started. If you change
  `$env:FOOTBALL_DATA_API_KEY` and Gradle still doesn’t see it, try one of these:
  - Run `./gradlew --no-daemon bootRun`
  - Or pass the key as a JVM option: `-DFOOTBALL_DATA_API_KEY=your-real-key` (preferred).
  - Or stop current daemons: `./gradlew --stop` and rerun.

Swagger UI:
http://localhost:8080/swagger-ui.html → redirects to /swagger-ui/index.html

OpenAPI JSON:
http://localhost:8080/v3/api-docs

OpenAPI YAML:
http://localhost:8080/v3/api-docs.yaml

Circuit Breaker and Retry (Resilience4j + Spring Retry)
-------------------------------------------------------

The Football-Data.org API client uses a combination of Spring Retry and Resilience4j circuit breaker for resilience:

- **Spring Retry (@Retryable)**: Handles retries with exponential backoff (up to 3 attempts)
- **Resilience4j Circuit Breaker**: Protects against cascading failures when the API is unavailable

### Configuration

Circuit breaker settings are defined in `application-resilience.yml`:

```yaml
resilience4j:
  circuitbreaker:
    instances:
      footballDataApi:
        waitDurationInOpenState: 60s
        failureRateThreshold: 50
```

Spring Retry is configured via @Retryable annotation with:
- 3 max attempts
- Exponential backoff: 1s initial delay, multiplier 2.0, max 8s

### Circuit Breaker States

1. **CLOSED**: Normal operation, requests pass through
2. **OPEN**: After failure threshold is reached, requests return empty results immediately
3. **HALF_OPEN**: After wait duration, allows limited requests to test service recovery

### Fallback Behavior

When the circuit breaker is open or the Football-Data.org API fails, the fallback returns `Optional.empty()` for competition info requests. This allows the application to continue operating in a degraded mode.

### Monitoring

Circuit breaker status is available via Spring Boot Actuator:

- `/actuator/health` - Overall health including circuit breaker status
- `/actuator/circuitbreakers` - Detailed circuit breaker information
- `/actuator/circuitbreakerevents` - Recent circuit breaker events