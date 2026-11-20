package com.apiclassroom.api_classroom;

public record ResumenSync(int tareasEncontradas, int creados, int actualizados,
                          int borrados, int sinCambios, int vencidas, int entregadas) {
}
