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
import net.vpg.rawf.api.requests.Route;
import net.vpg.rawf.api.requests.*;
import net.vpg.rawf.internal.utils.IOUtil;
import net.vpg.rawf.internal.utils.RAWFLogger;
import net.vpg.rawf.internal.utils.config.AuthorizationConfig;
import net.vpg.vjson.value.JSONObject;
import okhttp3.*;
import okhttp3.internal.http.HttpMethod;
import org.slf4j.Logger;

import javax.annotation.Nonnull;
import javax.net.ssl.SSLPeerUnverifiedException;
import java.io.IOException;
import java.io.InputStream;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.util.Locale;
import java.util.concurrent.RejectedExecutionException;
import java.util.function.Consumer;

public class Requester {
    public static final Logger LOGGER = RAWFLogger.getLog(Requester.class);
    public static final RequestBody EMPTY_BODY = RequestBody.create(null, new byte[0]);
    public static final MediaType MEDIA_TYPE_JSON = MediaType.parse("application/json; charset=utf-8");

    protected final RestApi api;
    protected final AuthorizationConfig authConfig;
    private final RestRateLimiter rateLimiter;
    private final String baseUrl;
    private final String userAgent;
    private final Consumer<? super Request.Builder> customBuilder;

    private final OkHttpClient httpClient;

    private volatile boolean retryOnTimeout = false;

    public Requester(RestApi api, AuthorizationConfig authConfig, RestConfig config, RestRateLimiter rateLimiter) {
        this.authConfig = authConfig;
        this.api = api;
        this.rateLimiter = rateLimiter;
        this.baseUrl = config.getBaseUrl();
        this.userAgent = config.getUserAgent();
        this.customBuilder = config.getCustomBuilder();
        this.httpClient = api.getRestConfig().getHttpClient();
    }

    private static boolean isRetry(Throwable e) {
        return e instanceof SocketException             // Socket couldn't be created or access failed
            || e instanceof SocketTimeoutException      // Connection timed out
            || e instanceof SSLPeerUnverifiedException; // SSL Certificate was wrong
    }

    private static boolean shouldRetry(int code) {
        return code == 502 || code == 504 || code == 529;
    }

    private static String getContentType(Response response) {
        String type = response.header("content-type");
        return type == null ? "" : type.toLowerCase(Locale.ROOT);
    }

    public RestApi getApi() {
        return api;
    }

    public <T> void request(RestRequest<T> apiRequest) {
        if (rateLimiter.isStopped())
            throw new RejectedExecutionException("The Requester has been stopped! No new requests can be requested!");

        if (apiRequest.shouldQueue())
            rateLimiter.enqueue(new WorkTask(apiRequest));
        else
            execute(new WorkTask(apiRequest), true);
    }

    public Response execute(WorkTask task) {
        return execute(task, false);
    }

    /**
     * Used to execute a Request. Processes request related to provided bucket.
     *
     * @param task              The API request that needs to be sent
     * @param handleOnRateLimit Whether to forward rate-limits, false if rate limit handling should take over
     * @return Non-null if the request was ratelimited. Returns a Long containing retry_after milliseconds until
     * the request can be made again. This could either be for the Per-Route ratelimit or the Global ratelimit.
     * <br>Check if globalCooldown is {@code null} to determine if it was Per-Route or Global.
     */
    public Response execute(WorkTask task, boolean handleOnRateLimit) {
        return execute(task, false, handleOnRateLimit);
    }

    public Response execute(WorkTask task, boolean retried, boolean handleOnRatelimit) {
        Route.CompiledRoute route = task.getRoute();

        Request.Builder builder = new Request.Builder();

        String url = baseUrl + route.getCompiledRoute();
        builder.url(url);

        RestRequest<?> apiRequest = task.request;

        applyBody(apiRequest, builder);
        applyHeaders(apiRequest, builder);
        if (customBuilder != null) {
            try {
                customBuilder.accept(builder);
            } catch (Exception e) {
                LOGGER.error("Custom request builder caused exception", e);
            }
        }

        Request request = builder.build();

        Response[] responses = new Response[4];
        // we have an array of all responses to later close them all at once
        //the response below this comment is used as the first successful response from the server
        Response lastResponse = null;
        try {
            LOGGER.trace("Executing request {} {}", task.getRoute().getMethod(), url);
            int code = 0;
            for (int attempt = 0; attempt < responses.length; attempt++) {
                if (apiRequest.isSkipped())
                    return null;

                Call call = httpClient.newCall(request);
                lastResponse = call.execute();
                code = lastResponse.code();
                responses[attempt] = lastResponse;

                // Retry a few specific server errors that are related to server issues
                if (!shouldRetry(code))
                    break;

                LOGGER.debug("Requesting {} -> {} returned status {}... retrying (attempt {})",
                    apiRequest.getRoute().getMethod(),
                    url, code, attempt + 1);
                try {
                    Thread.sleep(500 << attempt);
                } catch (InterruptedException ignored) {
                    break;
                }
            }

            LOGGER.trace("Finished Request {} {} with code {}", route.getMethod(), lastResponse.request().url(), code);

            if (shouldRetry(code)) {
                //Epic failure from other end. Attempted 4 times.
                task.handleResponse(lastResponse, -1);
                return null;
            }

            if (handleOnRatelimit && code == 429) {
                long retryAfter = parseRetry(lastResponse);
                task.handleResponse(lastResponse, retryAfter);
            } else if (code != 429) {
                task.handleResponse(lastResponse);
            } else if (getContentType(lastResponse).startsWith("application/json")) { // potentially not json when cloudflare does 429
                // On 429, replace the retry-after header if its wrong (discord moment)
                // We just pick whichever is bigger between body and header
                try (InputStream body = IOUtil.getBody(lastResponse)) {
                    long retryAfterBody = (long) Math.ceil(JSONObject.parse(body).getDouble("retry_after", 0));
                    long retryAfterHeader = parseRetry(lastResponse);
                    lastResponse = lastResponse.newBuilder()
                        .header(RestRateLimiter.RETRY_AFTER_HEADER, Long.toString(Math.max(retryAfterHeader, retryAfterBody)))
                        .build();
                } catch (Exception e) {
                    LOGGER.warn("Failed to parse retry-after response body", e);
                }
            }

            return lastResponse;
        } catch (UnknownHostException e) {
            LOGGER.error("DNS resolution failed: {}", e.getMessage());
            task.handleResponse(e);
            return null;
        } catch (IOException e) {
            if (retryOnTimeout && !retried && isRetry(e))
                return execute(task, true, handleOnRatelimit);
            LOGGER.error("There was an I/O error while executing a REST request: {}", e.getMessage());
            task.handleResponse(e);
            return null;
        } catch (Exception e) {
            LOGGER.error("There was an unexpected error while executing a REST request", e);
            task.handleResponse(e);
            return null;
        } finally {
            for (Response r : responses) {
                if (r == null)
                    break;
                r.close();
            }
        }
    }

    private void applyBody(RestRequest<?> apiRequest, Request.Builder builder) {
        String method = apiRequest.getRoute().getMethod();
        RequestBody body = apiRequest.getBody();

        if (body == null && HttpMethod.requiresRequestBody(method))
            body = EMPTY_BODY;

        builder.method(method, body);
    }

    private void applyHeaders(RestRequest<?> apiRequest, Request.Builder builder) {
        builder.header("user-agent", userAgent)
            .header("accept-encoding", "gzip")
            .header("authorization", authConfig.getToken())
            .header("x-ratelimit-precision", "millisecond"); // still sending this in case of regressions

        // Apply custom headers like X-Audit-Log-Reason
        // If customHeaders is null this does nothing
        if (apiRequest.getHeaders() != null) {
            apiRequest.getHeaders().forEach(builder::header);
        }
    }

    public OkHttpClient getHttpClient() {
        return httpClient;
    }

    public RestRateLimiter getRateLimiter() {
        return rateLimiter;
    }

    public void setRetryOnTimeout(boolean retryOnTimeout) {
        this.retryOnTimeout = retryOnTimeout;
    }

    public void stop(boolean shutdown, Runnable callback) {
        rateLimiter.stop(shutdown, callback);
    }

    private long parseRetry(Response response) {
        //noinspection ConstantConditions
        return (long) (Double.parseDouble(response.header(RestRateLimiter.RETRY_AFTER_HEADER, "0")) * 1000);
    }

    private class WorkTask implements RestRateLimiter.Work {
        private final RestRequest<?> request;
        private boolean done;

        private WorkTask(RestRequest<?> request) {
            this.request = request;
        }

        @Nonnull
        @Override
        public Route.CompiledRoute getRoute() {
            return request.getRoute();
        }

        @Nonnull
        @Override
        public RestApi getApi() {
            return request.getRestAction().getApi();
        }

        @Override
        public Response execute() {
            return Requester.this.execute(this);
        }

        @Override
        public boolean isSkipped() {
            return request.isSkipped();
        }

        @Override
        public boolean isDone() {
            return isSkipped() || done;
        }

        @Override
        public boolean isPriority() {
            return request.isPriority();
        }

        @Override
        public boolean isCancelled() {
            return request.isCancelled();
        }

        @Override
        public void cancel() {
            request.cancel();
        }

        private void handleResponse(Response response) {
            handleResponse(new RestResponse(response, -1));
        }

        private void handleResponse(Exception error) {
            handleResponse(new RestResponse(error));
        }

        private void handleResponse(Response response, long retryAfter) {
            handleResponse(new RestResponse(response, retryAfter));
        }

        private void handleResponse(RestResponse response) {
            done = true;
            request.handleResponse(response);
        }
    }
}
