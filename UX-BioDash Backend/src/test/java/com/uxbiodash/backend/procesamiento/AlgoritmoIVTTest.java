package com.uxbiodash.backend.procesamiento;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class AlgoritmoIVTTest {

    private static final AlgoritmoIVT IVT =
            new AlgoritmoIVT(new ConversorAnguloVisual(60, 53.1, 29.9), 30, 60, 75, 75, 0.5);

    /** Anade {@code n} muestras a 100 Hz (10 ms) en la posicion dada, empezando en {@code desdeMs}. */
    private static void agregar(List<MuestraNormalizada> lista, double desdeMs, int n, double x, double y) {
        for (int i = 0; i < n; i++) {
            lista.add(new MuestraNormalizada(desdeMs + i * 10.0, x, y, null));
        }
    }

    @Test
    void dosFijacionesSeparadasPorUnaSacada() {
        List<MuestraNormalizada> muestras = new ArrayList<>();
        agregar(muestras, 0, 30, 0.2, 0.2);     // t = 0..290 ms
        agregar(muestras, 300, 30, 0.8, 0.8);   // t = 300..590 ms

        AlgoritmoIVT.Resultado r = IVT.clasificar(muestras);

        assertThat(r.fijaciones()).hasSize(2);
        FijacionCalculada primera = r.fijaciones().get(0);
        FijacionCalculada segunda = r.fijaciones().get(1);
        assertThat(primera.orden()).isEqualTo(1);
        assertThat(segunda.orden()).isEqualTo(2);
        assertThat(primera.x()).isCloseTo(0.2, within(1e-6));
        assertThat(primera.y()).isCloseTo(0.2, within(1e-6));
        assertThat(segunda.x()).isCloseTo(0.8, within(1e-6));
        assertThat(segunda.y()).isCloseTo(0.8, within(1e-6));
        // La muestra de transicion (t=300) es sacada, por lo que la segunda fijacion va de 310 a 590 ms
        assertThat(primera.duracionMs()).isEqualTo(290);
        assertThat(segunda.duracionMs()).isEqualTo(280);

        assertThat(r.sacadas()).hasSize(1);
        SacadaCalculada sacada = r.sacadas().get(0);
        assertThat(sacada.indiceOrigen()).isEqualTo(0);
        assertThat(sacada.indiceDestino()).isEqualTo(1);
        assertThat(sacada.amplitudGrados()).isGreaterThan(0);
        assertThat(sacada.velocidadPicoGradosS()).isGreaterThan(30);
    }

    @Test
    void fijacionMasCortaQueLaMinimaSeDescarta() {
        List<MuestraNormalizada> muestras = new ArrayList<>();
        agregar(muestras, 0, 30, 0.2, 0.2);    // fijacion larga A
        agregar(muestras, 300, 4, 0.8, 0.8);   // t=300 sacada; t=310..330 fijacion de 20 ms (< 60 ms)
        agregar(muestras, 340, 30, 0.2, 0.2);  // fijacion larga C

        AlgoritmoIVT.Resultado r = IVT.clasificar(muestras);

        assertThat(r.fijaciones()).hasSize(2);
        assertThat(r.fijaciones()).allMatch(f -> Math.abs(f.x() - 0.2) < 1e-6);
    }

    @Test
    void menosDeDosMuestrasNoProduceNada() {
        AlgoritmoIVT.Resultado vacia = IVT.clasificar(List.of());
        assertThat(vacia.fijaciones()).isEmpty();
        assertThat(vacia.sacadas()).isEmpty();

        AlgoritmoIVT.Resultado una = IVT.clasificar(List.of(new MuestraNormalizada(0, 0.2, 0.2, null)));
        assertThat(una.fijaciones()).isEmpty();
        assertThat(una.sacadas()).isEmpty();
    }
}
