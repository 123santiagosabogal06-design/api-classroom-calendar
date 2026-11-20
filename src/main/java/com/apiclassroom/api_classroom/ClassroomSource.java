package com.apiclassroom.api_classroom;

import com.google.api.services.classroom.Classroom;
import com.google.api.services.classroom.model.Course;
import com.google.api.services.classroom.model.CourseWork;
import com.google.api.services.classroom.model.ListCourseWorkResponse;
import com.google.api.services.classroom.model.ListCoursesResponse;
import com.google.api.services.classroom.model.ListStudentSubmissionsResponse;
import com.google.api.services.classroom.model.StudentSubmission;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
// este va a buscar las tareas//

@Component
public class ClassroomSource implements TareaSource {

    private static final Logger log = LoggerFactory.getLogger(ClassroomSource.class);

    private final GoogleAuthService auth;
    private final ZoneId zona;

    public ClassroomSource(GoogleAuthService auth, @Value("${app.timezone}") String zona) {
        this.auth = auth;
        this.zona = ZoneId.of(zona);
    }

    @Override
    public String nombre() {
        return "Google Classroom";
    }

    @Override
    public List<Tarea> obtenerTareas(String userId) throws IOException {
        Classroom classroom = auth.classroom(userId);
        List<Tarea> resultado = new ArrayList<>();

        for (Course curso : listarCursos(classroom)) {
            Map<String, String> estados = estadosDeEntrega(classroom, curso.getId());

            String pagina = null;
            do {
                ListCourseWorkResponse r = classroom.courses().courseWork().list(curso.getId())
                        .setCourseWorkStates(List.of("PUBLISHED"))
                        .setPageToken(pagina)
                        .execute();

                if (r.getCourseWork() != null) {
                    for (CourseWork cw : r.getCourseWork()) {
                        Instant vence = calcularVencimiento(cw);
                        if (vence == null) {
                            continue; // sin fecha límite no hay nada que agendar
                        }
                        String estado = estados.get(cw.getId());
                        boolean entregada = "TURNED_IN".equals(estado) || "RETURNED".equals(estado);

                        resultado.add(new Tarea(
                                "classroom:" + curso.getId() + ":" + cw.getId(),
                                cw.getTitle(),
                                curso.getName(),
                                cw.getDescription(),
                                cw.getAlternateLink(),
                                vence,
                                entregada));
                    }
                }
                pagina = r.getNextPageToken();
            } while (pagina != null);
        }
        return resultado;
    }

    /** Solo cursos activos en los que el usuario es estudiante, recorriendo todas las páginas. */
    private List<Course> listarCursos(Classroom classroom) throws IOException {
        List<Course> cursos = new ArrayList<>();
        String pagina = null;
        do {
            ListCoursesResponse r = classroom.courses().list()
                    .setStudentId("me")
                    .setCourseStates(List.of("ACTIVE"))
                    .setPageToken(pagina)
                    .execute();
            if (r.getCourses() != null) {
                cursos.addAll(r.getCourses());
            }
            pagina = r.getNextPageToken();
        } while (pagina != null);
        return cursos;
    }

    /** Estado de entrega de todas las tareas de un curso en una sola consulta ("-" = todas). */
    private Map<String, String> estadosDeEntrega(Classroom classroom, String cursoId) {
        Map<String, String> estados = new HashMap<>();
        try {
            String pagina = null;
            do {
                ListStudentSubmissionsResponse r = classroom.courses().courseWork().studentSubmissions()
                        .list(cursoId, "-")
                        .setUserId("me")
                        .setPageToken(pagina)
                        .execute();
                if (r.getStudentSubmissions() != null) {
                    for (StudentSubmission s : r.getStudentSubmissions()) {
                        estados.put(s.getCourseWorkId(), s.getState());
                    }
                }
                pagina = r.getNextPageToken();
            } while (pagina != null);
        } catch (IOException e) {
            log.warn("No se pudo leer el estado de entrega del curso {}: {}", cursoId, e.getMessage());
        }
        return estados;
    }

    /**
     * Classroom entrega dueTime en UTC. Si la tarea solo trae fecha, se toma las 23:59
     * en la zona horaria configurada.
     */
    private Instant calcularVencimiento(CourseWork cw) {
        var d = cw.getDueDate();
        if (d == null || d.getYear() == null || d.getMonth() == null || d.getDay() == null) {
            return null;
        }
        var t = cw.getDueTime();
        if (t != null) {
            int horas = t.getHours() == null ? 0 : t.getHours();      // Google omite los ceros
            int minutos = t.getMinutes() == null ? 0 : t.getMinutes();
            return ZonedDateTime.of(d.getYear(), d.getMonth(), d.getDay(), horas, minutos, 0, 0, ZoneOffset.UTC)
                    .toInstant();
        }
        return ZonedDateTime.of(d.getYear(), d.getMonth(), d.getDay(), 23, 59, 0, 0, zona).toInstant();
    }
}
