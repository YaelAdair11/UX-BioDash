package com.uxbiodash.backend.ingesta;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.uxbiodash.backend.exception.ArchivoInvalidoException;
import com.uxbiodash.backend.ingesta.LectorArchivoEyeTracking.OpcionesLectura;
import com.uxbiodash.backend.ingesta.LectorArchivoEyeTracking.ResultadoLectura;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

class LectorArchivoEyeTrackingTest {

    private static ResultadoLectura leer(String contenido) {
        return leer(contenido, OpcionesLectura.ninguna());
    }

    private static ResultadoLectura leer(String contenido, OpcionesLectura opciones) {
        return LectorArchivoEyeTracking.leer(
                new ByteArrayInputStream(contenido.getBytes(StandardCharsets.UTF_8)), opciones);
    }

    private static String csv(String... lineas) {
        return String.join("\n", lineas);
    }

    @Test
    void leeMuestrasGenericasDeDosParticipantes() {
        ResultadoLectura r = leer(csv(
                "participante,tiempo_ms,x,y",
                "P01,0,100,200",
                "P01,10,110,210",
                "P02,0,300,400",
                "P02,10,310,410"));

        assertThat(r.muestras()).hasSize(4);
        assertThat(r.muestras()).extracting(RawGazeSampleDTO::participante)
                .containsExactly("P01", "P01", "P02", "P02");
        assertThat(r.muestras().get(2).xRaw()).isEqualTo(300.0);
        assertThat(r.filasLeidas()).isEqualTo(4);
        assertThat(r.filasMalformadas()).isZero();
        assertThat(r.mapeo().dispositivo()).isEqualTo(Dispositivo.GENERICO);
        assertThat(r.mapeo().nivel()).isEqualTo(NivelDatos.MUESTRAS);
    }

    @Test
    void soloCabeceraLanza() {
        assertThatThrownBy(() -> leer(csv("participante,tiempo_ms,x,y")))
                .isInstanceOf(ArchivoInvalidoException.class);
    }

    @Test
    void archivoVacioLanza() {
        assertThatThrownBy(() -> leer(""))
                .isInstanceOf(ArchivoInvalidoException.class);
    }

    @Test
    void filaMalformadaSeCuentaYSeDescarta() {
        ResultadoLectura r = leer(csv(
                "participante,tiempo_ms,x,y",
                "P01,0,100,200",
                "P01,10",
                "P01,20,120,220"));

        assertThat(r.filasMalformadas()).isEqualTo(1);
        assertThat(r.filasLeidas()).isEqualTo(3);
        assertThat(r.muestras()).hasSize(2);
        assertThat(r.advertencias()).anyMatch(a -> a.contains("descartadas"));
    }

    @Test
    void tobiiConPuntoYComaYComaDecimal() {
        ResultadoLectura r = leer(csv(
                "Participant name;Recording timestamp;Gaze point X;Gaze point Y",
                "P01;1000;1037,5;512,25",
                "P01;1010;1040,0;510,75"));

        assertThat(r.delimitador()).isEqualTo(';');
        assertThat(r.mapeo().dispositivo()).isEqualTo(Dispositivo.TOBII);
        assertThat(r.muestras()).hasSize(2);
        assertThat(r.muestras().get(0).xRaw()).isEqualTo(1037.5);
        assertThat(r.muestras().get(0).yRaw()).isEqualTo(512.25);
    }

    @Test
    void tobiiEventosConservaSoloFijaciones() {
        ResultadoLectura r = leer(csv(
                "Participant;Stimulus;Eye movement type;Eye movement id;Start timestamp;End timestamp;"
                        + "Duration;Fixation point X;Fixation point Y;Flagged eye movement",
                "P01;img1;Fixation;1;1000;1200;200;500;400;False",
                "P01;img1;Saccade;2;1200;1250;50;510;405;False",
                "P01;img1;Fixation;3;1250;1500;250;700;350;False"));

        assertThat(r.mapeo().nivel()).isEqualTo(NivelDatos.EVENTOS);
        assertThat(r.eventos()).hasSize(2);
        assertThat(r.eventos().get(0).inicioRaw()).isEqualTo(1000.0);
        assertThat(r.eventos().get(0).finRaw()).isEqualTo(1200.0);
        assertThat(r.eventos().get(0).duracionMs()).isEqualTo(200.0);
        assertThat(r.eventos().get(0).xRaw()).isEqualTo(500.0);
        assertThat(r.eventos().get(1).inicioRaw()).isEqualTo(1250.0);
        assertThat(r.eventos().get(1).xRaw()).isEqualTo(700.0);
        assertThat(r.eventos()).allMatch(RawFixationEventDTO::valido);
    }

    @Test
    void asignaParticipantePorDefectoCuandoNoHayColumna() {
        ResultadoLectura r = leer(
                csv("tiempo_ms,x,y", "0,100,200", "10,110,210"),
                new OpcionesLectura("P01", null, null));

        assertThat(r.muestras()).hasSize(2);
        assertThat(r.muestras()).extracting(RawGazeSampleDTO::participante).containsOnly("P01");
    }

    @Test
    void infiereCoordenadasNormalizadas() {
        ResultadoLectura r = leer(csv(
                "participante,tiempo_ms,x,y",
                "P01,0,0.2,0.3",
                "P01,10,0.5,0.6",
                "P01,20,0.9,0.95"));

        assertThat(r.mapeo().coordenadas()).isEqualTo(SistemaCoordenadas.NORMALIZADO);
        assertThat(r.advertencias()).anyMatch(a -> a.contains("inferido"));
    }

    @Test
    void infiereCoordenadasEnPixeles() {
        ResultadoLectura r = leer(csv(
                "participante,tiempo_ms,x,y",
                "P01,0,960,540",
                "P01,10,1000,600"));

        assertThat(r.mapeo().coordenadas()).isEqualTo(SistemaCoordenadas.PIXELES);
    }
}
