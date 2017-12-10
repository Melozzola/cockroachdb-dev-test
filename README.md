# Utility library for dev test java applications that use CockroacDB
This library contains a [Junit 4](http://junit.org/junit4/) Rule to enable easy tests when using [CokroachDB](https://www.cockroachlabs.com/) in a java project.

One of the most used strategies to implement dev tests that require a database is to use an in memory database. 
Anyway, the in memory database often is providing just a subset of the features that the production databases are offering, making testing difficult or incomplete.
Anther option would be to mock (or stub) the jdbc layer. Even though mocking and stubbing are powerful test techniques, mocking the jdbc layer can be time consuming and hard to maintain.

Leveraging on cockroachdb straight forward installation, start up speed and possibility to configure the storage to reside in memory, this library enables to quickly write
tests for your java application that relies on a cockroach db backend.


[![Master build Status](https://travis-ci.org/Melozzola/cockroachdb-dev-test.svg?branch=master)](https://travis-ci.org/Melozzola/cockroachdb-dev-test)

## Current version

```xml
<dependency>
    <groupId>io.github.melozzola</groupId>
    <artifactId>cockroachdb-dev-test</artifactId>
    <version>0.0.1-SNAPSHOT</version>
</dependency>
```

## Usage
Following there is an example of how the rule can be used within a test.

```java

public class MyTest {
    
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
```

Given that there is one single instance of CockroachDB for all the tests, care must be taken to guarantee that the tests are not interfering with each other.

Some options could be:

* Use the ```@FixMethodOrder(MethodSorters.NAME_ASCENDING)``` and have some naming convention that guarantees the order
* Use a single connection data source.

## Build from source

```bash
git clone https://github.com/Melozzola/cockroachdb-dev-test.git
cd cockroachdb-junit
./mvnw clean install
```