package org.example.Razonamiento.run.model;

import org.openxava.annotations.*;
import javax.persistence.*;
import javax.validation.constraints.*;
import java.util.*;
import lombok.*;

@Entity
@Getter
@Setter
@Table(name = "evaluados")
@View(members = "nombre, apellido, correo; telefono; hojasRespuesta")
public class Evaluado extends Usuario {

    @Size(max = 20)
    @Pattern(regexp = "^[+0-9\\-\\s()]*$")
    @Column(name = "telefono", length = 20)
    String telefono;

    @OneToMany(mappedBy = "evaluado", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    @ListProperties("sesionPrueba.codigoSesion, horaInicio, horaFin")
    List<HojaRespuesta> hojasRespuesta = new ArrayList<>();
}