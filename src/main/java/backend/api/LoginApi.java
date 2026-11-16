package backend.api;

import backend.database.UserRepository;
import backend.database.UserRepository.StaffUser;
import java.util.Map;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Username/password login using app_user */
@RestController
@RequestMapping("/api")
public class LoginApi {
    private final UserRepository repository = new UserRepository();

    public record LoginRequest(String username, String password) {
    }

    @PostMapping("/login")
    public ResponseEntity<?> login(@RequestBody LoginRequest request) throws Exception {
        if (request.username() == null || request.password() == null || request.username().isBlank() || request.password().isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("error", "Enter a username and password"));
        }
        StaffUser user = repository.findByLogin(request.username(), request.password());
        if (user == null) {
            return ResponseEntity.status(401).body(Map.of("error", "Wrong username or password"));
        }
        return ResponseEntity.ok(user);
    }
}
