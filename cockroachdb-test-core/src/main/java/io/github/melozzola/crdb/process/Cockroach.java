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
package io.github.melozzola.crdb.process;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static io.github.melozzola.crdb.utils.Utils.*;

/**
 * <p> CockroachDb process. This class is responsible for
 * <ul>
 *     <li>Allowing pre-configuration of cockroach (startup flags). See {@link Builder} and {@link #builder()}</li>
 *     <li>Installing the binaries from web or classpath (if needed).</li>
 *     <li>Run the CockroachDb process and wait for the database to be available. ( See {@link #startUp()} )</li>
 *     <li>Shut down the process and clean up temporary folders and resources. ( See {@link #shutDown()} )</li>
 * </ul>
 */
public class Cockroach {

    private String version = "v1.1.7";
    private boolean cleanUpDataFolder = true;
    private String executable;
    private int startupWaitTimeMs = 10000;// 10 secs
    private int shutDownWaitingTimeMs = 10000;// 10 secs
    private Appendable stdErr = System.out;
    private Appendable stdOut = System.err;
    private boolean redirectStdErr = false;
    private boolean redirectStdOut = false;
    private Path workFolder;

    private ProcessDetails processDetails;
    private Process crdb;
    private Flags flags = new Flags();
    private final AtomicInteger status = new AtomicInteger(0); //0=not started, 1=started, 2=stopped

    private Cockroach(){}

    public static class Builder {

        private Cockroach cockroach = new Cockroach();

        /**
         * <p> Specifies the cockroach db host. By default is 'localhost'.
         *
         * @param host The cockroach db host
         * @return The builder.
         */
        public Builder host(final String host){
            cockroach.flags.setHost(host);
            return this;
        }

        /**
         * <p> Specifies the port cockroach db will listen to. By default is randomly generated.
         *
         * @param port The database port
         * @return The builder.
         */
        public Builder port(final int port) {
            cockroach.flags.setPort(port);
            return this;
        }

        /**
         * <p> Switches on secure connection.
         *
         * @return The builder.
         */
        public Builder secure() {
            cockroach.flags.setInsecure(false);
            return this;
        }

        /**
         * <p> Where cockrach db will store the data. By default in a temporary folder and it is deleted after the test.
         *
         * @param location The data folder location.
         * @param cleanUpDataFolder if the folder needs to be deleted at the end of the test.
         * @return The builder.
         */
        public Builder dataFolder(final String location, final boolean cleanUpDataFolder) {
            cockroach.flags.setStore(location);
            cockroach.cleanUpDataFolder = cleanUpDataFolder;
            return this;
        }

        /**
         * <p> Sets the http port for the UI. By default is randomly generated.
         *
         * @param httpPort The http port for the UI.
         * @return The builder.
         */
        public Builder httpPort(final int httpPort) {
            cockroach.flags.setHttpPort(httpPort);
            return this;
        }

        /**
         * <p> Sets the executable location. By default the rule will download the executable into a temporary folder.
         *
         * @param executable The path to the executable.
         * @return The builder.
         */
        public Builder executable(final String executable){
            cockroach.executable = executable;
            return this;
        }

        /**
         * <p> How long (milliseconds) to wait for cockroach db to start up. By default is 10 seconds.
         *
         * @param startupWaitTimeMs How many milliseconds to wait.
         * @return The builder.
         */
        public Builder startupWaitTime(final int startupWaitTimeMs) {
            cockroach.startupWaitTimeMs = startupWaitTimeMs;
            return this;
        }

        /**
         * <p> How long (milliseconds) to wait for cockroach db to stop. By default is 10 seconds.
         *
         * @param shutDownWaitingTimeMs How many milliseconds to wait.
         * @return The builder.
         */
        public Builder shutDownWaitingTime(final int shutDownWaitingTimeMs) {
            cockroach.shutDownWaitingTimeMs = shutDownWaitingTimeMs;
            return this;
        }

        /**
         * <p> What cockroach db version to use. By default is 1.1.7.
         *
         * @param version The version to use
         * @return The builder.
         */
        public Builder version(String version){
            cockroach.version = version;
            return this;
        }

        /**
         * <p> Where to redirect the cockroach db std error. By default is disabled.
         *
         * @param stdErr The redirect destination.
         * @return The builder.
         */
        public Builder stdErr(final Appendable stdErr){
            cockroach.redirectStdErr = true;
            cockroach.stdErr = stdErr;
            return this;
        }

        /**
         * <p> Where to redirect the cockroach db std output. By default is disabled.
         *
         * @param stdOut The redirect destination.
         * @return The builder.
         */
        public Builder stdOut(final Appendable stdOut){
            cockroach.redirectStdOut = true;
            cockroach.stdOut = stdOut;
            return this;
        }

        /**
         * <p> Enables the std out redirection. Cockroach db std out will be printed to {@code System.out}.
         *
         * @return The builder.
         */
        public Builder redirectStdOut(){
            cockroach.redirectStdOut = true;
            return this;
        }

        /**
         * <p> Enables the std err redirection. Cockroach db std out will be printed to {@code System.err}.
         *
         * @return The builder.
         */
        public Builder redirectStdErr(){
            cockroach.redirectStdErr = true;
            return this;
        }

        /**
         * <p> Builds a {@link Cockroach} with the specified configuration.
         *
         * @return the {@link Cockroach} rule.
         */
        public Cockroach build(){
            setDefaultsIfNeeded();
            return cockroach;
        }

        // Sets defaults
        private void setDefaultsIfNeeded(){
            if (cockroach.executable == null) {
                cockroach.executable = installBinariesIfNeeded(cockroach.version);
            }
            cockroach.workFolder = createTemporaryDataFolderIn(System.getProperty("java.io.tmpdir"));
            cockroach.flags.setPidFile(cockroach.workFolder.resolve("pid.txt"));
            cockroach.flags.setListeningUrlFile(cockroach.workFolder.resolve("url.txt"));

        }

    }

    /**
     * <p> Static method that returns a builder that allows to configure the process.
     *
     * @return The builder.
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * <p> Starts up the process. This method can be called only once otherwise it will throw an {@link IllegalStateException}.
     *
     * @return The process details like the pid, host, port and url of the cockroach db.
     */
    public ProcessDetails startUp(){
        if (status.compareAndSet(0, 1)) {
            final String command = executable + " start" + flags.getFlags();
            crdb = runOrThrow(command);
            if (redirectStdOut) {
                new Thread(new StreamReader(crdb.getInputStream(), stdOut)).start();
            }
            if (redirectStdErr) {
                new Thread(new StreamReader(crdb.getErrorStream(), stdErr)).start();
            }
            processDetails = waitForStartup(flags.getPidFile(), flags.getListeningUrlFile(), startupWaitTimeMs);
            return processDetails;
        }else {
            throw new IllegalStateException("Invalid status: " + status.get());
        }
    }

    /**
     * <p> Shuts down the process. It must be called after the {@link #startUp} otherwise it will throw an {@link IllegalStateException}.
     */
    public void shutDown(){
        if (status.compareAndSet(1, 2)) {
            if (crdb == null){
                return;
            }
            try {
                killProcessOrThrow();
            }catch (Exception e){
                throw new IllegalStateException("failed to shut down cockroach db process. Pid: " + processDetails.pid + ", Url: " + processDetails.url, e);
            }finally {
                if (cleanUpDataFolder) {
                    recursiveDelete(workFolder);
                }
            }
        }else {
            throw new IllegalStateException("Invalid status. Status: " + status.get());
        }
    }

    private void killProcessOrThrow() throws Exception {
        crdb.destroyForcibly();
        boolean exited = crdb.waitFor(shutDownWaitingTimeMs, TimeUnit.MILLISECONDS);
        if (!exited){
            throw new IllegalStateException("Cockroach db process failed to stop within the " + shutDownWaitingTimeMs + " ms timeout. Pid: " + processDetails.pid);
        }
    }

    /**
     * <p> Run the process or throw an {@link IllegalStateException} if it fails.
     *
     * @param command The cockroach db command.
     * @return The started process.
     */
    static Process runOrThrow(final String command){
        try {
            return Runtime.getRuntime().exec(command);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to start the process " + command, e);
        }
    }

    /**
     * <p> {@link Runnable} that consumes a stream and pipes the data to an {@link Appendable}.
     *     Used to output the std out and err of the cockroach process to a different place.
     * <p> Each line will be prefixed with 'crdb>'.
     */
    private static class StreamReader implements Runnable {

        private static final String PREFIX = "crdb> ";
        private final BufferedReader in;
        private final Appendable out;

        private StreamReader(final InputStream in, final Appendable out) {
            this.in = new BufferedReader(new InputStreamReader(in));
            this.out = out;
        }

        @Override
        public void run() {
            while (true) {
                try {
                    final String line = in.readLine();
                    if (line != null) {
                        out.append(PREFIX).append(line).append("\n");
                    }
                } catch (Exception e) {
                    break;
                }
            }
        }
    }
}
