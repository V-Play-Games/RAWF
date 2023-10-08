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

import net.vpg.rawf.api.exceptions.ParsingException;
import net.vpg.rawf.api.utils.IOFunction;
import net.vpg.rawf.internal.utils.IOUtil;
import net.vpg.vjson.value.JSONArray;
import net.vpg.vjson.value.JSONObject;
import okhttp3.Response;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import javax.annotation.ParametersAreNonnullByDefault;
import java.io.*;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

@ParametersAreNonnullByDefault
public class RestResponse implements Closeable {
    public static final int ERROR_CODE = -1;
    public static final String ERROR_MESSAGE = "ERROR";

    public final int code;
    public final String message;
    public final long retryAfter;
    private final InputStream body;
    private final Response rawResponse;
    private final Set<String> cfRays;
    private String fallbackString;
    private Object object;
    private boolean attemptedParsing = false;
    private Exception exception;

    public RestResponse(Exception exception, Set<String> cfRays) {
        this(null, ERROR_CODE, ERROR_MESSAGE, -1, cfRays);
        this.exception = exception;
    }

    public RestResponse(@Nullable Response response, int code, String message, long retryAfter, Set<String> cfRays) {
        this.rawResponse = response;
        this.code = code;
        this.message = message;
        this.exception = null;
        this.retryAfter = retryAfter;
        this.cfRays = cfRays;

        if (response == null) {
            this.body = null;
        } else {
            try {
                this.body = IOUtil.getBody(response);
            } catch (Exception e) {
                throw new IllegalStateException("An error occurred while parsing the response for a RestAction", e);
            }
        }
    }

    public RestResponse(long retryAfter, Set<String> cfRays) {
        this(null, 429, "TOO MANY REQUESTS", retryAfter, cfRays);
    }

    public RestResponse(Response response, long retryAfter, Set<String> cfRays) {
        this(response, response.code(), response.message(), retryAfter, cfRays);
    }

    @Nonnull
    public JSONArray getArray() {
        return get(JSONArray.class, JSONArray::parse);
    }

    @Nonnull
    public Optional<JSONArray> optArray() {
        return parseBody(true, JSONArray.class, JSONArray::parse);
    }

    @Nonnull
    public JSONObject getObject() {
        return get(JSONObject.class, JSONObject::parse);
    }

    @Nonnull
    public Optional<JSONObject> optObject() {
        return parseBody(true, JSONObject.class, JSONObject::parse);
    }

    @Nonnull
    public String getString() {
        return parseBody(String.class, this::readString).orElseGet(() -> fallbackString == null ? "N/A" : fallbackString);
    }

    @Nonnull
    public <T> T get(Class<T> clazz, IOFunction<BufferedReader, T> parser) {
        return parseBody(clazz, parser).orElseThrow(IllegalStateException::new);
    }

    @Nullable
    public Response getRawResponse() {
        return this.rawResponse;
    }

    @Nonnull
    public Set<String> getCFRays() {
        return cfRays;
    }

    @Nullable
    public Exception getException() {
        return exception;
    }

    public boolean isError() {
        return code == RestResponse.ERROR_CODE;
    }

    public boolean isOk() {
        return 199 < code && code < 300;
    }

    public boolean isRateLimit() {
        return code == 429;
    }

    @Override
    public String toString() {
        return this.exception == null
            ? "HTTPResponse[" + this.code + (this.object == null ? "" : ", " + this.object.toString()) + ']'
            : "HttpException[" + this.exception.getMessage() + ']';
    }

    @Override
    public void close() {
        if (rawResponse != null)
            rawResponse.close();
    }

    private String readString(BufferedReader reader) {
        return reader.lines().collect(Collectors.joining("\n"));
    }

    private <T> Optional<T> parseBody(Class<T> clazz, IOFunction<BufferedReader, T> parser) {
        return parseBody(false, clazz, parser);
    }

    @SuppressWarnings("ConstantConditions")
    private <T> Optional<T> parseBody(boolean opt, Class<T> clazz, IOFunction<BufferedReader, T> parser) {
        if (attemptedParsing) {
            if (object != null && clazz.isAssignableFrom(object.getClass()))
                return Optional.of(clazz.cast(object));
            return Optional.empty();
        }

        attemptedParsing = true;
        if (body == null || rawResponse == null || rawResponse.body().contentLength() == 0)
            return Optional.empty();

        BufferedReader reader = null;
        try {
            reader = new BufferedReader(new InputStreamReader(body));
            reader.mark(1024);
            T t = parser.apply(reader);
            this.object = t;
            return Optional.ofNullable(t);
        } catch (Exception e) {
            try {
                reader.reset();
                this.fallbackString = readString(reader);
                reader.close();
            } catch (NullPointerException | IOException ignored) {
            }
            if (opt && e instanceof ParsingException)
                return Optional.empty();
            else
                throw new IllegalStateException("An error occurred while parsing the response for a RestAction", e);
        }
    }
}
