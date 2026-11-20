package backend.auth;

import java.util.List;
import org.springframework.security.oauth2.server.authorization.OAuth2TokenType;
import org.springframework.security.oauth2.server.authorization.token.JwtEncodingContext;
import org.springframework.security.oauth2.server.authorization.token.OAuth2TokenCustomizer;
import org.springframework.stereotype.Component;

// Adds user id, name, and role to the access token so the api and react app know who it is
@Component
public class StaffClaims implements OAuth2TokenCustomizer<JwtEncodingContext> {

    @Override
    public void customize(JwtEncodingContext context) {
        if (!OAuth2TokenType.ACCESS_TOKEN.equals(context.getTokenType())) {
            return;
        }
        if (!(context.getPrincipal().getPrincipal() instanceof StaffPrincipal staff)) {
            return;
        }
        context.getClaims().claim("user_id", staff.getUserId()).claim("name", staff.getUsername());
        context.getClaims().claim("perms", staff.getPerms()).claim("roles", List.of(staff.getPerms().toUpperCase()));
    }
}
