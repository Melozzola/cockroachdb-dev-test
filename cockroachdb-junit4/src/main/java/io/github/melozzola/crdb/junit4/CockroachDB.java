/**
 * Copyright 2017 Silvano Riz
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
package io.github.melozzola.crdb.junit4;

import io.github.melozzola.crdb.process.Cockroach;
import io.github.melozzola.crdb.process.ProcessDetails;
import org.junit.rules.ExternalResource;

import java.util.HashMap;
import java.util.Map;

/**
 * <p> Junit 4 rule that is starting up/shutting down a cockroachDb process.
 */
public class CockroachDB extends ExternalResource {

    /**
     * <p> Context key under which the process details ({@link ProcessDetails}) will be stored.
     */
    public static final String PROCESS_DETAILS_CTX_KEY = "PROCESS_DETAILS";

    private final Cockroach cockroach;
    private final Listener listener;
    private final Map<String, Object> context = new HashMap<>();

    /**
     * <p> Listener called when the {@link #before()} method finished the initialization and cockroach db is up and running.
     *     It can be used as a hook point for database initialization.
     */
    interface Listener{
        void onStartUp(Map<String, Object> context);
    }

    /**
     * <p> Instantiates a new {@link CockroachDB} rule.
     *
     * @param cockroach The cockroach db process configuration
     * @param listener {@link Listener} that will be called once cockroach db is up and running
     * @return An instance of the {@link CockroachDB} rule
     */
    public static CockroachDB newCockroachDB(final Cockroach cockroach, final Listener listener){
        return new CockroachDB(cockroach, listener);
    }

    /**
     * <p> Instantiates a new {@link CockroachDB} rule.
     *
     * @param cockroach The cockroach db process configuration
     * @return An instance of the {@link CockroachDB} rule
     */
    public static CockroachDB newCockroachDB(final Cockroach cockroach){
        return newCockroachDB(cockroach, null);
    }

    /**
     * <p> Private constructor. The rule can be instantiated via factory methods ( See {@link #newCockroachDB(Cockroach)} and {@link #newCockroachDB(Cockroach, Listener)} )
     *
     * @param cockroach The {@link Cockroach} object that allows to start the cockroach process.
     * @param listener An optional listener that will be called at the end of the {@link #before()} method.
     */
    private CockroachDB(final Cockroach cockroach, final Listener listener) {
        this.cockroach = cockroach;
        this.listener = listener;
    }

    @Override
    protected void before() throws Throwable {
        super.before();
        final ProcessDetails details = cockroach.startUp();
        context.put(PROCESS_DETAILS_CTX_KEY, details);
        if (listener != null){
            listener.onStartUp(context);
        }
    }

    @Override
    protected void after() {
        cockroach.shutDown();
    }

    /**
     * <p> Returns a value stored in the context. It throws an {@link IllegalStateException} if the value is not found
     *
     * @param key The key
     * @param type The type of the value object.
     * @param <T> type of the value object
     * @return The object in the context. If no object is found it will throe an {@link IllegalStateException}
     */
    public <T>T getFromContextOrThrow(final String key, final Class<T> type){
        return getFromContextOrThrow(this.context, key, type);
    }

    /**
     * <p> Returns a value stored in the context or {@code null} if the object is not found.
     *
     * @param key The key
     * @param type The type of the value object.
     * @param <T> type of the value object
     * @return The object in the context or {@code null}
     */
    public <T>T getFromContext(final String key, final Class<T> type){
        return getFromContext(this.context, key, type);
    }

    /**
     * <p> Returns a value stored in the context or the specified default if the object is not found.
     *
     * @param key The key
     * @param type The type of the value object.
     * @param defaultValue Default value to return if the object is not found
     * @param <T> type of the value object
     * @return The object in the context or the default value
     */
    public <T>T getFromContextOrDefault(final String key, final Class<T> type, T defaultValue){
        return getFromContextOrDefault(this.context, key, type, defaultValue);
    }

    /**
     * <p> Returns a value stored in the context. It throws an {@link IllegalStateException} if the value is not found
     *
     * @param context The context map
     * @param key The key
     * @param type The type of the value object.
     * @param <T> type of the value object
     * @return The object in the context. If no object is found it will throe an {@link IllegalStateException}
     */
    public static <T> T getFromContextOrThrow(final Map<String, Object> context, final String key, final Class<T> type){
        final Object value = context.get(key);
        if (value == null){
            throw new IllegalStateException("Value for key " + key + " not found");
        }
        return type.cast(value);
    }

    /**
     * <p> Returns a value stored in the context or {@code null} if the object is not found.
     *
     * @param context The context map
     * @param key The key
     * @param type The type of the value object.
     * @param <T> type of the value object
     * @return The object in the context or {@code null}
     */
    public static <T> T getFromContext(final Map<String, Object> context, final String key, final Class<T> type){
        final Object value = context.get(key);
        return type.cast(value);
    }

    /**
     * <p> Returns a value stored in the context or the specified default if the object is not found.
     *
     * @param context The context map
     * @param key The key
     * @param type The type of the value object.
     * @param defaultValue Default value to return if the object is not found
     * @param <T> type of the value object
     * @return The object in the context or the default value
     */
    public static <T> T getFromContextOrDefault(final Map<String, Object> context, final String key, final Class<T> type, T defaultValue){
        final Object value = context.get(key);
        if (value == null){
            return defaultValue;
        }
        return type.cast(value);
    }
}