package backend.api.auth;

import backend.database.UserRepository;
import backend.database.UserRepository.StaffAccount;
import org.springframework.security.authentication.InternalAuthenticationServiceException;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Component;

// Where the login page gets users from, username is the full name in app_user "first last"
@Component
public class StaffAccounts implements UserDetailsService {
    private final UserRepository repository = new UserRepository();

    @Override
    public UserDetails loadUserByUsername(String username) {
        StaffAccount account;
        try {
            account = repository.findByName(username);
        }
        catch (Exception e) {
            throw new InternalAuthenticationServiceException("Could not reach the user database", e);
        }
        if (account == null) {
            throw new UsernameNotFoundException("No such user");
        }
        // {noop} = plaintext password, if we switch to bcrypt hashes later they just need to start with {bcrypt}
        String password = "{noop}" + account.password();
        return new StaffPrincipal(account.user().userId(), account.user().name(), password, account.user().perms());
    }
}
