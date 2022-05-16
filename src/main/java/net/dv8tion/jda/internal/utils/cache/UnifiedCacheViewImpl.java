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
package net.dv8tion.jda.internal.utils.cache;

import net.dv8tion.jda.api.utils.ClosableIterator;
import net.dv8tion.jda.api.utils.cache.CacheView;
import net.dv8tion.jda.internal.utils.ChainedClosableIterator;

import javax.annotation.Nonnull;
import java.util.*;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class UnifiedCacheViewImpl<T, E extends CacheView<T>> implements CacheView<T> {
    protected final Supplier<? extends Stream<? extends E>> generator;

    public UnifiedCacheViewImpl(Supplier<? extends Stream<? extends E>> generator) {
        this.generator = generator;
    }

    @Override
    public long size() {
        return distinctStream().mapToLong(CacheView::size).sum();
    }

    @Override
    public boolean isEmpty() {
        return distinctStream().allMatch(CacheView::isEmpty);
    }

    @Override
    public void forEach(Consumer<? super T> action) {
        Objects.requireNonNull(action);
        try (ClosableIterator<T> it = lockedIterator()) {
            while (it.hasNext())
                action.accept(it.next());
        }
    }

    @Nonnull
    @Override
    public List<T> asList() {
        List<T> list = new LinkedList<>();
        forEach(list::add);
        return Collections.unmodifiableList(list);
    }

    @Nonnull
    @Override
    public Set<T> asSet() {
        try (ChainedClosableIterator<T> it = lockedIterator()) {
            //because the iterator needs to retain elements to avoid duplicates,
            // we can use the resulting HashSet as our return value!
            while (it.hasNext()) it.next();
            return Collections.unmodifiableSet(it.getItems());
        }
    }

    @Nonnull
    @Override
    public ChainedClosableIterator<T> lockedIterator() {
        Iterator<? extends E> gen = generator.get().iterator();
        return new ChainedClosableIterator<>(gen);
    }

    @Nonnull
    @Override
    public List<T> getElementsByName(@Nonnull String name, boolean ignoreCase) {
        return distinctStream()
            .flatMap(view -> view.getElementsByName(name, ignoreCase).stream())
            .distinct()
            .collect(Collectors.toUnmodifiableList());
    }

    @Nonnull
    @Override
    public Stream<T> stream() {
        return distinctStream().flatMap(CacheView::stream).distinct();
    }

    @Nonnull
    @Override
    public Stream<T> parallelStream() {
        return distinctStream().flatMap(CacheView::parallelStream).distinct();
    }

    @Nonnull
    @Override
    public Iterator<T> iterator() {
        return stream().iterator();
    }

    protected Stream<? extends E> distinctStream() {
        return generator.get().distinct();
    }
}
