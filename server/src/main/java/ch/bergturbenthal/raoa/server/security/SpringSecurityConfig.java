package ch.bergturbenthal.raoa.server.security;

import java.net.URI;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configuration.WebSecurityConfigurerAdapter;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.authority.mapping.GrantedAuthoritiesMapper;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.client.InMemoryOAuth2AuthorizedClientService;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientService;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.security.oauth2.core.user.OAuth2UserAuthority;

import com.vaadin.spring.annotation.UIScope;

import ch.bergturbenthal.raoa.server.configuration.RaoaProperties;
import ch.bergturbenthal.raoa.server.model.configuration.AccessLevel;
import ch.bergturbenthal.raoa.server.model.configuration.UserData;
import ch.bergturbenthal.raoa.server.model.configuration.UserData.UserDataBuilder;
import ch.bergturbenthal.raoa.server.service.RuntimeConfigurationService;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Configuration
@EnableWebSecurity
public class SpringSecurityConfig extends WebSecurityConfigurerAdapter {
    // @Resource(name = "authService")
    // private UserDetailsService userDetailsService;
    @Autowired
    private RaoaProperties              properties;
    @Autowired
    private RuntimeConfigurationService runtimeConfigurationService;

    @Bean
    public OAuth2AuthorizedClientService authorizedClientService(final ClientRegistrationRepository clientRegistrationRepository) {
        return new InMemoryOAuth2AuthorizedClientService(clientRegistrationRepository);
    }

    @Override
    protected void configure(final HttpSecurity http) throws Exception {
        // http.antMatcher("/**").authorizeRequests().anyRequest().authenticated();

        // http.authorizeRequests().anyRequest().authenticated().and().oauth2Login();

        final GrantedAuthoritiesMapper userAuthoritiesMapper = authorities -> {
            final ArrayList<GrantedAuthority> ret = new ArrayList<GrantedAuthority>(authorities);
            for (final GrantedAuthority grantedAuthority : authorities) {
                if (grantedAuthority instanceof OAuth2UserAuthority) {
                    final OAuth2UserAuthority new_name = (OAuth2UserAuthority) grantedAuthority;
                    final Map<String, Object> attributes = new_name.getAttributes();
                    final String email = (String) attributes.get("email");
                    runtimeConfigurationService.editGlobalConfiguration(c -> {
                        final HashMap<String, UserData> updatedUsers = new HashMap<>(c.getKnownUsers());
                        UserDataBuilder userDataBuilder;
                        final UserData existingUser = updatedUsers.get(email);
                        if (existingUser == null) {
                            userDataBuilder = UserData.builder().createdAt(Instant.now()).globalAccessLevel(AccessLevel.NONE)
                                    .admin(properties.getAdminEmail().equals(email));
                        } else {
                            userDataBuilder = existingUser.toBuilder();
                        }
                        final UserData userData = userDataBuilder.lastAccess(Instant.now()).build();
                        if (userData.isAdmin()) {
                            ret.add(new SimpleGrantedAuthority(Roles.ADMIN));
                        }
                        switch (userData.getGlobalAccessLevel()) {
                            case READ:
                                ret.add(new SimpleGrantedAuthority(Roles.SHOW));
                                break;
                            case NONE:
                                break;
                            default:
                                break;
                        }
                        updatedUsers.put(email, userData);
                        return c.toBuilder().knownUsers(Collections.unmodifiableMap(updatedUsers)).build();
                    });

                    for (final Entry<String, Object> attrEntry : attributes.entrySet()) {
                        log.info(attrEntry.getKey() + ": " + attrEntry.getValue());
                    }
                }
            }
            return ret;
        };

        http.csrf().disable().authorizeRequests()
                .antMatchers("/VAADIN/**", "/PUSH/**", "/UIDL/**", "/login", "/login/**", "/error/**", "/accessDenied/**", "/vaadinServlet/**",
                        "/oauth2/**")
                .permitAll().antMatchers("/authorized", "/**", "/").authenticated().and().oauth2Login().loginPage("/login").userInfoEndpoint()
                .userAuthoritiesMapper(userAuthoritiesMapper);

        // http.csrf().disable().exceptionHandling().authenticationEntryPoint(new LoginUrlAuthenticationEntryPoint("/login"))
        // .accessDeniedPage("/accessDenied").and().authorizeRequests()
        // .antMatchers("/VAADIN/**", "/PUSH/**", "/UIDL/**", "/login", "/login/**", "/error/**", "/accessDenied/**", "/vaadinServlet/**")
        // .permitAll().antMatchers("/authorized", "/**").fullyAuthenticated().and().oauth2Login();
    }

    // @Override
    // public void init(final WebSecurity web) {
    // web.ignoring().antMatchers("/");
    // }

    // @Bean
    // public DaoAuthenticationProvider createDaoAuthenticationProvider() {
    // final DaoAuthenticationProvider provider = new DaoAuthenticationProvider();
    //
    // provider.setUserDetailsService(userDetailsService);
    // provider.setPasswordEncoder(passwordEncoder());
    // return provider;
    // }
    //
    // @Bean
    // public BCryptPasswordEncoder passwordEncoder() {
    // return new BCryptPasswordEncoder();
    // }

    @UIScope
    @Bean
    public AuthenticatedUser currentUser() {
        final Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        final Object principal = authentication.getPrincipal();
        final OAuth2User oauthUser = (OAuth2User) principal;
        final Map<String, Object> attributes = oauthUser.getAttributes();
        final String username = (String) attributes.get("name");

        final Optional<String> email = Optional.ofNullable((String) attributes.get("email"));
        final Optional<URI> picture;
        if (attributes.containsKey("picture")) {
            picture = Optional.of((String) attributes.get("picture")).map(s -> URI.create(s));
        } else if (attributes.containsKey("avatar_url")) {
            picture = Optional.of((String) attributes.get("avatar_url")).map(s -> URI.create(s));
        } else {
            picture = Optional.empty();
        }
        return AuthenticatedUser.builder().name(username).attributes(attributes).authorities(authentication.getAuthorities()).email(email)
                .picture(picture).build();

    }
}
