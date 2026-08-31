package kz.afm.kendala.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import kz.afm.kendala.application.entity.User;
import kz.afm.kendala.auth.service.JwtService;
import kz.afm.kendala.application.repository.UserRepository;
import kz.afm.kendala.common.dto.ErrorResponse;
import kz.afm.kendala.common.i18n.ApiMessageResolver;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
public class JwtAuthFilter extends OncePerRequestFilter {

    private final JwtService jwtService;
    private final ObjectMapper objectMapper;
    private final UserRepository userRepository;
    private final ApiMessageResolver messages;

    public JwtAuthFilter(
            JwtService jwtService,
            ObjectMapper objectMapper,
            UserRepository userRepository,
            ApiMessageResolver messages
    ) {
        this.jwtService = jwtService;
        this.objectMapper = objectMapper;
        this.userRepository = userRepository;
        this.messages = messages;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {

        String header = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (header == null || !header.startsWith("Bearer ")) {
            chain.doFilter(request, response);
            return;
        }

        String token = header.substring(7);
        try {
            Claims claims = jwtService.parseToken(token);
            String userId = claims.getSubject();
            var user = userRepository.findById(java.util.UUID.fromString(userId))
                    .filter(User::isActive)
                    .orElseThrow(() -> new JwtException("User is inactive or missing"));

            UsernamePasswordAuthenticationToken auth = new UsernamePasswordAuthenticationToken(
                    userId, null, List.of(new SimpleGrantedAuthority("ROLE_" + user.getRole().name())));
            SecurityContextHolder.getContext().setAuthentication(auth);
        } catch (JwtException e) {
            SecurityContextHolder.clearContext();
            writeUnauthorized(request, response);
            return;
        }

        chain.doFilter(request, response);
    }

    private void writeUnauthorized(
            HttpServletRequest request,
            HttpServletResponse response
    ) throws IOException {
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        String message = messages.message(
                "error.invalid-token",
                "Недействительный или истёкший токен",
                request
        );
        ErrorResponse body = new ErrorResponse(Instant.now(), 401, "UNAUTHORIZED", message, Map.of());
        response.getWriter().write(objectMapper.writeValueAsString(body));
    }
}
