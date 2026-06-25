package org.example.Razonamiento.run.model;

import org.openxava.annotations.*;
import javax.persistence.*;
import javax.validation.constraints.*;
import java.util.UUID;
import lombok.*;

@Entity
@Getter
@Setter
@Table(name = "baremo_nacional",
        uniqueConstraints = @UniqueConstraint(columnNames = {"puntuacion_directa"}))
@View(members = "puntuacionDirecta, percentil")
public class BaremoNacional {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    @Column(name = "id", updatable = false, nullable = false)
    @Hidden
    UUID id;

    @Min(0) @Max(15)
    @Column(name = "puntuacion_directa", nullable = false)
    @Required
    int puntuacionDirecta;

    @Min(1) @Max(99)
    @Column(name = "percentil", nullable = false)
    @Required
    int percentil;

    @Override
    public String toString() { return "PD=" + puntuacionDirecta + " → P" + percentil; }
}
