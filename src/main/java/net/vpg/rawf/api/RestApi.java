package net.vpg.rawf.api;

import net.vpg.rawf.internal.requests.Requester;
import net.vpg.rawf.internal.utils.config.AuthorizationConfig;
import net.vpg.rawf.internal.utils.config.ConnectionConfig;
import okhttp3.OkHttpClient;

import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledThreadPoolExecutor;

public class RestApi {
    protected final OkHttpClient httpClient;
    protected final Requester requester;
    protected final ScheduledExecutorService callbackPool;
    protected final ScheduledExecutorService rateLimitPool;
    protected final AuthorizationConfig authorizationConfig;
    protected final ConnectionConfig connectionConfig;
    protected long globalRateLimit;

    public RestApi(AuthorizationConfig authorizationConfig, ConnectionConfig connectionConfig) {
        this.authorizationConfig = authorizationConfig;
        this.connectionConfig = connectionConfig;
        httpClient = connectionConfig.getHttpClient();
        requester = new Requester(this, authorizationConfig, connectionConfig);
        callbackPool = new ScheduledThreadPoolExecutor(8);
        rateLimitPool = new ScheduledThreadPoolExecutor(8);
    }

    public AuthorizationConfig getAuthorizationConfig() {
        return authorizationConfig;
    }

    public ConnectionConfig getConnectionConfig() {
        return connectionConfig;
    }

    public OkHttpClient getHttpClient() {
        return httpClient;
    }

    public Requester getRequester() {
        return requester;
    }

    public ScheduledExecutorService getCallbackPool() {
        return callbackPool;
    }

    public ScheduledExecutorService getRateLimitPool() {
        return rateLimitPool;
    }

    public long getGlobalRateLimit() {
        return globalRateLimit;
    }

    public void setGlobalRateLimit(long globalRateLimit) {
        this.globalRateLimit = globalRateLimit;
    }
}
