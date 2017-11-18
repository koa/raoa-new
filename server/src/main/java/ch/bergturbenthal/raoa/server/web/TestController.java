package ch.bergturbenthal.raoa.server.web;

import java.security.Principal;

import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class TestController {
    @RequestMapping("/")
    public String email(final Principal principal) {
        return "Hello " + principal.getName();
    }
}
