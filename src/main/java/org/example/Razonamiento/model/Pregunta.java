package org.example.Razonamiento.model;

import org.openxava.annotations.*;
import javax.persistence.*;
import javax.validation.constraints.*;
import java.util.UUID;
import lombok.*;

@Entity
@Getter
@Setter
@Table(name = "preguntas",
        uniqueConstraints = @UniqueConstraint(columnNames = {"numero", "prueba_id"}))
@View(members = "numero; serieIncompleta; opcionA, opcionB, opcionC, opcionD; respuestaCorrecta")
public class Pregunta {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    @Column(name = "id", updatable = false, nullable = false)
    @Hidden
    UUID id;

    @Min(1) @Max(100)
    @Column(name = "numero", nullable = false)
    @Required
    int numero;

    @NotBlank @Size(max = 200)
    @Column(name = "serie_incompleta", nullable = false, length = 200)
    @Required
    String serieIncompleta;

    @NotBlank @Size(max = 50)
    @Column(name = "opcion_a", nullable = false, length = 50)
    @Required
    String opcionA;

    @NotBlank @Size(max = 50)
    @Column(name = "opcion_b", nullable = false, length = 50)
    @Required
    String opcionB;

    @NotBlank @Size(max = 50)
    @Column(name = "opcion_c", nullable = false, length = 50)
    @Required
    String opcionC;

    @NotBlank @Size(max = 50)
    @Column(name = "opcion_d", nullable = false, length = 50)
    @Required
    String opcionD;

    @Column(name = "respuesta_correcta", nullable = false, length = 1)
    @Required
    char respuestaCorrecta;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "prueba_id", nullable = false)
    @NoCreate @NoSearch
    PruebaRazonamientoFormaA prueba;

    public boolean esCorrecta(char opcion) {
        return Character.toUpperCase(opcion) == Character.toUpperCase(respuestaCorrecta);
    }

    @Override
    public String toString() { return "Pregunta #" + numero + ": " + serieIncompleta; }
}
