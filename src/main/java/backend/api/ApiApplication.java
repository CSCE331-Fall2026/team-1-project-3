package backend.api;

import backend.config.DbSetup;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/** Starts the REST API that the React client talks to. */
@SpringBootApplication
public class ApiApplication {
    public static void main(String[] args) {
        // DbSetup is otherwise read lazily per request, so a missing variable would only show up as a 500.
        try {
            DbSetup.jdbcUrl();
            DbSetup.user();
            DbSetup.password();
        } catch (IllegalStateException e) {
            System.err.println("Cannot start: " + e.getMessage());
            System.exit(1);
        }
        SpringApplication.run(ApiApplication.class, args);
    }
}
