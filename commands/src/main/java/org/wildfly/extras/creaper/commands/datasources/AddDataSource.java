package org.wildfly.extras.creaper.commands.datasources;

import org.wildfly.extras.creaper.commands.foundation.offline.xml.GroovyXmlTransform;
import org.wildfly.extras.creaper.commands.foundation.offline.xml.Subtree;
import org.wildfly.extras.creaper.core.CommandFailedException;
import org.wildfly.extras.creaper.core.ServerVersion;
import org.wildfly.extras.creaper.core.offline.OfflineCommand;
import org.wildfly.extras.creaper.core.offline.OfflineCommandContext;
import org.wildfly.extras.creaper.core.online.OnlineCommand;
import org.wildfly.extras.creaper.core.online.OnlineCommandContext;
import org.wildfly.extras.creaper.core.online.operations.Address;
import org.wildfly.extras.creaper.core.online.operations.Batch;
import org.wildfly.extras.creaper.core.online.operations.Operations;
import org.wildfly.extras.creaper.core.online.operations.Values;
import org.wildfly.extras.creaper.core.online.operations.admin.Administration;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

/**
 * <p>A generic command for creating new datasource. For well-known databases, it's preferred to use the subclasses,
 * because they apply some common configuration. This class is supposed to be used for unknown databases or when
 * absolute control is desired.</p>
 *
 * <p><b>Note that the datasources are always created as <i>disabled</i>, unless {@link Builder#enableAfterCreate()}
 * is used!</b></p>
 *
 * @see AddDb2DataSource
 * @see AddMssqlDataSource
 * @see AddMysqlDataSource
 * @see AddOracleDataSource
 * @see AddPostgresPlusDataSource
 * @see AddPostgreSqlDataSource
 * @see AddSybaseDataSource
 */
public class AddDataSource implements OnlineCommand, OfflineCommand {
    private final String name;
    private final String connectionUrl;
    private final String jndiName;
    private final boolean enableAfterCreation;
    private final boolean replaceExisting;

    // parameters that can be modified by subclasses
    protected Integer allocationRetry;
    protected Integer allocationRetryWaitMillis;
    protected Boolean allowMultipleUsers;
    protected Boolean backgroundValidation;
    protected Integer backgroundValidationMillis;
    protected Integer blockingTimeoutMillis;
    protected String checkValidConnectionSql;
    protected Boolean connectable;
    protected Map<String, String> connectionProperties;
    protected String datasourceClass;
    protected String driverClass;
    protected String driverName;
    protected String exceptionSorterClass;
    protected Map<String, String> exceptionSorterProperties;
    protected Integer idleTimeoutMinutes;
    protected Boolean jta;
    protected String managedConnectionPool;
    protected Integer maxPoolSize;
    protected Integer minPoolSize;
    protected String newConnectionSql;
    protected String password;
    protected PoolFlushStrategy flushStrategy;
    protected Boolean prefill;
    protected Integer preparedStatementCacheSize;
    protected Integer queryTimeout;
    protected String reauthPluginClass;
    protected Map<String, String> reauthPluginProperties;
    protected String securityDomain;
    protected Boolean setTxQueryTimeout;
    protected Boolean sharePreparedStatements;
    protected Boolean spy;
    protected String staleConnectionCheckerClass;
    protected Map<String, String> staleConnectionCheckerProperties;
    protected Boolean statisticsEnabled;
    protected TrackStatementType trackStatements;
    protected TransactionIsolation transactionIsolation;
    protected String urlDelimiter;
    protected String urlSelectorStrategyClass;
    protected Boolean useCcm;
    protected Boolean useFastFailAllocation;
    protected Boolean useJavaContext;
    protected Boolean useStrictMinPoolSize;
    protected Integer useTryLock;
    protected String username;
    protected Boolean validateOnMatch;
    protected String validConnectionCheckerClass;
    protected Map<String, String> validConnectionCheckerProperties;

    protected AddDataSource(Builder builder) {
        this.name = builder.name;
        this.connectionUrl = builder.connectionUrl;
        this.jndiName = builder.jndiName;
        this.enableAfterCreation = builder.enableAfterCreation;
        this.replaceExisting = builder.replaceExisting;

        this.driverClass = builder.driverClass;
        this.datasourceClass = builder.datasourceClass;
        this.connectionProperties = builder.connectionProperties;
        this.driverName = builder.driverName;
        this.username = builder.username;
        this.password = builder.password;
        this.securityDomain = builder.securityDomain;
        this.reauthPluginClass = builder.reauthPluginClass;
        this.reauthPluginProperties = builder.reauthPluginProperties;
        this.jta = builder.jta;
        this.useJavaContext = builder.useJavaContext;
        this.connectable = builder.connectable;
        this.managedConnectionPool = builder.managedConnectionPool;
        this.maxPoolSize = builder.maxPoolSize;
        this.minPoolSize = builder.minPoolSize;
        this.statisticsEnabled = builder.statisticsEnabled;
        this.useCcm = builder.useCcm;
        this.prefill = builder.prefill;
        this.useStrictMinPoolSize = builder.useStrictMinPoolSize;
        this.flushStrategy = builder.flushStrategy;
        this.allowMultipleUsers = builder.allowMultipleUsers;
        this.newConnectionSql = builder.newConnectionSql;
        this.transactionIsolation = builder.transactionIsolation;
        this.urlDelimiter = builder.urlDelimiter;
        this.urlSelectorStrategyClass = builder.urlSelectorStrategyClass;
        this.checkValidConnectionSql = builder.checkValidConnectionSql;
        this.validateOnMatch = builder.validateOnMatch;
        this.backgroundValidation = builder.backgroundValidation;
        this.backgroundValidationMillis = builder.backgroundValidationMillis;
        this.useFastFailAllocation = builder.useFastFailAllocation;
        this.staleConnectionCheckerClass = builder.staleConnectionCheckerClass;
        this.staleConnectionCheckerProperties = builder.staleConnectionCheckerProperties;
        this.exceptionSorterClass = builder.exceptionSorterClass;
        this.exceptionSorterProperties = builder.exceptionSorterProperties;
        this.spy = builder.spy;
        this.blockingTimeoutMillis = builder.blockingTimeoutWaitMillis;
        this.idleTimeoutMinutes = builder.idleTimeoutMinutes;
        this.setTxQueryTimeout = builder.setTxQueryTimeout;
        this.queryTimeout = builder.queryTimeout;
        this.useTryLock = builder.useTryLock;
        this.allocationRetry = builder.allocationRetry;
        this.allocationRetryWaitMillis = builder.allocationRetryWaitMillis;
        this.validConnectionCheckerClass = builder.validConnectionCheckerClass;
        this.validConnectionCheckerProperties = builder.validConnectionCheckerProperties;
        this.preparedStatementCacheSize = builder.preparedStatementsCacheSize;
        this.trackStatements = builder.trackPreparedStatements;
        this.sharePreparedStatements = builder.sharePreparedStatements;
    }

    @Override
    public final void apply(OnlineCommandContext ctx) throws IOException, CommandFailedException {
        modifyIfNeeded(ctx.version);

        Operations ops = new Operations(ctx.client);

        Address dsAddress = Address.subsystem("datasources").and("data-source", name);

        if (replaceExisting) {
            try {
                ops.removeIfExists(dsAddress);
                new Administration(ctx.client).reloadIfRequired();
            } catch (Exception e) {
                throw new CommandFailedException("Failed to remove existing datasource " + name, e);
            }
        }

        Values values = Values.empty()
            .andOptional("connection-url", connectionUrl)
            .andOptional("jndi-name", jndiName)
            .andOptional("driver-name", driverName)
            .andOptional("user-name", username)
            .andOptional("password", password)
            .andOptional("jta", jta)
            .andOptional("use-java-context", useJavaContext)
            .andOptional("connectable", connectable)
            .andOptional("mcp", managedConnectionPool)
            .andOptional("max-pool-size", maxPoolSize)
            .andOptional("min-pool-size", minPoolSize)
            .andOptional("statistics-enabled", statisticsEnabled)
            .andOptional("driver-class", driverClass)
            .andOptional("datasource-class", datasourceClass)
            .andOptional("pool-use-strict-min", useStrictMinPoolSize)
            .andOptional("allow-multiple-users", allowMultipleUsers)
            .andOptional("pool-prefill", prefill)
            .andOptional("new-connection-sql", newConnectionSql)
            .andOptional("url-delimiter", urlDelimiter)
            .andOptional("url-selector-strategy-class-name", urlSelectorStrategyClass)
            .andOptional("check-valid-connection-sql", checkValidConnectionSql)
            .andOptional("validate-on-match", validateOnMatch)
            .andOptional("background-validation", backgroundValidation)
            .andOptional("background-validation-millis", backgroundValidationMillis)
            .andOptional("use-fast-fail", useFastFailAllocation)
            .andOptional("stale-connection-checker-class-name", staleConnectionCheckerClass)
            .andObjectOptional("stale-connection-checker-properties", Values.fromMap(staleConnectionCheckerProperties))
            .andOptional("exception-sorter-class-name", exceptionSorterClass)
            .andObjectOptional("exception-sorter-properties", Values.fromMap(exceptionSorterProperties))
            .andOptional("valid-connection-checker-class-name", validConnectionCheckerClass)
            .andObjectOptional("valid-connection-checker-properties", Values.fromMap(validConnectionCheckerProperties))
            .andOptional("spy", spy)
            .andOptional("blocking-timeout-wait-millis", blockingTimeoutMillis)
            .andOptional("idle-timeout-minutes", idleTimeoutMinutes)
            .andOptional("set-tx-query-timeout", setTxQueryTimeout)
            .andOptional("query-timeout", queryTimeout)
            .andOptional("use-try-lock", useTryLock)
            .andOptional("allocation-retry", allocationRetry)
            .andOptional("allocation-retry-wait-millis", allocationRetryWaitMillis)
            .andOptional("security-domain", securityDomain)
            .andOptional("reauth-plugin-class-name", reauthPluginClass)
            .andObjectOptional("reauth-plugin-properties", Values.fromMap(reauthPluginProperties))
            .andOptional("use-ccm", useCcm)
            .andOptional("prepared-statements-cache-size", preparedStatementCacheSize)
            .andOptional("share-prepared-statements", sharePreparedStatements)
            .and("enabled", enableAfterCreation); // enough to enable/disable on WildFly, and AS7 can handle it too
        if (flushStrategy != null) values = values.and("flush-strategy", flushStrategy.value());
        if (transactionIsolation != null) values = values.and("transaction-isolation", transactionIsolation.value());
        if (trackStatements != null) values = values.and("track-statements", trackStatements.value());

        Batch batch = new Batch();
        batch.add(dsAddress, values);

        if (connectionProperties != null) {
            for (Map.Entry<String, String> entry : connectionProperties.entrySet()) {
                batch.add(dsAddress.and("connection-properties", entry.getKey()),
                        Values.of("value", entry.getValue()));
            }
        }

        if (enableAfterCreation && ctx.version.lessThan(ServerVersion.VERSION_2_0_0)) {
            // AS7 needs this to actually enable the datasource, because the "enabled" attribute in fact doesn't work
            //
            // for WildFly, the "enabled" attribute works fine and this must not be called (enabling twice is an error)
            batch.invoke("enable", dsAddress);
        }

        ops.batch(batch);
    }

    @Override
    public final void apply(OfflineCommandContext ctx) throws CommandFailedException, IOException {
        modifyIfNeeded(ctx.version);

        GroovyXmlTransform transform = GroovyXmlTransform.of(AddDataSource.class)
                .subtree("datasources", Subtree.subsystem("datasources"))

                .parameter("poolName", name)
                .parameter("connectioUrl", connectionUrl)
                .parameter("jndiName", jndiName)
                .parameter("driverName", driverName)
                .parameter("userName", username)
                .parameter("password", password)
                .parameter("jta", jta)
                .parameter("useJavaContext", useJavaContext)
                .parameter("connectable", connectable)
                .parameter("maxPoolSize", maxPoolSize)
                .parameter("minPoolSize", minPoolSize)
                .parameter("statisticsEnabled", statisticsEnabled)
                .parameter("driverClass", driverClass)
                .parameter("datasourceClass", datasourceClass)
                .parameter("useStrictMinPoolSize", useStrictMinPoolSize)
                .parameter("flushStrategy", flushStrategy == null ? null : flushStrategy.value())
                .parameter("allowMultipleUsers", allowMultipleUsers)
                .parameter("mcp", managedConnectionPool)
                .parameter("prefill", prefill)
                .parameter("newConnectionSql", newConnectionSql)
                .parameter("transactionIsolation", transactionIsolation == null ? null : transactionIsolation.value())
                .parameter("urlDelimiter", urlDelimiter)
                .parameter("urlSelectorStrategyClassName", urlSelectorStrategyClass)
                .parameter("checkValidConnectionSql", checkValidConnectionSql)
                .parameter("validateOnMatch", validateOnMatch)
                .parameter("backgroundValidation", backgroundValidation)
                .parameter("backgroundValidationMillis", backgroundValidationMillis)
                .parameter("useFastFail", useFastFailAllocation)
                .parameter("staleConnectionCheckerClassName", staleConnectionCheckerClass)
                .parameter("staleConnectionCheckerProperties", staleConnectionCheckerProperties)
                .parameter("exceptionSorterClassName", exceptionSorterClass)
                .parameter("exceptionSorterProperties", exceptionSorterProperties)
                .parameter("validConnectionCheckerClassName", validConnectionCheckerClass)
                .parameter("validConnectionCheckerProperties", validConnectionCheckerProperties)
                .parameter("spy", spy)
                .parameter("blockingTimeoutWaitMillis", blockingTimeoutMillis)
                .parameter("idleTimeoutMinutes", idleTimeoutMinutes)
                .parameter("setTxQueryTimeout", setTxQueryTimeout)
                .parameter("queryTimeout", queryTimeout)
                .parameter("useTryLock", useTryLock)
                .parameter("allocationRetry", allocationRetry)
                .parameter("allocationRetryWaitMillis", allocationRetryWaitMillis)
                .parameter("securityDomain", securityDomain)
                .parameter("reauthPluginClassName", reauthPluginClass)
                .parameter("reauthPluginProperties", reauthPluginProperties)
                .parameter("useCcm", useCcm)
                .parameter("preparedStatementsCacheSize", preparedStatementCacheSize)
                .parameter("trackStatements", trackStatements == null ? null : trackStatements.value())
                .parameter("sharePreparedStatements", sharePreparedStatements)
                .parameter("connectionProperties", connectionProperties)

                .parameter("enableAfterCreation", enableAfterCreation)
                .parameter("replaceExisting", replaceExisting)

                .build();

        ctx.client.apply(transform);
    }

    protected void modifyIfNeeded(ServerVersion serverVersion) {
        // designed for override
    }

    @Override
    public final String toString() {
        return "AddDataSource " + name;
    }

    /**
     * Builder for configuration attributes of a datasource. The {@code THIS} type parameter is only meant
     * to be used by subclasses. If you're not inheriting from this class, don't use it.
     *
     * @see <a href="http://wildscribe.github.io/JBoss%20EAP/6.2.0/subsystem/datasources/data-source/">
     *        http://wildscribe.github.io/JBoss%20EAP/6.2.0/subsystem/datasources/data-source/</a>
     */
    public static class Builder<THIS extends Builder> {
        private String name;
        private boolean enableAfterCreation = false;
        private boolean replaceExisting = false;

        private Integer allocationRetry;
        private Integer allocationRetryWaitMillis;
        private Boolean allowMultipleUsers;
        private Boolean backgroundValidation;
        private Integer backgroundValidationMillis;
        private Integer blockingTimeoutWaitMillis;
        private String checkValidConnectionSql;
        private Boolean connectable;
        private Map<String, String> connectionProperties = new HashMap<String, String>();
        private String connectionUrl;
        private String datasourceClass;
        private String driverClass;
        private String driverName;
        private String exceptionSorterClass;
        private Map<String, String> exceptionSorterProperties = new HashMap<String, String>();
        private Integer idleTimeoutMinutes;
        private String jndiName;
        private Boolean jta;
        private String managedConnectionPool;
        private Integer maxPoolSize;
        private Integer minPoolSize;
        private String newConnectionSql;
        private String password;
        private PoolFlushStrategy flushStrategy;
        private Boolean prefill;
        private Integer preparedStatementsCacheSize;
        private Integer queryTimeout;
        private String reauthPluginClass;
        private Map<String, String> reauthPluginProperties = new HashMap<String, String>();
        private String securityDomain;
        private Boolean setTxQueryTimeout;
        private Boolean sharePreparedStatements;
        private Boolean spy;
        private String staleConnectionCheckerClass;
        private Map<String, String> staleConnectionCheckerProperties = new HashMap<String, String>();
        private Boolean statisticsEnabled;
        private TrackStatementType trackPreparedStatements;
        private TransactionIsolation transactionIsolation;
        private String urlDelimiter;
        private String urlSelectorStrategyClass;
        private Boolean useCcm;
        private Boolean useFastFailAllocation;
        private Boolean useJavaContext;
        private Boolean useStrictMinPoolSize;
        private Integer useTryLock;
        private String username;
        private Boolean validateOnMatch;
        private String validConnectionCheckerClass;
        private Map<String, String> validConnectionCheckerProperties = new HashMap<String, String>();

        public Builder(String name) {
            if (name == null) {
                throw new IllegalArgumentException("Name of the data-source must be specified as non null value");
            }

            this.name = name;
        }

        /**
         * Defines the JDBC driver connection URL.
         */
        public final THIS connectionUrl(String connectionUrl) {
            this.connectionUrl = connectionUrl;
            return (THIS) this;
        }

        /**
         * Defines the JDBC driver the datasource should use. It is a symbolic name matching the the name of installed
         * driver. In case the driver is deployed as jar, the name is the name of deployment unit.
         */
        public final THIS driverName(String driverName) {
            this.driverName = driverName;
            return (THIS) this;
        }

        /**
         * Specifies the JNDI name for the datasource.
         */
        public final THIS jndiName(String jndiName) {
            this.jndiName = jndiName;
            return (THIS) this;
        }

        /**
         * What driver class will be used for connection.
         * From version 4 of jdbc spec the driver jar class contains information about default driver class for the
         * particular driver under META-INF/services/java.sql.Driver.
         * If you don't need some special handling this will be resolved to default on its own.
         */
        public final THIS driverClass(String driverClass) {
            this.driverClass = driverClass;
            return (THIS) this;
        }

        /**
         * What datasource class will be used for connection.
         *
         * <p>Datasource class has to be defined as fully qualified datasource class name of jdbc driver.</p>
         *
         * <p>By default driver class is used for establishing connection but you can enforce the datasource class
         * would be use. If you use datasource you will probably needs to define connection properties as well.</p>
         */
        public final THIS datasourceClass(String datasourceClass) {
            this.datasourceClass = datasourceClass;
            return (THIS) this;
        }

        /**
         * Adding connection property.
         *
         * <p>When datasource class is defined, then JCA injects these properties by calling setters based on property
         * name (i.e., if connection property name is {@code user}, then JCA calls {@code Datasource.setUser(value)}).
         * When driver class is defined, these properties are passed
         * to {@link java.sql.Driver#connect(String, java.util.Properties)}.</p>
         */
        public final THIS addConnectionProperty(String name, String value) {
            connectionProperties.put(name, value);
            return (THIS) this;
        }

        /**
         * See {@link #addConnectionProperty(String, String)}
         */
        public final THIS addConnectionProperty(String name, boolean value) {
            connectionProperties.put(name, Boolean.toString(value));
            return (THIS) this;
        }

        /**
         * Adding connection properties in bulk. See {@link #addConnectionProperty(String, String)}.
         */
        public final THIS addConnectionProperties(Map<String, String> connectionProperties) {
            this.connectionProperties.putAll(connectionProperties);
            return (THIS) this;
        }

        /**
         *  Specify the user name and password used when creating a new connection.
         */
        public final THIS usernameAndPassword(String username, String password) {
            this.username = username;
            this.password = password;
            return (THIS) this;
        }

        /**
         * Defines whether the connector should be started on startup.
         */
        public final THIS enableAfterCreate() {
            this.enableAfterCreation = true;
            return (THIS) this;
        }

        /**
         * Specify whether to replace the existing datasource based on its name (pool-name).
         * By default existing datasource is not replaced and exception is thrown.
         * <b>Note that when enabled, this can cause server reload!</b>
         */
        public final THIS replaceExisting() {
            this.replaceExisting = true;
            return (THIS) this;
        }

        /**
         *  Enable JTA integration.
         */
        public final THIS jta(Boolean jta) {
            this.jta = jta;
            return (THIS) this;
        }

        /**
         * Setting this to {@code false} will bind the datasource into global JNDI.
         */
        public final THIS useJavaContext(Boolean useJavaContext) {
            this.useJavaContext = useJavaContext;
            return (THIS) this;
        }

        /**
         * Setting this to {@code true} will let to use CMR.
         */
        public final THIS connectable(Boolean connectable) {
            this.connectable = connectable;
            return (THIS) this;
        }

        /**
         * Specifies the maximum number of connections for a pool. No more connections will be created in each sub-pool.
         */
        public final THIS maxPoolSize(int maxPoolSize) {
            this.maxPoolSize = maxPoolSize;
            return (THIS) this;
        }

        /**
         * Specifies the minimum number of connections for a pool.
         */
        public final THIS minPoolSize(int minPoolSize) {
            this.minPoolSize = minPoolSize;
            return (THIS) this;
        }

        /**
         * Sets whether runtime statistics are enabled or not.
         */
        public final THIS statisticsEnabled(Boolean statisticsEnabled) {
            this.statisticsEnabled = statisticsEnabled;
            return (THIS) this;
        }

        /**
         * Enable the use of a cached connection manager.
         */
        public final THIS useCcm(Boolean useCcm) {
            this.useCcm = useCcm;
            return (THIS) this;
        }

        /**
         * SQL statement to execute whenever a connection is added to the JCA connection pool.
         */
        public final THIS newConnectionSql(String newConnectionSql) {
            this.newConnectionSql = newConnectionSql;
            return (THIS) this;
        }

        /**
         * Defines isolation level for connections created under this datasource.
         */
        public final THIS transactionIsolation(TransactionIsolation transactionIsolation) {
            this.transactionIsolation = transactionIsolation;
            return (THIS) this;
        }

        /**
         * Specifies the delimeter for URLs in connection-url for HA datasources
         */
        public final THIS urlDelimiter(String urlDelimiter) {
            this.urlDelimiter = urlDelimiter;
            return (THIS) this;
        }

        /**
         * A class that implements org.jboss.jca.adapters.jdbc.URLSelectorStrategy
         */
        public final THIS urlSelectorStrategyClass(String urlSelectorStrategyClass) {
            this.urlSelectorStrategyClass = urlSelectorStrategyClass;
            return (THIS) this;
        }

        /**
         *  Whether to attempt to prefill the connection pool.
         */
        public final THIS prefill(Boolean prefill) {
            this.prefill = prefill;
            return (THIS) this;
        }


        /**
         * Security domain name to be used for authentication to datasource.
         */
        public final THIS securityDomain(String securityDomain) {
            this.securityDomain = securityDomain;
            return (THIS) this;
        }

        /**
         * Setting reauth plugin class name.
         */
        public final THIS reauthPluginClass(String reauthPluginClass) {
            this.reauthPluginClass = reauthPluginClass;
            return (THIS) this;
        }

        /**
         * Property for {@link #reauthPluginClass}
         */
        public final THIS addReauthPluginProperty(String name, String value) {
            reauthPluginProperties.put(name, value);
            return (THIS) this;
        }

        /**
         * Property for {@link #reauthPluginClass}
         */
        public final THIS addReauthPluginProperty(String name, boolean value) {
            reauthPluginProperties.put(name, Boolean.toString(value));
            return (THIS) this;
        }

        /**
         * If the {@link #minPoolSize} should be considered a strictly.
         */
        public final THIS useStrictMinPoolSize(Boolean useStrictMinPoolSize) {
            this.useStrictMinPoolSize = useStrictMinPoolSize;
            return (THIS) this;
        }

        /**
         * How poool should be flushed. There is predefined strategies by JCA.
         * See {@link PoolFlushStrategy}.
         */
        public final THIS flushStrategy(PoolFlushStrategy flushStrategy) {
            this.flushStrategy = flushStrategy;
            return (THIS) this;
        }

        /**
         * SQL statement to check validity of a pool connection.
         * May be used when connection is taken from pool to use.
         */
        public final THIS checkValidConnectionSql(String checkValidConnectionSql) {
            this.checkValidConnectionSql = checkValidConnectionSql;
            return (THIS) this;
        }

        /**
         * Validation will be done on connection factory attempt to match
         * a managed connection for a given set.
         *
         * <p>Typically exclusive to use of {@link #backgroundValidation}.</p>
         */
        public final THIS validateOnMatch(Boolean validateOnMatch) {
            this.validateOnMatch = validateOnMatch;
            return (THIS) this;
        }

        /**
         * Connections should be validated on a background thread
         * (versus being validated prior to use).
         *
         * <p>Typically exclusive to use of {@link #validateOnMatch}.</p>
         */
        public final THIS backgroundValidation(Boolean backgroundValidation) {
            this.backgroundValidation = backgroundValidation;
            return (THIS) this;
        }

        /**
         * Amount of time that background validation will run.
         */
        public final THIS backgroundValidationMillis(Integer backgroundValidationMillis) {
            this.backgroundValidationMillis = backgroundValidationMillis;
            return (THIS) this;
        }

        /**
         * Whether fail a connection allocation on the first connection if it
         * is invalid (true) or keep trying until the pool is exhausted of all potential connections (false).
         */
        public final THIS useFastFailAllocation(Boolean useFastFailAllocation) {
            this.useFastFailAllocation = useFastFailAllocation;
            return (THIS) this;
        }

        /**
         * An org.jboss.jca.adapters.jdbc.StaleConnectionChecker.
         */
        public final THIS staleConnectionCheckerClass(String staleConnectionCheckerClass) {
            this.staleConnectionCheckerClass = staleConnectionCheckerClass;
            return (THIS) this;
        }

        /**
         * Property for {@link #staleConnectionCheckerClass}
         */
        public final THIS addStaleConnectionCheckerProperty(String name, String value) {
            staleConnectionCheckerProperties.put(name, value);
            return (THIS) this;
        }

        /**
         * Property for {@link #staleConnectionCheckerClass}
         */
        public final THIS addStaleConnectionCheckerProperty(String name, boolean value) {
            staleConnectionCheckerProperties.put(name, Boolean.toString(value));
            return (THIS) this;
        }

        /**
         * An org.jboss.jca.adapters.jdbc.ValidConnectionChecker.
         */
        public final THIS validConnectionCheckerClass(String validConnectionCheckerClass) {
            this.validConnectionCheckerClass = validConnectionCheckerClass;
            return (THIS) this;
        }

        /**
         * Property for {@link #validConnectionCheckerClass}
         */
        public final THIS addValidConnectionCheckerProperty(String name, String value) {
            validConnectionCheckerProperties.put(name, value);
            return (THIS) this;
        }

        /**
         * Property for {@link #validConnectionCheckerClass}
         */
        public final THIS addValidConnectionCheckerProperty(String name, boolean value) {
            validConnectionCheckerProperties.put(name, Boolean.toString(value));
            return (THIS) this;
        }

        /**
         * org.jboss.jca.adapters.jdbc.ExceptionSorter
         */
        public final THIS exceptionSorterClass(String exceptionSorterClass) {
            this.exceptionSorterClass = exceptionSorterClass;
            return (THIS) this;
        }

        /**
         * Property for {@link #exceptionSorterClass}
         */
        public final THIS addExceptionSorterProperty(String name, String value) {
            exceptionSorterProperties.put(name, value);
            return (THIS) this;
        }

        /**
         * Property for {@link #exceptionSorterClass}
         */
        public final THIS addExceptionSorterProperty(String name, boolean value) {
            exceptionSorterProperties.put(name, Boolean.toString(value));
            return (THIS) this;
        }

        /**
         * An org.jboss.jca.adapters.jdbc.ExceptionSorter.
         */
        public final THIS spy(Boolean spy) {
            this.spy = spy;
            return (THIS) this;
        }

        /**
         * The blocking-timeout-millis element indicates the maximum time in
         * milliseconds to block while waiting for a connection before throwing an exception.
         */
        public final THIS blockingTimeoutWaitMillis(Integer blockingTimeoutWaitMillis) {
            this.blockingTimeoutWaitMillis = blockingTimeoutWaitMillis;
            return (THIS) this;
        }

        /**
         * The idle-timeout-minutes elements indicates the maximum time in minutes
         * a connection may be idle before being closed.
         */
        public final THIS idleTimeoutMinutes(Integer idleTimeoutMinutes) {
            this.idleTimeoutMinutes = idleTimeoutMinutes;
            return (THIS) this;
        }

        /**
         * Whether to set the query timeout based on the time remaining until
         * transaction timeout, any configured query timeout will be used if there is no transaction.
         */
        public final THIS setTxQueryTimeout(Boolean setTxQueryTimeout) {
            this.setTxQueryTimeout = setTxQueryTimeout;
            return (THIS) this;
        }

        /**
         * Specifies if multiple users will access the datasource through the getConnection(user, password)
         * method and hence if the internal pool type should account for that
         */
        public final THIS allowMultipleUsers(Boolean allowMultipleUsers) {
            this.allowMultipleUsers = allowMultipleUsers;
            return (THIS) this;
        }

        /**
         * Any configured query timeout in seconds.
         */
        public final THIS queryTimeout(Integer queryTimeout) {
            this.queryTimeout = queryTimeout;
            return (THIS) this;
        }

        /**
         * Any configured timeout for internal locks on the resource adapter
         * objects in seconds.
         */
        public final THIS useTryLock(Integer useTryLock) {
            this.useTryLock = useTryLock;
            return (THIS) this;
        }

        /**
         * The allocation retry element indicates the number of times that allocating
         * a connection should be tried before throwing an exception.
         */
        public final THIS allocationRetry(Integer allocationRetry) {
            this.allocationRetry = allocationRetry;
            return (THIS) this;
        }

        /**
         * The allocation retry wait millis element indicates the time in milliseconds
         * to wait between retrying to allocate a connection
         */
        public final THIS allocationRetryWaitMillis(Integer allocationRetryWaitMillis) {
            this.allocationRetryWaitMillis = allocationRetryWaitMillis;
            return (THIS) this;
        }

        /**
         * Whether to check for unclosed statements when a connection is returned
         * to the pool and result sets are closed when a statement is closed/return
         * to the prepared statement cache.
         */
        public final THIS trackPreparedStatements(TrackStatementType trackStatements) {
            this.trackPreparedStatements = trackStatements;
            return (THIS) this;
        }

        /**
         * Whether to share prepare statements, i.e. whether asking for same
         * statement twice without closing uses the same underlying prepared statement.
         */
        public final THIS sharePreparedStatements(Boolean sharePreparedStatements) {
            this.sharePreparedStatements = sharePreparedStatements;
            return (THIS) this;
        }

        /**
         * The number of prepared statements per connection in an LRU cache
         */
        public final THIS preparedStatementsCacheSize(Integer preparedStatementsCacheSize) {
            this.preparedStatementsCacheSize = preparedStatementsCacheSize;
            return (THIS) this;
        }

        /**
         * Defines the ManagedConnectionPool implementation,
         * f.ex. org.jboss.jca.core.connectionmanager.pool.mcp.SemaphoreArrayListManagedConnectionPool
         */
        public final THIS managedConnectionPool(String managedConnectionPool) {
            this.managedConnectionPool = managedConnectionPool;
            return (THIS) this;
        }

        public AddDataSource build() {
            check();
            return new AddDataSource(this);
        }

        protected final void check() {
            if (connectionUrl == null) {
                throw new IllegalArgumentException("URL of the connection must be specified as non null value");
            }
            if (jndiName == null) {
                throw new IllegalArgumentException("jndiName must be specified as non null value");
            }
            if (driverName == null) {
                throw new IllegalArgumentException("driverName must be specified as non null value");
            }
            if (minPoolSize != null && minPoolSize < 0) {
                throw new IllegalArgumentException("minPoolSize must be greater than 0 but it's set to "
                        + minPoolSize);
            }
            if (maxPoolSize != null && maxPoolSize < 0) {
                throw new IllegalArgumentException("maxPoolSize must be greater than 0 but it's set to "
                        + maxPoolSize);
            }
            if (maxPoolSize != null && minPoolSize != null && minPoolSize > maxPoolSize) {
                throw new IllegalArgumentException("maxPoolSize has to be greater than minPoolSize but they are set to "
                        + minPoolSize + " and " + maxPoolSize);
            }
            if (backgroundValidationMillis != null && backgroundValidationMillis < 0) {
                throw new IllegalArgumentException("backgroundValidationMilis has to be greater than 0 but it's set to "
                        + backgroundValidationMillis);
            }
            if (queryTimeout != null && queryTimeout < 0) {
                throw new IllegalArgumentException("queryTimeout has to be greater than 0 but it's set to "
                        + queryTimeout);
            }
            if (useTryLock != null && useTryLock < 0) {
                throw new IllegalArgumentException("useTryLock has to be greater than 0 but it's set to "
                        + useTryLock);
            }
            if (allocationRetry != null && allocationRetry < 0) {
                throw new IllegalArgumentException("allocationRetry has to be greater than 0 but it's set to "
                        + allocationRetry);
            }
            if (allocationRetryWaitMillis != null && allocationRetryWaitMillis < 0) {
                throw new IllegalArgumentException("allocationRetryWaitMillis has to be greater than 0 but it's set to "
                        + allocationRetryWaitMillis);
            }
            if (preparedStatementsCacheSize != null && preparedStatementsCacheSize < 0) {
                throw new IllegalArgumentException("preparedStatementCacheSize has to be greater than 0 but it's set to "
                        + preparedStatementsCacheSize);
            }
            if (securityDomain != null && username != null) {
                throw new IllegalArgumentException("Setting username is invalid in combination with securityDomain");
            }
        }
    }
}
