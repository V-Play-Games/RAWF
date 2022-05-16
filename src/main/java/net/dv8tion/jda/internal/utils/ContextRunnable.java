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
package net.dv8tion.jda.internal.utils;

import java.util.concurrent.Callable;

public class ContextRunnable<E> implements Runnable, Callable<E> {
    private final Runnable runnable;
    private final Callable<E> callable;

    public ContextRunnable(Runnable runnable) {
        this.runnable = runnable;
        this.callable = null;
    }

    public ContextRunnable(Callable<E> callable) {
        this.runnable = null;
        this.callable = callable;
    }

    @Override
    public void run() {
        runnable.run();
    }

    @Override
    public E call() throws Exception {
        return callable.call();
    }
}
