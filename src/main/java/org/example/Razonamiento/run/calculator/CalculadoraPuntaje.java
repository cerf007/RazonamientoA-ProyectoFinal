package org.example.Razonamiento.run.calculator;

import org.example.Razonamiento.run.model.BaremoNacional;
import org.example.Razonamiento.run.model.RespuestaDetalles;

import java.util.List;

public class CalculadoraPuntaje {

    public int calcularPuntuacionDirecta(List<RespuestaDetalles> respuestas) {
        if (respuestas == null || respuestas.isEmpty()) return 0;
        return respuestas.stream().mapToInt(RespuestaDetalles::getAcierto).sum();
    }

    public int obtenerPercentil(int puntuacionDirecta, List<BaremoNacional> baremo) {
        if (puntuacionDirecta < 0)  return 1;
        if (puntuacionDirecta > 15) return 99;
        return baremo.stream()
                .filter(b -> b.getPuntuacionDirecta() == puntuacionDirecta)
                .map(BaremoNacional::getPercentil)
                .findFirst()
                .orElse(1);
    }

    public static int obtenerPercentilEstatico(int pd) {
        if (pd <= 1)  return 1;
        if (pd == 2)  return 5;
        if (pd == 3)  return 10;
        if (pd == 4)  return 25;
        if (pd == 5)  return 35;
        if (pd == 6)  return 50;
        if (pd == 7)  return 65;
        if (pd == 8)  return 75;
        if (pd == 9)  return 85;
        if (pd == 10) return 90;
        if (pd == 11) return 97;
        return 99; // 12+
    }
}
