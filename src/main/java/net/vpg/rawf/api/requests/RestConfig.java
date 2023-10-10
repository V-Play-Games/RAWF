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

package net.vpg.rawf.api.requests;

import net.vpg.rawf.internal.utils.Checks;
import okhttp3.OkHttpClient;
import okhttp3.Request;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * Configuration for REST-request handling.
 *
 * <p>This can be used to replace the {@link #setRateLimiterFactory(Function) rate-limit handling}
 * or to use a different {@link #setBaseUrl(String) base url} for requests, e.g. for mocked HTTP responses or proxies.
 */
public class RestConfig {
    private OkHttpClient httpClient;
    private String userAgent;
    private String baseUrl;
    private boolean relativeRateLimit = true;
    private Consumer<? super Request.Builder> customBuilder;
    private Function<? super RestRateLimiter.RateLimitConfig, ? extends RestRateLimiter> rateLimiter = SequentialRestRateLimiter::new;

    @Nonnull
    public OkHttpClient getHttpClient() {
        return httpClient;
    }

    public RestConfig setHttpClient(OkHttpClient httpClient) {
        this.httpClient = httpClient;
        return this;
    }

    /**
     * The adapted user-agent with the custom {@link #setUserAgent(String) suffix}.
     *
     * @return The user-agent
     */
    @Nonnull
    public String getUserAgent() {
        return userAgent;
    }

    /**
     * Provide a custom User-Agent.
     *
     * @param userAgent The new User-Agent
     * @return The current RestConfig for chaining convenience
     */
    @Nonnull
    public RestConfig setUserAgent(@Nullable String userAgent) {
        Checks.notBlank(userAgent, "User Agent");
        this.userAgent = userAgent;
        return this;
    }

    /**
     * The configured base-url for REST-api requests.
     *
     * @return The base-url
     */
    @Nonnull
    public String getBaseUrl() {
        return baseUrl;
    }

    /**
     * Provide a custom base URL for REST-api requests.
     *
     * @param baseUrl The new base url
     * @return The current RestConfig for chaining convenience
     * @throws IllegalArgumentException If the provided base url is null, empty, or not an HTTP(s) url
     */
    @Nonnull
    public RestConfig setBaseUrl(@Nonnull String baseUrl) {
        Checks.notEmpty(baseUrl, "URL");
        Checks.check(baseUrl.length() > 4 && baseUrl.substring(0, 4).equalsIgnoreCase("http"), "URL must be HTTP");
        this.baseUrl = baseUrl + (baseUrl.endsWith("/") ? "" : "/");
        return this;
    }

    /**
     * The configured rate-limiter implementation.
     *
     * @return The rate-limiter
     */
    @Nonnull
    public Function<? super RestRateLimiter.RateLimitConfig, ? extends RestRateLimiter> getRateLimiterFactory() {
        return rateLimiter;
    }

    /**
     * Provide a custom implementation of {@link RestRateLimiter}.
     * <br>By default, this will use the {@link SequentialRestRateLimiter}.
     *
     * @param rateLimiter The new implementation
     * @return The current RestConfig for chaining convenience
     * @throws IllegalArgumentException If the provided rate-limiter is null
     */
    @Nonnull
    public RestConfig setRateLimiterFactory(@Nonnull Function<? super RestRateLimiter.RateLimitConfig, ? extends RestRateLimiter> rateLimiter) {
        Checks.notNull(rateLimiter, "RateLimiter");
        this.rateLimiter = rateLimiter;
        return this;
    }

    /**
     * The custom request interceptor.
     *
     * @return The custom interceptor, or null if none is configured
     */
    @Nullable
    public Consumer<? super Request.Builder> getCustomBuilder() {
        return customBuilder;
    }

    /**
     * Provide an interceptor to update outgoing requests with custom headers or other modifications.
     * <br>Be careful not to replace any important headers, like authorization or content-type.
     * This is allowed by JDA, to allow proper use of {@link #setBaseUrl(String)} with any exotic proxy.
     *
     * <p><b>Example</b>
     * <pre>{@code
     * setCustomBuilder((request) -> {
     *     request.header("X-My-Header", "MyValue");
     * })
     * }</pre>
     *
     * @param customBuilder The request interceptor, or null to disable
     * @return The current RestConfig for chaining convenience
     */
    @Nonnull
    public RestConfig setCustomBuilder(@Nullable Consumer<? super Request.Builder> customBuilder) {
        this.customBuilder = customBuilder;
        return this;
    }

    /**
     * Whether to use {@code X-RateLimit-Reset-After} to determine the rate-limit backoff.
     * <br>If this is disabled, the default {@link RestRateLimiter} will use the {@code X-RateLimit-Reset} header timestamp to compute the relative backoff.
     *
     * @return True, if relative reset after is enabled
     */
    public boolean isRelativeRateLimit() {
        return relativeRateLimit;
    }

    /**
     * Whether to use {@code X-RateLimit-Reset-After} to determine the rate-limit backoff.
     * <br>If this is disabled, the default {@link RestRateLimiter} will use the {@code X-RateLimit-Reset} header timestamp to compute the relative backoff.
     *
     * @param relativeRateLimit True, to use relative reset after
     * @return The current RestConfig for chaining convenience
     */
    @Nonnull
    public RestConfig setRelativeRateLimit(boolean relativeRateLimit) {
        this.relativeRateLimit = relativeRateLimit;
        return this;
    }
}
