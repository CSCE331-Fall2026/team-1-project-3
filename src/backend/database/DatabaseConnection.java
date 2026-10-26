package backend.database;

import backend.config.DbSetup;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

/** Creates configured PostgreSQL connections for the application backend. */
public final class DatabaseConnection {
    private static final int LOGIN_TIMEOUT_SECONDS = 15;
    private static final int NETWORK_TIMEOUT_MILLISECONDS = 30000;

    private DatabaseConnection() {
    }

    public static Connection open() throws SQLException, ClassNotFoundException {
        Class.forName("org.postgresql.Driver");
        DriverManager.setLoginTimeout(LOGIN_TIMEOUT_SECONDS);
        Connection connection = DriverManager.getConnection(DbSetup.jdbcUrl(), DbSetup.user(), DbSetup.password());
        try {
            connection.setNetworkTimeout(Runnable::run, NETWORK_TIMEOUT_MILLISECONDS);
            return connection;
        } catch (SQLException exception) {
            try {
                connection.close();
            } catch (SQLException closeException) {
                exception.addSuppressed(closeException);
            }
            throw exception;
        }
    }
}
