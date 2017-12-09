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

/**
 * <p> Junit 4 rule that is starting up/shutting down a cockroachDb process.
 * <p> <b>Important:</b> the rule is meant to be used as class rule.
 * <p> The process details are stored in a thread local variable and they are exposed via the method {@link #getProcessDetailsOrThrow}
 */
public class CockroachDB extends ExternalResource {

    private final Cockroach cockroach;

    public CockroachDB(final Cockroach cockroach) {
        this.cockroach = cockroach;
    }

    private static ThreadLocal<ProcessDetails> processDetails = new ThreadLocal<>();

    /**
     * <p> Returns the cockroach db process details.
     * <p> The process details will be available after the rule has been applied (method {@link #before()} has been successfully called), if there are no process details it will throw an {@link IllegalStateException}.
     *
     * @return The cockroach db process details.
     */
    public static ProcessDetails getProcessDetailsOrThrow(){
        final ProcessDetails config = processDetails.get();
        if (config == null){
            throw new IllegalStateException("Process details not found. Verify that the rule has started.");
        }
        return config;
    }

    @Override
    protected void before() throws Throwable {
        super.before();
        final ProcessDetails details = cockroach.startUp();
        processDetails.set(details);
    }

    @Override
    protected void after() {
        cockroach.shutDown();
    }
}