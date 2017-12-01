package ch.bergturbenthal.raoa.server.ui.view;

import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.format.FormatStyle;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Locale;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;

import javax.annotation.security.RolesAllowed;

import com.vaadin.data.ValueProvider;
import com.vaadin.data.provider.DataProvider;
import com.vaadin.navigator.View;
import com.vaadin.spring.annotation.SpringView;
import com.vaadin.ui.CheckBox;
import com.vaadin.ui.ComboBox;
import com.vaadin.ui.CustomComponent;
import com.vaadin.ui.Grid;
import com.vaadin.ui.Grid.Column;
import com.vaadin.ui.UI;

import ch.bergturbenthal.raoa.server.model.configuration.AccessLevel;
import ch.bergturbenthal.raoa.server.model.configuration.GlobalConfigurationData;
import ch.bergturbenthal.raoa.server.model.configuration.UserData;
import ch.bergturbenthal.raoa.server.security.Roles;
import ch.bergturbenthal.raoa.server.service.RuntimeConfigurationService;

@SpringView(name = "Users")
@RolesAllowed(Roles.ADMIN)
public class EditUsersView extends CustomComponent implements View {
    public EditUsersView(final RuntimeConfigurationService configService) {
        final GlobalConfigurationData globalConfiguration = configService.getGlobalConfiguration();
        final Map<String, UserData> knownUsers = globalConfiguration.getKnownUsers();
        final Set<Entry<String, UserData>> entrySet = knownUsers.entrySet();
        final Grid<Entry<String, UserData>> grid = new Grid<>(DataProvider.ofCollection(entrySet));
        final Column<Entry<String, UserData>, String> emailColumn = grid.addColumn(entry -> entry.getKey());
        emailColumn.setCaption("eMail");

        final AtomicReference<DateTimeFormatter> dateTimeFormatter = new AtomicReference<DateTimeFormatter>(
                DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM));
        final AtomicReference<ZoneOffset> currentZone = new AtomicReference<ZoneOffset>(ZoneOffset.UTC);

        addInstantColumn(grid, "created at", (entry) -> entry.getValue().getCreatedAt(), dateTimeFormatter, currentZone);
        addInstantColumn(grid, "last login", (entry) -> entry.getValue().getLastAccess(), dateTimeFormatter, currentZone);

        final Column<Entry<String, UserData>, Boolean> adminColumn = grid.addColumn(entry -> entry.getValue().isAdmin());
        adminColumn.setCaption("admin");

        adminColumn.setEditorComponent(new CheckBox(), (bean, fieldvalue) -> configService.editGlobalConfiguration(
                GlobalConfigurationData.editUser(bean.getKey(), u -> u.toBuilder().admin(fieldvalue.booleanValue()).build())));
        adminColumn.setEditable(true);

        final Column<Entry<String, UserData>, AccessLevel> accessLevelColumn = grid.addColumn(entry -> entry.getValue().getGlobalAccessLevel());
        accessLevelColumn.setCaption("Access");

        final ComboBox<AccessLevel> accessLevelEditor = new ComboBox<>("", Arrays.asList(AccessLevel.values()));
        accessLevelEditor.setEmptySelectionAllowed(false);
        accessLevelColumn.setEditorComponent(accessLevelEditor, (bean, fieldvalue) -> configService
                .editGlobalConfiguration(GlobalConfigurationData.editUser(bean.getKey(), u -> u.toBuilder().globalAccessLevel(fieldvalue).build())));
        accessLevelColumn.setEditable(true);

        grid.getEditor().setEnabled(true);
        grid.setSizeFull();
        setCompositionRoot(grid);
        addAttachListener(event -> {
            final UI ui = event.getConnector().getUI();
            final Locale locale = ui.getLocale();
            DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM).withLocale(locale);
            final int timezoneOffset = ui.getPage().getWebBrowser().getRawTimezoneOffset();
            currentZone.set(ZoneOffset.ofTotalSeconds(timezoneOffset / 1000));

        });
    }

    private void addInstantColumn(final Grid<Entry<String, UserData>> grid, final String caption,
            final ValueProvider<Entry<String, UserData>, Instant> valueProvider, final AtomicReference<DateTimeFormatter> dateTimeFormatter,
            final AtomicReference<ZoneOffset> currentZone) {
        final Column<Entry<String, UserData>, String> createdAtColumn = grid
                .addColumn(entry -> dateTimeFormatter.get().format(valueProvider.apply(entry).atOffset(currentZone.get())));
        createdAtColumn.setCaption(caption);
        final Comparator<Entry<String, UserData>> comparing = Comparator.comparing(valueProvider);
        createdAtColumn.setComparator(comparing::compare);
    }
}
