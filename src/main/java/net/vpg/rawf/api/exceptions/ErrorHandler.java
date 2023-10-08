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
import net.vpg.rawf.internal.utils.Checks;
import net.vpg.rawf.internal.utils.Helpers;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import javax.annotation.ParametersAreNonnullByDefault;
import java.util.*;
import java.util.function.Consumer;
import java.util.function.Predicate;

/**
 * Utility class to simplify exception handling with {@link RestAction RestActions} and {@link HttpException}.
 *
 * <h2>Example</h2>
 * <pre>{@code
 * // Send message to user and delete it 30 seconds later, handles blocked messages in context channel.
 * public void sendMessage(TextChannel context, User user, String content) {
 *     user.openPrivateChannel()
 *         .flatMap(channel -> channel.sendMessage(content))
 *         .delay(Duration.ofSeconds(30))
 *         .flatMap(Message::delete) // delete after 30 seconds
 *         .queue(null, new ErrorHandler()
 *             .ignore(HttpException.UNKNOWN_MESSAGE) // if delete fails that's fine
 *             .handle(
 *                 HttpException.CANNOT_SEND_TO_USER,  // Fallback handling for blocked messages
 *                 (e) -> context.sendMessage("Failed to send message, you block private messages!").queue()));
 * }
 * }</pre>
 *
 * @see HttpException
 * @see RestAction#queue(Consumer, Consumer)
 * @since 4.2.0
 */
@ParametersAreNonnullByDefault
public class ErrorHandler implements Consumer<Throwable> {
    private final Consumer<? super Throwable> base;
    private final Map<Predicate<? super Throwable>, Consumer<? super Throwable>> cases = new LinkedHashMap<>();

    /**
     * Create an ErrorHandler with {@link RestAction#getDefaultFailure()} as base consumer.
     * <br>If none of the provided ignore/handle cases apply, the base consumer is applied instead.
     */
    public ErrorHandler() {
        this(RestAction.getDefaultFailure());
    }

    /**
     * Create an ErrorHandler with the specified consumer as base consumer.
     * <br>If none of the provided ignore/handle cases apply, the base consumer is applied instead.
     *
     * @param base The base {@link Consumer}
     */
    public ErrorHandler(Consumer<? super Throwable> base) {
        Checks.notNull(base, "Consumer");
        this.base = base;
    }

    /**
     * Ignore the specified set of error responses.
     *
     * <p><b>Example</b><br>
     * <pre>{@code
     * // Creates a message with the provided content and deletes it 30 seconds later
     * public static void selfDestruct(MessageChannel channel, String content) {
     *     channel.sendMessage(content)
     *         .delay(Duration.ofSeconds(30))
     *         .flatMap(Message::delete)
     *         .queue(null, new ErrorHandler().ignore(UNKNOWN_MESSAGE));
     * }
     * }</pre>
     *
     * @param http Error responses to ignore
     * @return This ErrorHandler with the applied ignore cases
     * @throws IllegalArgumentException If provided with null
     */
    @Nonnull
    public ErrorHandler ignore(HttpException... http) {
        Checks.noneNull(http, "HttpException");
        return ignore(Set.of(http));
    }

    /**
     * Ignore the specified set of error responses.
     *
     * <p><b>Example</b><br>
     * <pre>{@code
     * // Creates a message with the provided content and deletes it 30 seconds later
     * public static void selfDestruct(User user, String content) {
     *     user.openPrivateChannel()
     *         .flatMap(channel -> channel.sendMessage(content))
     *         .delay(Duration.ofSeconds(30))
     *         .flatMap(Message::delete)
     *         .queue(null, new ErrorHandler().ignore(EnumSet.of(UNKNOWN_MESSAGE, CANNOT_SEND_TO_USER)));
     * }
     * }</pre>
     *
     * @param http The error responses to ignore
     * @return This ErrorHandler with the applied ignore cases
     * @throws IllegalArgumentException If provided with null
     */
    @Nonnull
    public ErrorHandler ignore(Collection<HttpException> http) {
        return handle(http, Helpers.emptyConsumer());
    }

    /**
     * Ignore exceptions of the specified types.
     *
     * <p><b>Example</b><br>
     * <pre>{@code
     * // Ignore SocketTimeoutException
     * public static void ban(Guild guild, String userId) {
     *     guild.ban(userId).queue(null, new ErrorHandler().ignore(SocketTimeoutException.class);
     * }
     * }</pre>
     *
     * @param classes The classes to ignore
     * @return This ErrorHandler with the applied ignore case
     * @throws IllegalArgumentException If provided with null
     * @see java.net.SocketTimeoutException
     */
    @Nonnull
    public ErrorHandler ignore(Class<?>... classes) {
        Checks.noneNull(classes, "Classes");
        return ignore(it -> Arrays.stream(classes).anyMatch(e -> e.isAssignableFrom(it.getClass())));
    }

    /**
     * Ignore exceptions on specific conditions.
     *
     * <p><b>Example</b><br>
     * <pre>{@code
     * // Ignore all exceptions except for HttpException
     * public static void ban(Guild guild, String userId) {
     *     guild.ban(userId).queue(null, new ErrorHandler().ignore((ex) -> !(ex instanceof HttpException));
     * }
     * }</pre>
     *
     * @param condition The condition to check
     * @return This ErrorHandler with the applied ignore case
     * @throws IllegalArgumentException If provided with null
     * @see HttpException
     */
    @Nonnull
    public ErrorHandler ignore(Predicate<? super Throwable> condition) {
        return handle(condition, Helpers.emptyConsumer());
    }

    /**
     * Handle specific {@link HttpException HttpExceptions}.
     * <br>This will apply the specified handler to use instead of the base consumer if one of the provided HttpExceptions happens.
     *
     * <p><b>Example</b><br>
     * <pre>{@code
     * public static void sendMessage(TextChannel context, User user, String content) {
     *     user.openPrivateChannel()
     *         .flatMap(channel -> channel.sendMessage(content))
     *         .queue(null, new ErrorHandler()
     *             .handle(HttpException.CANNOT_SEND_TO_USER,
     *                 (ex) -> context.sendMessage("Cannot send direct message, please enable direct messages from server members!").queue()));
     * }
     * }</pre>
     *
     * @param response The first {@link HttpException} to match
     * @param handler  The alternative handler
     * @return This ErrorHandler with the applied handler
     * @throws IllegalArgumentException If provided with null
     */
    @Nonnull
    public ErrorHandler handle(HttpException response, Consumer<? super HttpException> handler) {
        Checks.notNull(response, "HttpException");
        return handle(Set.of(response), handler);
    }

    /**
     * Handle specific {@link HttpException HttpExceptions}.
     * <br>This will apply the specified handler to use instead of the base consumer if one of the provided HttpExceptions happens.
     *
     * <p><b>Example</b><br>
     * <pre>{@code
     * public static void sendMessage(TextChannel context, User user, String content) {
     *     user.openPrivateChannel()
     *         .flatMap(channel -> channel.sendMessage(content))
     *         .queue(null, new ErrorHandler()
     *             .handle(EnumSet.of(HttpException.CANNOT_SEND_TO_USER),
     *                 (ex) -> context.sendMessage("Cannot send direct message, please enable direct messages from server members!").queue()));
     * }
     * }</pre>
     *
     * @param http The {@link HttpException HttpExceptions} to match
     * @param handler        The alternative handler
     * @return This ErrorHandler with the applied handler
     * @throws IllegalArgumentException If provided with null
     */
    @Nonnull
    public ErrorHandler handle(Collection<HttpException> http, Consumer<? super HttpException> handler) {
        Checks.notNull(handler, "Handler");
        Checks.noneNull(http, "HttpException");
        return handle(HttpException.class, http::contains, handler);
    }

    /**
     * Handle specific throwable types.
     * <br>This will apply the specified handler if the throwable is of the specified type. The check is done using {@link Class#isInstance(Object)}.
     *
     * <p><b>Example</b><br>
     * <pre>{@code
     * public static void logHttpException(RestAction<?> action) {
     *     action.queue(null, new ErrorHandler()
     *         .handle(HttpException.class,
     *             (ex) -> System.out.println(ex.getHttpException())));
     * }
     * }</pre>
     *
     * @param clazz   The throwable type
     * @param handler The alternative handler
     * @param <T>     The type
     * @return This ErrorHandler with the applied handler
     */
    @Nonnull
    public <T> ErrorHandler handle(Class<T> clazz, Consumer<? super T> handler) {
        Checks.notNull(clazz, "Class");
        Checks.notNull(handler, "Handler");
        return handle(clazz::isInstance, ex -> handler.accept(clazz.cast(ex)));
    }

    /**
     * Handle specific throwable types.
     * <br>This will apply the specified handler if the throwable is of the specified type. The check is done using {@link Class#isInstance(Object)}.
     *
     * <p><b>Example</b><br>
     * <pre>{@code
     * public static void logHttpException(RestAction<?> action) {
     *     action.queue(null, new ErrorHandler()
     *         .handle(HttpException.class,
     *             HttpException::isServerError,
     *             (ex) -> System.out.println(ex.getErrorCode() + ": " + ex.getMeaning())));
     * }
     * }</pre>
     *
     * @param clazz     The throwable type
     * @param condition Additional condition that must apply to use this handler
     * @param handler   The alternative handler
     * @param <T>       The type
     * @return This ErrorHandler with the applied handler
     */
    @Nonnull
    public <T> ErrorHandler handle(Class<T> clazz, Predicate<? super T> condition, Consumer<? super T> handler) {
        Checks.notNull(clazz, "Class");
        Checks.notNull(handler, "Handler");
        return handle(
            it -> clazz.isInstance(it) && condition.test(clazz.cast(it)),
            ex -> handler.accept(clazz.cast(ex)));
    }

    /**
     * Handle specific throwable types.
     * <br>This will apply the specified handler if the throwable is of the specified type. The check is done using {@link Class#isInstance(Object)}.
     *
     * <p><b>Example</b><br>
     * <pre>{@code
     * public static void logHttpException(RestAction<?> action) {
     *     action.queue(null, new ErrorHandler()
     *         .handle(Arrays.asList(Throwable.class),
     *             (ex) -> ex instanceof Error,
     *             (ex) -> ex.printStackTrace()));
     * }
     * }</pre>
     *
     * @param clazz     The throwable types
     * @param condition Additional condition that must apply to use this handler, or null to apply no additional condition
     * @param handler   The alternative handler
     * @return This ErrorHandler with the applied handler
     */
    @Nonnull
    public ErrorHandler handle(Collection<Class<?>> clazz, @Nullable Predicate<? super Throwable> condition, Consumer<? super Throwable> handler) {
        Checks.noneNull(clazz, "Class");
        Checks.notNull(handler, "Handler");
        List<Class<?>> classes = new ArrayList<>(clazz);
        Predicate<? super Throwable> check = (Predicate<Throwable>) it -> classes.stream().anyMatch(c -> c.isInstance(it)) && (condition == null || condition.test(it));
        return handle(check, handler);
    }

    /**
     * Handle specific conditions.
     *
     * <p><b>Example</b><br>
     * <pre>{@code
     * public static void logHttpException(RestAction<?> action) {
     *     action.queue(null, new ErrorHandler()
     *         .handle(
     *             (ex) -> !(ex instanceof HttpException),
     *             Throwable::printStackTrace));
     * }
     * }</pre>
     *
     * @param condition Condition that must apply to use this handler
     * @param handler   The alternative handler
     * @return This ErrorHandler with the applied handler
     */
    @Nonnull
    public ErrorHandler handle(Predicate<? super Throwable> condition, Consumer<? super Throwable> handler) {
        Checks.notNull(condition, "Condition");
        Checks.notNull(handler, "Handler");
        cases.put(condition, handler);
        return this;
    }

    @Override
    public void accept(Throwable t) {
        cases.forEach((condition, callback) -> {
            if (condition.test(t))
                callback.accept(t);
        });

        base.accept(t);
    }
}
