package org.example.Razonamiento.run.model;

import org.openxava.annotations.*;
import javax.persistence.*;
import java.time.LocalDateTime;
import java.util.*;
import lombok.*;

@Entity
@Getter
@Setter
@Table(name = "hojas_respuesta")
@View(members = "evaluado; sesionPrueba; horaInicio, horaFin; respuestasDetalle; resultado")
public class HojaRespuesta {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    @Column(name = "id", updatable = false, nullable = false)
    @Hidden
    UUID id;

    @Column(name = "hora_inicio")
    @ReadOnly
    LocalDateTime horaInicio;

    @Column(name = "hora_fin")
    @ReadOnly
    LocalDateTime horaFin;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "evaluado_id", nullable = false)
    @Required
    @ReferenceView("Evaluado")
    Evaluado evaluado;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "sesion_prueba_id", nullable = false)
    @Required
    @ReferenceView("SesionPrueba")
    SesionPrueba sesionPrueba;

    @OneToMany(mappedBy = "hojaRespuesta", cascade = CascadeType.ALL,
            orphanRemoval = true, fetch = FetchType.LAZY)
    @OrderBy("pregunta.numero ASC")
    @ListProperties("pregunta.numero, opcionSeleccionada, esOmitida, acierto")
    List<RespuestaDetalles> respuestasDetalle = new ArrayList<>();

    @OneToOne(mappedBy = "hojaRespuesta", cascade = CascadeType.ALL,
            fetch = FetchType.LAZY, orphanRemoval = true)
    Resultado resultado;

    public void iniciarPrueba() { this.horaInicio = LocalDateTime.now(); }
    public void finalizarPrueba() { this.horaFin = LocalDateTime.now(); }

    @Transient
    public long getMinutosTranscurridos() {
        if (horaInicio == null) return 0;
        LocalDateTime fin = (horaFin != null) ? horaFin : LocalDateTime.now();
        return java.time.Duration.between(horaInicio, fin).toMinutes();
    }
}