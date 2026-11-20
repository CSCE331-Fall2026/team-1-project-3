package backend.database;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;

// Looks up staff accounts in app_user. 
public final class UserRepository {
    // A signed-in staff member. Currently either perms of Staff / Cashier 
    public record StaffUser(int userId, String name, String perms) {
    }

    // A staff member + their password, only used for checking logins 
    public record StaffAccount(StaffUser user, String password) {
    }

    
    // Returns the account with this name, or null if there isnt one. app_user has no username column, so the
    // full name ("First Last") is the current username. Currently stores passwords as plaintext which is unsafe.
    // If two users have the same name the lower user_id wins, replace with username soon and hash the passwords
    
    public StaffAccount findByName(String name) throws Exception {
        final String SQL = "SELECT user_id, name, password, perms::text AS perms FROM app_user WHERE name = ? ORDER BY user_id LIMIT 1";
        try (Connection connection = DatabaseConnection.open(); PreparedStatement statement = connection.prepareStatement(SQL)) {
            statement.setString(1, name);
            try (ResultSet rs = statement.executeQuery()) {
                if (!rs.next()) {
                    return null;
                }
                StaffUser user = new StaffUser(rs.getInt("user_id"), rs.getString("name"), rs.getString("perms"));
                return new StaffAccount(user, rs.getString("password"));
            }
        }
    }
}
