package net.vpg.rawf.api;

import net.vpg.rawf.api.requests.RestConfig;
import net.vpg.rawf.api.requests.RestRateLimiter;
import net.vpg.rawf.internal.requests.Requester;
import net.vpg.rawf.internal.utils.config.AuthorizationConfig;
import net.vpg.rawf.internal.utils.config.ThreadingConfig;
import okhttp3.OkHttpClient;

import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledThreadPoolExecutor;

public class RestApi {
    protected final Requester requester;
    protected final AuthorizationConfig authorizationConfig;
    protected final ThreadingConfig threadingConfig;
    protected final RestConfig restConfig;
    protected final RestRateLimiter.GlobalRateLimit globalRateLimit;

    public RestApi(AuthorizationConfig authorizationConfig, ThreadingConfig threadingConfig, RestConfig restConfig) {
        this.authorizationConfig = authorizationConfig;
        this.threadingConfig = threadingConfig;
        this.restConfig = restConfig;
        this.globalRateLimit = RestRateLimiter.GlobalRateLimit.create();
        RestRateLimiter ratelimiter = restConfig.getRateLimiterFactory().apply(
            new RestRateLimiter.RateLimitConfig(
                threadingConfig.getRateLimitPool(),
                globalRateLimit,
                restConfig.isRelativeRateLimit()
            )
        );
        this.requester = new Requester(this, authorizationConfig, restConfig, ratelimiter);
    }

    public AuthorizationConfig getAuthorizationConfig() {
        return authorizationConfig;
    }

    public ThreadingConfig getThreadingConfig() {
        return threadingConfig;
    }

    public RestConfig getRestConfig() {
        return restConfig;
    }

    public Requester getRequester() {
        return requester;
    }

    public RestRateLimiter.GlobalRateLimit getGlobalRateLimit() {
        return globalRateLimit;
    }
}
