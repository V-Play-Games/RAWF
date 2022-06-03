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
package net.dv8tion.jda.internal.requests;

import net.dv8tion.jda.api.requests.RateLimiter;
import net.dv8tion.jda.api.requests.RestRequest;
import net.dv8tion.jda.api.requests.RestResponse;
import net.dv8tion.jda.internal.requests.ratelimit.DefaultRateLimiter;
import net.dv8tion.jda.internal.utils.Checks;
import net.dv8tion.jda.internal.utils.Helpers;
import net.dv8tion.jda.internal.utils.JDALogger;
import net.dv8tion.jda.internal.utils.config.AuthorizationConfig;
import okhttp3.*;
import okhttp3.internal.http.HttpMethod;
import org.slf4j.Logger;
import org.slf4j.MDC;

import javax.net.ssl.SSLPeerUnverifiedException;
import java.io.IOException;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Map.Entry;
import java.util.Set;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.RejectedExecutionException;

public class Requester {
    public static final Logger LOGGER = JDALogger.getLog(Requester.class);
    public static final String DISCORD_API_PREFIX = Helpers.format("https://discord.com/api/v%d/"/*, JDAInfo.DISCORD_REST_VERSION*/);
    public static final String USER_AGENT = "DiscordBot (" /*+ JDAInfo.GITHUB + ", " + JDAInfo.VERSION*/ + ")";
    public static final RequestBody EMPTY_BODY = RequestBody.create(null, new byte[0]);
    public static final MediaType MEDIA_TYPE_JSON = MediaType.parse("application/json; charset=utf-8");
    public static final MediaType MEDIA_TYPE_OCTET = MediaType.parse("application/octet-stream; charset=utf-8");

    //    protected final JDAImpl api;
    protected final AuthorizationConfig authConfig;
    private final RateLimiter rateLimiter;

    private final OkHttpClient httpClient;
    private final ConcurrentMap<String, String> contextMap = null;
    //when we actually set the shard info we can also set the mdc context map, before it makes no sense
    private boolean isContextReady = false;
    private volatile boolean retryOnTimeout = false;

    public Requester(/*JDA api*/) {
        this(null);
    }

    public Requester(/*JDA api,*/ AuthorizationConfig authConfig) {
        Checks.notNull(authConfig, "Authorization Config");

        this.authConfig = authConfig;
//        this.api = (JDAImpl) api;
        this.rateLimiter = new DefaultRateLimiter(this);
        this.httpClient = /*this.api.getHttpClient()*/null;
    }

    private static boolean isRetry(Throwable e) {
        return e instanceof SocketException             // Socket couldn't be created or access failed
            || e instanceof SocketTimeoutException      // Connection timed out
            || e instanceof SSLPeerUnverifiedException; // SSL Certificate was wrong
    }

    public void setContextReady(boolean ready) {
        this.isContextReady = ready;
    }

//    public JDAImpl getJDA()
//    {
//        return api;
//    }

    public void setContext() {
        if (!isContextReady)
            return;
//        if (contextMap == null)
//            contextMap = api.getContextMap();
        contextMap.forEach(MDC::put);
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

        String url = DISCORD_API_PREFIX + route.getCompiledRoute();
        builder.url(url);

        String method = apiRequest.getRoute().getMethod().toString();
        RequestBody body = apiRequest.getBody();

        if (body == null && HttpMethod.requiresRequestBody(method))
            body = EMPTY_BODY;

        builder.method(method, body)
            .header("X-RateLimit-Precision", "millisecond")
            .header("user-agent", USER_AGENT)
            .header("accept-encoding", "gzip");

        //adding token to all requests to the discord api or cdn pages
        //we can check for startsWith(DISCORD_API_PREFIX) because the cdn endpoints don't need any kind of authorization
//        if (url.startsWith(DISCORD_API_PREFIX))
//            builder.header("authorization", api.getToken());

        // Apply custom headers like X-Audit-Log-Reason
        // If customHeaders is null this does nothing
        if (apiRequest.getHeaders() != null) {
            for (Entry<String, String> header : apiRequest.getHeaders().entrySet())
                builder.addHeader(header.getKey(), header.getValue());
        }

        Request request = builder.build();

        Set<String> rays = new LinkedHashSet<>();
        Response[] responses = new Response[4];
        // we have an array of all responses to later close them all at once
        //the response below this comment is used as the first successful response from the server
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
                    //noinspection BusyWait
                    Thread.sleep(50L * attempt);
                } catch (InterruptedException ignored) {
                }
            } while (attempt < 3 && lastResponse.code() >= 500);

            LOGGER.trace("Finished Request {} {} with code {}", route.getMethod(), lastResponse.request().url(), lastResponse.code());

            if (lastResponse.code() >= 500) {
                //Epic failure from other end. Attempted 4 times.
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
        String method = apiRequest.getRoute().getMethod().toString();
        RequestBody body = apiRequest.getBody();

        if (body == null && HttpMethod.requiresRequestBody(method))
            body = EMPTY_BODY;

        builder.method(method, body);
    }

    private void applyHeaders(RestRequest<?> apiRequest, Request.Builder builder, boolean authorized) {
        builder.header("user-agent", USER_AGENT)
            .header("accept-encoding", "gzip")
            .header("x-ratelimit-precision", "millisecond"); // still sending this in case of regressions

        //adding token to all requests to the discord api or cdn pages
        //we can check for startsWith(DISCORD_API_PREFIX) because the cdn endpoints don't need any kind of authorization
        if (authorized)
            builder.header("authorization", authConfig.getToken());

        // Apply custom headers like X-Audit-Log-Reason
        // If customHeaders is null this does nothing
        if (apiRequest.getHeaders() != null) {
            for (Entry<String, String> header : apiRequest.getHeaders().entrySet())
                builder.addHeader(header.getKey(), header.getValue());
        }
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
