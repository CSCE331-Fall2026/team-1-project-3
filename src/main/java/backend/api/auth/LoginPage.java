package backend.api.auth;

import org.springframework.http.MediaType;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.util.HtmlUtils;

// The login page the oauth server shows, username/password/Enter/Back 
@RestController
public class LoginPage {

    @GetMapping(value = "/login", produces = MediaType.TEXT_HTML_VALUE)
    public String login(CsrfToken csrf, @RequestParam(required = false) String error) {
        String message = "";
        if (error != null) {
            message = "<p>Wrong username or password</p>";
        }
        String csrfName = HtmlUtils.htmlEscape(csrf.getParameterName());
        String csrfValue = HtmlUtils.htmlEscape(csrf.getToken());

        return """
                <!doctype html>
                <html lang="en">
                <head>
                  <meta charset="UTF-8" />
                  <meta name="viewport" content="width=device-width, initial-scale=1.0" />
                  <title>Login</title>
                  <style>
                    body { margin: 0; font-family: system-ui, sans-serif; }
                    form { height: 100vh; display: flex; flex-direction: column; align-items: center;
                           justify-content: center; gap: 12px; }
                    button, .back { font: inherit; font-size: 15px; background: #fff; color: #000; padding: 5px 10px;
                           border: 1px solid grey; border-radius: 10px; cursor: pointer; text-decoration: none; }
                    button:hover, .back:hover { border-color: #D61927; }
                  </style>
                </head>
                <body>
                  <form method="post" action="/login">
                    <input name="username" placeholder="Username" autofocus />
                    <input name="password" type="password" placeholder="Password" />
                    <input type="hidden" name="%s" value="%s" />
                    <button type="submit">Enter</button>
                    <a class="back" href="/">Back</a>
                    %s
                  </form>
                </body>
                </html>
                """.formatted(csrfName, csrfValue, message);
    }
}
