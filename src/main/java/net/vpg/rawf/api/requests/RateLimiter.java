package net.vpg.rawf.api.requests;

import net.vpg.rawf.internal.requests.Route;
import okhttp3.Response;

// TODO: Docs
public interface RateLimiter {
    long getRateLimit(Route.CompiledRoute route);

    void queueRequest(RestRequest<?> request);

    long handleResponse(Route.CompiledRoute route, Response response);

    int cancelRequests();

    default boolean isRateLimited(Route.CompiledRoute route) {
        return getRateLimit(route) > 0L;
    }

    void shutdown();

    boolean isShutdown();
}
