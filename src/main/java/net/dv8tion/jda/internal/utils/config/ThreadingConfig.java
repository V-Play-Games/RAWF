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
package net.dv8tion.jda.internal.utils.config;

import net.dv8tion.jda.internal.utils.concurrent.CountingThreadFactory;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.concurrent.*;
import java.util.function.Supplier;

public class ThreadingConfig {
    private ScheduledExecutorService rateLimitPool;
    private ExecutorService callbackPool;

    private boolean shutdownRateLimitPool;
    private boolean shutdownCallbackPool;

    public ThreadingConfig() {
        this.callbackPool = ForkJoinPool.commonPool();

        this.shutdownRateLimitPool = true;
        this.shutdownCallbackPool = false;
    }

    @Nonnull
    public static ScheduledThreadPoolExecutor newScheduler(int coreSize, Supplier<String> identifier, String baseName) {
        return newScheduler(coreSize, identifier, baseName, true);
    }

    @Nonnull
    public static ScheduledThreadPoolExecutor newScheduler(int coreSize, Supplier<String> identifier, String baseName, boolean daemon) {
        return new ScheduledThreadPoolExecutor(coreSize, new CountingThreadFactory(identifier, baseName, daemon));
    }

    @Nonnull
    public static ThreadingConfig getDefault() {
        return new ThreadingConfig();
    }

    public void setRateLimitPool(@Nullable ScheduledExecutorService executor, boolean shutdown) {
        this.rateLimitPool = executor;
        this.shutdownRateLimitPool = shutdown;
    }

    public void setCallbackPool(@Nullable ExecutorService executor, boolean shutdown) {
        this.callbackPool = executor == null ? ForkJoinPool.commonPool() : executor;
        this.shutdownCallbackPool = shutdown;
    }

    public void init(@Nonnull Supplier<String> identifier) {
        if (this.rateLimitPool == null)
            this.rateLimitPool = newScheduler(5, identifier, "RateLimit", false);
    }

    public void shutdown() {
        if (shutdownCallbackPool)
            callbackPool.shutdown();
        if (shutdownRateLimitPool) {
            if (rateLimitPool instanceof ScheduledThreadPoolExecutor) {
                ScheduledThreadPoolExecutor executor = (ScheduledThreadPoolExecutor) rateLimitPool;
                executor.setKeepAliveTime(5L, TimeUnit.SECONDS);
                executor.allowCoreThreadTimeOut(true);
            } else {
                rateLimitPool.shutdown();
            }
        }
    }

    public void shutdownRequester() {
        if (shutdownRateLimitPool)
            rateLimitPool.shutdown();
    }

    public void shutdownNow() {
        if (shutdownCallbackPool)
            callbackPool.shutdownNow();
        if (shutdownRateLimitPool)
            rateLimitPool.shutdownNow();
    }

    @Nonnull
    public ScheduledExecutorService getRateLimitPool() {
        return rateLimitPool;
    }

    @Nonnull
    public ExecutorService getCallbackPool() {
        return callbackPool;
    }

    public boolean isShutdownRateLimitPool() {
        return shutdownRateLimitPool;
    }

    public boolean isShutdownCallbackPool() {
        return shutdownCallbackPool;
    }
}
