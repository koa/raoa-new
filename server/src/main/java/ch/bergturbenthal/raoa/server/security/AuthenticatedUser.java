package ch.bergturbenthal.raoa.server.security;

import java.net.URI;
import java.util.Collection;
import java.util.Map;
import java.util.Optional;

import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.core.user.OAuth2User;

import lombok.Builder;
import lombok.NonNull;
import lombok.Value;

@Value
@Builder
public class AuthenticatedUser implements OAuth2User {
    @NonNull
    private String                                 name;
    @NonNull
    private Collection<? extends GrantedAuthority> authorities;
    @NonNull
    private Map<String, Object>                    attributes;
    @NonNull
    private Optional<String>                       email;
    private Optional<URI>                          picture;
}
