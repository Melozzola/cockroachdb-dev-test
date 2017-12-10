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
import org.junit.*;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;

/**
 * <p> CockroachDB junit 4 rule test
 */
public class CockroachDBTest {

    @ClassRule
    public static CockroachDB cockroachDB = new CockroachDB(Cockroach.builder().build());
    private static String url;

    @BeforeClass
    public static void setUp() throws Exception{

        // For example here we can initialise the database with some data...

        Class.forName("org.postgresql.Driver");
        final ProcessDetails pd = CockroachDB.getProcessDetailsOrThrow();
        url = String.format("jdbc:postgresql://%s:%d/test?sslmode=disable", pd.getHost(), pd.getPort());
        try (final Connection db = DriverManager.getConnection(url, "root", "")){
            db.createStatement().execute("CREATE DATABASE IF NOT EXISTS test;");
            db.createStatement().execute("CREATE TABLE IF NOT EXISTS logs (id INT PRIMARY KEY, log TEXT);");
        }
    }

    @Test
    public void testRule() throws Exception {

        // Here we can interact with the cockroach db.

        try (final Connection db = DriverManager.getConnection(url, "root", "")){

            db.createStatement().execute("INSERT INTO test.logs (id, log) VALUES (1, 'value 1'), (2, 'value 2');");
            final ResultSet res = db.createStatement().executeQuery("SELECT id, log FROM test.logs ORDER BY id ASC;");
            int count = 1;
            while (res.next()) {
                Assert.assertEquals(count, res.getInt("id"));
                Assert.assertEquals(String.format("value %d", count), res.getString("log"));
                count++;
            }
            res.close();
        }
    }

}