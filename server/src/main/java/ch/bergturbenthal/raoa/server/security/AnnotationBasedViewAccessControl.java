package ch.bergturbenthal.raoa.server.security;

import java.util.Set;
import java.util.stream.Collectors;

import javax.annotation.security.RolesAllowed;

import org.springframework.beans.factory.ObjectFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Component;

import com.vaadin.spring.access.ViewAccessControl;
import com.vaadin.ui.UI;

@Component
public class AnnotationBasedViewAccessControl implements ViewAccessControl {

    @Autowired
    private ObjectFactory<AuthenticatedUser> currentUserFactory;

    @Autowired
    private ApplicationContext               applicationContext;

    @Override
    public boolean isAccessGranted(final UI ui, final String beanName) {
        final Class<?> viewClass = applicationContext.getType(beanName);
        if (viewClass.isAnnotationPresent(RolesAllowed.class)) {
            final Set<String> assignedRoles = currentUserFactory.getObject().getAuthorities().stream().map(ga -> ga.getAuthority())
                    .collect(Collectors.toSet());
            final RolesAllowed rolesAnnotation = viewClass.getAnnotation(RolesAllowed.class);
            for (final String role : rolesAnnotation.value()) {
                if (assignedRoles.contains(role)) {
                    return true;
                }
            }
            return false;
        }
        return true;
    }

}
