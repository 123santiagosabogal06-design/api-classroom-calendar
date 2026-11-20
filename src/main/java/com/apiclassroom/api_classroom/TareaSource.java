package com.apiclassroom.api_classroom;

import java.io.IOException;
import java.util.List;

/**
 * Una fuente de tareas. Para añadir Teams (u otra app) basta con crear otra clase
 * @Component que implemente esta interfaz: SyncService la detecta sola.
 */
public interface TareaSource {

    String nombre();

    List<Tarea> obtenerTareas(String userId) throws IOException;
}
