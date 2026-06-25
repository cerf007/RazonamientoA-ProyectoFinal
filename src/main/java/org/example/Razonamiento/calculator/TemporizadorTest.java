package org.example.Razonamiento.calculator;

import org.example.Razonamiento.model.HojaRespuesta;

import java.time.Duration;
import java.time.LocalDateTime;

public class TemporizadorTest {

    private static final int LIMITE_MINUTOS_DEFAULT = 10;

    public void iniciarConteo(HojaRespuesta hoja) {
        if (hoja == null) throw new IllegalArgumentException("La hoja no puede ser nula");
        hoja.setHoraInicio(LocalDateTime.now());
    }

    public boolean verificarTiempoAgotado(HojaRespuesta hoja, int limiteMinutos) {
        if (hoja == null || hoja.getHoraInicio() == null) return false;
        long min = Duration.between(hoja.getHoraInicio(), LocalDateTime.now()).toMinutes();
        return min >= limiteMinutos;
    }

    public boolean verificarTiempoAgotado(HojaRespuesta hoja) {
        return verificarTiempoAgotado(hoja, LIMITE_MINUTOS_DEFAULT);
    }

    public void forzarCierreTest(HojaRespuesta hoja) {
        if (hoja == null) throw new IllegalArgumentException("La hoja no puede ser nula");
        if (hoja.getHoraFin() == null) hoja.setHoraFin(LocalDateTime.now());
    }

    public long calcularSegundosRestantes(HojaRespuesta hoja, int limiteMinutos) {
        if (hoja == null || hoja.getHoraInicio() == null) return (long) limiteMinutos * 60;
        long transcurridos = Duration.between(hoja.getHoraInicio(), LocalDateTime.now()).getSeconds();
        return Math.max(0, (long) limiteMinutos * 60 - transcurridos);
    }

    public long calcularSegundosRestantes(HojaRespuesta hoja) {
        return calcularSegundosRestantes(hoja, LIMITE_MINUTOS_DEFAULT);
    }
}
