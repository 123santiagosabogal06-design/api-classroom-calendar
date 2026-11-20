package com.apiclassroom.api_classroom;

import java.time.Instant;

/**
 * Tarea en un formato neutral, sin depender de Classroom ni de Calendar.
 * Cualquier fuente futura (Teams, Moodle...) solo tiene que producir objetos Tarea.
 *
 * @param clave      identificador único y estable, ej. "classroom:<idCurso>:<idTarea>"
 * @param vence      momento exacto de entrega
 * @param entregada  true si el estudiante ya la entregó
 */
public record Tarea(
        String clave,
        String titulo,
        String curso,
        String descripcion,
        String url,
        Instant vence,
        boolean entregada) {
}
