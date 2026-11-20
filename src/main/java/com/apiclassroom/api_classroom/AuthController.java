package com.apiclassroom.api_classroom;

import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

@Controller
public class AuthController {

    static final String SESSION_USER = "userEmail";
    private static final String SESSION_STATE = "oauthState";
    private static final Logger log = LoggerFactory.getLogger(AuthController.class);

    private final GoogleAuthService auth;
    private final SyncService sync;

    public AuthController(GoogleAuthService auth, SyncService sync) {
        this.auth = auth;
        this.sync = sync;
    }

    /** El usuario abre /login: se le manda a Google y vuelve solo a /oauth2/callback  */
    @GetMapping("/login")
    public void login(@RequestParam(name = "force", defaultValue = "false") boolean force,
                      HttpSession session, HttpServletResponse resp) throws IOException {
        String state = (force ? "f-" : "n-") + UUID.randomUUID();
        session.setAttribute(SESSION_STATE, state);
        resp.sendRedirect(auth.urlDeLogin(state, force));
    }

    @GetMapping("/oauth2/callback")
    public void callback(@RequestParam(name = "code", required = false) String code,
                         @RequestParam(name = "state", required = false) String state,
                         @RequestParam(name = "error", required = false) String error,
                         HttpSession session, HttpServletResponse resp) throws IOException {
        if (error != null) {
            redirigirConError(resp, error);
            return;
        }
        Object esperado = session.getAttribute(SESSION_STATE);
        session.removeAttribute(SESSION_STATE);
        if (code == null || esperado == null || !esperado.equals(state)) {
            redirigirConError(resp, "La sesión de inicio expiró, intenta de nuevo.");
            return;
        }

        try {
            GoogleAuthService.ResultadoLogin r = auth.procesarCallback(code);
            if (r.necesitaConsentimiento()) {
                if (state.startsWith("f-")) {
                    redirigirConError(resp, "Google no entregó el permiso offline. Quita el acceso de la app en tu cuenta de Google e intenta de nuevo.");
                } else {
                    resp.sendRedirect("/login?force=true"); 
                }
                return;
            }
            session.setAttribute(SESSION_USER, r.email());
            String email = r.email();
            CompletableFuture.runAsync(() -> {      
                try {
                    log.info("Sync inicial {}: {}", email, sync.sincronizar(email));
                } catch (Exception e) {
                    log.warn("Falló la sync inicial de {}: {}", email, e.getMessage());
                }
            });
            resp.sendRedirect("/?ok=1");
        } catch (Exception e) {
            log.warn("Error en el callback de OAuth", e);
            redirigirConError(resp, e.getMessage() == null ? "Error al iniciar sesión" : e.getMessage());
        }
    }

    
    @GetMapping("/logout")
    public void logout(HttpSession session, HttpServletResponse resp) throws IOException {
        session.invalidate();
        resp.sendRedirect("/");
    }

    private void redirigirConError(HttpServletResponse resp, String mensaje) throws IOException {
        resp.sendRedirect("/?error=" + URLEncoder.encode(mensaje, StandardCharsets.UTF_8));
    }
}
