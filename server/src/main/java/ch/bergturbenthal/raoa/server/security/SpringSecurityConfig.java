package ch.bergturbenthal.raoa.server.security;

import java.util.ArrayList;
import java.util.Map;

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

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Configuration
@EnableWebSecurity
public class SpringSecurityConfig extends WebSecurityConfigurerAdapter {
    // @Resource(name = "authService")
    // private UserDetailsService userDetailsService;

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
                    final Object email = attributes.get("email");
                    if ("andreas.koenig@berg-turbenthal.ch".equals(email)) {
                        ret.add(new SimpleGrantedAuthority("ROLE_ADMIN"));
                    }
                    // for (final Entry<String, Object> attrEntry : attributes.entrySet()) {
                    // log.info(attrEntry.getKey() + ": " + attrEntry.getValue());
                    // }
                }
            }
            return ret;
        };
        http.csrf().disable().authorizeRequests()
                .antMatchers("/VAADIN/**", "/PUSH/**", "/UIDL/**", "/login", "/login/**", "/error/**", "/accessDenied/**", "/vaadinServlet/**",
                        "/oauth2/**")
                .permitAll().antMatchers("/authorized", "/**", "/").authenticated().and().oauth2Login().userInfoEndpoint()
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
    public OAuth2User currentUser() {
        final Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        final Object principal = authentication.getPrincipal();
        return (OAuth2User) principal;
    }
}
