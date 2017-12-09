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

import java.nio.file.Path;

/**
 * <p> Cockroach start configuration.
 */
public class Flags {

    // --advertise-host
    private String advertiseHost;

    // --attrs
    private String attributes;

    //--background
    private Boolean background = false;

    // --locality
    private String locality;

    // --join
    private String join;

    // --cache (default 128MiB)
    private Long cache = 3L;// 3MiB

    // --certs-dir (default ${HOME}/.cockroach-certs/)
    private String certsDir;

    // --host
    private String host = "localhost";

    // --http-host
    private String httpHost;

    // --http-port
    private Integer httpPort = 0;// 0 means random

    // --insecure
    private Boolean insecure = true;

    // --listening-url-file
    private Path listeningUrlFile;

    // --max-disk-temp-storage
    private Long maxDiskTempStorage;

    // --max-offset
    private Long maxOffset;

    // --max-sql-memory
    private Long maxSqlMemory = 3L;// 3MiB;

    // --pid-file
    private Path pidFile;

    // --port
    private Integer port = 0;// 0 means random;

    // --store
    private String store = "type=mem,size=640MiB";

    public String getFlags(){
        final StringBuilder flags = new StringBuilder();
        if (advertiseHost != null){
            flags.append(" --advertise-host=").append(advertiseHost);
        }
        if (attributes != null){
            flags.append(" --attrs=").append(attributes);
        }
        if (background){
            flags.append(" --background");
        }
        if (locality != null){
            flags.append(" --locality=").append(locality);
        }
        if (join != null){
            flags.append(" --join=").append(join);
        }
        if (cache != null) {
            flags.append(" --cache=").append(cache).append("MiB");
        }
        if (certsDir != null){
            flags.append(" --certs-dir=").append(certsDir);
        }
        if (host != null){
            flags.append(" --host=").append(host);
        }
        if (httpHost != null){
            flags.append(" --http-host=").append(httpHost);
        }
        if (httpPort != null){
            flags.append(" --http-port=").append(httpPort);
        }
        if (insecure){
            flags.append(" --insecure");
        }
        if (listeningUrlFile != null){
            flags.append(" --listening-url-file=").append(listeningUrlFile.toAbsolutePath().toString());
        }
        if (maxDiskTempStorage != null){
            flags.append(" --max-disk-temp-storage=").append(maxDiskTempStorage).append("MiB");
        }
        if (maxOffset != null){
            flags.append(" --max-offset=").append(maxOffset);
        }
        if (maxSqlMemory != null){
            flags.append(" --max-sql-memory=").append(maxSqlMemory).append("MiB");
        }
        if (pidFile != null){
            flags.append(" --pid-file=").append(pidFile.toAbsolutePath().toString());
        }
        if (port != null){
            flags.append(" --port=").append(port);
        }
        if (store != null){
            flags.append(" --store=").append(store);
        }
        return flags.toString();
    }

    public String getAdvertiseHost() {
        return advertiseHost;
    }

    public void setAdvertiseHost(String advertiseHost) {
        this.advertiseHost = advertiseHost;
    }

    public String getAttributes() {
        return attributes;
    }

    public void setAttributes(String attributes) {
        this.attributes = attributes;
    }

    public Boolean getBackground() {
        return background;
    }

    public void setBackground(Boolean background) {
        this.background = background;
    }

    public String getLocality() {
        return locality;
    }

    public void setLocality(String locality) {
        this.locality = locality;
    }

    public String getJoin() {
        return join;
    }

    public void setJoin(String join) {
        this.join = join;
    }

    public Long getCache() {
        return cache;
    }

    public void setCache(Long cache) {
        this.cache = cache;
    }

    public String getCertsDir() {
        return certsDir;
    }

    public void setCertsDir(String certsDir) {
        this.certsDir = certsDir;
    }

    public String getHost() {
        return host;
    }

    public void setHost(String host) {
        this.host = host;
    }

    public String getHttpHost() {
        return httpHost;
    }

    public void setHttpHost(String httpHost) {
        this.httpHost = httpHost;
    }

    public Integer getHttpPort() {
        return httpPort;
    }

    public void setHttpPort(Integer httpPort) {
        this.httpPort = httpPort;
    }

    public Boolean getInsecure() {
        return insecure;
    }

    public void setInsecure(Boolean insecure) {
        this.insecure = insecure;
    }

    public Path getListeningUrlFile() {
        return listeningUrlFile;
    }

    public void setListeningUrlFile(Path listeningUrlFile) {
        this.listeningUrlFile = listeningUrlFile;
    }

    public Long getMaxDiskTempStorage() {
        return maxDiskTempStorage;
    }

    public void setMaxDiskTempStorage(Long maxDiskTempStorage) {
        this.maxDiskTempStorage = maxDiskTempStorage;
    }

    public Long getMaxOffset() {
        return maxOffset;
    }

    public void setMaxOffset(Long maxOffset) {
        this.maxOffset = maxOffset;
    }

    public Long getMaxSqlMemory() {
        return maxSqlMemory;
    }

    public void setMaxSqlMemory(Long maxSqlMemory) {
        this.maxSqlMemory = maxSqlMemory;
    }

    public Path getPidFile() {
        return pidFile;
    }

    public void setPidFile(Path pidFile) {
        this.pidFile = pidFile;
    }

    public Integer getPort() {
        return port;
    }

    public void setPort(Integer port) {
        this.port = port;
    }

    public String getStore() {
        return store;
    }

    public void setStore(String store) {
        this.store = store;
    }
}
