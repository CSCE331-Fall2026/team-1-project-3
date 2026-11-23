package backend.auth;

import backend.database.UserRepository;
import backend.database.UserRepository.StaffAccount;
import org.springframework.security.authentication.InternalAuthenticationServiceException;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Component;

// Where the login page gets users from, by the username column in app_user
@Component
public class StaffAccounts implements UserDetailsService {
    private final UserRepository repository = new UserRepository();
    private final PasswordCipher cipher = PasswordCipher.fromEnvironment();

    @Override
    public UserDetails loadUserByUsername(String username) {
        StaffAccount account;
        try {
            account = repository.findByUsername(username);
        }
        catch (Exception e) {
            throw new InternalAuthenticationServiceException("Could not reach the user database", e);
        }
        if (account == null) {
            throw new UsernameNotFoundException("No such user");
        }
        // AI generated, reads the encrypted passwords from PR #73
        // decrypt it if the generator encrypted it, then {noop} = compare as plaintext
        String password = "{noop}" + cipher.readable(account.password());
        return new StaffPrincipal(account.user().userId(), account.user().name(), password, account.user().perms());
    }
}
