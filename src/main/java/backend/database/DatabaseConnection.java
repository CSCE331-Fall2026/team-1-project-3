package backend.database;

import backend.config.DbSetup;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import java.sql.Connection;
import java.sql.SQLException;

/**
 * Hands out pooled PostgreSQL connections. Callers close them as before; close() returns the
 * connection to the pool, which rolls back unfinished work and resets autoCommit.
 */
public final class DatabaseConnection {
    private static volatile HikariDataSource pool;

    private DatabaseConnection() {
    }

    /** Signature kept from the DriverManager version so the repositories stay untouched. */
    public static Connection open() throws SQLException, ClassNotFoundException {
        return pool().getConnection();
    }

    /** Creates the pool without connecting; a background thread opens the first connection. */
    public static void start() {
        pool();
    }

    private static HikariDataSource pool() {
        HikariDataSource p = pool;
        if (p == null) {
            synchronized (DatabaseConnection.class) {
                if (pool == null)
                    pool = new HikariDataSource(config(DbSetup.jdbcUrl(), DbSetup.user(), DbSetup.password()));
                p = pool;
            }
        }
        return p;
    }

    static HikariConfig config(String url, String user, String password) {
        HikariConfig config = new HikariConfig();
        config.setPoolName("db");
        config.setJdbcUrl(url);
        config.setUsername(user);
        config.setPassword(password);
        // Shared course DB: 1 warm connection for latency, a 2nd only under concurrency.
        config.setMaximumPoolSize(2);
        config.setMinimumIdle(1);
        config.setConnectionTimeout(5_000); // fail a request fast when the DB is down; connects took ~1.2 s in prod
        config.setInitializationFailTimeout(-1); // never block or fail startup on the DB
        config.setKeepaliveTime(120_000); // keep the idle connection alive through NAT/proxy timeouts
        // Defaults kept: idleTimeout 10 min retires the 2nd connection; maxLifetime 30 min recycles all.
        config.addDataSourceProperty("connectTimeout", 15); // seconds, per TCP connect
        config.addDataSourceProperty("socketTimeout", 30); // seconds, replaces setNetworkTimeout(30 s)
        return config;
    }

    /** Test seam: swap in a pool pointed at a disposable database. */
    static synchronized void usePool(HikariDataSource testPool) {
        pool = testPool;
    }
}
