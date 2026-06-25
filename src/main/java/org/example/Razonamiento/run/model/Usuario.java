package org.example.Razonamiento.run.model;

import org.openxava.annotations.*;
import javax.persistence.*;
import javax.validation.constraints.*;
import java.util.UUID;
import lombok.*;

@MappedSuperclass
@Getter
@Setter
public abstract class Usuario {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    @Column(name = "id", updatable = false, nullable = false)
    @Hidden
    protected UUID id;

    @NotBlank(message = "El nombre es obligatorio")
    @Size(max = 100)
    @Column(name = "nombre", nullable = false, length = 100)
    @Required
    protected String nombre;

    @NotBlank(message = "El apellido es obligatorio")
    @Size(max = 100)
    @Column(name = "apellido", nullable = false, length = 100)
    @Required
    protected String apellido;

    @NotBlank(message = "El correo es obligatorio")
    @Email
    @Size(max = 150)
    @Column(name = "correo", nullable = false, length = 150)
    @Required
    protected String correo;

    @Transient
    public String getNombreCompleto() { return nombre + " " + apellido; }

    @Override
    public String toString() { return getNombreCompleto(); }
}