package com.apiclassroom.api_classroom;

import com.google.api.client.util.DateTime;
import com.google.api.services.calendar.Calendar;
import com.google.api.services.calendar.model.Event;
import com.google.api.services.calendar.model.EventDateTime;
import com.google.api.services.calendar.model.EventReminder;
import com.google.api.services.calendar.model.Events;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.time.Instant;
import java.time.ZoneId;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Escribe las tareas en Google Calendar sin duplicar: cada evento lleva escondida la
 * "clave" de la tarea, y en cada sincronización se crea, actualiza o borra según haga falta.
 */
@Component
public class CalendarSink {

    private static final String ORIGEN = "tareas-sync";

    private final GoogleAuthService auth;
    private final ZoneId zona;
    private final int minutosEmail;
    private final int minutosPopup;

    public CalendarSink(GoogleAuthService auth,
                        @Value("${app.timezone}") String zona,
                        @Value("${app.reminder.email-minutes}") int minutosEmail,
                        @Value("${app.reminder.popup-minutes}") int minutosPopup) {
        this.auth = auth;
        this.zona = ZoneId.of(zona);
        this.minutosEmail = minutosEmail;
        this.minutosPopup = minutosPopup;
    }

    public ResumenSync sincronizar(String userId, List<Tarea> tareas) throws IOException {
        Calendar cal = auth.calendar(userId);
        Map<String, Event> existentes = eventosExistentes(cal);
        Instant ahora = Instant.now();
        int creados = 0, actualizados = 0, borrados = 0, sinCambios = 0, vencidas = 0, entregadas = 0;

        // Claves de todas las tareas que la fuente sigue reportando (para detectar las que desaparecieron).
        Set<String> clavesVigentes = new HashSet<>();

        for (Tarea t : tareas) {
            clavesVigentes.add(t.clave());
            Event existente = existentes.get(t.clave());

            if (t.entregada()) {                       // ya la entregó: quitar el recordatorio
                if (existente != null) {
                    cal.events().delete("primary", existente.getId()).execute();
                    borrados++;
                } else {
                    entregadas++;                      // entregada y sin evento: nada que hacer
                }
                continue;
            }
            if (t.vence().isBefore(ahora)) {           // ya venció: no la agregamos
                vencidas++;
                continue;
            }

            Event nuevo = construirEvento(t);
            if (existente == null) {
                cal.events().insert("primary", nuevo).execute();
                creados++;
            } else if (cambio(existente, nuevo)) {
                cal.events().update("primary", existente.getId(), nuevo).execute();
                actualizados++;
            } else {
                sinCambios++;                          // el evento ya estaba y sigue igual
            }
        }

        // Eventos "huérfanos": los creó esta app, pero su tarea ya no aparece en ninguna fuente
        // (la borraron o despublicaron en Classroom, le quitaron la fecha o el curso ya no está activo).
        // Solo se borran los que aún no han pasado; el historial de eventos viejos se conserva.
        long ahoraMs = ahora.toEpochMilli();
        for (Map.Entry<String, Event> par : existentes.entrySet()) {
            if (clavesVigentes.contains(par.getKey())) {
                continue;
            }
            Long fin = millis(par.getValue().getEnd());
            if (fin != null && fin > ahoraMs) {
                cal.events().delete("primary", par.getValue().getId()).execute();
                borrados++;
            }
        }

        return new ResumenSync(tareas.size(), creados, actualizados, borrados, sinCambios, vencidas, entregadas);
    }

    /** Eventos que esta app creó antes, indexados por la clave de la tarea. */
    private Map<String, Event> eventosExistentes(Calendar cal) throws IOException {
        Map<String, Event> mapa = new HashMap<>();
        String pagina = null;
        do {
            Events r = cal.events().list("primary")
                    .setPrivateExtendedProperty(List.of("origen=" + ORIGEN))
                    .setMaxResults(250)
                    .setPageToken(pagina)
                    .execute();
            if (r.getItems() != null) {
                for (Event e : r.getItems()) {
                    if (e.getExtendedProperties() != null && e.getExtendedProperties().getPrivate() != null) {
                        String clave = e.getExtendedProperties().getPrivate().get("clave");
                        if (clave != null) {
                            mapa.put(clave, e);
                        }
                    }
                }
            }
            pagina = r.getNextPageToken();
        } while (pagina != null);
        return mapa;
    }

    private Event construirEvento(Tarea t) {
        StringBuilder desc = new StringBuilder("Curso: ").append(t.curso());
        if (t.url() != null) {
            desc.append("\nAbrir en Classroom: ").append(t.url());
        }
        if (t.descripcion() != null && !t.descripcion().isBlank()) {
            desc.append("\n\n").append(t.descripcion());
        }

        long fin = t.vence().toEpochMilli();
        long inicio = fin - 30 * 60 * 1000L; // el evento ocupa los 30 min previos a la entrega

        Event e = new Event()
                .setSummary("Tarea: " + t.titulo() + " (" + t.curso() + ")")
                .setDescription(desc.toString())
                .setStart(new EventDateTime().setDateTime(new DateTime(inicio)).setTimeZone(zona.getId()))
                .setEnd(new EventDateTime().setDateTime(new DateTime(fin)).setTimeZone(zona.getId()))
                .setReminders(new Event.Reminders()
                        .setUseDefault(false)
                        .setOverrides(List.of(
                                new EventReminder().setMethod("email").setMinutes(minutosEmail),
                                new EventReminder().setMethod("popup").setMinutes(minutosPopup))))
                .setExtendedProperties(new Event.ExtendedProperties()
                        .setPrivate(Map.of("origen", ORIGEN, "clave", t.clave())));

        if (t.url() != null) {
            e.setSource(new Event.Source().setTitle("Google Classroom").setUrl(t.url()));
        }
        return e;
    }

    private boolean cambio(Event viejo, Event nuevo) {
        return !Objects.equals(viejo.getSummary(), nuevo.getSummary())
                || !Objects.equals(millis(viejo.getStart()), millis(nuevo.getStart()))
                || !Objects.equals(millis(viejo.getEnd()), millis(nuevo.getEnd()));
    }

    private Long millis(EventDateTime d) {
        return (d == null || d.getDateTime() == null) ? null : d.getDateTime().getValue();
    }
}