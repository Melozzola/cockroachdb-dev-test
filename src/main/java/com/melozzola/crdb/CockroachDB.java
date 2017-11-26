/**
 * Copyright 2018 Silvano Riz
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
package com.melozzola.crdb;

import org.junit.rules.ExternalResource;

import javax.net.ServerSocketFactory;
import java.io.*;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.SocketAddress;
import java.nio.channels.FileLock;
import java.nio.channels.SocketChannel;
import java.util.*;

/**
 * <p> Junit rule that sets up CocokroachDB for testing.
 * <p> The rule is spanning a new CocokroachDB process every time is called.
 */
public class CockroachDB extends ExternalResource {

    // Random generator used for ports lookup
    private static final Random random = new Random(System.currentTimeMillis());

    /**
     * Min socket port.
     */
    private  static final int PORT_RANGE_MIN = 1024;

    /**
     * Max socket port.
     */
    private static final int PORT_RANGE_MAX = 65535;

    private Config config;
    private Map<String, Object> ctx;
    private String command;
    private Process crdb;

    /**
     * <p> Listener invoked when CocokroachDB is up and running
     */
    public interface Listener {
        /**
         * <p> Callback invoked when CocokroachDB has started.
         *
         * @param dbPort The db port CocokroachDB is listening to
         * @param httpPort The CocokroachDB UI port
         * @param ctx A context that can be used to pass values from the rule to the actual unit tests.
         */
        void onStartup(final int dbPort, final int httpPort, final Map<String, Object> ctx);
    }

    /**
     * <p> Builder to help configuring the {@link CockroachDB} rule in a fluent way.
     */
    public static class Builder {

        private final Config config;

        private Builder() {
            this.config = new Config();
        }

        /**
         * <p> Specifies the cockroach db host. By default is 'localhost'.
         *
         * @param host The cockroach db host
         * @return The builder.
         */
        public Builder host(final String host){
            config.host = host;
            return this;
        }

        /**
         * <p> Specifies the port cockroach db will listen to. By default is randomly generated.
         *
         * @param dbPort The database port
         * @return The builder.
         */
        public Builder dbPort(final int dbPort) {
            config.dbPort = dbPort;
            return this;
        }

        /**
         * <p> Switches on secure connection.
         *
         * @return The builder.
         */
        public Builder secure() {
            config.secure = true;
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
            config.dataFolder = new File(location);
            config.cleanUpDataFolder = cleanUpDataFolder;
            return this;
        }

        /**
         * <p> Sets the http port for the UI. By default is randomly generated.
         *
         * @param httpPort The http port for the UI.
         * @return The builder.
         */
        public Builder httpPort(final int httpPort) {
            config.httpPort = httpPort;
            return this;
        }

        /**
         * <p> Sets the executable location. By default the rule will download the executable into a temporary folder.
         *
         * @param executable The path to the executable.
         * @return The builder.
         */
        public Builder executable(final String executable){
            config.executable = executable;
            return this;
        }

        /**
         * <p> How long (milliseconds) to wait for cockroach db to start up. By default is 10 seconds.
         *
         * @param startupWaitTimeMs How many milliseconds to wait.
         * @return The builder.
         */
        public Builder startupWaitTime(final int startupWaitTimeMs) {
            config.startupWaitTimeMs = startupWaitTimeMs;
            return this;
        }

        /**
         * <p> What cockroach db version to use. By default is 1.1.2.
         *
         * @param version The version to use
         * @return The builder.
         */
        public Builder version(Version version){
            config.version = version;
            return this;
        }

        /**
         * <p> Add an {@link Appendable} to get logging messages.
         *
         * @param logger The logger.
         * @return The builder.
         */
        public Builder logger(final Appendable logger){
            config.logger = logger;
            return this;
        }

        /**
         * <p> Where to redirect the cockroach db std error. By default is disabled.
         *
         * @param stdErr The redirect destination.
         * @return The builder.
         */
        public Builder stdErr(final Appendable stdErr){
            config.redirectStdErr = true;
            config.stdErr = stdErr;
            return this;
        }

        /**
         * <p> Where to redirect the cockroach db std output. By default is disabled.
         *
         * @param stdOut The redirect destination.
         * @return The builder.
         */
        public Builder stdOut(final Appendable stdOut){
            config.redirectStdOut = true;
            config.stdOut = stdOut;
            return this;
        }

        /**
         * <p> Enables the std out redirection. Cockroach db std out will be printed to {@code System.out}.
         *
         * @return The builder.
         */
        public Builder redirectStdOut(){
            config.redirectStdOut = true;
            return this;
        }

        /**
         * <p> Enables the std err redirection. Cockroach db std out will be printed to {@code System.err}.
         *
         * @return The builder.
         */
        public Builder redirectStdErr(){
            config.redirectStdErr = true;
            return this;
        }

        /**
         * <p> Builds a {@link CockroachDB} with the specified configuration and with the specified startup listener.
         *
         * @param listener The startup listener.
         * @return the {@link CockroachDB} rule.
         */
        public CockroachDB build(final Listener listener){
            config.listener = listener;
            return new CockroachDB(config);
        }

        /**
         * <p> Builds a {@link CockroachDB} with the specified configuration.
         *
         * @return the {@link CockroachDB} rule.
         */
        public CockroachDB build(){
            return new CockroachDB(config);
        }
    }

    /**
     * <p> Returns a {@link Builder} to configure and instantiate a {@code CockroachDB} rule.
     *
     * @return The builder.
     */
    public static Builder builder(){
        return new Builder();
    }

    /**
     * <p> CockroachDB configuration.
     */
    private static class Config {
        private String host = "localhost";
        private Version version = Version.V_1_1_2;
        private int dbPort = findAvailablePort(PORT_RANGE_MIN, PORT_RANGE_MAX, Collections.emptySet());
        private boolean secure = false;
        private File dataFolder = createTemporaryDataFolderIn(System.getProperty("java.io.tmpdir"));
        private boolean cleanUpDataFolder = true;
        private String executable = installBinariesIfNeeded(getBinaryName(version));
        private int httpPort = findAvailablePort(PORT_RANGE_MIN, PORT_RANGE_MAX, new HashSet<Integer>(){{add(dbPort);}});
        private int startupWaitTimeMs = 10000;// 10 secs
        private Appendable logger;
        private Listener listener;
        private Appendable stdErr = System.out;
        private Appendable stdOut = System.err;
        private boolean redirectStdErr = false;
        private boolean redirectStdOut = false;
    }

    /**
     * <p> Private constructor.
     *
     * @param config The configuration
     */
    private CockroachDB(final Config config) {
        this.ctx = new HashMap<>();
        this.config = config;
        this.command = buildCommand(config);
        log("Create CockroachDB");
    }

    /**
     * <p> Utility method to build the command to start up cockroach db.
     *
     * @param config The configuration.
     * @return The command.
     */
    private String buildCommand(final Config config){
        final StringBuilder commandSb = new StringBuilder(config.executable).append(" start");
        commandSb.append(" --port=").append(config.dbPort);
        commandSb.append(" --host=").append(config.host);
        commandSb.append(" --store=").append(config.dataFolder.getAbsolutePath());
        commandSb.append(" --http-port=").append(config.httpPort);
        if (!config.secure){
            commandSb.append(" --insecure");
        }
        return commandSb.toString();
    }

    @Override
    protected void before() throws Throwable {
        super.before();
        log("Run CockroachDB. Command: " + command);
        crdb = Runtime.getRuntime().exec(command);
        if (config.redirectStdOut) {
            new Thread(new StreamReader(crdb.getInputStream(), config.stdOut)).start();
        }
        if (config.redirectStdErr) {
            new Thread(new StreamReader(crdb.getErrorStream(), config.stdErr)).start();
        }
        waitForPort(config.host, config.dbPort, config.startupWaitTimeMs);
        if (config.listener != null){
            config.listener.onStartup(config.dbPort, config.httpPort, ctx);
        }
    }

    @Override
    protected void after() {
        super.after();
        log("Destroy CockroachDB");
        try {
            crdb.destroy();
        }catch (Exception e){
            log("failed to destroy the CockroachDB process: " + e.getMessage());
        }
        if (config.cleanUpDataFolder) {
            recursiveDelete(config.dataFolder);
        }
    }

    /**
     * <p> Retrieves an object from the rule context and casts it to the specified type.
     *     If the object is not in the context it will return null.
     *
     * @param key The object key.
     * @param type The object type.
     * @param <T> The type of the object.
     * @return The object.
     */
    public <T> T getFromCtxOrNull(final String key, final Class<T> type){
        return getFromCtxOrDefault(key, type, null);
    }

    /**
     * <p> Retrieves an object from the rule context and casts it to the specified type.
     *     If the object is not in the context it will return the default value specified as parameter.
     *
     * @param key The object key.
     * @param type The object type.
     * @param defaultValue The default value.
     * @param <T> The type of the object.
     * @return The object.
     */
    public <T> T getFromCtxOrDefault(final String key, final Class<T> type, final T defaultValue){
        final Object value = ctx.get(key);
        if (value == null){
            return defaultValue;
        }
        return type.cast(value);
    }

    /**
     * <p> Retrieves an object from the rule context and casts it to the specified type.
     *     If the object is not in the context it will throw an {@link IllegalStateException}
     *
     * @param key The object key.
     * @param type The object type
     * @param <T> The type of the object
     * @return The object.
     */
    public <T> T getFromCtxOrThrow(final String key, final Class<T> type){
        final Object value = ctx.get(key);
        if (value == null){
            throw new IllegalStateException("Cannot find object with key '" + key + "' in the context");
        }
        return type.cast(value);
    }

    /**
     * <p> Utility method to install the binaries in a temp folder.
     *     The temp folder is always the same so is not installing the file at every test run.
     *
     * @param binaryName The binary name
     * @return The full path of the binary.
     */
    private static String installBinariesIfNeeded(final String binaryName){

        final File tmpFolder = new File(System.getProperty("java.io.tmpdir"), "crdb-bin");
        if (!tmpFolder.exists()) {
            tmpFolder.mkdirs();
        }

        final File binary = new File(tmpFolder, binaryName);
        if (binary.exists()){
            return binary.getAbsolutePath();
        }

        /// XXX - refactor this...it's a bit too messy

        FileLock lock = null;
        try(InputStream is = CockroachDB.class.getResourceAsStream("/binaries/" + binaryName);
            FileOutputStream binaryOs = new FileOutputStream(binary)) {

            // Cross process lock. Only one process should install the binaries.
            lock = binaryOs.getChannel().lock();

            // From here after, only one process will execute this block.
            if (binary.exists() && binary.length() > 0){
                return binary.getAbsolutePath();
            }
            byte[] buffer = new byte[1024*4];
            int len;
            while ((len = is.read(buffer)) != -1) {
                binaryOs.write(buffer, 0, len);
            }
            binaryOs.close();
            binary.setExecutable(true);
            return binary.getAbsolutePath();
        }catch (Exception e){
            throw new IllegalStateException("Error saving the binary", e);
        }finally {
            if (lock != null){
                try {
                    lock.release();
                }catch (Exception e){
                    // Nothing we can do
                }
            }
        }

    }

    /**
     * <p> Utility method to log messages to an {@link Appendable} specified during the rule configuration.
     *
     * @param message The message to log.
     */
    private void log(final String message){
        if (config.logger == null){
            return;
        }
        try{
            config.logger.append(message).append("\n");
        }catch (Exception e){
            // Shh...
        }
    }

    /**
     * <p> Utility method to create a tmp folder for the cockroachDB data.
     *
     * @param parentFolder The parent folder
     * @return The temporary folder.
     */
    private static File createTemporaryDataFolderIn(final String parentFolder) {
        try {
            File parent = new File(parentFolder);
            File createdFolder = File.createTempFile("crdb-data", "", parent);
            createdFolder.delete();
            createdFolder.mkdir();
            return createdFolder;
        }catch (Exception e){
            throw new IllegalStateException("Unable to create the cockroachDB data folder");
        }
    }

    /**
     * <p> Utility method to recursively delete a folder. Used to clean up the cockroachDB data after the test finished.
     *
     * @param file The folder to delete.
     */
    private static void recursiveDelete(final File file) {
        File[] files = file.listFiles();
        if (files != null) {
            for (File each : files) {
                recursiveDelete(each);
            }
        }
        file.delete();
    }

    /**
     * <p> Utility method to find an open port.
     *
     * @param minPort The min port.
     * @param maxPort The max port
     * @param skip A set of ports to skip
     * @return The pseudo-randomly generated port number.
     */
    private static int findAvailablePort(final int minPort, final int maxPort, final Set<Integer> skip) {
        if(minPort <= 0){
            throw new IllegalArgumentException("'minPort' must be greater than 0");
        }
        else if(minPort > maxPort){
            throw new IllegalArgumentException("'maxPort' must be greater than 'minPort'");
        }
        else if(maxPort > PORT_RANGE_MAX){
            throw new IllegalArgumentException("'maxPort' must be less than or equal to " + PORT_RANGE_MAX);
        }

        int portRange = maxPort - minPort;
        int candidatePort;
        int searchCounter = 0;
        do {
            if(++searchCounter > portRange) {
                throw new IllegalStateException(String.format("Could not find an available port in the range [%d, %d] after %d attempts", minPort, maxPort, searchCounter));
            }
            candidatePort = findRandomPort(minPort, maxPort);
        } while(!skip.contains(candidatePort) && !isPortAvailable(candidatePort));

        return candidatePort;
    }

    /**
     * <p> Generates a random port between a max and min port.
     *
     * @param minPort The min port.
     * @param maxPort The max port.
     * @return The pseudo-randomly generated port number.
     */
    private static int findRandomPort(final int minPort, final int maxPort) {
        int portRange = maxPort - minPort;
        return minPort + random.nextInt(portRange);
    }

    /**
     * <p> Utility method to verify if the port is available.
     *
     * @param port The port.
     * @return {@code true} if the port is available, false otherwise.
     */
    private static boolean isPortAvailable(final int port) {
        try {
            ServerSocket serverSocket = ServerSocketFactory.getDefault().createServerSocket(port);
            serverSocket.close();
            return true;
        } catch(Exception ex) {
            return false;
        }
    }

    /**
     * <p> Utility method that waits for cockroach db to start. It waits until the database port is available.
     *     It will wait up to the time specified and it will throw an exception if the time is exceeded.
     *
     * @param host The host.
     * @param port The database port.
     * @param milliseconds The maximum milliseconds to wait.
     */
    private static void waitForPort(final String host, int port, long milliseconds) {
        final SocketAddress address = new InetSocketAddress(host, port);
        long totalWait = 0;
        while (true) {
            try {
                SocketChannel.open(address);
                return;
            } catch (IOException e) {
                try {
                    System.out.println("Wait for cockroach to start up (" + totalWait + "/" + milliseconds + ")...");
                    Thread.sleep(100);
                    totalWait += 100;
                    if (totalWait > milliseconds) {
                        throw new IllegalStateException("Timeout while waiting for port " + port);
                    }
                } catch (InterruptedException ie) {
                    throw new IllegalStateException(ie);
                }
            }
        }
    }

    /**
     * <p> Supported cockroach DB versions.
     */
    public enum Version {
        V_1_1_2("v1.1.2");

        final String version;

        Version(String version) {
            this.version = version;
        }

        public String getVersion() {
            return version;
        }
    }

    /**
     * <p> Utility method to get the binary name based on OS type and cockroachDB version.
     *
     * @param crdbVersion The cockroach DB version.
     * @return The binary name.
     */
    private static String getBinaryName(final Version crdbVersion){
        final String arch = System.getProperty("os.arch");
        final String osName = System.getProperty("os.name", "generic").toLowerCase(Locale.ENGLISH);
        String name = "";
        if (osName.contains("mac") || osName.contains("darwin")){
            name = "darwin";
        }else if (osName.contains("win")){
            name = "win";
        }else if (osName.contains("nux")){
            name = "linux";
        }else{
            throw new IllegalStateException("No binary for the OS " + osName);
        }
        return String.format("cockroach-%s.%s-amd64", crdbVersion.getVersion(), name);
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
