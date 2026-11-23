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

    // Returns the account with this username, or null if there isnt one
    public StaffAccount findByUsername(String username) throws Exception {
        final String SQL = "SELECT user_id, name, password, perms::text AS perms FROM app_user WHERE username = ?";
        try (Connection connection = DatabaseConnection.open(); PreparedStatement statement = connection.prepareStatement(SQL)) {
            statement.setString(1, username);
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
