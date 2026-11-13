package br.com.duplicataescritural.signataria.seguranca;

import java.io.IOException;

import br.com.duplicataescritural.signataria.escrituracao.infraestrutura.persistencia.SacadorRepository;
import jakarta.servlet.DispatcherType;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.AnonymousAuthenticationFilter;

@Configuration(proxyBeanMethods = false)
public class SegurancaEscrituracao {

    @Bean
    SecurityFilterChain seguranca(HttpSecurity http, Environment environment, SacadorRepository sacadores) throws Exception {
        boolean local = environment.acceptsProfiles(Profiles.of("local"));
        if (local) {
            var tokens = Binder.get(environment).bind("signataria.autenticacao-local", TokensLocais.class)
                    .orElseThrow(() -> new IllegalStateException("Perfil local exige configuração de tokens SHA-256"));
            http.addFilterBefore(new FiltroTokenLocal(tokens, sacadores), AnonymousAuthenticationFilter.class);
        }
        return http.csrf(AbstractHttpConfigurer::disable)
                .httpBasic(AbstractHttpConfigurer::disable)
                .formLogin(AbstractHttpConfigurer::disable)
                .logout(AbstractHttpConfigurer::disable)
                .requestCache(AbstractHttpConfigurer::disable)
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(requests -> {
                    requests.dispatcherTypeMatchers(DispatcherType.ERROR).permitAll()
                            .requestMatchers("/actuator/health", "/actuator/health/**", "/actuator/info").permitAll();
                    if (local) {
                        requests.requestMatchers(HttpMethod.GET, "/v3/api-docs", "/v3/api-docs/swagger-config",
                                "/swagger-ui.html", "/swagger-ui/**").permitAll();
                        requests.requestMatchers("/api/v1/escrituracoes/**").hasAuthority("SACADOR_LOCAL");
                        requests.requestMatchers(HttpMethod.POST, "/api/v1/autorizacoes-consulta").hasAuthority("FINANCIADOR_LOCAL")
                                .requestMatchers(HttpMethod.GET, "/api/v1/autorizacoes-consulta/*/duplicatas").hasAuthority("FINANCIADOR_LOCAL")
                                .requestMatchers(HttpMethod.PUT, "/api/v1/autorizacoes-consulta/*/decisao").hasAuthority("SACADOR_LOCAL")
                                .requestMatchers(HttpMethod.GET, "/api/v1/autorizacoes-consulta/*").hasAnyAuthority("SACADOR_LOCAL", "FINANCIADOR_LOCAL")
                                .requestMatchers(HttpMethod.DELETE, "/api/v1/autorizacoes-consulta/*").hasAnyAuthority("SACADOR_LOCAL", "FINANCIADOR_LOCAL");
                    }
                    requests.anyRequest().denyAll();
                })
                .exceptionHandling(errors -> errors
                        .authenticationEntryPoint((request, response, exception) -> erro(response, 401))
                        .accessDeniedHandler((request, response, exception) -> erro(response, 403)))
                .build();
    }

    @Bean
    UserDetailsService semUsuariosPadrao() {
        // Impede a autoconfiguração de usuário/senha gerados pelo Spring Boot.
        return username -> { throw new UsernameNotFoundException("Autenticação somente por token local"); };
    }

    static void erro(HttpServletResponse response, int status) throws IOException {
        response.setStatus(status);
        response.setContentType("application/problem+json");
        if (status == 401) {
            response.setHeader("WWW-Authenticate", "Bearer");
        }
        String titulo = switch (status) {
            case 401 -> "Autenticação necessária";
            case 403 -> "Acesso não permitido";
            case 500 -> "Erro interno";
            default -> "Serviço indisponível";
        };
        response.setCharacterEncoding("UTF-8");
        response.getWriter().write("{\"status\":" + status + ",\"title\":\"" + titulo + "\"}");
    }
}
