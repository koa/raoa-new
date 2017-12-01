package ch.bergturbenthal.raoa.server.ui.view;

import javax.annotation.security.RolesAllowed;

import com.vaadin.data.Binder;
import com.vaadin.navigator.View;
import com.vaadin.spring.annotation.SpringView;
import com.vaadin.ui.CustomComponent;
import com.vaadin.ui.FormLayout;
import com.vaadin.ui.TextField;

import ch.bergturbenthal.raoa.server.configuration.RaoaProperties;
import ch.bergturbenthal.raoa.server.security.Roles;

@SpringView(name = "Show Configuration")
@RolesAllowed(Roles.ADMIN)
public class ConfigView extends CustomComponent implements View {
    public ConfigView(final RaoaProperties configuration) {
        final Binder<RaoaProperties> binder = new Binder<>();
        binder.setBean(configuration);
        final FormLayout mainLayout = new FormLayout();

        final TextField emailField = new TextField("Admin eMail");
        emailField.setReadOnly(true);
        emailField.setWidth(50, Unit.EM);
        binder.bind(emailField, source -> source.getAdminEmail(), null);
        mainLayout.addComponent(emailField);

        final TextField configBaseField = new TextField("Configuration Base");
        configBaseField.setReadOnly(true);
        configBaseField.setWidth(50, Unit.EM);
        binder.forField(configBaseField).bind(source -> source.getConfigurationBase().getAbsolutePath(), null);
        mainLayout.addComponent(configBaseField);

        final TextField storageBaseField = new TextField("Storage Base");
        storageBaseField.setReadOnly(true);
        storageBaseField.setWidth(50, Unit.EM);
        binder.forField(storageBaseField).bind(source -> source.getStorageBase().getAbsolutePath(), null);
        mainLayout.addComponent(storageBaseField);

        mainLayout.setMargin(true);
        setCompositionRoot(mainLayout);
    }
}
