package com.application.apiratelimiter.controller;

import com.application.apiratelimiter.service.RateLimiterService;
import org.springframework.http.ResponseEntity;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.net.InetAddress;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@RestController
@RequestMapping("/gateway")
public class StatusController {

    private final RateLimiterService rateLimiterService;
    private static final Logger log = LoggerFactory.getLogger(StatusController.class);

    public StatusController(RateLimiterService rateLimiterService) {
        this.rateLimiterService = rateLimiterService;
    }

    @GetMapping("/health")
    public Mono<ResponseEntity<Map<String, String>>> health() {
        return Mono.just(ResponseEntity.ok(Map.of(
                "status", "UP",
                "service", "Rate Limiter Gateway"
        )));
    }

    @GetMapping("/rate-limit/status")
    public Mono<ResponseEntity<Map<String, Object>>> getRateLimitStatus(ServerWebExchange exchange) {
        ServerHttpRequest request = exchange.getRequest();
        String clientId = getClientId(request);
        return Mono.just(ResponseEntity.ok(Map.of(
                "clientId", clientId,
                "capacity", rateLimiterService.getCapacity(clientId),
                "availableTokens", rateLimiterService.getAvailableTokens(clientId)
        )));
    }

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


