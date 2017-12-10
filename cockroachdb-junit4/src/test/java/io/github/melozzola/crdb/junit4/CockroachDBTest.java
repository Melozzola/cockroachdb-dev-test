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

import io.github.melozzola.crdb.process.ProcessDetails;
import org.junit.Assert;
import org.junit.ClassRule;
import org.junit.Test;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;

import static io.github.melozzola.crdb.junit4.CockroachDB.*;
import static io.github.melozzola.crdb.process.Cockroach.builder;

/**
 * <p> CockroachDB junit 4 rule test
 */
public class CockroachDBTest {

    private static final String JDBC_URL_CTX_KEY = "JDBC_URL";

    @ClassRule
    public static CockroachDB cockroachDB = newCockroachDB(
            builder()
                    .stdErr(System.err)// Redirect the cockroach db process errors to std err
                    .stdOut(System.out)// Redirect the cockroach db process std out to System.out
                    // There ae other config options here like .version(...) .host(...) etc..
                    .build(),
            context -> {

                // Some initialization. You can put this in a @BeforeClass method

                final ProcessDetails pd = getFromContext(context, PROCESS_DETAILS_CTX_KEY, ProcessDetails.class);
                final String jdbcUrl = String.format("jdbc:postgresql://%s:%d/test?sslmode=disable", pd.getHost(), pd.getPort());
                context.put(JDBC_URL_CTX_KEY, jdbcUrl);
                initDatabase(jdbcUrl);
            }
    );

    private static void initDatabase(final String jdbcUrl){
        try (final Connection db = DriverManager.getConnection(jdbcUrl, "root", "")){
            Class.forName("org.postgresql.Driver");
            db.createStatement().execute("CREATE DATABASE IF NOT EXISTS test;");
            db.createStatement().execute("CREATE TABLE IF NOT EXISTS logs (id INT PRIMARY KEY, log TEXT);");
        }catch (Exception e){
            throw new IllegalStateException("Cannot initialise the database", e);
        }
    }

    @Test
    public void testRule() throws Exception {

        // Here we can interact with the cockroach db.

        final String jdbcUrl = cockroachDB.getFromContextOrThrow(JDBC_URL_CTX_KEY, String.class);
        try (final Connection db = DriverManager.getConnection(jdbcUrl, "root", "")){

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