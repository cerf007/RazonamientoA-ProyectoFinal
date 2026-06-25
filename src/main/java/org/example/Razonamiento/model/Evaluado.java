package org.example.Razonamiento.model;

import org.openxava.annotations.*;
import javax.persistence.*;
import javax.validation.constraints.*;
import java.time.LocalDate;
import java.util.*;
import lombok.*;

@Entity
@Getter
@Setter
@Table(name = "evaluados")
@View(members = "nombre, apellido, correo; telefono, sexo, fechaNacimiento; hojasRespuesta")
public class Evaluado extends Usuario {

    @Size(max = 20)
    @Pattern(regexp = "^[+0-9\\-\\s()]*$")
    @Column(name = "telefono", length = 20)
    String telefono;

    @Size(max = 1)
    @Column(name = "sexo", length = 1)
    String sexo;

    @Column(name = "fecha_nacimiento")
    LocalDate fechaNacimiento;

    @OneToMany(mappedBy = "evaluado", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    @ListProperties("sesionPrueba.codigoSesion, horaInicio, horaFin")
    List<HojaRespuesta> hojasRespuesta = new ArrayList<>();
}