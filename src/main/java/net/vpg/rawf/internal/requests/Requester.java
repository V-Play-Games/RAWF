/*
 * Copyright 2015 Austin Keener, Michael Ritter, Florian Spieß, and the JDA contributors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package net.vpg.rawf.internal.requests;

import net.vpg.rawf.api.RestApi;
import net.vpg.rawf.api.requests.RateLimiter;
import net.vpg.rawf.api.requests.RestRequest;
import net.vpg.rawf.api.requests.RestResponse;
import net.vpg.rawf.internal.requests.ratelimit.DefaultRateLimiter;
import net.vpg.rawf.internal.utils.RAWFLogger;
import net.vpg.rawf.internal.utils.config.AuthorizationConfig;
import net.vpg.rawf.internal.utils.config.ConnectionConfig;
import okhttp3.*;
import okhttp3.internal.http.HttpMethod;
import org.slf4j.Logger;

import javax.net.ssl.SSLPeerUnverifiedException;
import java.io.IOException;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.RejectedExecutionException;

public class Requester {
    public static final Logger LOGGER = RAWFLogger.getLog(Requester.class);
    public static final RequestBody EMPTY_BODY = RequestBody.create(null, new byte[0]);
    public static final MediaType MEDIA_TYPE_JSON = MediaType.parse("application/json; charset=utf-8");

    protected final RestApi api;
    protected final AuthorizationConfig authorizationConfig;
    protected final ConnectionConfig connectionConfig;
    protected final RateLimiter rateLimiter;

    protected final OkHttpClient httpClient;
    protected volatile boolean retryOnTimeout = false;

    public Requester(RestApi api, AuthorizationConfig authorizationConfig, ConnectionConfig connectionConfig) {
        this.api = api;
        this.authorizationConfig = authorizationConfig;
        this.connectionConfig = connectionConfig;
        this.rateLimiter = new DefaultRateLimiter(this);
        this.httpClient = api.getHttpClient();
    }

    private static boolean isRetry(Throwable e) {
        return e instanceof SocketException             // Socket couldn't be created or access failed
            || e instanceof SocketTimeoutException      // Connection timed out
            || e instanceof SSLPeerUnverifiedException; // SSL Certificate was wrong
    }

    public RestApi getApi() {
        return api;
    }

    public <T> void request(RestRequest<T> apiRequest) {
        if (rateLimiter.isShutdown())
            throw new RejectedExecutionException("The Requester has been stopped! No new requests can be requested!");

        if (apiRequest.shouldQueue())
            rateLimiter.queueRequest(apiRequest);
        else
            execute(apiRequest, true);
    }

    public long execute(RestRequest<?> apiRequest) {
        return execute(apiRequest, false);
    }

    /**
     * Used to execute a Request. Processes request related to provided bucket.
     *
     * @param apiRequest        The API request that needs to be sent
     * @param handleOnRateLimit Whether to forward rate-limits, false if rate limit handling should take over
     * @return Non-null if the request was ratelimited. Returns a Long containing retry_after milliseconds until
     * the request can be made again. This could either be for the Per-Route ratelimit or the Global ratelimit.
     * <br>Check if globalCooldown is {@code null} to determine if it was Per-Route or Global.
     */
    public long execute(RestRequest<?> apiRequest, boolean handleOnRateLimit) {
        return execute(apiRequest, false, handleOnRateLimit);
    }

    public long execute(RestRequest<?> apiRequest, boolean retried, boolean handleOnRatelimit) {
        Route.CompiledRoute route = apiRequest.getRoute();
        long retryAfter = rateLimiter.getRateLimit(route);
        if (retryAfter > 0) {
            if (handleOnRatelimit)
                apiRequest.handleResponse(new RestResponse(retryAfter, Collections.emptySet()));
            return retryAfter;
        }

        Request.Builder builder = new Request.Builder();

        String url = connectionConfig.getBaseUrl() + route.getCompiledRoute();
        builder.url(url);

        String method = apiRequest.getRoute().getMethod().toString();
        RequestBody body = apiRequest.getBody();

        if (body == null && HttpMethod.requiresRequestBody(method))
            body = EMPTY_BODY;

        builder.method(method, body)
            .header("user-agent", connectionConfig.getUserAgent())
            .header("accept-encoding", "gzip")
            .header("x-ratelimit-precision", "millisecond"); // still sending this in case of regressions

        if (route.getBaseRoute().isAuthorizationRequired())
            builder.header("authorization", authorizationConfig.getToken());

        // Apply custom headers like X-Audit-Log-Reason
        // If customHeaders is null this does nothing
        Map<String, String> headers = apiRequest.getHeaders();
        if (headers != null) {
            headers.forEach(builder::addHeader);
        }

        Request request = builder.build();

        Set<String> rays = new LinkedHashSet<>();
        Response[] responses = new Response[4];
        // we have an array of all responses to later close them all at once
        // the response below this comment is used as the first successful response from the server
        Response lastResponse;
        try {
            LOGGER.trace("Executing request {} {}", apiRequest.getRoute().getMethod(), url);
            int attempt = 0;
            do {
                if (apiRequest.isSkipped())
                    return 0L;

                Call call = httpClient.newCall(request);
                lastResponse = call.execute();
                responses[attempt] = lastResponse;
                String cfRay = lastResponse.header("CF-RAY");
                if (cfRay != null)
                    rays.add(cfRay);

                if (lastResponse.code() < 500)
                    break; // break loop, got a successful response!

                attempt++;
                LOGGER.debug("Requesting {} -> {} returned status {}... retrying (attempt {})",
                    apiRequest.getRoute().getMethod(),
                    url, lastResponse.code(), attempt);
                try {
                    // noinspection BusyWait
                    Thread.sleep(50L * attempt);
                } catch (InterruptedException ignored) {
                }
            } while (attempt < 3 && lastResponse.code() >= 500);

            LOGGER.trace("Finished Request {} {} with code {}", route.getMethod(), lastResponse.request().url(), lastResponse.code());

            if (lastResponse.code() >= 500) {
                // Epic failure from other end. Attempted 4 times.
                RestResponse response = new RestResponse(lastResponse, -1, rays);
                apiRequest.handleResponse(response);
                return 0L;
            }

            retryAfter = rateLimiter.handleResponse(route, lastResponse);
            if (!rays.isEmpty())
                LOGGER.debug("Received response with following cf-rays: {}", rays);

            if (retryAfter == 0)
                apiRequest.handleResponse(new RestResponse(lastResponse, -1, rays));
            else if (handleOnRatelimit)
                apiRequest.handleResponse(new RestResponse(lastResponse, retryAfter, rays));

            return retryAfter;
        } catch (UnknownHostException e) {
            LOGGER.error("DNS resolution failed: {}", e.getMessage());
            apiRequest.handleResponse(new RestResponse(e, rays));
        } catch (IOException e) {
            if (retryOnTimeout && !retried && isRetry(e))
                return execute(apiRequest, true, handleOnRatelimit);
            LOGGER.error("There was an I/O error while executing a REST request: {}", e.getMessage());
            apiRequest.handleResponse(new RestResponse(e, rays));
        } catch (Exception e) {
            LOGGER.error("There was an unexpected error while executing a REST request", e);
            apiRequest.handleResponse(new RestResponse(e, rays));
        } finally {
            for (Response r : responses) {
                if (r == null)
                    break;
                r.close();
            }
        }
        return 0L;
    }

    private void applyBody(RestRequest<?> apiRequest, Request.Builder builder) {
    }

    private void applyHeaders(RestRequest<?> apiRequest, Request.Builder builder, boolean authorized) {
    }

    public OkHttpClient getHttpClient() {
        return this.httpClient;
    }

    public RateLimiter getRateLimiter() {
        return rateLimiter;
    }

    public void setRetryOnTimeout(boolean retryOnTimeout) {
        this.retryOnTimeout = retryOnTimeout;
    }

    public void shutdown() {
        rateLimiter.shutdown();
    }
}
