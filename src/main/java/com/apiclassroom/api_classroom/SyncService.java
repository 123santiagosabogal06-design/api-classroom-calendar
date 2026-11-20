package com.apiclassroom.api_classroom;

import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class SyncService {

    private final List<TareaSource> fuentes;
    private final CalendarSink calendario;
    private final Map<String, Object> bloqueos = new ConcurrentHashMap<>();

    
    public SyncService(List<TareaSource> fuentes, CalendarSink calendario) {
        this.fuentes = fuentes;
        this.calendario = calendario;
    }

   
    public ResumenSync sincronizar(String userId) throws IOException {
        synchronized (bloqueos.computeIfAbsent(userId, k -> new Object())) {
            List<Tarea> todas = new ArrayList<>();
            for (TareaSource fuente : fuentes) {
                todas.addAll(fuente.obtenerTareas(userId));
            }
            return calendario.sincronizar(userId, todas);
        }
    }
}
