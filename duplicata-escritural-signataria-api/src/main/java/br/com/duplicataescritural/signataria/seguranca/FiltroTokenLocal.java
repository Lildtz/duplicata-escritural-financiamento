package br.com.duplicataescritural.signataria.seguranca;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;

import br.com.duplicataescritural.signataria.escrituracao.dominio.Sacador;
import br.com.duplicataescritural.signataria.escrituracao.infraestrutura.persistencia.Sacadores;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.RecoverableDataAccessException;
import org.springframework.dao.TransientDataAccessException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

final class FiltroTokenLocal extends OncePerRequestFilter {

    private final TokensLocais configuracao;
    private final Sacadores sacadores;

    FiltroTokenLocal(TokensLocais configuracao, Sacadores sacadores) {
        this.configuracao = configuracao;
        this.sacadores = sacadores;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !request.getServletPath().startsWith("/api/v1/escrituracoes/");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String authorization = request.getHeader("Authorization");
        if (authorization == null || !authorization.regionMatches(true, 0, "Bearer ", 0, 7)) {
            SegurancaEscrituracao.erro(response, 401);
            return;
        }
        String token = authorization.substring(7);
        if (!token.matches("[A-Za-z0-9_-]{43,128}")) {
            SegurancaEscrituracao.erro(response, 401);
            return;
        }
        byte[] impressao = sha256(token);
        Sacador sacador = null;
        try {
            for (var credencial : configuracao.tokens()) {
                if (MessageDigest.isEqual(impressao, HexFormat.of().parseHex(credencial.sha256()))) {
                    sacador = sacadores.findById(credencial.sacadorId()).orElse(null);
                    break;
                }
            }
        } catch (TransientDataAccessException | RecoverableDataAccessException exception) {
            SegurancaEscrituracao.erro(response, 503);
            return;
        } catch (DataAccessException exception) {
            SegurancaEscrituracao.erro(response, 500);
            return;
        }
        if (sacador == null) {
            SegurancaEscrituracao.erro(response, 401);
            return;
        }
        if (!sacador.habilitado()) {
            SegurancaEscrituracao.erro(response, 403);
            return;
        }
        var context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(UsernamePasswordAuthenticationToken.authenticated(new SacadorAutenticado(sacador.fotografia()), null,
                List.of(new SimpleGrantedAuthority("SACADOR_LOCAL"))));
        SecurityContextHolder.setContext(context);
        try {
            chain.doFilter(request, response);
        } finally {
            SecurityContextHolder.clearContext();
        }
    }

    private static byte[] sha256(String token) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.US_ASCII));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 indisponível", exception);
        }
    }
}
