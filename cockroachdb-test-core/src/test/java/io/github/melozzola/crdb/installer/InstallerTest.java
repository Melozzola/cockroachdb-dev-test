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

import org.junit.Assert;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.nio.file.Files;
import java.nio.file.Path;

/**
 * <p> Installer test
 */
public class InstallerTest {

    @Rule
    public TemporaryFolder temporaryFolder = new TemporaryFolder();

    @Test
    public void installFromHttp() throws Exception {

        final Path destination = temporaryFolder.newFolder("binaries").toPath();
        final String resource = Config.getInstance().get("darwin.v1.0.6.web");
        final String compression = Config.getInstance().get("darwin.v1.0.6.web.compression");
        Installer.install(resource, destination, "cockroach", compression);

        Path binary = destination.resolve("cockroach");
        Assert.assertTrue(Files.exists(binary));

    }

    @Test
    public void installFromClasspath() throws Exception {

        final Path destination = temporaryFolder.newFolder("binaries").toPath();
        final String resource = Config.getInstance().get("darwin.v1.1.3.classpath");
        final String compression = Config.getInstance().get("darwin.v1.1.3.classpath.compression");
        Installer.install(resource, destination, "cockroach", compression);

        Path binary = destination.resolve("cockroach");
        Assert.assertTrue(Files.exists(binary));
    }
}