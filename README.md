# JUnit rule for CockroacDB testing
This library contains a [Junit 4](http://junit.org/junit4/) Rule to enable easy tests when using [CokroachDB](https://www.cockroachlabs.com/) in a java project.

[![Master build Status](https://travis-ci.org/Melozzola/cockroachdb-junit.svg?branch=master)](https://travis-ci.org/Melozzola/cockroachdb-junit)

## Current version

```xml
<dependency>
    <groupId>com.melozzola</groupId>
    <artifactId>cockroachdb-junit</artifactId>
    <version>0.0.1-SNAPSHOT</version>
</dependency>
```

## Usage
Following there is an example of how the rule can be used within a test.

```java

public class MyTest {
    
    // In this example we use the Hikari datasource to connect to CockroachDB.
    private static HikariDataSource DS;
    
    @ClassRule
    public static CockroachDB CRDB = builder()
        .logger(System.out)// Redirect rules log messages to std out
        .stdErr(System.err)// Redirect cockroach db error messages to std err
        .stdOut(System.out)// Redirect cockroach db messages to std out
        // More options here...
        .build((dbPort, httpPort, ctx) -> {
            
            System.out.println("Received started callback. Db Port:" + dbPort + ", Http Port: " + httpPort);
            
            // Put the current time in the context. This can be retrieved in the tests (see below).
            ctx.put("TIME", new Date());
            
            // Initialise the datasource using the randomply assigne database port.
            DS = new HikariDataSource();
            DS.setJdbcUrl("jdbc:postgresql://localhost:"+dbPort+"/test?sslmode=disable");
            DS.setUsername("root");
            DS.setMaximumPoolSize(10);
            
            // Add some test data
            initData(DS, ctx);
    });
    
    @Test
    public void test() throws Exception {
        
        // Here Cockroach is up and running and I can execute my test against it.
        
        // The CockroachDB context can be accessed if needed. For example:
        final Date time = CRDB.getFromCtxOrThrow("TIME", Date.class);
    }
    
    private static void initData(final DataSource ds, final Map<String, Object> ctx){
        // initialize with test data...
    } 
}
```

Given that there is one single instance of CockroachDB for all the tests, care must be taken to guarantee that the tests are not interfering with each other.

Some options could be:

* Use the ```@FixMethodOrder(MethodSorters.NAME_ASCENDING)``` and have some naming convention that guarantees the order
* Use a single connection data source.

The rule can be used as a ```@Rule``` but it would mean that for each test there is a dedicated CockroachDB process, which could result too heavy.

## Build from source

```bash
git clone https://github.com/Melozzola/cockroachdb-junit.git
cd cockroachdb-junit
./mvnw clean install
```

## TODOs

* Junit 5: Provide same functionality using Junit 5 extension (Rules have been removed from Junit 5)
* Spring: Provide same functionality for spring using ```AbstractTestExecutionListener```
* Docker: Instead of using the binaries, use docker containers.