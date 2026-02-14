# API Rate Limiter

A Basic rate limiting gateway built with Spring Boot and Spring Cloud Gateway. This project implements the Token Bucket algorithm with Redis for distributed rate limiting.

## Features

- **Token Bucket Algorithm**: Efficient rate limiting algorithm for controlling API traffic
- **Distributed Rate Limiting**: Redis-backed rate limiting for distributed systems
- **Spring Cloud Gateway Integration**: Seamless integration with Spring Cloud Gateway
- **Configurable Limits**: Easily configure capacity, refill rate, and timeout settings
- **Reactive Architecture**: Non-blocking, high-performance request handling with Project Reactor
- **Health Check Endpoint**: Built-in health monitoring for the gateway service

## Tech Stack

- **Java 21**
- **Spring Boot 3.2.0**
- **Spring Cloud Gateway** (2023.0.0)
- **Redis** for distributed state management
- **Gradle** for build management
- **Lombok** for reducing boilerplate code

## Prerequisites

- Java 21 or higher
- Redis server running (default: localhost:6379)
- Gradle 7.x or higher (included via wrapper)

## Installation

1. **Clone the repository**
   ```bash
   git clone <repository-url>
   cd ApiRateLimiter
   ```

2. **Ensure Redis is running**
   ```bash
   # Using Docker
   docker run -d -p 6379:6379 redis:latest
   
   # Or start your local Redis instance
   redis-server
   ```

3. **Build the project**
   ```bash
   ./gradlew build
   ```

## Configuration

Configure the rate limiter through `application.properties`:

```properties
spring.application.name=ApiRateLimiter
server.port=8080

# Redis Configuration
spring.data.redis.host=localhost
spring.data.redis.port=6379
spring.data.redis.timeout=2000

# Rate Limiter Configuration
rate-limiter.capacity=10              # Maximum tokens in the bucket
rate-limiter.refill-rate=1            # Tokens refilled per second
rate-limiter.api-server-url=http://localhost:8081
rate-limiter.timeout=5000             # Request timeout in milliseconds

# Gateway Discovery
spring.cloud.gateway.discovery.locator.enabled=true
```

### Configuration Properties

| Property | Default | Description |
|----------|---------|-------------|
| `rate-limiter.capacity` | 10 | Maximum number of tokens the bucket can hold |
| `rate-limiter.refill-rate` | 1 | Number of tokens added per second |
| `rate-limiter.api-server-url` | http://localhost:8081 | Backend API server URL |
| `rate-limiter.timeout` | 5000 | Request timeout in milliseconds |
| `spring.data.redis.host` | localhost | Redis server host |
| `spring.data.redis.port` | 6379 | Redis server port |

## Running the Application

```bash
./gradlew bootRun
```

The gateway will start on `http://localhost:8080`

## API Endpoints

### Health Check
```
GET /gateway/health
```

**Response:**
```json
{
  "status": "UP",
  "service": "Rate Limiter Gateway"
}
```

## How It Works

### Token Bucket Algorithm

The Token Bucket algorithm works by:

1. Maintaining a "bucket" with a maximum capacity
2. Tokens are added to the bucket at a fixed refill rate
3. Each incoming request consumes one token
4. If tokens are available, the request is allowed
5. If no tokens are available, the request is rejected with HTTP 429 (Too Many Requests)

**Example:**
- Capacity: 10 tokens
- Refill Rate: 1 token/second
- Maximum sustainable throughput: 1 request/second
- Burst capacity: up to 10 requests at once (if bucket is full)

### Rate Limiting Response

When a request exceeds the rate limit:

**Status Code:** `429 Too Many Requests`

**Response Body:**
```json
{
  "error": "Rate limited exceeded",
  "clientId": "client-123"
}
```

## Architecture

```
Client Request
    ↓
[TokenBucketRateLimiterFilter]
    ↓
[RateLimiterService]
    ↓
[Redis] ← Token count & client state
    ↓
Allowed/Rejected Decision
    ↓
Backend API or Error Response
```

## Project Structure

```
src/
├── main/
│   ├── java/com/application/apiratelimiter/
│   │   ├── ApiRateLimiterApplication.java
│   │   ├── config/
│   │   │   ├── GatewayConfig.java
│   │   │   ├── RateLimiterProperties.java
│   │   │   └── RedisProperties.java
│   │   ├── controller/
│   │   │   └── StatusController.java
│   │   ├── filter/
│   │   │   └── TokenBucketRateLimiterFilter.java
│   │   └── service/
│   │       ├── RateLimiterService.java
│   │       └── RateLimiterServiceImpl.java
│   └── resources/
│       └── application.properties
└── test/
    └── java/com/application/apiratelimiter/
        └── ApiRateLimiterApplicationTests.java
```

## Contributing

1. Fork the repository
2. Create a feature branch (`git checkout -b feature/your-feature`)
3. Commit your changes (`git commit -m 'Add some feature'`)
4. Push to the branch (`git push origin feature/your-feature`)
5. Open a Pull Request

## Author

Bhavani Sankar Katta - https://github.com/bhavanisankar29

---

