package org.example.Razonamiento.model;

import lombok.Getter;
import lombok.Setter;
import org.openxava.annotations.Hidden;
import org.openxava.annotations.NoCreate;
import org.openxava.annotations.NoSearch;
import org.openxava.annotations.ReadOnly;
import org.openxava.annotations.View;

import javax.persistence.*;
import java.util.UUID;

@Entity
@Getter
@Setter
@Table(name = "resultados")
@View(members = "hojaRespuesta; puntuacionDirecta, percentil; interpretacion")
public class Resultado {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    @Column(name = "id", updatable = false, nullable = false)
    @Hidden
    UUID id;

    @Column(name = "puntuacion_directa", nullable = false)
    @ReadOnly
    int puntuacionDirecta;

    @Column(name = "percentil", nullable = false)
    @ReadOnly
    int percentil;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "hoja_respuesta_id", nullable = false, unique = true)
    @NoCreate @NoSearch
    HojaRespuesta hojaRespuesta;

    @Transient
    public String getInterpretacion() {
        if (percentil >= 90) return "Muy Alto (P ≥ 90)";
        if (percentil >= 75) return "Alto (P 75-89)";
        if (percentil >= 50) return "Promedio Alto (P 50-74)";
        if (percentil >= 25) return "Promedio Bajo (P 25-49)";
        if (percentil >= 10) return "Bajo (P 10-24)";
        return "Muy Bajo (P < 10)";
    }
}
