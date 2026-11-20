package com.apiclassroom.api_classroom;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class Sincronizador {

    private static final Logger log = LoggerFactory.getLogger(Sincronizador.class);

    private final GoogleAuthService auth;
    private final SyncService sync;

    public Sincronizador(GoogleAuthService auth, SyncService sync) {
        this.auth = auth;
        this.sync = sync;
    }

    /** Sincroniza a todos los usuarios guardados, sin que nadie tenga que abrir el navegador. */
    @Scheduled(fixedDelayString = "${app.sync.interval}", initialDelayString = "${app.sync.initial-delay}")
    public void sincronizarTodos() {
        try {
            for (String userId : auth.usuarios()) {
                try {
                    ResumenSync r = sync.sincronizar(userId);
                    log.info("Sync {}: {}", userId, r);
                } catch (Exception e) {
                    log.warn("Falló la sincronización de {}: {}", userId, e.getMessage());
                }
            }
        } catch (Exception e) {
            log.warn("Sincronización automática omitida: {}", e.getMessage());
        }
    }
}
