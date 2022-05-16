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

import com.neovisionaries.ws.client.WebSocketFactory;
import okhttp3.OkHttpClient;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.EnumSet;

public class SessionConfig
{
    private final OkHttpClient httpClient;
    private final WebSocketFactory webSocketFactory;
    private final int largeThreshold;
    private int maxReconnectDelay;

    public SessionConfig(
        @Nullable OkHttpClient httpClient,
        @Nullable WebSocketFactory webSocketFactory, int maxReconnectDelay, int largeThreshold)
    {
        this.httpClient = httpClient;
        this.webSocketFactory = webSocketFactory == null ? newWebSocketFactory() : webSocketFactory;
        this.maxReconnectDelay = maxReconnectDelay;
        this.largeThreshold = largeThreshold;
    }

    private static WebSocketFactory newWebSocketFactory()
    {
        return new WebSocketFactory().setConnectionTimeout(10000);
    }

    public void setAutoReconnect(boolean autoReconnect)
    {
    }

    @Nullable
    public OkHttpClient getHttpClient()
    {
        return httpClient;
    }

    @Nonnull
    public WebSocketFactory getWebSocketFactory()
    {
        return webSocketFactory;
    }

    public int getMaxReconnectDelay()
    {
        return maxReconnectDelay;
    }

    public int getLargeThreshold()
    {
        return largeThreshold;
    }

    @Nonnull
    public static SessionConfig getDefault()
    {
        return new SessionConfig(new OkHttpClient(), null, 900, 250);
    }
}
