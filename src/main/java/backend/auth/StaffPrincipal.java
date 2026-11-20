package backend.auth;

import java.util.List;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.User;

// A logged in app_user row, role is ROLE_MANAGER or ROLE_CASHIER 
// should eventually extend for ROLE_KITCHEN
public class StaffPrincipal extends User {
    private final int userId;
    private final String perms;

    public StaffPrincipal(int userId, String name, String password, String perms) {
        super(name, password, List.of(new SimpleGrantedAuthority("ROLE_" + perms.toUpperCase())));
        this.userId = userId;
        this.perms = perms;
    }

    public int getUserId() {
        return userId;
    }

    public String getPerms() {
        return perms;
    }
}
