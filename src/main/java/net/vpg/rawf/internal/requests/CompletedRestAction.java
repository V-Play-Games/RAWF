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

import net.vpg.rawf.api.requests.RestAction;
import net.vpg.rawf.api.utils.MiscUtil;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

public class CompletedRestAction<T> implements RestAction<T> {
    //    private final JDA api;
    private final T value;
    private final Throwable error;

    public CompletedRestAction(/*JDA api,*/ T value, Throwable error) {
//        this.api = api;
        this.value = value;
        this.error = error;
    }

    public CompletedRestAction(/*JDA api,*/ T value) {
        this(value, null);
    }

    public CompletedRestAction(/*JDA api,*/ Throwable error) {
        this(null, error);
    }

//    @Nonnull
//    @Override
//    public JDA getJDA()
//    {
//        return api;
//    }

    @Nonnull
    @Override
    public CompletedRestAction<T> setCheck(@Nullable BooleanSupplier checks) {
        return this;
    }

    @Nonnull
    @Override
    public CompletedRestAction<T> timeout(long timeout, @Nonnull TimeUnit unit) {
        return this;
    }

    @Nonnull
    @Override
    public CompletedRestAction<T> deadline(long timestamp) {
        return this;
    }

    @Override
    public void queue(@Nullable Consumer<? super T> success, @Nullable Consumer<? super Throwable> failure) {
        if (error == null) {
            MiscUtil.getRestActionSuccess(success).accept(value);
        } else {
            MiscUtil.getRestActionFailure(failure).accept(error);
        }
    }

    @Override
    public T complete(boolean shouldQueue) {
        if (error != null) {
            if (error instanceof RuntimeException)
                throw (RuntimeException) error;
            if (error instanceof Error)
                throw (Error) error;
            throw new IllegalStateException(error);
        }
        return value;
    }

    @Nonnull
    @Override
    public CompletableFuture<T> submit(boolean shouldQueue) {
        if (error != null)
            return CompletableFuture.failedFuture(error);
        else
            return CompletableFuture.completedFuture(value);
    }
}
