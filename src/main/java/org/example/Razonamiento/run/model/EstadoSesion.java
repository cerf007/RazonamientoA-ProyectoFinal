package org.example.Razonamiento.run.model;

import lombok.*;

@Getter
public enum EstadoSesion {
    PROGRAMADA("Programada"),
    EN_PROGRESO("En Progreso"),
    FINALIZADA("Finalizada"),
    CORREGIDA("Corregida");

    private final String etiqueta;

    EstadoSesion(String etiqueta) { this.etiqueta = etiqueta; }

    @Override
    public String toString() { return etiqueta; }
}
