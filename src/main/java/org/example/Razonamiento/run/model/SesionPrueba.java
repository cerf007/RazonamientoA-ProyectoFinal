package org.example.Razonamiento.run.model;

import org.openxava.annotations.*;
import javax.persistence.*;
import javax.validation.constraints.NotNull;
import java.time.LocalDateTime;
import java.util.*;
import lombok.*;

@Entity
@Getter
@Setter
@Table(name = "sesiones_prueba")
@View(members = "codigoSesion, estado; evaluador; fechaProgramada; hojasRespuesta")
public class SesionPrueba {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    @Column(name = "id", updatable = false, nullable = false)
    @Hidden
    UUID id;

    @Column(name = "codigo_sesion", nullable = false, unique = true, length = 10)
    @ReadOnly
    String codigoSesion;

    @NotNull
    @Column(name = "fecha_programada", nullable = false)
    @Required
    LocalDateTime fechaProgramada;

    @Enumerated(EnumType.STRING)
    @Column(name = "estado", nullable = false, length = 20)
    @ReadOnly
    EstadoSesion estado = EstadoSesion.PROGRAMADA;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "evaluador_id", nullable = false)
    @Required
    @ReferenceView("Evaluador")
    Evaluador evaluador;

    @OneToMany(mappedBy = "sesionPrueba", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    @ListProperties("evaluado.nombreCompleto, horaInicio, horaFin")
    List<HojaRespuesta> hojasRespuesta = new ArrayList<>();

    @PrePersist
    public void antesDeGuardar() {
        if (this.codigoSesion == null || this.codigoSesion.isBlank()) {
            generarCodigoUnico();
        }
    }

    public void generarCodigoUnico() {
        String uuid = UUID.randomUUID().toString().replace("-", "").toUpperCase();
        this.codigoSesion = uuid.substring(0, 8);
    }

    @Override
    public String toString() { return codigoSesion + " [" + estado + "]"; }
}
