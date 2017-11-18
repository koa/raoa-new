package ch.bergturbenthal.raoa.server.ui.view;

import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.core.user.OAuth2User;

import com.vaadin.navigator.View;
import com.vaadin.spring.annotation.SpringView;
import com.vaadin.ui.CustomComponent;
import com.vaadin.ui.Label;
import com.vaadin.ui.VerticalLayout;

@SpringView(name = "")
public class EmptyView extends CustomComponent implements View {
    public EmptyView(final OAuth2User u2) {
        final VerticalLayout rootLayout = new VerticalLayout();
        final OAuth2User oAuth2User = u2;
        oAuth2User.getAttributes().get("name");
        final Label label = new Label((String) oAuth2User.getAttributes().get("name"));
        rootLayout.addComponent(label);
        for (final GrantedAuthority authority : u2.getAuthorities()) {
            rootLayout.addComponent(new Label("Authority: " + authority));
        }
        setCompositionRoot(rootLayout);

    }
}
