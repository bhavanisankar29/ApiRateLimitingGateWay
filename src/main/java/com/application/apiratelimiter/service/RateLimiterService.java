package com.application.apiratelimiter.service;

import org.springframework.stereotype.Service;

@Service
public interface RateLimiterService {

    public boolean isAllowed(String clientId);

    public long getCapacity(String clientId);

    public long getAvailableTokens(String clientId);
}
