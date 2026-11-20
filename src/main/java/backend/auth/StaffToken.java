// AI generated, fixes from Pablo's review on PR #69 (old tokens after a restart, and checkout trusting the browser's userId)
package backend.auth;

import java.security.Principal;
import java.util.Map;
import org.springframework.http.ResponseEntity;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

// Reads who's logged in from the token the server signed, so the browser cant just say who it is
@RestController
public class StaffToken {

    // the app calls this before showing the signed in page, a token from before a server restart gets a 401 here
    @GetMapping("/api/me")
    public ResponseEntity<Map<String, Object>> me(Principal principal) {
        Jwt token = tokenOf(principal);
        if (token == null) {
            return ResponseEntity.status(401).build();
        }
        Map<String, Object> user = Map.of("userId", userId(principal), "name", token.getClaimAsString("name"),
                "perms", token.getClaimAsString("perms"));
        return ResponseEntity.ok(user);
    }

    // the logged in staff member's user_id, or null if there's no token (customer kiosk)
    public static Integer userId(Principal principal) {
        Jwt token = tokenOf(principal);
        if (token == null) {
            return null;
        }
        Object id = token.getClaim("user_id");
        if (id instanceof Number number) {
            return number.intValue();
        }
        return null;
    }

    private static Jwt tokenOf(Principal principal) {
        if (principal instanceof JwtAuthenticationToken jwtAuth) {
            return jwtAuth.getToken();
        }
        return null;
    }
}
