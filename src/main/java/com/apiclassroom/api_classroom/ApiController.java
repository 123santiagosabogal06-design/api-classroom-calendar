package com.apiclassroom.api_classroom;

import jakarta.servlet.http.HttpSession;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api")
public class ApiController {

    private final GoogleAuthService auth;
    private final SyncService sync;

    public ApiController(GoogleAuthService auth, SyncService sync) {
        this.auth = auth;
        this.sync = sync;
    }

    @GetMapping("/ping")
    public String ping() {
        return "¡Servidor Spring Boot funcionando correctamente!";
    }

    @GetMapping("/me")
    public ResponseEntity<?> me(HttpSession session) {
        String email = (String) session.getAttribute(AuthController.SESSION_USER);
        if (email == null) {
            return ResponseEntity.status(401).body(Map.of("error", "No has iniciado sesión"));
        }
        return ResponseEntity.ok(Map.of("email", email));
    }

    /** Sincroniza ahora mismo al usuario que tiene la sesión abierta. */
    @GetMapping("/sync")
    public ResponseEntity<?> sincronizar(HttpSession session) {
        String email = (String) session.getAttribute(AuthController.SESSION_USER);
        if (email == null) {
            return ResponseEntity.status(401).body(Map.of("error", "No has iniciado sesión"));
        }
        try {
            return ResponseEntity.ok(sync.sincronizar(email));
        } catch (Exception e) {
            return ResponseEntity.status(500).body(Map.of("error", String.valueOf(e.getMessage())));
        }
    }

    /** Borra los tokens guardados de este usuario (deja de sincronizarse). */
    @PostMapping("/disconnect")
    public ResponseEntity<?> desconectar(HttpSession session) {
        String email = (String) session.getAttribute(AuthController.SESSION_USER);
        if (email == null) {
            return ResponseEntity.status(401).body(Map.of("error", "No has iniciado sesión"));
        }
        try {
            auth.desconectar(email);
            session.invalidate();
            return ResponseEntity.ok(Map.of("ok", true));
        } catch (Exception e) {
            return ResponseEntity.status(500).body(Map.of("error", String.valueOf(e.getMessage())));
        }
    }
}
