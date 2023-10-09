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

import net.vpg.rawf.api.RestApi;
import net.vpg.rawf.api.exceptions.ContextException;
import net.vpg.rawf.api.exceptions.RateLimitedException;
import net.vpg.rawf.internal.requests.CallbackContext;
import net.vpg.rawf.internal.requests.RestActionImpl;
import okhttp3.RequestBody;
import org.apache.commons.collections4.map.CaseInsensitiveMap;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.Map;
import java.util.concurrent.CancellationException;
import java.util.concurrent.TimeoutException;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

public class RestRequest<T> {
    private final RestActionImpl<T> restAction;
    private final Consumer<? super T> onSuccess;
    private final Consumer<? super Throwable> onFailure;
    private final BooleanSupplier checks;
    private final boolean shouldQueue;
    private final Route.CompiledRoute route;
    private final RequestBody body;
    private final Object rawBody;
    private final CaseInsensitiveMap<String, String> headers;
    private final long deadline;
    private final boolean priority;
    private final RestApi api;

    private boolean done = false;
    private boolean isCancelled = false;

    public RestRequest(
        RestActionImpl<T> restAction, Consumer<? super T> onSuccess, Consumer<? super Throwable> onFailure,
        BooleanSupplier checks, boolean shouldQueue, RequestBody body, Object rawBody, long deadline, boolean priority,
        Route.CompiledRoute route, CaseInsensitiveMap<String, String> headers) {
        this.deadline = deadline;
        this.priority = priority;
        this.restAction = restAction;
        this.onSuccess = onSuccess;
        this.onFailure = ContextException.wrapIfApplicable(onFailure);
        this.checks = checks;
        this.shouldQueue = shouldQueue;
        this.body = body;
        this.rawBody = rawBody;
        this.route = route;
        this.headers = headers;

        this.api = restAction.getApi();
    }

    public void onSuccess(T successObj) {
        if (done)
            return;
        done = true;
        api.getThreadingConfig().getCallbackPool().execute(() -> {
            try (CallbackContext ___ = CallbackContext.getInstance()) {
                onSuccess.accept(successObj);
            } catch (Throwable t) {
                RestActionImpl.LOG.error("Encountered error while processing success consumer", t);
                if (t instanceof Error) {
//                    api.handleEvent(new ExceptionEvent(api, t, true));
                    throw (Error) t;
                }
            }
        });
    }

    public void onFailure(RestResponse response) {
        if (response.code == 429) {
            onFailure(new RateLimitedException(route, response.retryAfter));
        }
    }

    public void onFailure(Throwable failException) {
        if (done)
            return;
        done = true;
        api.getThreadingConfig().getCallbackPool().execute(() -> {
            try (CallbackContext ___ = CallbackContext.getInstance()) {
                onFailure.accept(failException);
            } catch (Throwable t) {
                RestActionImpl.LOG.error("Encountered error while processing failure consumer", t);
                if (t instanceof Error) {
//                    api.handleEvent(new ExceptionEvent(api, t, true));
                    throw (Error) t;
                }
            }
        });
    }

    public void onCancelled() {
        onFailure(new CancellationException("RestAction has been cancelled"));
    }

    public void onTimeout() {
        onFailure(new TimeoutException("RestAction has timed out"));
    }

    @Nonnull
    public RestAction<T> getRestAction() {
        return restAction;
    }

    @Nonnull
    public Consumer<? super T> getOnSuccess() {
        return onSuccess;
    }

    @Nonnull
    public Consumer<? super Throwable> getOnFailure() {
        return onFailure;
    }

    public boolean isPriority() {
        return priority;
    }

    public boolean isSkipped() {
        if (isTimeout()) {
            onTimeout();
            return true;
        }
        boolean skip = runChecks();
        if (skip)
            onCancelled();
        return skip;
    }

    private boolean isTimeout() {
        return deadline > 0 && deadline < System.currentTimeMillis();
    }

    private boolean runChecks() {
        try {
            return isCancelled() || checks != null && !checks.getAsBoolean();
        } catch (Exception e) {
            onFailure(e);
            return true;
        }
    }

    @Nullable
    public Map<String, String> getHeaders() {
        return headers;
    }

    @Nonnull
    public Route.CompiledRoute getRoute() {
        return route;
    }

    @Nullable
    public RequestBody getBody() {
        return body;
    }

    @Nullable
    public Object getRawBody() {
        return rawBody;
    }

    public boolean shouldQueue() {
        return shouldQueue;
    }

    public void cancel() {
        if (!isCancelled) {
            onCancelled();
            isCancelled = true;
        }
    }

    public boolean isCancelled() {
        return isCancelled;
    }

    public void handleResponse(@Nonnull RestResponse response) {
        restAction.handleResponse(response, this);
//        api.handleEvent(new HttpRequestEvent(this, response));
    }
}
