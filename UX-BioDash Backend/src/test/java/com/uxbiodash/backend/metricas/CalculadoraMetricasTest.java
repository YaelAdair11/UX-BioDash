package com.uxbiodash.backend.metricas;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;

import com.uxbiodash.backend.domain.FormaGeometrica;
import com.uxbiodash.backend.exception.SolicitudInvalidaException;
import com.uxbiodash.backend.metricas.CalculadoraMetricas.FijacionVista;
import com.uxbiodash.backend.metricas.CalculadoraMetricas.MetricasAoi;
import com.uxbiodash.backend.metricas.CalculadoraMetricas.ResumenSesion;
import com.uxbiodash.backend.metricas.CoordenadasAoi.Punto;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class CalculadoraMetricasTest {

    private static final GeometriaAoi RECTANGULO =
            GeometriaAoi.de(FormaGeometrica.RECTANGULO, CoordenadasAoi.rectangulo(0.1, 0.1, 0.2, 0.2));

    private static FijacionVista f(int orden, int inicio, int fin, double x, double y) {
        return new FijacionVista(orden, inicio, fin, fin - inicio, x, y);
    }

    // ------------------------------------------------------------ metricas por AOI

    @Test
    void secuenciaDentroFueraDentroDaDosVisitas() {
        List<FijacionVista> fijaciones = List.of(
                f(1, 50, 250, 0.2, 0.2),    // dentro
                f(2, 250, 350, 0.8, 0.8),   // fuera
                f(3, 350, 550, 0.25, 0.15)); // dentro

        MetricasAoi m = CalculadoraMetricas.metricasAoi(fijaciones, RECTANGULO, 1000);

        assertThat(m.conteoFijaciones()).isEqualTo(2);
        assertThat(m.numeroVisitas()).isEqualTo(2);
        assertThat(m.ttffMs()).isEqualTo(50);
        assertThat(m.duracionMediaFijacionMs()).isEqualTo(200.0);
        // Visita 1: 250 - 50 = 200; visita 2: 550 - 350 = 200
        assertThat(m.dwellTimeMs()).isEqualTo(400);
        assertThat(m.porcentajeDwell()).isCloseTo(40.0, within(1e-9));
    }

    @Test
    void fijacionesConsecutivasDentroFormanUnaSolaVisita() {
        List<FijacionVista> fijaciones = List.of(
                f(1, 0, 100, 0.2, 0.2),
                f(2, 100, 300, 0.25, 0.25));

        MetricasAoi m = CalculadoraMetricas.metricasAoi(fijaciones, RECTANGULO, 1000);

        assertThat(m.numeroVisitas()).isEqualTo(1);
        assertThat(m.dwellTimeMs()).isEqualTo(300);
    }

    @Test
    void aoiNuncaMiradoDaTtffNuloYConteoCero() {
        List<FijacionVista> fijaciones = List.of(
                f(1, 0, 100, 0.8, 0.8),
                f(2, 100, 200, 0.9, 0.1));

        MetricasAoi m = CalculadoraMetricas.metricasAoi(fijaciones, RECTANGULO, 1000);

        assertThat(m.conteoFijaciones()).isZero();
        assertThat(m.ttffMs()).isNull();
        assertThat(m.duracionMediaFijacionMs()).isNull();
        assertThat(m.dwellTimeMs()).isZero();
        assertThat(m.numeroVisitas()).isZero();
    }

    // ------------------------------------------------------------ geometria

    @Test
    void poligonoTrianguloContieneOExcluyePuntos() {
        GeometriaAoi triangulo = GeometriaAoi.de(FormaGeometrica.POLIGONO, CoordenadasAoi.poligono(List.of(
                new Punto(0.1, 0.1), new Punto(0.5, 0.1), new Punto(0.3, 0.5))));

        assertThat(triangulo.contiene(0.3, 0.2)).isTrue();
        assertThat(triangulo.contiene(0.9, 0.9)).isFalse();
        assertThat(triangulo.contiene(0.05, 0.05)).isFalse();
        assertThat(triangulo.contiene(0.3, 0.6)).isFalse();
        assertThat(triangulo.area()).isCloseTo(0.08, within(1e-9));
    }

    @Test
    void aoiDeFijacionEligeElDeMenorArea() {
        GeometriaAoi grande = GeometriaAoi.de(FormaGeometrica.RECTANGULO, CoordenadasAoi.rectangulo(0, 0, 1, 1));
        GeometriaAoi pequeno = GeometriaAoi.de(FormaGeometrica.RECTANGULO,
                CoordenadasAoi.rectangulo(0.4, 0.4, 0.2, 0.2));
        Map<Long, GeometriaAoi> aois = Map.of(1L, grande, 2L, pequeno);

        assertThat(CalculadoraMetricas.aoiDeFijacion(0.5, 0.5, aois)).isEqualTo(2L);
        assertThat(CalculadoraMetricas.aoiDeFijacion(0.1, 0.1, aois)).isEqualTo(1L);
        assertThat(CalculadoraMetricas.aoiDeFijacion(0.5, 0.5, Map.of(2L, pequeno))).isEqualTo(2L);
        assertThat(CalculadoraMetricas.aoiDeFijacion(0.9, 0.9, Map.of(2L, pequeno))).isNull();
    }

    @Test
    void rectanguloQueSaleDeLaImagenLanza() {
        assertThatThrownBy(() -> GeometriaAoi.de(FormaGeometrica.RECTANGULO,
                CoordenadasAoi.rectangulo(0.9, 0.1, 0.2, 0.2)))
                .isInstanceOf(SolicitudInvalidaException.class);
    }

    @Test
    void poligonoConMenosDeTresPuntosLanza() {
        assertThatThrownBy(() -> GeometriaAoi.de(FormaGeometrica.POLIGONO,
                CoordenadasAoi.poligono(List.of(new Punto(0.1, 0.1), new Punto(0.5, 0.5)))))
                .isInstanceOf(SolicitudInvalidaException.class);
    }

    // ------------------------------------------------------------ transiciones

    @Test
    void matrizTransicionesIgnoraNulosYRepeticionesConsecutivas() {
        List<Long> porFijacion = Arrays.asList(1L, 1L, null, 2L, 2L, 3L, null, 1L);

        int[][] matriz = CalculadoraMetricas.matrizTransiciones(porFijacion, List.of(1L, 2L, 3L));

        assertThat(Arrays.deepToString(matriz)).isEqualTo("[[0, 1, 0], [0, 0, 1], [1, 0, 0]]");
    }

    @Test
    void nuloEntreDosMismosAoisNoCuentaComoTransicion() {
        List<Long> porFijacion = Arrays.asList(1L, null, 1L);

        int[][] matriz = CalculadoraMetricas.matrizTransiciones(porFijacion, List.of(1L, 2L));

        assertThat(Arrays.deepToString(matriz)).isEqualTo("[[0, 0], [0, 0]]");
    }

    // ------------------------------------------------------------ resumen de sesion

    @Test
    void resumenDeSesionVaciaDaCeroFijaciones() {
        ResumenSesion r = CalculadoraMetricas.resumenSesion(List.of(), List.of(), 0);

        assertThat(r.totalFijaciones()).isZero();
        assertThat(r.duracionMediaFijacionMs()).isNull();
        assertThat(r.tasaFijacionesPorSegundo()).isNull();
    }

    @Test
    void resumenDeSesionCalculaMediasYTasa() {
        List<FijacionVista> fijaciones = List.of(
                f(1, 0, 200, 0.2, 0.2),
                f(2, 200, 300, 0.4, 0.4),
                f(3, 300, 500, 0.6, 0.6));

        ResumenSesion r = CalculadoraMetricas.resumenSesion(fijaciones, List.of(10.0, 5.0), 1000);

        assertThat(r.totalFijaciones()).isEqualTo(3);
        assertThat(r.duracionMediaFijacionMs()).isCloseTo(500.0 / 3, within(1e-9));
        assertThat(r.duracionMedianaFijacionMs()).isEqualTo(200.0);
        assertThat(r.tasaFijacionesPorSegundo()).isCloseTo(3.0, within(1e-9));
        assertThat(r.amplitudMediaSacadaGrados()).isCloseTo(7.5, within(1e-9));
    }
}
