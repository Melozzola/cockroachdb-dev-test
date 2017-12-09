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
package io.github.melozzola.crdb.utils;

import io.github.melozzola.crdb.installer.Config;
import io.github.melozzola.crdb.installer.Installer;
import io.github.melozzola.crdb.process.ProcessDetails;

import java.io.File;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.nio.channels.SocketChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Optional;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * <p> A bunch of utilities...
 */
public class Utils {

    // (1:protocol)(2:domain)(3:port)(4:uri)
    private static final Pattern URL_PATTERN = Pattern.compile("(postgresql?://)([^:^/]*):(\\d*)?(.*)?");

    public static void waitFroFile(final Path file, long milliseconds){

        long totalWait = 0;

        while (totalWait < milliseconds) {
            try{
                if (Files.exists(file) && Files.size(file) > 0) {
                    return;
                }
            }catch (IOException e){
                // shh
            }

            try{
                Thread.sleep(100);
                totalWait += 100;
            }catch(Exception e){
                throw new IllegalStateException("Failed waiting for the file " + file + " to appear", e);
            }

        }
    }

    public static String readUrl(final Path urlFile){
        waitFroFile(urlFile, 5000);
        try {
            final Optional<String> url = Files.lines(urlFile).filter(s -> s != null && s.toLowerCase().startsWith("postgres")).findFirst();
            return url.orElseThrow(() -> new IllegalStateException("Url not found in file " + urlFile));
        } catch (Exception e) {
            throw new IllegalStateException("Unable to read the url from the file " + urlFile, e);
        }
    }

    public static Long readPid(final Path pidFile){
        waitFroFile(pidFile, 5000);
        try {
            final Optional<String> pid = Files.lines(pidFile).filter(s -> s != null && s.matches("^\\d+$")).findFirst();
            return pid.flatMap((Function<String, Optional<Long>>) s -> Optional.of(Long.parseLong(s))).orElseThrow(() -> new IllegalStateException("Pid not found in file " + pidFile));
        }catch (Exception e){
            throw new IllegalStateException("Unable to read the pid from the file " + pidFile, e);
        }
    }

    private static boolean fileHasContent(final Path file){
        try{
            return Files.size(file) > 0;
        }catch (Exception e){
            return false;
        }
    }

    /**
     * <p> Waits for cockroach db to start up.
     *
     * @param pidFile The pid file where cockrach is writing the process pid
     * @param urlFile The url file where cockroach is writing the url
     * @param maxWaitTimeMs Maximum wait time in milliseconds.
     */
    public static ProcessDetails waitForStartup(final Path pidFile, final Path urlFile, long maxWaitTimeMs) {
        long totalWait = 0;

        String host = null;
        Integer port = null;
        Long pid = null;
        String url = null;
        while(totalWait < maxWaitTimeMs){

            try {
                Thread.sleep(100);
                totalWait = totalWait + 100;
            }catch (Exception e){
                throw new IllegalStateException("Interrupted while waiting fot cockroach to start up", e);
            }

            if (pid == null && Files.exists(pidFile) && fileHasContent(pidFile)){
                pid = readPid(pidFile);
            }else{
                continue;
            }

            if (url == null && Files.exists(urlFile) && fileHasContent(urlFile)){
                url = readUrl(urlFile);
                final Matcher matcher = URL_PATTERN.matcher(url);
                if (!matcher.find()){
                    throw new IllegalStateException("Cannot find the port number in the url: " + url);
                }
                port = Integer.parseInt(matcher.group(3));
                host = matcher.group(2);
                if (host.indexOf('@') > 0){
                    host = host.substring(host.indexOf('@')+1);
                }

            }else{
                continue;
            }

            final SocketAddress address = new InetSocketAddress(host, port);
            try {
                SocketChannel.open(address);
                return new ProcessDetails(pid, port, host, url);
            } catch (IOException e) {
                // Shh
            }
        }

        throw new IllegalStateException("Timeout while waiting for cockroach db to start up " + port);
    }

    /**
     * <p> Utility method to create a tmp folder for the cockroachDB data.
     *
     * @param parentFolder The parent folder
     * @return The temporary folder.
     */
    public static Path createTemporaryDataFolderIn(final String parentFolder) {
        try {
            File parent = new File(parentFolder);
            File createdFolder = File.createTempFile("crdb-data", "", parent);
            createdFolder.delete();
            createdFolder.mkdir();
            return createdFolder.toPath();
        }catch (Exception e){
            throw new IllegalStateException("Unable to create the cockroachDB data folder");
        }
    }

    /**
     * <p> Utility method to recursively delete a folder. Used to clean up the cockroachDB data after the test finished.
     *
     * @param folder The folder to delete.
     */
    public static void recursiveDelete(final Path folder) {
        if (!Files.exists(folder)){
            return;
        }
        File f = folder.toFile();
        File[] files = f.listFiles();
        if (files != null) {
            for (File each : files) {
                recursiveDelete(each.toPath());
            }
        }
        f.delete();
    }

    /**
     * <p> Utility method to get the binary name based on OS type and cockroachDB version.
     *
     * @param crdbVersion The cockroach DB version.
     * @return The binary name.
     */
    private static String getBinaryName(final String os, final String crdbVersion){
        return String.format("cockroach-%s.%s-amd64", crdbVersion, os);
    }

    /**
     * <p> Utility method to install the binaries in a temp folder.
     *     The temp folder is always the same so is not installing the file at every test run.
     *
     * @param version The version
     * @return The full path of the binary.
     */
    public static String installBinariesIfNeeded(final String version){

        final File tmpFolder = new File(System.getProperty("java.io.tmpdir"), "crdb-bin");
        if (!tmpFolder.exists()) {
            tmpFolder.mkdirs();
        }

        final String osId = getOsId();
        final String binaryName = getBinaryName(osId, version);
        final File binary = new File(tmpFolder, binaryName);
        if (binary.exists()){
            return binary.getAbsolutePath();
        }

        // Get the binary for the classpath first...
        String resource = Config.getInstance().get(String.format("%s.%s.classpath", osId, version));
        String compression = Config.getInstance().get(String.format("%s.%s.classpath.compression", osId, version));

        // If binaries are not in the classpath, download from the web....
        if (resource == null){
            resource = Config.getInstance().get(String.format("%s.%s.web", osId, version));
            compression = Config.getInstance().get(String.format("%s.%s.web.compression", osId, version));
        }

        Installer.install(resource, tmpFolder.toPath(), binaryName, compression);
        return binary.getAbsolutePath();
    }

    private static String getOsId(){

        final String arch = System.getProperty("os.arch");
        final String osName = System.getProperty("os.name", "generic").toLowerCase(Locale.ENGLISH);
        String name = "";
        if (osName.contains("mac") || osName.contains("darwin")){
            return "darwin";
        }else if (osName.contains("win")){
            return "win";
        }else if (osName.contains("nux")){
            return "linux";
        }else{
            throw new IllegalStateException("No binary for the OS " + osName);
        }

    }

}
