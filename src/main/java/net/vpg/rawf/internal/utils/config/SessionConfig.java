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
package net.vpg.rawf.internal.utils.config;

import okhttp3.OkHttpClient;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

public class SessionConfig {
    private final OkHttpClient httpClient;
    private final int maxReconnectDelay;

    public SessionConfig(@Nullable OkHttpClient httpClient, int maxReconnectDelay) {
        this.httpClient = httpClient;
        this.maxReconnectDelay = maxReconnectDelay;
    }

    @Nonnull
    public static SessionConfig getDefault() {
        return new SessionConfig(new OkHttpClient(), 900);
    }

    public void setAutoReconnect(boolean autoReconnect) {
    }

    @Nullable
    public OkHttpClient getHttpClient() {
        return httpClient;
    }

    public int getMaxReconnectDelay() {
        return maxReconnectDelay;
    }
}
