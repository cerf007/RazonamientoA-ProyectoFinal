package org.example.Razonamiento.model;

import lombok.Getter;
import lombok.Setter;
import org.openxava.annotations.Hidden;
import org.openxava.annotations.ListProperties;
import org.openxava.annotations.Stereotype;
import org.openxava.annotations.View;

import javax.persistence.*;
import javax.validation.constraints.Max;
import javax.validation.constraints.Min;
import javax.validation.constraints.NotBlank;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Getter
@Setter
@Table(name = "prueba_razonamiento_forma_a")
@View(members = "limiteTiempoMinutos; instrucciones; preguntas")
public class PruebaRazonamientoFormaA {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    @Column(name = "id", updatable = false, nullable = false)
    @Hidden
    UUID id;

    @Min(1) @Max(60)
    @Column(name = "limite_tiempo_minutos", nullable = false)
    int limiteTiempoMinutos = 10;

    @NotBlank
    @Column(name = "instrucciones", nullable = false, columnDefinition = "TEXT")
    @Stereotype("MEMO")
    String instrucciones;

    @OneToMany(mappedBy = "prueba", cascade = CascadeType.ALL,
            orphanRemoval = true, fetch = FetchType.LAZY)
    @OrderBy("numero ASC")
    @ListProperties("numero, serieIncompleta, opcionA, opcionB, opcionC, opcionD, respuestaCorrecta")
    List<Pregunta> preguntas = new ArrayList<>();

    @Transient
    public int getTotalPreguntas() { return preguntas.size(); }
}