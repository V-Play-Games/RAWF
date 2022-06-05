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

import net.vpg.rawf.internal.utils.Checks;
import net.vpg.rawf.internal.utils.Helpers;

import javax.annotation.CheckReturnValue;
import javax.annotation.Nonnull;
import javax.annotation.ParametersAreNonnullByDefault;
import java.util.HashSet;
import java.util.Set;

import static net.vpg.rawf.internal.requests.Method.*;

@ParametersAreNonnullByDefault
public class Route {
    private final String route;
    private final Method method;
    private final int paramCount;

    private Route(Method method, String route) {
        this.method = method;
        this.route = route;
        this.paramCount = Helpers.countMatches(route, '{'); //All parameters start with {

        if (paramCount != Helpers.countMatches(route, '}'))
            throw new IllegalArgumentException("An argument does not have both {}'s for route: " + method + " " + route);
    }

    @Nonnull
    public static Route custom(Method method, String route) {
        Checks.notNull(method, "Method");
        Checks.notEmpty(route, "Route");
        Checks.noWhitespace(route, "Route");
        return new Route(method, route);
    }

    @Nonnull
    public static Route delete(String route) {
        return custom(DELETE, route);
    }

    @Nonnull
    public static Route post(String route) {
        return custom(POST, route);
    }

    @Nonnull
    public static Route put(String route) {
        return custom(PUT, route);
    }

    @Nonnull
    public static Route patch(String route) {
        return custom(PATCH, route);
    }

    @Nonnull
    public static Route get(String route) {
        return custom(GET, route);
    }

    public Method getMethod() {
        return method;
    }

    public String getRoute() {
        return route;
    }

    public int getParamCount() {
        return paramCount;
    }

    public CompiledRoute compile(String... params) {
        if (params.length != paramCount) {
            throw new IllegalArgumentException(Helpers.format("Error Compiling Route: [%s], incorrect amount of parameters provided. Expected: %d, Provided: %d", route, paramCount, params.length));
        }

        // Compile the route for interfacing with discord.
        Set<String> paramSet = new HashSet<>();
        StringBuilder compiledRoute = new StringBuilder(route);
        for (int i = 0; i < paramCount; i++) {
            int paramStart = compiledRoute.indexOf("{");
            int paramEnd = compiledRoute.indexOf("}");
            String paramName = compiledRoute.substring(paramStart + 1, paramEnd);
            paramSet.add(paramName + "=" + params[i]);

            compiledRoute.replace(paramStart, paramEnd + 1, params[i]);
        }

        return new CompiledRoute(this, compiledRoute.toString(), paramSet.isEmpty() ? "N/A" : String.join(",", paramSet));
    }

    @Override
    public int hashCode() {
        return toString().hashCode();
    }

    @Override
    public boolean equals(Object o) {
        if (o == this)
            return true;
        if (!(o instanceof Route))
            return false;
        Route other = (Route) o;
        return method.equals(other.method) && route.equals(other.route);
    }

    @Override
    public String toString() {
        return method + "/" + route;
    }

    public class CompiledRoute {
        private final Route baseRoute;
        private final String params;
        private final String compiledRoute;
        private final boolean hasQueryParams;

        private CompiledRoute(Route baseRoute, String compiledRoute, String params, boolean hasQueryParams) {
            this.baseRoute = baseRoute;
            this.compiledRoute = compiledRoute;
            this.params = params;
            this.hasQueryParams = hasQueryParams;
        }

        private CompiledRoute(Route baseRoute, String compiledRoute, String params) {
            this(baseRoute, compiledRoute, params, false);
        }

        @Nonnull
        @CheckReturnValue
        public CompiledRoute withQueryParams(String... params) {
            Checks.check(params.length >= 2, "params length must be at least 2");
            Checks.check(params.length % 2 == 0, "params length must be a multiple of 2");

            StringBuilder newRoute = new StringBuilder(compiledRoute);

            for (int i = 0; i < params.length; i++)
                newRoute.append(!hasQueryParams && i == 0 ? '?' : '&').append(params[i]).append('=').append(params[++i]);

            return new CompiledRoute(baseRoute, newRoute.toString(), this.params, true);
        }

        public String getParams() {
            return params;
        }

        public String getCompiledRoute() {
            return compiledRoute;
        }

        public Route getBaseRoute() {
            return baseRoute;
        }

        public Method getMethod() {
            return baseRoute.method;
        }

        @Override
        public int hashCode() {
            return (compiledRoute + method.toString()).hashCode();
        }

        @Override
        public boolean equals(Object o) {
            if (o == this)
                return true;
            if (!(o instanceof CompiledRoute))
                return false;
            CompiledRoute other = (CompiledRoute) o;
            return baseRoute.equals(other.baseRoute) && compiledRoute.equals(other.compiledRoute);
        }

        @Override
        public String toString() {
            return "CompiledRoute(" + method + ": " + compiledRoute + ")";
        }
    }
}
