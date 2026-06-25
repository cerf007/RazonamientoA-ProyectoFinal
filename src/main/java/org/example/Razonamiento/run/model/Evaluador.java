package org.example.Razonamiento.run.model;

import org.openxava.annotations.*;
import javax.persistence.*;
import javax.validation.constraints.*;
import java.util.*;
import lombok.*;

@Entity
@Getter
@Setter
@Table(name = "evaluadores")
@View(members = "nombre, apellido, correo; username; sesiones")
public class Evaluador extends Usuario {

    @NotBlank
    @Size(min = 4, max = 50)
    @Column(name = "username", nullable = false, unique = true, length = 50)
    @Required
    String username;

    @NotBlank
    @Size(min = 6)
    @Column(name = "password", nullable = false, length = 255)
    @Required
    @Password
    String password;

    @OneToMany(mappedBy = "evaluador", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    @ListProperties("codigoSesion, fechaProgramada, estado")
    List<SesionPrueba> sesiones = new ArrayList<>();
}