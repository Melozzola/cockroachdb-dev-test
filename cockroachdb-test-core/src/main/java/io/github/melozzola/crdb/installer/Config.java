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

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.net.URLConnection;
import java.net.URLStreamHandler;
import java.util.Properties;

/**
 * <p> This class loads the configuration properties and gives access to their values.
 * <p> By default it will load two files:
 *
 * <ul>
 *     <li>/cockroachdb-junit.properties</li> Default properties backed into the jar.
 *     <li>/cockroachdb-junit-override.properties</li> Override properties that a user can add to the classpath. It takes precedence over the default.
 * </ul>
 *
 * <p> Overriding the default properties is useful to install binaries from classpath resource. For example:
 * <pre>
 *     {@code
 *     win.v1.0.6.classpath=classpath:/cockroach-v1.0.6.windows-6.2-amd64.zip
 *     win.v1.0.6.classpath.compression=zip
 *     }
 * </pre>
 * allows to install cockroach 1.0.6 windows version, installing the binary from the classpath zip.
 *
 * It also allow testing against newer versions or developer versions.
 */
public class Config {

    private static final String DEFAULT = "/cockroachdb-junit.properties";
    private static final String OVERRIDE = "/cockroachdb-junit-override.properties";

    private static Config instance = null;

    private Properties defaultConfig;
    private Properties overrideConfig;

    private static class ClasspathURLStreamHandler extends URLStreamHandler {

        protected URLConnection openConnection(final URL url) throws IOException {
            final String path = url.getPath();
            URL classpathUrl = Thread.currentThread().getContextClassLoader().getResource(path);
            if (classpathUrl == null) {
                classpathUrl = ClasspathURLStreamHandler.class.getResource(path);
            }

            if (classpathUrl == null) {
                throw new IllegalStateException("Classpath resource not found. Url: " + url);
            } else {
                return classpathUrl.openConnection();
            }
        }
    }

    public static Config getInstance() {
        if(instance == null) {

            synchronized (Config.class){
                if (instance != null){
                    return instance;
                }
                instance = new Config();
                instance.defaultConfig = loadFromClasspathOrThrow(DEFAULT);
                instance.overrideConfig = loadFromClasspathOrNull(OVERRIDE);

                URL.setURLStreamHandlerFactory(protocol -> {
                    if ("classpath".equalsIgnoreCase(protocol)){
                        return new ClasspathURLStreamHandler();
                    }
                    return null;
                });

            }
        }
        return instance;
    }

    public String get(final String key){
        if (overrideConfig != null && overrideConfig.containsKey(key)){
            return overrideConfig.getProperty(key);
        }
        return defaultConfig.getProperty(key);
    }

    private static Properties loadFromClasspathOrThrow(String classpathResource){
        try {
            Properties props = new Properties();
            props.load(Installer.class.getResourceAsStream(classpathResource));
            return props;
        }catch (Exception e){
            throw new IllegalStateException("Unable to read the properties " + classpathResource);
        }
    }

    private static Properties loadFromClasspathOrNull(String classpathResource){
        InputStream src = Installer.class.getResourceAsStream(classpathResource);
        if (src == null){
            return null;
        }
        try {
            Properties props = new Properties();
            props.load(src);
            return props;
        }catch (Exception e){
            throw new IllegalStateException("Unable to read the properties " + classpathResource);
        }
    }

}
