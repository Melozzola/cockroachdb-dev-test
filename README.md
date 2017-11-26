# JUnit rule for CockroacDB testing
This library contains a Junit rule to make it easier the development of java projects that uses cokroachDB.

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

```java

// In this example we use the Hikari datasource to connect to CockroachDB.
    private static HikariDataSource DS;

    @ClassRule
    public static CockroachDB CRDB = builder()
        .logger(System.out)
        .stdErr(System.err)
        .stdOut(System.out)
        .build((dbPort, httpPort, ctx) -> {

            System.out.println("Received start callback. Db Port:" + dbPort + ", Http Port: " + httpPort);

            // Put the current time in the context
            ctx.put("TIME", new Date());

            // Initialise the datasource using the randomply assigne database port.
            DS = new HikariDataSource();
            DS.setJdbcUrl("jdbc:postgresql://localhost:"+dbPort+"/test?sslmode=disable");
            DS.setUsername("root");
            DS.setMaximumPoolSize(10);

    });

    @Test
    public void test() throws Exception {
        
        // Here Cockroach is up and running and I can execute my test against it.
        
    }

```
