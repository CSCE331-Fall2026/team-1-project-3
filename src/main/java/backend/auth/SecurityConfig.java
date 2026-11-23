package backend.auth;

import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.source.ImmutableJWKSet;
import com.nimbusds.jose.jwk.source.JWKSource;
import com.nimbusds.jose.proc.SecurityContext;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.time.Duration;
import java.util.List;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.ClientAuthenticationMethod;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.server.authorization.client.InMemoryRegisteredClientRepository;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClient;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClientRepository;
import org.springframework.security.oauth2.server.authorization.config.annotation.web.configuration.OAuth2AuthorizationServerConfiguration;
import org.springframework.security.oauth2.server.authorization.config.annotation.web.configurers.OAuth2AuthorizationServerConfigurer;
import org.springframework.security.oauth2.server.authorization.settings.AuthorizationServerSettings;
import org.springframework.security.oauth2.server.authorization.settings.ClientSettings;
import org.springframework.security.oauth2.server.authorization.settings.TokenSettings;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.LoginUrlAuthenticationEntryPoint;
import org.springframework.security.web.servlet.util.matcher.PathPatternRequestMatcher;
import org.springframework.security.web.util.matcher.MediaTypeRequestMatcher;
import org.springframework.security.web.util.matcher.RequestMatcher;


// OAuth2 login with our own app_user accounts, no google authentication using google accounts.
// This app is the login server, the react app is the client, and /api checks the token on every request.

@Configuration
public class SecurityConfig {
    public static final String CLIENT_ID = "pos-web";

    // oauth endpoints (/oauth2/authorize, /oauth2/token), sends you to /login if you arent logged in yet
    @Bean
    @Order(1)
    SecurityFilterChain authorizationServerChain(HttpSecurity http) throws Exception {
        OAuth2AuthorizationServerConfigurer authServer = OAuth2AuthorizationServerConfigurer.authorizationServer();
        LoginUrlAuthenticationEntryPoint toLogin = new LoginUrlAuthenticationEntryPoint("/login");
        MediaTypeRequestMatcher browsers = new MediaTypeRequestMatcher(MediaType.TEXT_HTML);

        http.securityMatcher(authServer.getEndpointsMatcher()).with(authServer, Customizer.withDefaults());
        http.authorizeHttpRequests(auth -> auth.anyRequest().authenticated());
        http.exceptionHandling(e -> e.defaultAuthenticationEntryPointFor(toLogin, browsers));
        return http.build();
    }

    // the api, manager stuff needs a manager token, kitchen stuff needs kitchen or manager, /api/me needs any login,
    // everything else is public for the customer kiosk
    // no sessions here since every request sends its own token
    @Bean
    @Order(2)
    SecurityFilterChain apiChain(HttpSecurity http) throws Exception {
        JwtGrantedAuthoritiesConverter roles = new JwtGrantedAuthoritiesConverter();
        roles.setAuthoritiesClaimName("roles");
        roles.setAuthorityPrefix("ROLE_");
        JwtAuthenticationConverter tokenToUser = new JwtAuthenticationConverter();
        tokenToUser.setJwtGrantedAuthoritiesConverter(roles);

        http.securityMatcher("/api/**");
        // AI generated, fixes from Pablo's review on PR #69, added /api/me for checking old tokens
        http.authorizeHttpRequests(auth -> auth.requestMatchers("/api/manager/**").hasRole("MANAGER")
                .requestMatchers("/api/kitchen/**").hasAnyRole("KITCHEN", "MANAGER")
                .requestMatchers("/api/me").authenticated().anyRequest().permitAll());
        http.oauth2ResourceServer(rs -> rs.jwt(jwt -> jwt.jwtAuthenticationConverter(tokenToUser)));
        http.sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS));
        http.csrf(csrf -> csrf.disable());
        return http.build();
    }

    // the react app, the login form, and logout
    // logout also ends the login page session, otherwise the next login would skip the password
    @Bean
    @Order(3)
    SecurityFilterChain defaultChain(HttpSecurity http) throws Exception {
        RequestMatcher logoutUrl = PathPatternRequestMatcher.withDefaults().matcher(HttpMethod.GET, "/logout");
        http.authorizeHttpRequests(auth -> auth.anyRequest().permitAll());
        http.formLogin(form -> form.loginPage("/login").permitAll());
        http.logout(logout -> logout.logoutRequestMatcher(logoutUrl).logoutSuccessUrl("/"));
        return http.build();
    }

    @Bean
    PasswordEncoder passwordEncoder() {
        return PasswordEncoderFactories.createDelegatingPasswordEncoder();
    }

    // the react app, uses PKCE
    @Bean
    RegisteredClientRepository registeredClients(@Value("${app.oauth.redirect-uris}") List<String> redirectUris) {
        ClientSettings clientSettings = ClientSettings.builder().requireProofKey(true).requireAuthorizationConsent(false).build();

        // 72 hours just cause its not in production and i dont wanna relog 150 times, change closer to end of project if we think about it
        TokenSettings tokenSettings = TokenSettings.builder().accessTokenTimeToLive(Duration.ofHours(72)).build();
        RegisteredClient.Builder client = RegisteredClient.withId(CLIENT_ID).clientId(CLIENT_ID).scope("pos");
        client.clientAuthenticationMethod(ClientAuthenticationMethod.NONE).authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE);
        client.clientSettings(clientSettings).tokenSettings(tokenSettings);

        for (String uri : redirectUris) {
            client.redirectUri(uri);
        }
        return new InMemoryRegisteredClientRepository(client.build());
    }

    // key that signs the tokens, made new on every startup, restart logs everyone out
    @Bean
    JWKSource<SecurityContext> signingKey() throws Exception {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        KeyPair pair = generator.generateKeyPair();
        RSAKey.Builder key = new RSAKey.Builder((RSAPublicKey) pair.getPublic());
        key.privateKey((RSAPrivateKey) pair.getPrivate()).keyID(UUID.randomUUID().toString());
        return new ImmutableJWKSet<>(new JWKSet(key.build()));
    }

    @Bean
    JwtDecoder jwtDecoder(JWKSource<SecurityContext> signingKey) {
        return OAuth2AuthorizationServerConfiguration.jwtDecoder(signingKey);
    }

    @Bean
    AuthorizationServerSettings authorizationServerSettings() {
        return AuthorizationServerSettings.builder().build();
    }
}
