package backend.database;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;

/** Looks up staff accounts in app_user. */
public final class UserRepository {
    /** A signed-in staff member. Currently either perms of Staff / Cashier */
    public record StaffUser(int userId, String name, String perms) {
    }

    /**
     * Returns the user whose name and password both match, or null if none does. app_user has no username
     * column, so the full name ("First Last") is the current username. Currently stores passwords as plaintext which is unsafe.
     */
    public StaffUser findByLogin(String name, String password) throws Exception {
        final String SQL = "SELECT user_id, name, perms::text AS perms FROM app_user "
                + "WHERE name = ? AND password = ? ORDER BY user_id LIMIT 1";
        try (Connection connection = DatabaseConnection.open(); PreparedStatement statement = connection.prepareStatement(SQL)) {
            statement.setString(1, name);
            statement.setString(2, password);
            try (ResultSet rs = statement.executeQuery()) {
                if (!rs.next()) {
                    return null;
                }
                return new StaffUser(rs.getInt("user_id"), rs.getString("name"), rs.getString("perms"));
            }
        }
    }
}
