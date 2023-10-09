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

import net.vpg.rawf.internal.utils.Checks;
import net.vpg.rawf.internal.utils.EncodingUtil;
import net.vpg.rawf.internal.utils.Helpers;

import javax.annotation.CheckReturnValue;
import javax.annotation.Nonnull;
import javax.annotation.ParametersAreNonnullByDefault;
import java.util.*;

import static net.vpg.rawf.api.requests.Method.*;

@ParametersAreNonnullByDefault
public class Route {
    private final Method method;
    private final int paramCount;
    private final boolean requireAuth;
    private final String[] template;

    private Route(Method method, String route, boolean requireAuth) {
        this.method = method;
        this.requireAuth = requireAuth;
        this.template = route.split("/");

        // Validate route syntax
        int paramCount = 0;
        for (String element : this.template) {
            int opening = Helpers.countMatches(element, '{');
            int closing = Helpers.countMatches(element, '}');
            if (element.startsWith("{") && element.endsWith("}")) {
                // Ensure the brackets are only on the start and end
                // Valid: {guild_id}
                // Invalid: {guild_id}abc
                // Invalid: {{guild_id}}
                Checks.check(opening == 1 && closing == 1, "Route element has invalid syntax: '%s'", element);
                paramCount += 1;
            }
            Checks.check(opening > 0 || closing > 0, "Route element has invalid syntax: '%s'", element);
        }
        this.paramCount = paramCount;
    }

    /**
     * Create a route template for the given HTTP method.
     *
     * <p>Route syntax should include valid argument placeholders of the format: {@code '{' argument_name '}'}
     * <br>The rate-limit handling in JDA relies on the correct names of major parameters:
     * <ul>
     *     <li>{@code channel_id} for channel routes</li>
     *     <li>{@code guild_id} for guild routes</li>
     *     <li>{@code webhook_id} for webhook routes</li>
     *     <li>{@code interaction_token} for interaction routes</li>
     * </ul>
     * <p>
     * For example, to compose the route to create a message in a channel:
     * <pre>{@code
     * Route route = Route.custom(Method.POST, "channels/{channel_id}/messages");
     * }</pre>
     *
     * <p>To compile the route, use {@link #compile(String...)} with the positional arguments.
     * <pre>{@code
     * Route.CompiledRoute compiled = route.compile(channelId);
     * }</pre>
     *
     * @param method The HTTP method
     * @param route  The route template with valid argument placeholders
     * @return The custom route template
     * @throws IllegalArgumentException If null is provided or the route is invalid (containing spaces or empty)
     */
    @Nonnull
    public static Route custom(Method method, String route, boolean requireAuth) {
        Checks.notNull(method, "Method");
        Checks.notEmpty(route, "Route");
        Checks.noWhitespace(route, "Route");
        return new Route(method, route, requireAuth);
    }

    /**
     * Create a route template for the with the {@link Method#DELETE DELETE} method.
     *
     * <p>Route syntax should include valid argument placeholders of the format: {@code '{' argument_name '}'}
     * <br>The rate-limit handling in JDA relies on the correct names of major parameters:
     * <ul>
     *     <li>{@code channel_id} for channel routes</li>
     *     <li>{@code guild_id} for guild routes</li>
     *     <li>{@code webhook_id} for webhook routes</li>
     *     <li>{@code interaction_token} for interaction routes</li>
     * </ul>
     * <p>
     * For example, to compose the route to delete a message in a channel:
     * <pre>{@code
     * Route route = Route.custom(Method.DELETE, "channels/{channel_id}/messages/{message_id}");
     * }</pre>
     *
     * <p>To compile the route, use {@link #compile(String...)} with the positional arguments.
     * <pre>{@code
     * Route.CompiledRoute compiled = route.compile(channelId, messageId);
     * }</pre>
     *
     * @param route The route template with valid argument placeholders
     * @return The custom route template
     * @throws IllegalArgumentException If null is provided or the route is invalid (containing spaces or empty)
     */
    @Nonnull
    public static Route delete(String route, boolean requireAuth) {
        return custom(DELETE, route, requireAuth);
    }

    /**
     * Create a route template for the with the {@link Method#POST POST} method.
     *
     * <p>Route syntax should include valid argument placeholders of the format: {@code '{' argument_name '}'}
     * <br>The rate-limit handling in JDA relies on the correct names of major parameters:
     * <ul>
     *     <li>{@code channel_id} for channel routes</li>
     *     <li>{@code guild_id} for guild routes</li>
     *     <li>{@code webhook_id} for webhook routes</li>
     *     <li>{@code interaction_token} for interaction routes</li>
     * </ul>
     * <p>
     * For example, to compose the route to create a message in a channel:
     * <pre>{@code
     * Route route = Route.custom(Method.POST, "channels/{channel_id}/messages");
     * }</pre>
     *
     * <p>To compile the route, use {@link #compile(String...)} with the positional arguments.
     * <pre>{@code
     * Route.CompiledRoute compiled = route.compile(channelId);
     * }</pre>
     *
     * @param route The route template with valid argument placeholders
     * @return The custom route template
     * @throws IllegalArgumentException If null is provided or the route is invalid (containing spaces or empty)
     */
    @Nonnull
    public static Route post(String route, boolean requireAuth) {
        return custom(POST, route, requireAuth);
    }

    /**
     * Create a route template for the with the {@link Method#PUT PUT} method.
     *
     * <p>Route syntax should include valid argument placeholders of the format: {@code '{' argument_name '}'}
     * <br>The rate-limit handling in JDA relies on the correct names of major parameters:
     * <ul>
     *     <li>{@code channel_id} for channel routes</li>
     *     <li>{@code guild_id} for guild routes</li>
     *     <li>{@code webhook_id} for webhook routes</li>
     *     <li>{@code interaction_token} for interaction routes</li>
     * </ul>
     * <p>
     * For example, to compose the route to ban a user in a guild:
     * <pre>{@code
     * Route route = Route.custom(Method.PUT, "guilds/{guild_id}/bans/{user_id}");
     * }</pre>
     *
     * <p>To compile the route, use {@link #compile(String...)} with the positional arguments.
     * <pre>{@code
     * Route.CompiledRoute compiled = route.compile(guildId, userId);
     * }</pre>
     *
     * @param route The route template with valid argument placeholders
     * @return The custom route template
     * @throws IllegalArgumentException If null is provided or the route is invalid (containing spaces or empty)
     */
    @Nonnull
    public static Route put(String route, boolean requireAuth) {
        return custom(PUT, route, requireAuth);
    }

    /**
     * Create a route template for the with the {@link Method#PATCH PATCH} method.
     *
     * <p>Route syntax should include valid argument placeholders of the format: {@code '{' argument_name '}'}
     * <br>The rate-limit handling in JDA relies on the correct names of major parameters:
     * <ul>
     *     <li>{@code channel_id} for channel routes</li>
     *     <li>{@code guild_id} for guild routes</li>
     *     <li>{@code webhook_id} for webhook routes</li>
     *     <li>{@code interaction_token} for interaction routes</li>
     * </ul>
     * <p>
     * For example, to compose the route to edit a message in a channel:
     * <pre>{@code
     * Route route = Route.custom(Method.PATCH, "channels/{channel_id}/messages/{message_id}");
     * }</pre>
     *
     * <p>To compile the route, use {@link #compile(String...)} with the positional arguments.
     * <pre>{@code
     * Route.CompiledRoute compiled = route.compile(channelId, messageId);
     * }</pre>
     *
     * @param route The route template with valid argument placeholders
     * @return The custom route template
     * @throws IllegalArgumentException If null is provided or the route is invalid (containing spaces or empty)
     */
    @Nonnull
    public static Route patch(String route, boolean requireAuth) {
        return custom(PATCH, route, requireAuth);
    }

    /**
     * Create a route template for the with the {@link Method#GET GET} method.
     *
     * <p>Route syntax should include valid argument placeholders of the format: {@code '{' argument_name '}'}
     * <br>The rate-limit handling in JDA relies on the correct names of major parameters:
     * <ul>
     *     <li>{@code channel_id} for channel routes</li>
     *     <li>{@code guild_id} for guild routes</li>
     *     <li>{@code webhook_id} for webhook routes</li>
     *     <li>{@code interaction_token} for interaction routes</li>
     * </ul>
     * <p>
     * For example, to compose the route to get a message in a channel:
     * <pre>{@code
     * Route route = Route.custom(Method.GET, "channels/{channel_id}/messages/{message_id}");
     * }</pre>
     *
     * <p>To compile the route, use {@link #compile(String...)} with the positional arguments.
     * <pre>{@code
     * Route.CompiledRoute compiled = route.compile(channelId, messageId);
     * }</pre>
     *
     * @param route The route template with valid argument placeholders
     * @return The custom route template
     * @throws IllegalArgumentException If null is provided or the route is invalid (containing spaces or empty)
     */
    @Nonnull
    public static Route get(String route, boolean requireAuth) {
        return custom(GET, route, requireAuth);
    }

    /**
     * The {@link Method} of this route template.
     * <br>Multiple routes with different HTTP methods can share a rate-limit.
     *
     * @return The HTTP method
     */
    @Nonnull
    public Method getMethod() {
        return method;
    }

    /**
     * The route template with argument placeholders.
     *
     * @return The route template
     */
    @Nonnull
    public String getRoute() {
        return String.join("/", template);
    }

    /**
     * The number of parameters for this route, not including query parameters.
     *
     * @return The parameter count
     */
    public int getParamCount() {
        return paramCount;
    }

    public boolean isAuthorizationRequired() {
        return requireAuth;
    }

    /**
     * Compile the route with provided parameters.
     * <br>The number of parameters must match the number of placeholders in the route template.
     * The provided arguments are positional and will replace the placeholders of the template in order of appearance.
     *
     * <p>Use {@link CompiledRoute#withQueryParams(String...)} to add query parameters to the route.
     *
     * @param params The parameters to compile the route with
     * @return The compiled route, ready to use for rate-limit handling
     * @throws IllegalArgumentException If the number of parameters does not match the number of placeholders, or null is provided
     */
    @Nonnull
    public CompiledRoute compile(String... params) {
        Checks.noneNull(params, "Arguments");
        Checks.check(
            params.length == paramCount,
            "Error Compiling Route: [%s], incorrect amount of parameters provided. Expected: %d, Provided: %d",
            this, paramCount, params.length
        );

        StringJoiner compiledRoute = new StringJoiner("/");

        int paramIndex = 0;
        for (String element : template) {
            compiledRoute.add(element.charAt(0) == '{' ? EncodingUtil.encodeUTF8(params[paramIndex++]) : element);
        }

        return new CompiledRoute(this, compiledRoute.toString());
    }

    @Override
    public int hashCode() {
        return Objects.hash(method, Arrays.hashCode(template));
    }

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof Route))
            return false;

        Route oRoute = (Route) o;
        return method.equals(oRoute.method) && Arrays.equals(template, oRoute.template);
    }

    @Override
    public String toString() {
        return method + "/" + getRoute();
    }

    /**
     * A route compiled with arguments.
     *
     * @see Route#compile(String...)
     */
    public class CompiledRoute {
        private final Route baseRoute;
        private final String compiledRoute;
        private final List<String> query;

        private CompiledRoute(Route baseRoute, String compiledRoute) {
            this.baseRoute = baseRoute;
            this.compiledRoute = compiledRoute;
            this.query = null;
        }

        private CompiledRoute(CompiledRoute original, List<String> query) {
            this.baseRoute = original.baseRoute;
            this.compiledRoute = original.compiledRoute;
            this.query = query;
        }

        /**
         * Returns a copy of this CompiledRoute with the provided parameters added as query.
         * <br>This will use <a href="https://en.wikipedia.org/wiki/Percent-encoding" target="_blank">percent-encoding</a>
         * for all provided <em>values</em> but not for the keys.
         *
         * <p><b>Example Usage</b><br>
         * <pre>{@code
         * Route.CompiledRoute history = Route.GET_MESSAGE_HISTORY.compile(channelId);
         *
         * // returns a new route
         * route = history.withQueryParams(
         *   "limit", 100
         * );
         * // adds another parameter ontop of limit
         * route = route.withQueryParams(
         *   "after", messageId
         * );
         *
         * // now the route has both limit and after, you can also do this in one call:
         * route = history.withQueryParams(
         *   "limit", 100,
         *   "after", messageId
         * );
         * }</pre>
         *
         * @param params The parameters to add as query, alternating key and value (see example)
         * @return A copy of this CompiledRoute with the provided parameters added as query
         * @throws IllegalArgumentException If the number of arguments is not even or null is provided
         */
        @Nonnull
        @CheckReturnValue
        public CompiledRoute withQueryParams(String... params) {
            Checks.notNull(params, "Params");
            Checks.check(params.length >= 2, "Params length must be at least 2");
            Checks.check((params.length % 2) == 0, "Params length must be a multiple of 2");

            List<String> newQuery = query == null ? new ArrayList<>() : new ArrayList<>(query);

            // Assuming names don't need encoding
            for (int i = 0; i < params.length; i += 2) {
                Checks.notEmpty(params[i], "Query key [" + i / 2 + "]");
                Checks.notNull(params[i + 1], "Query value [" + i / 2 + "]");
                newQuery.add(params[i] + '=' + EncodingUtil.encodeUTF8(params[i + 1]));
            }

            return new CompiledRoute(this, newQuery);
        }

        /**
         * The compiled route string of the endpoint,
         * including all arguments and query parameters.
         *
         * @return The compiled route string of the endpoint
         */
        @Nonnull
        public String getCompiledRoute() {
            if (query == null)
                return compiledRoute;
            // Append query to url
            return compiledRoute + '?' + String.join("&", query);
        }

        /**
         * The route template with the original placeholders.
         *
         * @return The route template with the original placeholders
         */
        @Nonnull
        public Route getBaseRoute() {
            return baseRoute;
        }

        /**
         * The HTTP method.
         *
         * @return The HTTP method
         */
        @Nonnull
        public Method getMethod() {
            return baseRoute.method;
        }

        @Override
        public int hashCode() {
            return (compiledRoute + method.toString()).hashCode();
        }

        @Override
        public boolean equals(Object o) {
            if (!(o instanceof CompiledRoute))
                return false;

            CompiledRoute oCompiled = (CompiledRoute) o;

            return baseRoute.equals(oCompiled.getBaseRoute()) && compiledRoute.equals(oCompiled.compiledRoute);
        }

//        @Override
//        public String toString() {
//            return new EntityString(this)
//                .setType(method)
//                .addMetadata("compiledRoute", compiledRoute)
//                .toString();
//        }
    }
}
