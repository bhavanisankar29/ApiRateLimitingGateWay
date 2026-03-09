package com.application.apiratelimiter.config;

import com.application.apiratelimiter.filter.TokenBucketRateLimiterFilter;
import org.springframework.cloud.gateway.route.RouteLocator;
import org.springframework.cloud.gateway.route.builder.RouteLocatorBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class GatewayConfig {

    private final RateLimiterProperties rateLimiterProperties;
    private final TokenBucketRateLimiterFilter tokenBucketRateLimiterFilter;


    public GatewayConfig(RateLimiterProperties rateLimiterProperties, TokenBucketRateLimiterFilter tokenBucketRateLimiterFilter){
        this.rateLimiterProperties = rateLimiterProperties;
        this.tokenBucketRateLimiterFilter = tokenBucketRateLimiterFilter;
    }

    @Bean
    public RouteLocator customRouteLocator(RouteLocatorBuilder builder){

        /*

        * This route configuration does the following:

        * 1. It matches any incoming request with a path that starts with /api/** (This route doesnt exist in the API server, but it is used as a pattern to match incoming requests to the gateway).

        * 2. If matched, it applies the rate limiting logic to this request.
        
        * 3. Then it strips the /api prefix and then forwards the request to the actual API server(http://localhost:8081)

        * For example, if a request comes in as http://localhost:8080/api/resource, the gateway will match it, apply rate limiting, strip the /api prefix, and forward it to http://localhost:8081/resource.
        
        */
        return builder.routes()
                .route("api-route", r -> r
                        .path("/api/**")
                        .filters(f -> f
                                .stripPrefix(1)
                                .filter(tokenBucketRateLimiterFilter.apply(new TokenBucketRateLimiterFilter.Config()))
                        )
                        .uri(rateLimiterProperties.getApiServerUrl()))
                .build();
    }

}
