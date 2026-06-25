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
    char opcionSeleccionada;

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
    @ReferenceView("Pregunta")
    Pregunta pregunta;

    public void registrarRespuesta(char opcion) {
        if (opcion == '\0' || opcion == ' ') {
            this.esOmitida = true;
            this.opcionSeleccionada = '\0';
            this.acierto = 0;
        } else {
            this.esOmitida = false;
            this.opcionSeleccionada = Character.toUpperCase(opcion);
            this.acierto = (pregunta != null && pregunta.esCorrecta(opcion)) ? 1 : 0;
        }
    }
}
