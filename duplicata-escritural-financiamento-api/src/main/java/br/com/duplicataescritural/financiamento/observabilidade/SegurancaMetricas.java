package br.com.duplicataescritural.financiamento.observabilidade;

import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.core.annotation.Order;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.ProviderManager;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.security.web.SecurityFilterChain;

@Configuration(proxyBeanMethods = false)
@Profile("monitoramento")
public class SegurancaMetricas {
    private final String usuario;
    private final String senhaHash;

    public SegurancaMetricas(Environment ambiente) {
        if (!ambiente.acceptsProfiles(Profiles.of("local"))
                || !"127.0.0.1".equals(ambiente.getProperty("server.address"))) {
            throw new IllegalStateException("Monitoramento exige perfil local e bind em 127.0.0.1");
        }
        if (!"/actuator".equals(ambiente.getProperty("management.endpoints.web.base-path", "/actuator"))
                || !"prometheus".equals(ambiente.getProperty("management.endpoints.web.path-mapping.prometheus", "prometheus"))
                || ambiente.containsProperty("management.server.port")
                || ambiente.containsProperty("management.server.address")) {
            throw new IllegalStateException("Monitoramento exige /actuator/prometheus sem porta ou endereço próprios de gerenciamento");
        }
        usuario = ambiente.getProperty("financiamento.monitoramento.usuario", "");
        senhaHash = ambiente.getProperty("financiamento.monitoramento.senha-hash", "");
        if (!usuario.matches("[A-Za-z0-9][A-Za-z0-9._-]{2,63}")
                || !senhaHash.matches("\\$2[aby]\\$(1[0-4])\\$[./A-Za-z0-9]{53}")) {
            throw new IllegalStateException("Monitoramento exige usuário explícito e hash BCrypt válido com custo entre 10 e 14");
        }
    }

    @Bean
    @Order(1)
    @ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
    SecurityFilterChain metricasPrometheus(HttpSecurity http) throws Exception {
        // O provider pertence somente a esta cadeia; Basic não autentica participantes de negócio.
        var usuarios = new InMemoryUserDetailsManager(User.withUsername(usuario).password(senhaHash)
                .authorities("METRICAS").build());
        var provider = new DaoAuthenticationProvider(usuarios);
        provider.setPasswordEncoder(new BCryptPasswordEncoder());
        return http.securityMatcher("/actuator/prometheus", "/actuator/prometheus/**")
                .authenticationManager(new ProviderManager(provider))
                .csrf(AbstractHttpConfigurer::disable)
                .formLogin(AbstractHttpConfigurer::disable)
                .logout(AbstractHttpConfigurer::disable)
                .requestCache(AbstractHttpConfigurer::disable)
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .httpBasic(basic -> basic.realmName("metricas"))
                .authorizeHttpRequests(requests -> requests
                        .requestMatchers(HttpMethod.GET, "/actuator/prometheus").hasAuthority("METRICAS")
                        .anyRequest().denyAll())
                .build();
    }
}
