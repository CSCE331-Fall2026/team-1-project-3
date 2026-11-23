package backend.auth;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.MediaType;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.util.HtmlUtils;

// The login page the oauth server shows, the html is in resources/templates/login.html
@RestController
public class LoginPage {

    @GetMapping(value = "/login", produces = MediaType.TEXT_HTML_VALUE)
    public String login(CsrfToken csrf, @RequestParam(required = false) String error) throws IOException {
        String page = new ClassPathResource("templates/login.html").getContentAsString(StandardCharsets.UTF_8);

        String message = "";
        if (error != null) {
            message = "<p>Wrong username or password</p>";
        }

        page = page.replace("{{csrfName}}", HtmlUtils.htmlEscape(csrf.getParameterName()));
        page = page.replace("{{csrfValue}}", HtmlUtils.htmlEscape(csrf.getToken()));
        page = page.replace("{{message}}", message);
        return page;
    }
}
