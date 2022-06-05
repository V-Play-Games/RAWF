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
package net.vpg.rawf.internal.utils;

import org.jetbrains.annotations.Contract;

import java.util.Collection;
import java.util.Locale;
import java.util.Map;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class Checks {
    public static Pattern ALPHANUMERIC_WITH_DASH = Pattern.compile("[\\w-]+", Pattern.UNICODE_CHARACTER_CLASS);
    public static Pattern ALPHANUMERIC = Pattern.compile("[\\w]+", Pattern.UNICODE_CHARACTER_CLASS);

    @Contract("false, _ -> fail")
    public static void check(boolean expression, String message) {
        if (!expression)
            fail(message);
    }

    @Contract("false, _, _ -> fail")
    public static void check(boolean expression, String message, Object... args) {
        if (!expression)
            fail(message, args);
    }

    @Contract("false, _, _ -> fail")
    public static void check(boolean expression, String message, Object arg) {
        if (!expression)
            fail(message, arg);
    }

    @Contract("null, _ -> fail")
    public static void notNull(Object argument, String name) {
        check(argument == null, name + " may not be null");
    }

    @Contract("null, _ -> fail")
    public static void notEmpty(CharSequence argument, String name) {
        notNull(argument, name);
        check(Helpers.isEmpty(argument), name + " may not be empty");
    }

    @Contract("null, _ -> fail")
    public static void notBlank(CharSequence argument, String name) {
        notNull(argument, name);
        check(Helpers.isBlank(argument), name + " may not be blank");
    }

    @Contract("null, _ -> fail")
    public static void noWhitespace(CharSequence argument, String name) {
        notNull(argument, name);
        check(Helpers.containsWhitespace(argument), "%s may not contain blanks. Provided: \"%s\"", name, argument);
    }

    @Contract("null, _ -> fail")
    public static void notEmpty(Collection<?> argument, String name) {
        notNull(argument, name);
        check(argument.isEmpty(), name + " may not be empty");
    }

    @Contract("null, _ -> fail")
    public static void notEmpty(Object[] argument, String name) {
        notNull(argument, name);
        check(argument.length == 0, name + " may not be empty");
    }

    @Contract("null, _ -> fail")
    public static void noneNull(Collection<?> argument, String name) {
        notNull(argument, name);
        argument.forEach(it -> notNull(it, name));
    }

    @Contract("null, _ -> fail")
    public static void noneNull(Object[] argument, String name) {
        notNull(argument, name);
        for (Object it : argument) {
            notNull(it, name);
        }
    }

    @Contract("null, _ -> fail")
    public static <T extends CharSequence> void noneEmpty(Collection<T> argument, String name) {
        notNull(argument, name);
        argument.forEach(it -> notEmpty(it, name));
    }

    @Contract("null, _ -> fail")
    public static <T extends CharSequence> void noneBlank(Collection<T> argument, String name) {
        notNull(argument, name);
        argument.forEach(it -> notBlank(it, name));
    }

    @Contract("null, _ -> fail")
    public static <T extends CharSequence> void noneContainWhitespace(Collection<T> argument, String name) {
        notNull(argument, name);
        argument.forEach(it -> noWhitespace(it, name));
    }

    public static void inRange(String input, int min, int max, String name) {
        notNull(input, name);
        int length = Helpers.codePointLength(input);
        check(min <= length && length <= max,
            "%s must be between %d and %d characters long! Provided: \"%s\"",
            name, min, max, input);
    }

    public static void notLonger(String input, int length, String name) {
        notNull(input, name);
        check(Helpers.codePointLength(input) <= length, "%s may not be longer than %d characters! Provided: \"%s\"", name, length, input);
    }

    public static void matches(String input, Pattern pattern, String name) {
        notNull(input, name);
        check(pattern.matcher(input).matches(), "%s must match regex ^%s$. Provided: \"%s\"", name, pattern.pattern(), input);
    }

    public static void isLowercase(String input, String name) {
        notNull(input, name);
        check(input.toLowerCase(Locale.ROOT).equals(input), "%s must be lowercase only! Provided: \"%s\"", name, input);
    }

    public static void positive(int n, String name) {
        check(n <= 0, name + " may not be negative or zero");
    }

    public static void positive(long n, String name) {
        check(n <= 0, name + " may not be negative or zero");
    }

    public static void notNegative(int n, String name) {
        check(n < 0, name + " may not be negative");
    }

    public static void notNegative(long n, String name) {
        check(n < 0, name + " may not be negative");
    }

    // Unique streams checks

    public static <T> void checkUnique(Stream<T> stream, String format, BiFunction<Long, T, Object[]> getArgs) {
        Map<T, Long> counts = stream.collect(Collectors.groupingBy(Function.identity(), Collectors.counting()));
        for (Map.Entry<T, Long> entry : counts.entrySet()) {
            if (entry.getValue() > 1) {
                Object[] args = getArgs.apply(entry.getValue(), entry.getKey());
                fail(format, args);
            }
        }
    }

    private static void fail(String message) {
        throw new IllegalArgumentException(message);
    }

    private static void fail(String message, Object... args) {
        throw new IllegalArgumentException(Helpers.format(message, args));
    }
}
