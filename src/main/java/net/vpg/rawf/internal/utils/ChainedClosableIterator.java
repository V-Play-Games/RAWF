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

import net.vpg.rawf.api.utils.ClosableIterator;
import net.vpg.rawf.api.utils.cache.CacheView;

import java.util.HashSet;
import java.util.Iterator;
import java.util.NoSuchElementException;
import java.util.Set;

public class ChainedClosableIterator<T> implements ClosableIterator<T> {
    private final Set<T> items;
    private final Iterator<? extends CacheView<T>> generator;
    private ClosableIterator<T> currentIterator;
    private T item;

    public ChainedClosableIterator(Iterator<? extends CacheView<T>> generator) {
        this.items = new HashSet<>();
        this.generator = generator;
    }

    public Set<T> getItems() {
        return items;
    }

    @Override
    public void close() {
        if (currentIterator != null)
            currentIterator.close();
        currentIterator = null;
    }

    @Override
    public boolean hasNext() {
        if (item != null)
            return true;
        // get next item from current iterator if exists
        if (currentIterator != null) {
            if (findNext()) {
                return true;
            }
            currentIterator.close();
            currentIterator = null;
        }

        // get next iterator in chain
        while (item == null) {
            CacheView<T> view = null;
            while (generator.hasNext()) {
                view = generator.next();
                if (!view.isEmpty())
                    break;
                view = null;
            }
            if (view == null)
                return false;

            // find next item in this iterator
            currentIterator = view.lockedIterator();
            if (findNext()) break;
        }
        return true;
    }

    private boolean findNext() {
        while (currentIterator.hasNext()) {
            T next = currentIterator.next();
            if (!items.add(next))
                continue;
            item = next;
            return true;
        }
        return false;
    }

    @Override
    public T next() {
        if (!hasNext())
            throw new NoSuchElementException();
        T tmp = item;
        item = null;
        return tmp;
    }
}
