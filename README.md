# Utility library for dev test java applications that use CockroacDB
This library contains a [Junit 4](http://junit.org/junit4/) Rule to enable easy tests when using [CokroachDB](https://www.cockroachlabs.com/) in a java project.

One of the most used strategies to implement dev tests that require a database is to use an in memory database. 
Anyway, the in memory database is providing providing just a subset of the features that the production databases are offering, making testing difficult or incomplete.

Anther option would be to mock (or stub) the jdbc layer. Even though mocking and stubbing are powerful test techniques, mocking the jdbc layer can be time consuming and hard to maintain.

Leveraging on CockroachDB straight forward installation, start up speed and possibility to configure the storage to reside in memory, this library enables you to quickly write
dev tests for your java application that uses CockroachDB.

This library should not be used for performance tests or 'heavy' integration tests. 

[![Master build Status](https://travis-ci.org/Melozzola/cockroachdb-dev-test.svg?branch=master)](https://travis-ci.org/Melozzola/cockroachdb-dev-test)

## Current version

The most recent release is ```1.0.1```

The Maven group ID is ```io.github.melozzola```, and there are four different artifacts you can choose from:

* ```cockroachdb-junit4```: Enables you to use the cockroach db junit 4 rule
* ```cockroachdb-junit4 (with classifier 'all')```: Same as ```cockroachdb-junit4``` but the artifact is free from dependencies (fat jar with packages relocated)
* ```cockroachdb-test-core```: Enables you to install cockroach, start a process and shut it down. It is useful when you are using different test framework (e.g. spring-test)
* ```cockroachdb-test-core (with classifier 'all')```: Same as ```cockroachdb-test-core``` but the artifact is free from dependencies (fat jar with packages relocated)

Following there is an example of how to use the ```cockroachdb-junit4``` in your maven and gradle project

**Gradle**

```groovy
    testCompile('io.github.melozzola:cockroachdb-junit4:1.0.1-SNAPSHOT')
```

**Maven**

```xml
<dependency>
    <groupId>io.github.melozzola</groupId>
    <artifactId>cockroachdb-junit4</artifactId>
    <version>1.0.1</version>
    <scope>test</scope>
</dependency>
```

or, if you prefer a fat jar:

**Gradle**

```groovy
    testCompile('io.github.melozzola:cockroachdb-junit4:1.0.1-SNAPSHOT:all@jar')
```

**Maven**

```xml
<dependency>
    <groupId>io.github.melozzola</groupId>
    <artifactId>cockroachdb-junit4</artifactId>
    <version>1.0.1</version>
    <scope>test</scope>
    <classifier>all</classifier>
</dependency>
```

### Change Log

**1.0.0**
- First release.

**1.0.1**
- Fixed bug where CockroachDB#Listener was package private.
- Implemented a safer way to shut down the cockroach process.
- Made 1.1.7 the default version.


## Usage

### Junit 4

Following there is an example of how the rule can be used in a junit test.

```java

public class MyTest {
    
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
```

Depending on the configuration, there might be a single instance of cockroach shared among different tests.
If that's the case, care must be taken to guarantee that the tests are not interfering with each other.

Some options could be:

* Use the ```@FixMethodOrder(MethodSorters.NAME_ASCENDING)``` and have some naming convention that guarantees the order
* Use a single connection data source.
* Use different database names, but same schema

### Spring

Spring tests often requires the spring context. 
The problem is that the cockroachDB port is randomly generated, therefore we need a way to inject the correct jdbc url to
the DataSource.

One way to achieve this is to use an ```ApplicationContextInitializer``` in conjunction with a ```TestExecutionListener```.

The ```TestExecutionListener``` will start up the CockroachDB process before the execution of the tests.
The ```ProcessDetails``` are added to a thread local variable such that the ```ApplicationContextInitializer``` can grab the value of the random port.

```java
public class CrDbTestExecutionListener implements TestExecutionListener {

    private static final Logger logger = LoggerFactory.getLogger(CrDbApplicationContextInitializer.class);

    private Cockroach cockroach;

    private static ThreadLocal<ProcessDetails> crdbProcessDetails = new ThreadLocal<ProcessDetails>();
    public static ProcessDetails getCrDbProcessDetailsForThisThreadOrThrow(){
        final ProcessDetails details = crdbProcessDetails.get();
        if (details == null){
            throw new IllegalStateException("CockroacDB process details not initialised");
        }
        return details;
    }

    @Override
    public void beforeTestClass(TestContext testContext) throws Exception {
        logger.info("Starting cockroach db");
        final Cockroach cockroach = builder()
                .stdOut(System.out)
                .stdErr(System.err)
                .build();
        final ProcessDetails processDetails = cockroach.startUp();
        crdbProcessDetails.set(processDetails);
        logger.info("Cockroach db up and running. Details: " + processDetails);
    }

    @Override
    public void afterTestClass(TestContext testContext) throws Exception {
        logger.info("Shutting down cockroach db");
        if (cockroach != null) {
            cockroach.shutDown();
        }
        logger.info("cockroach db shut down");
    }
}
```

The ```ApplicationContextInitializer``` is reading the CockroachDB port and host from the thread local variable initialised by the ```TestExecutionListener```
and it will add a custom ```PropertySource``` capable of resolving the ```spring.datasource.url``` property.
At this point any bean that relies on the ```spring.datasource.url``` will get the url pointing to the instance of CockroachDB started up by the ```TestExecutionListener```


```java
@Order(0)
public class CrDbApplicationContextInitializer implements ApplicationContextInitializer<ConfigurableApplicationContext> {

    private static final Logger logger = LoggerFactory.getLogger(CrDbApplicationContextInitializer.class);

    private static final String DATASOURCE_URL_PROPERTY = "spring.datasource.url";

    @Override
    public void initialize(ConfigurableApplicationContext configurableApplicationContext) {

        logger.info("CrDbApplicationContextInitializer initialize");

        final ProcessDetails details = CrDbTestExecutionListener.getCrDbProcessDetailsForThisThreadOrThrow();
        final String jdbcUrl = String.format("jdbc:postgresql://%s:%d/consustags?sslmode=disable", details.getHost(), details.getPort());
        configurableApplicationContext.getEnvironment().getPropertySources().addFirst(new PropertySource<Object>("junit") {
            @Nullable
            @Override
            public Object getProperty(@NonNull String propertyName) {
                if (propertyName.equalsIgnoreCase(DATASOURCE_URL_PROPERTY)){
                    logger.info("Serving " + propertyName + ": " + jdbcUrl);
                    return jdbcUrl;
                }else{
                    return null;
                }
            }
        });
    }
}
```

The test will have to be annotated in order to enable the custom ```ApplicationContextInitializer``` and ```TestExecutionListener```

```java
@RunWith(SpringRunner.class)
@ContextConfiguration(initializers = {CrDbApplicationContextInitializer.class})
@TestExecutionListeners({CrDbTestExecutionListener.class})
public class CrDbTest{
    
    // The DataSource bean should be initialised with the url served by the custom PropertySource added to the spring 
    // application context in the CrDbApplicationContextInitializer
    // Instead of the DataSource the bean, you could inject a JdbcTemplate, a DAO or a Service...whatever object that gives access to te database.
    @Autowired
    public DataSource datasource;
    
    @Test
    public void test(){
        // here we can use the DataSource...
    }
    
}
```

## The cockroachDB binary

The library will install the CockroachDB into a temp directory and unless the temp directory is cleared, the subsequent test execution will rely on the cached binary.
By default the installation is downloading the binary from the CockroachDB website, but it can be configured to install from a classpath resource or a different url.

To customise the installation:

* Add to your classpath the file ```cockroachdb-junit-override.properties``` (under the root)
* Customise the cockroachdb-junit-override.properties, for example adding the following properties:

```properties
# If we specify the version 'v1.1.3' and the machine OS is a mac, the installation will pick up the file /binaries/cockroach-v1.1.3.darwin-10.9-amd64.tgz
# from the classpath.
darwin.v1.1.3.classpath=classpath:/binaries/cockroach-v1.1.3.darwin-10.9-amd64.tgz
darwin.v1.1.3.classpath.compression=tgz

# Same as above but for linux
linux.v1.1.3.web=classpath:/binaries/cockroach-v1.1.3.linux-amd64.tgz
linux.v1.1.3.web.compression=tgz

# Same as above but for windows
win.v1.1.3.web=classpath:/binaries/cockroach-v1.1.3.windows-6.2-amd64.zip
win.v1.1.3.web.compression=zip

# This mechanism allows to add support for new releases as well, jut add the entry for the new release
# The version 'v1.2-alpha.20171204' needs to be specified when building the Cockroach object
darwin.v1.2-alpha.20171204.web=https://binaries.cockroachdb.com/cockroach-v1.2-alpha.20171204.darwin-10.9-amd64.tgz
darwin.v1.2-alpha.20171204.web.compression=tgz
```

## Build from source

```bash
git clone https://github.com/Melozzola/cockroachdb-dev-test.git
cd cockroachdb-junit
./mvnw clean install
```