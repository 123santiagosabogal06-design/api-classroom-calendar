package com.apiclassroom.api_classroom;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

/**
 * Fuente SIMULADA de tareas: sirve para demostrar que el sistema acepta otra plataforma
 * sin modificar SyncService ni CalendarSink (solo hay que implementar TareaSource).
 *
 * Está apagada por defecto. Solo entrega tareas si en application.properties se pone
 *     app.fuente-simulada.activa=true
 * Apagada devuelve una lista vacía, y en la siguiente sincronización los eventos de
 * ejemplo se borran solos del calendario (eventos huérfanos).
 */
@Component
public class FuenteSimulada implements TareaSource {

    private final boolean activa;
    private final Instant venceUno;
    private final Instant venceDos;

    public FuenteSimulada(@Value("${app.fuente-simulada.activa:false}") boolean activa) {
        this.activa = activa;
        // Fechas fijas mientras la app esté corriendo, para que no se "actualicen" en cada sincronización.
        Instant base = Instant.now().plus(2, ChronoUnit.DAYS).truncatedTo(ChronoUnit.HOURS);
        this.venceUno = base;
        this.venceDos = base.plus(1, ChronoUnit.DAYS);
    }

    @Override
    public String nombre() {
        return "Plataforma simulada";
    }

    @Override
    public List<Tarea> obtenerTareas(String userId) {
        if (!activa) {
            return List.of();
        }
        return List.of(
                new Tarea("simulada:1", "Tarea de ejemplo 1", "Plataforma B (simulada)",
                        "Tarea generada por la fuente simulada.", null, venceUno, false),
                new Tarea("simulada:2", "Tarea de ejemplo 2", "Plataforma B (simulada)",
                        "Tarea generada por la fuente simulada.", null, venceDos, false));
    }
}