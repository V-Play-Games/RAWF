package net.vpg.rawf.api;

import net.vpg.rawf.internal.requests.Requester;

import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledThreadPoolExecutor;

public class RestApi {
    private final Requester requester;
    private final ScheduledExecutorService callbackPool;
    private final ScheduledExecutorService rateLimitPool;

    public RestApi() {
        requester = new Requester(this);
        callbackPool = new ScheduledThreadPoolExecutor(8);
        rateLimitPool = new ScheduledThreadPoolExecutor(8);
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
}
