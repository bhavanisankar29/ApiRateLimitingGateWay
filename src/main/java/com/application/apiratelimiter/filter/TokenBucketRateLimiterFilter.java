package com.application.apiratelimiter.filter;

import com.application.apiratelimiter.service.RateLimiterService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cloud.gateway.filter.GatewayFilter;
import org.springframework.cloud.gateway.filter.factory.AbstractGatewayFilterFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.net.InetAddress;
import java.nio.charset.StandardCharsets;

@Component
public class TokenBucketRateLimiterFilter extends AbstractGatewayFilterFactory<TokenBucketRateLimiterFilter.Config>{

    private static final Logger log = LoggerFactory.getLogger(TokenBucketRateLimiterFilter.class);
    private final RateLimiterService rateLimiterService;

    public TokenBucketRateLimiterFilter(RateLimiterService rateLimiterService){
        super(Config.class);
        this.rateLimiterService = rateLimiterService;
    }

    @Override
    public TokenBucketRateLimiterFilter.Config newConfig(){
        return new Config();
    }

    @Override
    public GatewayFilter apply(Config config){

        return (exchange, chain) -> {

            ServerHttpRequest request = exchange.getRequest();
            ServerHttpResponse response = exchange.getResponse();

            String clientId = getClientId(request);

            // Wrap blocking Redis call in bounded elastic scheduler
            return Mono.fromCallable(() -> rateLimiterService.isAllowed(clientId))
                    .subscribeOn(Schedulers.boundedElastic())
                    .flatMap(allowed -> {
                        if (!allowed) {
                            response.setStatusCode(HttpStatus.TOO_MANY_REQUESTS);
                            response.getHeaders().setContentType(MediaType.APPLICATION_JSON);
                            response.getHeaders().add("Retry-After", "1");
                            addRateLimitHeaders(response, clientId);

                            String errorBody = String.format(
                                    "{\"error\":\"Rate limit exceeded\",\"clientId\":\"%s\",\"retryAfter\":1}",
                                    clientId
                            );

                            return response.writeWith(
                                    Mono.just(response.bufferFactory().wrap(errorBody.getBytes(StandardCharsets.UTF_8)))
                            );
                        }

                        return chain.filter(exchange).then(Mono.fromRunnable(() -> {
                            addRateLimitHeaders(response, clientId);
                        }));
                    });
        };
    }

    // Function to add the Headers to the response
    private void addRateLimitHeaders(ServerHttpResponse response, String clientId){
        try {
            response.getHeaders().add("X-RateLimit-Limit",
                    String.valueOf(rateLimiterService.getCapacity(clientId)));
            response.getHeaders().add("X-RateLimit-Remaining",
                    String.valueOf(rateLimiterService.getAvailableTokens(clientId)));
        } catch (Exception e) {
            log.warn("Could not add rate limit headers for client {}: {}", clientId, e.getMessage());
        }
    }

    public static class Config{}

    private String getClientId(ServerHttpRequest request){
        // Try X-Forwarded-For header first (for proxy-requests)
        String xForwardedFor = request.getHeaders().getFirst("X-Forwarded-For");
        if(xForwardedFor != null && !xForwardedFor.isEmpty()){
            String candidateIp = xForwardedFor.split(",")[0].trim();
            if(isValidIpAddress(candidateIp)){
                return candidateIp;
            }
            log.warn("Invalid IP address in X-Forwarded-For header: {}", candidateIp);
        }

        // Check remote address if xForwardFor doesn't get a valid ip
        var remoteAddress = request.getRemoteAddress();
        if(remoteAddress != null && remoteAddress.getAddress() != null){
            return remoteAddress.getAddress().getHostAddress();
        }

        // Log warning when client ID cannot be determined
        log.warn("Could not determine client ID for request to {}, using 'unknown'", request.getPath());
        return "unknown";
    }

    // Validates if the given string is IPv4 or IPv6 address.
    private boolean isValidIpAddress(String ip){
        if(ip == null || ip.isEmpty()){
            return false;
        }
        try {
            InetAddress.getByName(ip);
            return true;
        } catch (Exception e) {
            return false;
        }
    }
}

