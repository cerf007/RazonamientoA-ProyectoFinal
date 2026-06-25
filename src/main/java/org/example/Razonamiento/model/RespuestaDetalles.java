package org.example.Razonamiento.model;

import org.openxava.annotations.*;

import javax.persistence.*;
import java.util.UUID;
import lombok.*;

@Entity
@Getter
@Setter
@Table(name = "respuestas_detalle",
        uniqueConstraints = @UniqueConstraint(columnNames = {"hoja_respuesta_id", "pregunta_id"}))
@View(members = "pregunta; opcionSeleccionada, esOmitida, acierto")
public class RespuestaDetalles {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    @Column(name = "id", updatable = false, nullable = false)
    @Hidden
    UUID id;

    @Column(name = "opcion_seleccionada", length = 1)
    String opcionSeleccionada;

    @Column(name = "es_omitida", nullable = false)
    boolean esOmitida = false;

    @Column(name = "acierto", nullable = false)
    @ReadOnly
    int acierto = 0;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "hoja_respuesta_id", nullable = false)
    @NoCreate @NoSearch
    HojaRespuesta hojaRespuesta;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "pregunta_id", nullable = false)
    @Required
    Pregunta pregunta;

    public void registrarRespuesta(String opcion) {
        if (opcion == null || opcion.isBlank()) {
            this.esOmitida = true;
            this.opcionSeleccionada = null;
            this.acierto = 0;
        } else {
            this.esOmitida = false;
            this.opcionSeleccionada = opcion.toUpperCase();
            this.acierto = (pregunta != null && pregunta.esCorrecta(opcion)) ? 1 : 0;
        }
    }
}
