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

import net.dv8tion.jda.api.requests.RestAction;
import net.dv8tion.jda.api.utils.MiscUtil;
import net.dv8tion.jda.internal.utils.Checks;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.Supplier;

public class DeferredRestAction<T, R extends RestAction<T>> implements RestAction<T> {
    //    private final JDA api;
    private final Class<T> type;
    private final Supplier<T> valueSupplier;
    private final Supplier<R> actionSupplier;

    private String reason;
    private long deadline = -1;
    private BooleanSupplier isAction;
    private BooleanSupplier transitiveChecks;

    public DeferredRestAction(/*JDA api,*/ Supplier<R> actionSupplier) {
        this(null, null, actionSupplier);
    }

    public DeferredRestAction(/*JDA api,*/ Class<T> type,
                                           Supplier<T> valueSupplier,
                                           Supplier<R> actionSupplier) {
//        this.api = api;
        this.type = type;
        this.valueSupplier = valueSupplier;
        this.actionSupplier = actionSupplier;
    }

//    @Nonnull
//    @Override
//    public JDA getJDA()
//    {
//        return api;
//    }

    @Nonnull
    @Override
    public RestAction<T> setCheck(BooleanSupplier checks) {
        this.transitiveChecks = checks;
        return this;
    }

    @Nullable
    @Override
    public BooleanSupplier getCheck() {
        return transitiveChecks;
    }

    @Nonnull
    @Override
    public RestAction<T> timeout(long timeout, @Nonnull TimeUnit unit) {
        Checks.notNull(unit, "TimeUnit");
        return deadline(timeout <= 0 ? 0 : System.currentTimeMillis() + unit.toMillis(timeout));
    }

    @Nonnull
    @Override
    public RestAction<T> deadline(long timestamp) {
        this.deadline = timestamp;
        return this;
    }

    public RestAction<T> setCacheCheck(BooleanSupplier checks) {
        this.isAction = checks;
        return this;
    }

    @Override
    public void queue(Consumer<? super T> success, Consumer<? super Throwable> failure) {
        Consumer<? super T> finalSuccess = MiscUtil.getRestActionSuccess(success);

        if (type == null) {
            BooleanSupplier checks = this.isAction;
            if (checks != null && checks.getAsBoolean())
                getAction().queue(success, failure);
            else
                finalSuccess.accept(null);
            return;
        }

        T value = valueSupplier.get();
        if (value == null) {
            getAction().queue(success, failure);
        } else {
            finalSuccess.accept(value);
        }
    }

    @Nonnull
    @Override
    public CompletableFuture<T> submit(boolean shouldQueue) {
        if (type == null) {
            BooleanSupplier checks = this.isAction;
            if (checks != null && checks.getAsBoolean())
                return getAction().submit(shouldQueue);
            return CompletableFuture.completedFuture(null);
        }
        T value = valueSupplier.get();
        if (value != null)
            return CompletableFuture.completedFuture(value);
        return getAction().submit(shouldQueue);
    }

    @Override
    public T complete(boolean shouldQueue) {
        if (type == null) {
            BooleanSupplier checks = this.isAction;
            if (checks != null && checks.getAsBoolean())
                return getAction().complete(shouldQueue);
            return null;
        }
        T value = valueSupplier.get();
        if (value != null)
            return value;
        return getAction().complete(shouldQueue);
    }

    private R getAction() {
        R action = actionSupplier.get();
        action.setCheck(transitiveChecks);
        if (deadline >= 0)
            action.deadline(deadline);
//        if (action instanceof AuditableRestAction && reason != null)
//            ((AuditableRestAction<?>) action).reason(reason);
        return action;
    }
}
