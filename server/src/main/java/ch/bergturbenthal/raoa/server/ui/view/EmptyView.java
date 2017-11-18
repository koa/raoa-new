package ch.bergturbenthal.raoa.server.ui.view;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.core.user.OAuth2User;

import com.vaadin.navigator.View;
import com.vaadin.spring.annotation.SpringView;
import com.vaadin.ui.CustomComponent;
import com.vaadin.ui.Label;
import com.vaadin.ui.VerticalLayout;

@SpringView(name = "")
public class EmptyView extends CustomComponent implements View {
    public EmptyView(final OAuth2User u2) {
        final Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        final Object principal = authentication.getPrincipal();
        final VerticalLayout rootLayout = new VerticalLayout();
        if (principal instanceof OAuth2User) {
            final OAuth2User oAuth2User = (OAuth2User) principal;
            oAuth2User.getAttributes().get("name");
            final Label label = new Label((String) oAuth2User.getAttributes().get("name"));
            rootLayout.addComponent(label);
        }
        for (final GrantedAuthority authority : authentication.getAuthorities()) {
            rootLayout.addComponent(new Label("Authority: " + authority));
        }
        setCompositionRoot(rootLayout);

    }
}
