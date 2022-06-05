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

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.concurrent.ConcurrentMap;

public class MetaConfig {
    private static final MetaConfig defaultConfig = new MetaConfig(2048, null);
    //    private final ConcurrentMap<String, String> mdcContextMap;
//    private final boolean enableMDC;
//    private final boolean useShutdownHook;
    private final int maxBufferSize;

    public MetaConfig(
        int maxBufferSize,
        @Nullable ConcurrentMap<String, String> mdcContextMap) {
        this.maxBufferSize = maxBufferSize;
//        if (enableMDC)
//            this.mdcContextMap = mdcContextMap == null ? new ConcurrentHashMap<>() : mdcContextMap;
//        else
//            this.mdcContextMap = null;
    }

//    @Nullable
//    public ConcurrentMap<String, String> getMdcContextMap()
//    {
//        return mdcContextMap;
//    }
//
//
//    public boolean isEnableMDC()
//    {
//        return enableMDC;
//    }
//
//    public boolean isUseShutdownHook()
//    {
//        return useShutdownHook;
//    }

    @Nonnull
    public static MetaConfig getDefault() {
        return defaultConfig;
    }

    public int getMaxBufferSize() {
        return maxBufferSize;
    }
}