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

import net.vpg.rawf.api.exceptions.ErrorResponseException;
import net.vpg.rawf.internal.utils.Checks;

import javax.annotation.Nonnull;
import javax.annotation.ParametersAreNonnullByDefault;
import java.util.Collection;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Predicate;

/**
 * Constants for easy use in {@link ErrorResponseException} and {@link net.vpg.rawf.api.exceptions.ErrorHandler ErrorHandler}.
 *
 * @see RestAction
 * @see net.vpg.rawf.api.exceptions.ErrorHandler
 */
@ParametersAreNonnullByDefault
public interface ErrorResponse {
    ErrorResponse SERVER_ERROR = of(0, "The API encountered an internal server error! Not good!");

    static ErrorResponse of(int code, String meaning) {
        return new DefaultErrorResponse(code, meaning);
    }

    /**
     * Provides a tests whether a given throwable is an {@link ErrorResponseException} with {@link ErrorResponseException#getErrorResponse()} being one of the provided responses.
     * <br>This is very useful in combination with {@link RestAction#onErrorMap(Predicate, Function)} and {@link RestAction#onErrorFlatMap(Predicate, Function)}!
     *
     * @param responses The responses to test for
     * @return {@link Predicate} which returns true, if the error response is equal to this
     */
    @Nonnull
    static Predicate<Throwable> test(ErrorResponse... responses) {
        Checks.noneNull(responses, "ErrorResponse");
        return test(Set.of(responses));
    }

    /**
     * Provides a tests whether a given throwable is an {@link ErrorResponseException} with {@link ErrorResponseException#getErrorResponse()} being one of the provided responses.
     * <br>This is very useful in combination with {@link RestAction#onErrorMap(Predicate, Function)} and {@link RestAction#onErrorFlatMap(Predicate, Function)}!
     *
     * @param responses The responses to test for
     * @return {@link Predicate} which returns true, if the error response is equal to this
     */
    @Nonnull
    static Predicate<Throwable> test(Collection<ErrorResponse> responses) {
        Checks.noneNull(responses, "ErrorResponse");
        return test(Set.copyOf(responses));
    }

    private static Predicate<Throwable> test(Set<ErrorResponse> responses) {
        return error -> error instanceof ErrorResponseException && responses.contains(((ErrorResponseException) error).getErrorResponse());
    }

    int getCode();

    @Nonnull
    String getMeaning();

    /**
     * Tests whether the given throwable is an {@link ErrorResponseException} with {@link ErrorResponseException#getErrorResponse()} equal to this.
     * <br>This is very useful in combination with {@link RestAction#onErrorMap(Predicate, Function)} and {@link RestAction#onErrorFlatMap(Predicate, Function)}!
     *
     * @param throwable The throwable to test
     * @return True, if the error response is equal to this
     */
    default boolean test(Throwable throwable) {
        return throwable instanceof ErrorResponseException && ((ErrorResponseException) throwable).getErrorResponse() == this;
    }

    class DefaultErrorResponse implements ErrorResponse {
        private final int code;
        private final String meaning;

        public DefaultErrorResponse(int code, String meaning) {
            this.code = code;
            this.meaning = meaning;
        }

        @Override
        public int getCode() {
            return code;
        }

        @Nonnull
        @Override
        public String getMeaning() {
            return meaning;
        }
    }
}
