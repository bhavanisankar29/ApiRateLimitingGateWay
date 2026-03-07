# API Rate Limiter

This project is a Spring Cloud Gateway application that applies Redis-backed token bucket rate limiting to all requests routed through `/api/**`.

The gateway listens on port `8080` and forwards matching traffic to a backend server configured by `rate-limiter.api-server-url`. A small mock backend is included in this repository as `simpleServer.py`, which runs on port `8081` by default.

## What It Does

- Applies token bucket rate limiting per client IP.
- Stores token state in Redis so rate limiting works across multiple gateway instances.
- Uses `X-Forwarded-For` when present to identify the client.
- Exposes status and health endpoints for local testing.
- Proxies `/api/**` traffic to the configured backend after stripping the `/api` prefix.

## Stack

- Java 21
- Spring Boot 3.2.0
- Spring Cloud Gateway 2023.0.0
- Redis with Jedis
- Gradle Wrapper
- Lombok

## Prerequisites

- Java 21
- Redis running on `localhost:6379`, or equivalent config overrides
- Bash if you want to run `quickTest.sh`

## Configuration

Current defaults from `src/main/resources/application.properties`:

```properties
spring.application.name=ApiRateLimiter
server.port=8080

spring.cloud.gateway.discovery.locator.enabled=true

spring.data.redis.host=localhost
spring.data.redis.port=6379
spring.data.redis.timeout=2000

rate-limiter.capacity=10
rate-limiter.refill-rate=1
rate-limiter.api-server-url=http://localhost:8081
rate-limiter.timeout=5000
```

### Property Reference

| Property | Default | Meaning |
|----------|---------|---------|
| `server.port` | `8080` | Gateway HTTP port |
| `spring.data.redis.host` | `localhost` | Redis host |
| `spring.data.redis.port` | `6379` | Redis port |
| `spring.data.redis.timeout` | `2000` | Redis timeout in milliseconds |
| `rate-limiter.capacity` | `10` | Maximum tokens available per client bucket |
| `rate-limiter.refill-rate` | `1` | Tokens added per second |
| `rate-limiter.api-server-url` | `http://localhost:8081` | Upstream server that receives routed `/api/**` traffic |
| `rate-limiter.timeout` | `5000` | Reserved timeout property currently kept in config |

## Project Workflow

This is the request flow for traffic that enters the gateway through `/api/**`:

1. A client sends a request to the gateway on port `8080`.
2. Spring Cloud Gateway matches the request against the route configured in `GatewayConfig`.
3. The route applies the `TokenBucketRateLimiterFilter` before forwarding the request.
4. The filter determines the client ID, using `X-Forwarded-For` first and the remote IP as a fallback.
5. The filter calls `RateLimiterService` to check whether the client is allowed to consume a token.
6. `RateLimiterServiceImpl` executes a Redis Lua script so token refill and token consumption happen atomically.
7. If a token is available, the request continues through the gateway and is forwarded to the upstream server configured by `rate-limiter.api-server-url`.
8. If no token is available, the gateway stops the request and returns `429 Too Many Requests` with rate-limit metadata.
9. For allowed requests, the gateway adds rate-limit headers to the response before returning the upstream response to the client.

## Local Run

### 1. Start Redis

Using Docker:

```bash
docker run --name api-rate-limiter-redis -p 6379:6379 redis:latest
```

Or start a local Redis instance however you normally run it.

### 2. Start the mock backend

The repository includes a simple Python server for local routing tests:

```bash
python simpleServer.py
```

That server responds on `http://localhost:8081` and returns JSON for any HTTP method.

### 3. Start the gateway

On macOS or Linux:

```bash
./gradlew bootRun
```

On Windows PowerShell:

```powershell
.\gradlew.bat bootRun
```

### 4. Build or test

On macOS or Linux:

```bash
./gradlew build
./gradlew test
```

On Windows PowerShell:

```powershell
.\gradlew.bat build
.\gradlew.bat test
```
## Rate Limit Behavior

The limiter uses a token bucket per client ID.

- A new client starts with a full bucket.
- Each allowed request consumes one token.
- Tokens refill at `rate-limiter.refill-rate` per second.
- If the bucket is empty, the gateway returns `429 Too Many Requests`.

Default behavior with the current config:

- Capacity: `10`
- Refill rate: `1` token per second
- Immediate burst: up to `10` requests for a new client

When a request is blocked, the response body looks like this:

```json
{
    "error": "Rate limit exceeded",
    "clientId": "127.0.0.1",
    "retryAfter": 1
}
```

The gateway also adds these headers when it can resolve token state:

- `X-RateLimit-Limit`
- `X-RateLimit-Remaining`
- `Retry-After` on `429` responses

## Quick Test Script

`quickTest.sh` exercises the gateway with a fixed client ID of `127.0.0.1`.

It does the following:

1. Calls `/gateway/rate-limit/status` and prints the initial token count.
2. Sends 20 requests to `/api/test` with the same `X-Forwarded-For` header.
3. Prints how many requests were allowed versus blocked.
4. Calls `/gateway/rate-limit/status` again and prints the final token count.

Run it after Redis, the mock backend, and the gateway are already running:

```bash
bash quickTest.sh
```

If `jq` is installed, the script pretty-prints JSON. Without `jq`, it still runs and falls back to plain output parsing.

## Failure Mode

If Redis is unavailable during a rate-limit check, the service currently fails open and allows the request. Status reads fall back to reporting full capacity when Redis cannot be reached.

## Project Layout

```text
src/main/java/com/application/apiratelimiter/
├── ApiRateLimiterApplication.java
├── config/
│   ├── GatewayConfig.java
│   ├── RateLimiterProperties.java
│   └── RedisProperties.java
├── controller/
│   └── StatusController.java
├── filter/
│   └── TokenBucketRateLimiterFilter.java
└── service/
        ├── RateLimiterService.java
        └── RateLimiterServiceImpl.java
```

Supporting files:

- `src/main/resources/application.properties` for runtime configuration
- `simpleServer.py` for a local mock server
- `quickTest.sh` for an end-to-end manual test

## Contributing

Contributions are welcome.

1. Fork the repository.
2. Create a feature branch for your change.
3. Make the code or documentation update.
4. Run the relevant checks locally.
5. Open a pull request with a clear summary of the change.

For behavior changes, include the reason for the change and the impact on rate limiting or routing behavior.

## Author

Bhavani Sankar Katta -  https://github.com/bhavanisankar29