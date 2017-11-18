package ch.bergturbenthal.raoa.server.ui.view;

import javax.annotation.security.RolesAllowed;

import com.vaadin.navigator.View;
import com.vaadin.spring.annotation.SpringView;
import com.vaadin.ui.CustomComponent;

import ch.bergturbenthal.raoa.server.security.Roles;

@SpringView(name = "config")
@RolesAllowed(Roles.ADMIN)
public class ConfigView extends CustomComponent implements View {

}
