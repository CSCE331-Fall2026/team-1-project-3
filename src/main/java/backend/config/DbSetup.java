package backend.config;

/** Loads database configuration from the process environment. */
public final class DbSetup {
    private DbSetup() {
    }

    public static String jdbcUrl() {
        String host = required("DB_HOST");
        String port = required("DB_PORT");
        validatePort(port);
        return "jdbc:postgresql://" + host + ":" + port + "/" + required("DB_NAME");
    }

    public static String user() {
        return required("DB_USER");
    }

    public static String password() {
        return required("DB_PASSWORD");
    }

    private static String required(String variable) {
        String value = System.getenv(variable);
        if (value == null || value.isBlank()) {
            throw new IllegalStateException("Missing required database environment variable: " + variable);
        }
        return value;
    }

    private static void validatePort(String value) {
        try {
            int portNumber = Integer.parseInt(value);
            if (portNumber < 1 || portNumber > 65535) {
                throw new NumberFormatException();
            }
        } catch (NumberFormatException exception) {
            throw new IllegalStateException("DB_PORT must be a number from 1 to 65535");
        }
    }
}
