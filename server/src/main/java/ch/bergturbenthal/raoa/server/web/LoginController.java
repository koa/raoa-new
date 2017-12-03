package ch.bergturbenthal.raoa.server.web;

import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.WebAttributes;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.SessionAttribute;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Controller
public class LoginController {
    @RequestMapping("/login")
    public String login(@RequestParam(name = "error", required = false) final boolean error,
            @SessionAttribute(name = WebAttributes.AUTHENTICATION_EXCEPTION, required = false) final AuthenticationException exception) {
        if (error) {
            log.info("Error");
        }
        if (exception != null) {
            log.warn("Error on login", exception);
        }
        return "login";
    }
}
