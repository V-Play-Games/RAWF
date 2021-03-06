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
package net.vpg.rawf.internal.requests.ratelimit;

import net.vpg.rawf.api.requests.RestRequest;
import net.vpg.rawf.api.utils.MiscUtil;
import net.vpg.rawf.internal.requests.AbstractRateLimiter;
import net.vpg.rawf.internal.requests.Requester;
import net.vpg.rawf.internal.requests.Route;
import net.vpg.rawf.internal.utils.RAWFLogger;
import okhttp3.Headers;
import okhttp3.Response;
import org.jetbrains.annotations.Contract;
import org.slf4j.Logger;

import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Supplier;

/*
** How does it work? **

A bucket is determined via the Path+Method+Major in the following way:

    1. Get Hash from Path+Method (we call this route)
    2. Get bucket from Hash+Major (we call this bucketId)

If no hash is known we default to the constant "unlimited" hash. The hash is loaded from HTTP responses using the "X-RateLimit-Bucket" response header.
This hash is per Method+Path and can be stored indefinitely once received.
Some endpoints don't return a hash, this means that the endpoint is **unlimited** and will be in queue with only the major parameter.

To explain this further, lets look at the example of message history. The endpoint to fetch message history is "GET/channels/{channel.id}/messages".
This endpoint does not have any rate limit (unlimited) and will thus use the hash "unlimited+GET/channels/{channel.id}/messages".
The bucket id for this will be "unlimited+GET/channels/{channel.id}/messages:guild_id:{channel.id}:webhook_id" where "{channel.id}" would be replaced with the respective id.
This means you can fetch history concurrently for multiple channels but it will be in sequence for the same channel.

If the endpoint is not unlimited we will receive a hash on the first response.
Once this happens every unlimited bucket will start moving its queue to the correct bucket.
This is done during the queue work iteration so many requests to one endpoint would be moved correctly.

For example, the first message sending:

    public void onReady(ReadyEvent event) {
      TextChannel channel = event.getApi().getTextChannelById("123");
      for (int i = 1; i <= 100; i++) {
        channel.sendMessage("Message: " + i).queue();
      }
    }

This will send 100 messages on startup. At this point we don't yet know the hash for this route so we put them all in "unlimited+POST/channels/{channel.id}/messages:guild_id:123:webhook_id".
The bucket iterates the requests in sync and gets the first response. This response provides the hash for this route and we create a bucket for it.
Once the response is handled we continue with the next request in the unlimited bucket and notice the new bucket. We then move all related requests to this bucket.

 */
public class DefaultRateLimiter extends AbstractRateLimiter {
    private static final Logger LOGGER = RAWFLogger.getLog(DefaultRateLimiter.class);
    private static final String RESET_AFTER_HEADER = "X-RateLimit-Reset-After";
    private static final String RESET_HEADER = "X-RateLimit-Reset";
    private static final String LIMIT_HEADER = "X-RateLimit-Limit";
    private static final String REMAINING_HEADER = "X-RateLimit-Remaining";
    private static final String GLOBAL_HEADER = "X-RateLimit-Global";
    private static final String HASH_HEADER = "X-RateLimit-Bucket";
    private static final String RETRY_AFTER_HEADER = "Retry-After";
    private static final String UNLIMITED_BUCKET = "unlimited"; // we generate an unlimited bucket for every major parameter configuration

    private final ReentrantLock bucketLock = new ReentrantLock();
    // Route -> Should we print warning for 429? AKA did we already hit it once before
    private final Set<Route> hitRateLimit = ConcurrentHashMap.newKeySet(5);
    // Route -> Hash
    private final Map<Route, String> hashes = new ConcurrentHashMap<>();
    // Hash + Major Parameter -> Bucket
    private final Map<String, Bucket> buckets = new ConcurrentHashMap<>();
    // Bucket -> Rate-Limit Worker
    private final Map<Bucket, Future<?>> rateLimitQueue = new ConcurrentHashMap<>();
    private final Future<?> cleanupWorker;

    public DefaultRateLimiter(Requester requester) {
        super(requester);
        cleanupWorker = getScheduler().scheduleAtFixedRate(this::cleanup, 30, 30, TimeUnit.SECONDS);
    }

    public static long getNow() {
        return System.currentTimeMillis();
    }

    private ScheduledExecutorService getScheduler() {
        return requester.getApi().getRateLimitPool();
    }

    @Override
    public int cancelRequests() {
        return bucketLocked(() -> {
            // Empty buckets will be removed by the cleanup worker, which also checks for rate limit parameters
            AtomicInteger count = new AtomicInteger(0);
            buckets.values()
                .stream()
                .map(Bucket::getRequests)
                .flatMap(Collection::stream)
                .filter(request -> !request.isPriority() && !request.isCancelled())
                .peek(x -> count.incrementAndGet())
                .forEach(RestRequest::cancel);

            int cancelled = count.get();
            if (cancelled == 1)
                LOGGER.warn("Cancelled 1 request!");
            else if (cancelled > 1)
                LOGGER.warn("Cancelled {} requests!", cancelled);
            return cancelled;
        });
    }

    private void bucketLocked(Runnable runnable) {
        MiscUtil.locked(bucketLock, runnable);
    }

    private <E> E bucketLocked(Supplier<E> supplier) {
        return MiscUtil.locked(bucketLock, supplier);
    }

    private void cleanup() {
        // This will remove buckets that are no longer needed every 30 seconds to avoid memory leakage
        // We will keep the hashes in memory since they are very limited (by the amount of possible routes)
        bucketLocked(() -> {
            int size = buckets.size();
            Iterator<Bucket> entries = buckets.values().iterator();
            while (entries.hasNext()) {
                Bucket bucket = entries.next();
                // Remove cancelled requests
                bucket.requests.removeIf(RestRequest::isSkipped);

                // Check if the bucket is empty and remove it if:
                // - It is unlimited
                // - The reset is expired (the bucket has no valuable information)
                // - The rate limiter is stopped
                if (bucket.getRequests().isEmpty() && (bucket.isUnlimited() || bucket.reset <= getNow() || shutdown))
                    entries.remove();
            }
            // Log how many buckets were removed
            size -= buckets.size();
            if (size == 1)
                LOGGER.debug("Removed 1 expired bucket");
            else if (size > 1)
                LOGGER.debug("Removed {} expired buckets", size);
        });
    }

    private String getRouteHash(Route route) {
        return hashes.getOrDefault(route, UNLIMITED_BUCKET + "+" + route);
    }

    @Override
    public void shutdown() {
        bucketLocked(() -> {
            if (shutdown)
                return;
            if (cleanupWorker != null)
                cleanupWorker.cancel(false);
            cleanup();
            int size = buckets.size();
            if (!shutdown && size > 0) // Tell user about active buckets so they don't get confused by the longer shutdown
            {
                int average = (int) Math.ceil(
                    buckets.values()
                        .stream()
                        .map(Bucket::getRequests)
                        .mapToInt(Collection::size)
                        .average()
                        .orElse(0)
                );

                LOGGER.info("Waiting for {} bucket(s) to finish. Average queue size of {} requests", size, average);
            }
        });
    }

    @Override
    public long getRateLimit(Route.CompiledRoute route) {
        Bucket bucket = getBucket(route, false);
        return bucket == null ? 0L : bucket.getRateLimit();
    }

    @Override
    @SuppressWarnings("rawtypes")
    public void queueRequest(RestRequest request) {
        // Create bucket and enqueue request
        bucketLocked(() -> {
            Bucket bucket = getBucket(request.getRoute(), true);
            bucket.enqueue(request);
            bucket.schedule();
        });
    }

    @Override
    public long handleResponse(Route.CompiledRoute route, Response response) {
        return bucketLocked(() -> {
            long rateLimit = updateBucket(route, response).getRateLimit();
            return response.code() == 429 ? rateLimit : 0L;
        });
    }

    private Bucket updateBucket(Route.CompiledRoute route, Response response) {
        return bucketLocked(() -> {
            try {
                Bucket bucket = getBucket(route, true);
                Headers headers = response.headers();

                boolean global = headers.get(GLOBAL_HEADER) != null;
                boolean cloudflare = headers.get("via") == null;
                String hash = headers.get(HASH_HEADER);
                long now = getNow();

                // Create a new bucket for the hash if needed
                Route baseRoute = route.getBaseRoute();
                if (hash != null) {
                    if (!hashes.containsKey(baseRoute)) {
                        hashes.put(baseRoute, hash);
                        LOGGER.debug("Caching bucket hash {} -> {}", baseRoute, hash);
                    }

                    bucket = getBucket(route, true);
                }

                if (response.code() == 429) {
                    String retryAfterHeader = headers.get(RETRY_AFTER_HEADER);
                    long retryAfter = parseLong(retryAfterHeader) * 1000; // seconds precision
                    // Handle global rate limit if necessary
                    if (global) {
                        requester.getApi().setGlobalRateLimit(now + retryAfter);
                        LOGGER.error("Encountered global rate limit! Retry-After: {} ms", retryAfter);
                    }
                    // Handle cloudflare rate limits, this applies to all routes and uses seconds for retry-after
                    else if (cloudflare) {
                        requester.getApi().setGlobalRateLimit(now + retryAfter);
                        LOGGER.error("Encountered cloudflare rate limit! Retry-After: {} s", retryAfter / 1000);
                    }
                    // Handle hard rate limit, pretty much just log that it happened
                    else {
                        boolean firstHit = hitRateLimit.add(baseRoute) && retryAfter < 60000;
                        // Update the bucket to the new information
                        bucket.remaining = 0;
                        bucket.reset = getNow() + retryAfter;
                        // don't log warning if we hit the rate limit for the first time, likely due to initialization of the bucket
                        // unless its a long retry-after delay (more than a minute)
                        if (firstHit)
                            LOGGER.debug("Encountered 429 on route {} with bucket {} Retry-After: {} ms", baseRoute, bucket.bucketId, retryAfter);
                        else
                            LOGGER.warn("Encountered 429 on route {} with bucket {} Retry-After: {} ms", baseRoute, bucket.bucketId, retryAfter);
                    }
                    return bucket;
                }

                // If hash is null this means we didn't get enough information to update a bucket
                if (hash == null)
                    return bucket;

                // Update the bucket parameters with new information
                String limitHeader = headers.get(LIMIT_HEADER);
                String remainingHeader = headers.get(REMAINING_HEADER);
                String resetAfterHeader = headers.get(RESET_AFTER_HEADER);

                bucket.limit = (int) Math.max(1L, parseLong(limitHeader));
                bucket.remaining = (int) parseLong(remainingHeader);
                bucket.reset = now + parseDouble(resetAfterHeader);
                LOGGER.trace("Updated bucket {} to ({}/{}, {})", bucket.bucketId, bucket.remaining, bucket.limit, bucket.reset - now);
                return bucket;
            } catch (Exception e) {
                Bucket bucket = getBucket(route, true);
                LOGGER.error("Encountered Exception while updating a bucket. Route: {} Bucket: {} Code: {} Headers:\n{}",
                    route.getBaseRoute(), bucket, response.code(), response.headers(), e);
                return bucket;
            }
        });
    }

    @Contract("_,true->!null")
    private Bucket getBucket(Route.CompiledRoute route, boolean create) {
        return bucketLocked(() -> {
            // Retrieve the hash via the route
            String hash = getRouteHash(route.getBaseRoute());
            // Get or create a bucket for the hash + major parameters
            String bucketId = hash + ":" + route.getParams();
            Bucket bucket = buckets.get(bucketId);
            if (bucket == null && create) {
                bucket = new Bucket(bucketId);
                buckets.put(bucketId, bucket);
            }
            return bucket;
        });
    }

    private long parseLong(String input) {
        return input == null ? 0L : Long.parseLong(input);
    }

    private long parseDouble(String input) {
        // The header value is using a double to represent milliseconds and seconds:
        // 5.250 this is 5 seconds and 250 milliseconds (5250 milliseconds)
        return input == null ? 0L : (long) (Double.parseDouble(input) * 1000);
    }

    private class Bucket implements IBucket, Runnable {
        private final String bucketId;
        private final Deque<RestRequest<?>> requests = new ConcurrentLinkedDeque<>();

        private long reset = 0;
        private int remaining = 1;
        private int limit = 1;

        public Bucket(String bucketId) {
            this.bucketId = bucketId;
        }

        public void enqueue(RestRequest<?> request) {
            requests.addLast(request);
        }

        public void retry(RestRequest<?> request) {
            requests.addFirst(request);
        }

        private boolean isGlobalRateLimit() {
            return /*requester.getApi().getSessionController().getGlobalRatelimit() > getNow()*/ false;
        }

        public long getRateLimit() {
            long now = getNow();
            long global = /*requester.getApi().getSessionController().getGlobalRatelimit()*/0;
            // Global rate limit is more important to handle
            if (global > now)
                return global - now;
            // Check if the bucket reset time has expired
            if (reset <= now) {
                // Update the remaining uses to the limit (we don't know better)
                remaining = limit;
                return 0L;
            }

            // If there are remaining requests we don't need to do anything, otherwise return backoff in milliseconds
            return remaining < 1 ? reset - now : 0L;
        }

        public long getReset() {
            return reset;
        }

        public int getRemaining() {
            return remaining;
        }

        public int getLimit() {
            return limit;
        }

        private boolean isUnlimited() {
            return bucketId.startsWith("unlimited");
        }

        private void backoff() {
            // Schedule backoff if requests are not done
            bucketLocked(() -> {
                rateLimitQueue.remove(Bucket.this);
                if (!requests.isEmpty())
                    schedule();
                else if (shutdown)
                    buckets.remove(bucketId);
            });
        }

        private void schedule() {
            if (shutdown)
                return;
            // Schedule a new bucket worker if no worker is running
            bucketLocked(() ->
                rateLimitQueue.computeIfAbsent(this,
                    k -> getScheduler().schedule(this, getRateLimit(), TimeUnit.MILLISECONDS)
                )
            );
        }

        @Override
        public void run() {
            LOGGER.trace("Bucket {} is running {} requests", bucketId, requests.size());
            while (!requests.isEmpty()) {
                long rateLimit = getRateLimit();
                if (rateLimit > 0L) {
                    // We need to backoff since we ran out of remaining uses or hit the global rate limit
                    RestRequest<?> request = requests.peekFirst(); // this *should* not be null
                    String baseRoute = request != null ? request.getRoute().getBaseRoute().toString() : "N/A";
                    if (!isGlobalRateLimit() && rateLimit >= 1000 * 60 * 30) // 30 minutes
                        LOGGER.warn("Encountered long {} minutes Rate-Limit on route {}", TimeUnit.MILLISECONDS.toMinutes(rateLimit), baseRoute);
                    LOGGER.debug("Backing off {} ms for bucket {} on route {}", rateLimit, bucketId, baseRoute);
                    break;
                }

                RestRequest<?> request = requests.removeFirst();
                if (request.isSkipped())
                    continue;
                if (isUnlimited()) {
                    boolean shouldSkip = bucketLocked(() -> {
                        // Attempt moving request to correct bucket if it has been created
                        Bucket bucket = getBucket(request.getRoute(), true);
                        if (bucket != Bucket.this) {
                            bucket.getRequests().addAll(requests);
                            requests.clear();
                            bucket.schedule();
                            return true;
                        }
                        return false;
                    });
                    if (shouldSkip) continue;
                }

                try {
                    rateLimit = requester.execute(request);
                    if (rateLimit != 0)
                        retry(request); // this means we hit a hard rate limit (429) so the request needs to be retried
                } catch (Throwable ex) {
                    LOGGER.error("Encountered exception trying to execute request", ex);
                    if (ex instanceof Error)
                        throw (Error) ex;
                    break;
                }
            }

            backoff();
        }

        @Override
        public Queue<RestRequest<?>> getRequests() {
            return requests;
        }

        @Override
        public String toString() {
            return bucketId;
        }
    }
}
