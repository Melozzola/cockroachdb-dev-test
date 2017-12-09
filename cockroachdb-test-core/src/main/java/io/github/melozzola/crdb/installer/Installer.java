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
package io.github.melozzola.crdb.installer;

import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream;

import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermission;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;
import java.util.zip.GZIPInputStream;
import java.util.zip.ZipInputStream;

/**
 * <p> Cockroach db binary installer.
 * <p> Installs cockroach from the web or classpath.
 */
public class Installer {

    private static final Set<PosixFilePermission> PERMISSIONS = new HashSet<PosixFilePermission>(){{
        add(PosixFilePermission.OWNER_READ);
        add(PosixFilePermission.OWNER_WRITE);
        add(PosixFilePermission.OWNER_EXECUTE);
    }};

    enum Compression{
        NONE(""),
        GZIP("gz"),
        TAR_GZIP("tgz"),
        ZIP("zip");

        final String compression;

        Compression(String compression) {
            this.compression = compression;
        }

        public static Compression get(final String compression){
            for (Compression c: Compression.values()){
                if (c.compression.equalsIgnoreCase(compression)){
                    return c;
                }
            }
            throw new IllegalStateException("Invalid compression " + compression);
        }
    }

    enum Protocol{
        CLASSPATH("classpath"),
        HTTP("http"),
        HTTPS("https");

        private final String protocol;

        Protocol(String protocol) {
            this.protocol = protocol;
        }

        static Protocol get(final String protocol){
            Objects.requireNonNull(protocol, "protocol cannot be null");
            for (Protocol p : Protocol.values()){
                if (p.protocol.equalsIgnoreCase(protocol.trim())){
                    return p;
                }
            }
            throw new IllegalStateException("Protocol " + protocol + " not supported");
        }

    }

    public static void install(final String resource, final Path destinationFolder, final String name, final String compression){

        URL url;
        try{
            url = new URL(resource);
        }catch (Exception e){
            throw new IllegalStateException("Invalid URL " + resource, e);
        }

        Compression compressionToUse;
        if (compression == null || "".equals(compression.trim())){
            compressionToUse = Compression.NONE;
        }else {
            compressionToUse = Compression.get(compression);
        }

        install(url, destinationFolder, name, compressionToUse);
    }

    public static void install(final URL resource, final Path destinationFolder, final String name, final Compression compression){
        final Protocol protocol = Protocol.get(resource.getProtocol());
        switch (protocol){

            case CLASSPATH:
                installFromClasspath(resource, destinationFolder, name, compression);
                break;
            case HTTP:
            case HTTPS:
                installFromWeb(resource, destinationFolder, name, compression);
                break;
            default:
                throw new IllegalStateException("Protocol " + protocol.protocol + " not supported");
        }

    }

    private static void install(InputStream source, final Path destinationFolder, final String name, Compression compression){
        createFoldersOrTrow(destinationFolder);
        final Path destination = destinationFolder.resolve(name);
        if (Files.exists(destination)){
            return;
        }

        final Path tmpDestination = destinationFolder.resolve(name+".tmp");

        // Somebody is installing the binary...
        // I know, not bullet proof...some concurrency problem could happen here :-(
        int waitingTime = 30000;// Wait up to 30 secs
        int waitedFor = 0;
        while (Files.exists(tmpDestination) && waitedFor < waitingTime){
            try {
                Thread.sleep(10000);
                waitedFor += 10000;
            }catch (Exception e){
                throw new IllegalStateException("Unable to install the cockroach db binary", e);
            }
        }
        if (Files.exists(destination)){
            return;
        }

        try(InputStream wrappedSource = wrapInputStreamIfNeeded(source, compression);
            FileOutputStream binaryOs = new FileOutputStream(tmpDestination.toFile())) {

            byte[] buffer = new byte[1024*4];
            int len;
            while ((len = wrappedSource.read(buffer)) != -1) {
                binaryOs.write(buffer, 0, len);
            }
            binaryOs.close();
            Files.move(tmpDestination, destination);
            Files.setPosixFilePermissions(destination, PERMISSIONS);
        }catch (Exception e){
            deleteFileIfExistsOrThrow(destination);
            throw new IllegalStateException("Unable to install the cockroach db binary", e);
        }finally {
            deleteFileIfExistsOrThrow(tmpDestination);
        }
    }

    private static void deleteFileIfExistsOrThrow(final Path path){
        try {
            Files.deleteIfExists(path);
        }catch (Exception e){
            throw new IllegalStateException("Cannot delete file "+ path.toAbsolutePath());
        }
    }

    private static void installFromClasspath(final URL classpathResource, final Path destinationFolder, final String name, Compression compression){
        final String binary = classpathResource.getPath();
        install(Installer.class.getResourceAsStream(binary), destinationFolder, name, compression);
    }

    private static void installFromWeb(final URL webResource, final Path destinationFolder, final String name, Compression compression){
        InputStream source;
        try {
            source = webResource.openStream();
        }catch (Exception e){
            throw new IllegalStateException("Unable to install the cockroach db binary from " + webResource);
        }
        install(source, destinationFolder, name, compression);
    }

    private static InputStream wrapInputStreamIfNeeded(final InputStream toWrap, final Compression compression){
        try {
            switch (compression) {
                case ZIP:
                    return new ZipInputStream(toWrap);
                case GZIP:
                    return new GZIPInputStream(toWrap);
                case TAR_GZIP:
                    TarArchiveInputStream wrapper = new TarArchiveInputStream(new GZIPInputStream(toWrap));
                    TarArchiveEntry entry;
                    while ((entry = wrapper.getNextTarEntry()) != null) {
                        if (!entry.isDirectory()) {
                            break;
                        }
                    }
                    return wrapper;
                case NONE:
                default:
                    return toWrap;
            }
        }catch (Exception e){
            throw new IllegalStateException("Unable to install the cockroach db binary", e);
        }
    }

    private static void createFoldersOrTrow(final Path path){
        try {
            Files.createDirectories(path);
        }catch (Exception e){
            throw new IllegalStateException("Cannot create path " + path.toAbsolutePath().toString());
        }
    }


}
