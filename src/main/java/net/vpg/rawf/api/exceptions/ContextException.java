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
package net.vpg.rawf.api.exceptions;

import net.vpg.rawf.api.requests.RestAction;
import net.vpg.rawf.api.utils.MiscUtil;
import net.vpg.rawf.internal.utils.Checks;
import net.vpg.rawf.internal.utils.Helpers;
import org.jetbrains.annotations.Contract;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import javax.annotation.ParametersAreNonnullByDefault;
import java.util.function.Consumer;

/**
 * Used to pass a context to async exception handling for debugging purposes.
 */
@ParametersAreNonnullByDefault
public class ContextException extends Exception {
    /**
     * Creates a failure consumer that appends a context cause
     * before printing the stack trace using {@link Throwable#printStackTrace()}.
     * <br>Equivalent to {@code here(Throwable::printStackTrace)}
     *
     * @return Wrapping failure consumer around {@code Throwable::printStackTrace}
     */
    @Contract(" -> new")
    @Nonnull
    public static Consumer<Throwable> herePrintingTrace() {
        return here(Throwable::printStackTrace);
    }

    /**
     * Creates a wrapping {@link Consumer} for the provided target.
     *
     * @param acceptor The end-target for the throwable
     * @return Wrapper of the provided consumer that will append a context with the current stack-trace
     */
    @Contract("_ -> new")
    @Nonnull
    public static Consumer<Throwable> here(Consumer<? super Throwable> acceptor) {
        return new ContextConsumer(new ContextException(), acceptor);
    }

    /**
     * Creates a wrapping {@link Consumer} for the provided target.
     *
     * @param acceptor The end-target for the throwable
     * @return Wrapper of the provided consumer that will append a context with the current stack-trace
     */
    @Nullable
    public static Consumer<? super Throwable> wrapIfApplicable(@Nullable Consumer<? super Throwable> acceptor) {
        if (acceptor instanceof ContextException.ContextConsumer)
            return acceptor;
        else if (RestAction.isPassContext())
            return here(MiscUtil.getRestActionFailure(acceptor));
        return acceptor;
    }

    public static class ContextConsumer implements Consumer<Throwable> {
        private final ContextException context;
        private final Consumer<? super Throwable> callback;

        public ContextConsumer(ContextException context, Consumer<? super Throwable> callback) {
            Checks.notNull(context, "Context");
            Checks.notNull(callback, "Callback");

            this.context = context;
            this.callback = callback;
        }

        @Override
        public void accept(Throwable throwable) {
            callback.accept(Helpers.appendCause(throwable, context));
        }
    }
}
