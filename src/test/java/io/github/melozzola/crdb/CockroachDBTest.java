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
package io.github.melozzola.crdb;

import com.zaxxer.hikari.HikariDataSource;
import org.junit.Assert;
import org.junit.ClassRule;
import org.junit.FixMethodOrder;
import org.junit.Test;
import org.junit.runners.MethodSorters;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.ResultSet;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.Map;

/**
 * <p> Example of test.
 * <p> In this example the test order is important and it is achieved using the annotation {@link FixMethodOrder}
 *     and the naming convention: {@code _001${name}}, {@code _002{name}}
 *
 * @author Silvano Riz
 */
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
public class CockroachDBTest {

    private static HikariDataSource DS;

    @ClassRule
    public static CockroachDB CRDB = CockroachDB.builder()
            .logger(System.out)
            .stdErr(System.err)
            .stdOut(System.out)
            .build((dbPort, httpPort, ctx) -> {

        System.out.println("Received start callback. Db Port:" + dbPort + ", Http Port: " + httpPort);

        // Put the current time in the context
        ctx.put("TIME", new Date());

        // Initialise the datasource
        DS = new HikariDataSource();
        DS.setJdbcUrl("jdbc:postgresql://localhost:"+dbPort+"/test?sslmode=disable");
        DS.setUsername("root");
        DS.setMaximumPoolSize(10);

        // Initialise the database and add some data
        initDatabase(DS, ctx);

    });

    @Test
    public void _001TestRead() throws Exception {

        final Date time = CRDB.getFromCtxOrThrow("TIME", Date.class);
        Assert.assertNotNull(time);

        final List<String> expected = Arrays.asList("001_one", "002_two", "003_three");
        try(Connection connection = DS.getConnection()){
            try(ResultSet rs = connection.prepareStatement("SELECT * FROM test_data ORDER BY data;").executeQuery()){
                int index = 0;
                while (rs.next()){
                    String id = rs.getString("id");
                    String data = rs.getString("data");
                    String expectedData = expected.get(index++);
                    String expectedId = CRDB.getFromCtxOrThrow(expectedData, String.class);
                    Assert.assertEquals(expectedId, id);
                    Assert.assertEquals(expectedData, data);
                }
                Assert.assertEquals(3, index);
            }
        }
    }

    @Test
    public void _002TestAdd() throws Exception {

        final Date time = CRDB.getFromCtxOrThrow("TIME", Date.class);
        Assert.assertNotNull(time);

        final List<String> expected = Arrays.asList("004_four", "005_five");
        try(Connection connection = DS.getConnection()){
            try(ResultSet rs = connection.prepareStatement("INSERT INTO test_data (data) VALUES ('004_four'), ('005_five') RETURNING id, data;").executeQuery()) {
                int index = 0;
                while (rs.next()) {
                    String id = rs.getString("id");
                    String data = rs.getString("data");
                    String expectedData = expected.get(index++);
                    Assert.assertEquals(expectedData, data);
                    Assert.assertNotNull(id);
                }
            }
        }
    }

    private static void initDatabase(final DataSource ds, final Map<String, Object> ctx){
        ResultSet rs = null;
        try(Connection connection = ds.getConnection()){
            connection.prepareStatement("DROP DATABASE IF EXISTS test CASCADE;").execute();
            connection.prepareStatement("CREATE DATABASE IF NOT EXISTS test;").execute();
            connection.prepareStatement("CREATE TABLE IF NOT EXISTS test_data (id UUID PRIMARY KEY DEFAULT gen_random_uuid(), data STRING);").execute();
            rs = connection.prepareStatement("INSERT INTO test_data (data) VALUES ('001_one'), ('002_two'), ('003_three') RETURNING id, data;").executeQuery();

            // Here we use the context to add an inverted index to look up the automatically generated uuid from the data.
            while (rs.next()){
                ctx.put(rs.getString("data"), rs.getString("id"));
            }

        }catch (Exception e){
            throw new IllegalStateException("Error initializing the database", e);
        }finally{
            if (rs != null) {
                try {
                    rs.close();
                } catch (Exception e) {
                    // Shh...
                }
            }
        }
    }

}