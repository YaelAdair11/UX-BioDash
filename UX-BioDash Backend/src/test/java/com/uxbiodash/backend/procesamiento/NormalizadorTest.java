package com.uxbiodash.backend.procesamiento;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import com.uxbiodash.backend.config.ProcesamientoProperties;
import com.uxbiodash.backend.ingesta.RawGazeSampleDTO;
import com.uxbiodash.backend.ingesta.SistemaCoordenadas;
import com.uxbiodash.backend.ingesta.UnidadTiempo;
import com.uxbiodash.backend.procesamiento.Normalizador.Resolucion;
import com.uxbiodash.backend.procesamiento.Normalizador.ResultadoNormalizacion;
import java.util.List;
import org.junit.jupiter.api.Test;

class NormalizadorTest {

    private static final ConversorAnguloVisual CONVERSOR = new ConversorAnguloVisual(60, 53.1, 29.9);
    private static final double VELOCIDAD_MAX =
            ProcesamientoProperties.porDefecto().velocidadMaximaFisiologicaGradosS();
    private static final Resolucion FULL_HD = new Resolucion(1920, 1080);

    private static RawGazeSampleDTO muestra(double ts, Double x, Double y, boolean valido) {
        return new RawGazeSampleDTO("P01", null, ts, x, y, null, valido);
    }

    private static ResultadoNormalizacion normalizar(List<RawGazeSampleDTO> crudas, UnidadTiempo unidad,
                                                     SistemaCoordenadas sistema) {
        return Normalizador.normalizarMuestras(crudas, unidad, sistema, FULL_HD, CONVERSOR, VELOCIDAD_MAX);
    }

    @Test
    void coordenadasEnPixelesSeDividenEntreLaResolucion() {
        assertThat(Normalizador.normalizarCoordenada(960, SistemaCoordenadas.PIXELES, 1920))
                .isCloseTo(0.5, within(1e-9));
        assertThat(Normalizador.normalizarCoordenada(540, SistemaCoordenadas.PIXELES, 1080))
                .isCloseTo(0.5, within(1e-9));
    }

    @Test
    void coordenadasNormalizadasSeConservan() {
        assertThat(Normalizador.normalizarCoordenada(0.3, SistemaCoordenadas.NORMALIZADO, 1920))
                .isCloseTo(0.3, within(1e-9));
    }

    @Test
    void valoresFueraDelIntervaloSeAcotan() {
        assertThat(Normalizador.normalizarCoordenada(2500, SistemaCoordenadas.PIXELES, 1920)).isEqualTo(1.0);
        assertThat(Normalizador.normalizarCoordenada(1.7, SistemaCoordenadas.NORMALIZADO, 1920)).isEqualTo(1.0);
        assertThat(Normalizador.limitar(-0.2)).isEqualTo(0.0);
    }

    @Test
    void normalizaPixelesYLimitaAUnoEnLaSalida() {
        // 2100 px en una pantalla de 1920 px supera 1.0 y debe acotarse
        ResultadoNormalizacion r = normalizar(List.of(
                        muestra(0, 960.0, 540.0, true),
                        muestra(100, 2100.0, 540.0, true)),
                UnidadTiempo.MILISEGUNDOS, SistemaCoordenadas.PIXELES);

        assertThat(r.muestras()).hasSize(2);
        assertThat(r.muestras().get(0).x()).isCloseTo(0.5, within(1e-9));
        assertThat(r.muestras().get(0).y()).isCloseTo(0.5, within(1e-9));
        assertThat(r.muestras().get(1).x()).isEqualTo(1.0);
        assertThat(r.muestras().get(1).y()).isCloseTo(0.5, within(1e-9));
    }

    @Test
    void descartaSentinelasNulosYBanderaInvalida() {
        ResultadoNormalizacion r = normalizar(List.of(
                        muestra(0, 960.0, 540.0, true),
                        muestra(100, -1.0, 540.0, true),   // centinela EyeLink
                        muestra(200, 960.0, 540.0, false), // parpadeo / perdida de senal
                        muestra(300, null, 540.0, true),   // nulo
                        muestra(400, 960.0, 540.0, true)),
                UnidadTiempo.MILISEGUNDOS, SistemaCoordenadas.PIXELES);

        assertThat(r.muestras()).hasSize(2);
        assertThat(r.descartadasInvalidas()).isEqualTo(3);
        assertThat(r.descartadasVelocidad()).isZero();
    }

    @Test
    void primeraMuestraValidaEsTiempoCeroYMicrosegundosSeConvierten() {
        ResultadoNormalizacion r = normalizar(List.of(
                        muestra(900_000, -1.0, 540.0, true), // invalida: no fija t0
                        muestra(1_000_000, 960.0, 540.0, true),
                        muestra(1_100_000, 960.0, 540.0, true)),
                UnidadTiempo.MICROSEGUNDOS, SistemaCoordenadas.PIXELES);

        assertThat(r.muestras()).hasSize(2);
        assertThat(r.muestras().get(0).tiempoMs()).isCloseTo(0.0, within(1e-9));
        assertThat(r.muestras().get(1).tiempoMs()).isCloseTo(100.0, within(1e-6));
        assertThat(UnidadTiempo.MICROSEGUNDOS.aMilisegundos(2500)).isCloseTo(2.5, within(1e-9));
    }

    @Test
    void descartaSaltoFisiologicamenteImposible() {
        // 0.5 de la pantalla (~25 grados) en 10 ms es ~2500 grados/s, por encima de 1000
        ResultadoNormalizacion r = normalizar(List.of(
                        muestra(0, 960.0, 540.0, true),
                        muestra(10, 1920.0, 540.0, true),
                        muestra(20, 960.0, 540.0, true)),
                UnidadTiempo.MILISEGUNDOS, SistemaCoordenadas.PIXELES);

        assertThat(r.descartadasVelocidad()).isEqualTo(1);
        assertThat(r.muestras()).hasSize(2);
        assertThat(r.muestras().get(1).tiempoMs()).isCloseTo(20.0, within(1e-9));
    }
}
