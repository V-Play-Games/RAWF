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
package net.vpg.rawf.api.utils;

import gnu.trove.impl.sync.TSynchronizedLongObjectMap;
import gnu.trove.map.TLongObjectMap;
import gnu.trove.map.hash.TLongObjectHashMap;
import net.vpg.rawf.api.requests.RestAction;
import net.vpg.rawf.internal.utils.Checks;
import net.vpg.rawf.internal.utils.Helpers;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.Formatter;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Consumer;
import java.util.function.Supplier;

public class MiscUtil {
    /**
     * Generates a new thread-safe {@link TLongObjectMap}
     *
     * @param <T> The Object type
     * @return a new thread-safe {@link TLongObjectMap}
     */
    public static <T> TLongObjectMap<T> newLongMap() {
        return new TSynchronizedLongObjectMap<>(new TLongObjectHashMap<>(), new Object());
    }

    public static long parseLong(String input) {
        if (input.startsWith("-"))
            return Long.parseLong(input);
        else
            return Long.parseUnsignedLong(input);
    }

    public static <T> Consumer<? super T> getRestActionSuccess(Consumer<? super T> success) {
        return success != null ? success : RestAction.getDefaultSuccess();
    }

    public static <T> Consumer<? super Throwable> getRestActionFailure(Consumer<? super Throwable> failure) {
        return failure != null ? failure : RestAction.getDefaultFailure();
    }

    public static long parseSnowflake(String input) {
        Checks.notEmpty(input, "ID");
        try {
            return parseLong(input);
        } catch (NumberFormatException ex) {
            throw new NumberFormatException(
                Helpers.format("The specified ID is not a valid snowflake (%s). Expecting a valid long value!", input));
        }
    }

    public static <E> E locked(ReentrantLock lock, Supplier<E> task) {
        try {
            tryLock(lock);
            return task.get();
        } finally {
            if (lock.isHeldByCurrentThread())
                lock.unlock();
        }
    }

    public static <T> T requireNonNullElse(T obj, T other) {
        return obj != null ? obj : other;
    }

    public static void locked(ReentrantLock lock, Runnable task) {
        try {
            tryLock(lock);
            task.run();
        } finally {
            if (lock.isHeldByCurrentThread())
                lock.unlock();
        }
    }

    /**
     * Tries to acquire the provided lock in a 10 second timeframe.
     *
     * @param lock The lock to acquire
     * @throws IllegalStateException If the lock could not be acquired
     */
    public static void tryLock(Lock lock) {
        try {
            if (!lock.tryLock() && !lock.tryLock(10, TimeUnit.SECONDS))
                throw new IllegalStateException("Could not acquire lock in a reasonable timeframe! (10 seconds)");
        } catch (InterruptedException e) {
            throw new IllegalStateException("Unable to acquire lock while thread is interrupted!");
        }
    }

    /**
     * Can be used to append a String to a formatter.
     *
     * @param formatter     The {@link Formatter}
     * @param width         Minimum width to meet, filled with space if needed
     * @param precision     Maximum amount of characters to append
     * @param leftJustified Whether or not to left-justify the value
     * @param out           The String to append
     */
    public static void appendTo(Formatter formatter, int width, int precision, boolean leftJustified, String out) {
        try {
            Appendable appendable = formatter.out();
            if (precision > -1 && out.length() > precision) {
                appendable.append(Helpers.truncate(out, precision));
                return;
            }

            if (leftJustified)
                appendable.append(Helpers.rightPad(out, width));
            else
                appendable.append(Helpers.leftPad(out, width));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
