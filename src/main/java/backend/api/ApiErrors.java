package backend.api;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.sql.SQLException;
import java.util.Map;

/** Gives the client a small JSON error instead of a stack trace. */
@RestControllerAdvice
public class ApiErrors {
    private static final Logger log = LoggerFactory.getLogger(ApiErrors.class);

    @ExceptionHandler({ SQLException.class, ClassNotFoundException.class })
    public ResponseEntity<Map<String, String>> database(Exception e) {
        if (e instanceof SQLException s) {
            String state = s.getSQLState();
            // 22007 invalid_datetime_format, 22008 datetime_field_overflow: a bad start/end on the chart endpoints.
            if ("22007".equals(state) || "22008".equals(state)) {
                log.warn("Rejected date input: {}", s.getMessage());
                return ResponseEntity.badRequest().body(Map.of("error", "Invalid date or time"));
            }
            // 23505 unique_violation: today only addMenuItem can reach here with it (checkout catches its own).
            if ("23505".equals(state)) {
                log.warn("Rejected duplicate key: {}", s.getMessage());
                return ResponseEntity.status(409).body(Map.of("error", "That ID already exists"));
            }
        }
        log.error("Database request failed", e);
        return ResponseEntity.status(500).body(Map.of("error", "Database request failed"));
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<Map<String, String>> malformedBody(HttpMessageNotReadableException e) {
        return ResponseEntity.badRequest().body(Map.of("error", "Malformed request body"));
    }
}
