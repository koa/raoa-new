package ch.bergturbenthal.raoa.server.model.configuration;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;

import lombok.Builder;
import lombok.NonNull;
import lombok.Value;

@Value
@Builder(toBuilder = true)
public class GlobalConfigurationData {
    public static Function<GlobalConfigurationData, GlobalConfigurationData> editUser(final String email,
            final Function<UserData, UserData> userEditor) {
        return c -> {
            final Map<String, UserData> users = c.getKnownUsers();
            final UserData existingUser = users.get(email);
            if (existingUser == null) {
                return c;
            }
            final UserData updatedUser = userEditor.apply(existingUser);
            if (updatedUser == existingUser || updatedUser.equals(existingUser)) {
                return c;
            }
            final HashMap<String, UserData> editedUsers = new HashMap<>(users);
            editedUsers.put(email, updatedUser);
            return c.toBuilder().knownUsers(editedUsers).build();
        };
    }

    @NonNull
    private Map<String, UserData> knownUsers;
}
