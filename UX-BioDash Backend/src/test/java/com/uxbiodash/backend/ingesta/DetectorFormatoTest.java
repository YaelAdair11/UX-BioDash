package com.uxbiodash.backend.ingesta;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.uxbiodash.backend.exception.ArchivoInvalidoException;
import java.util.List;
import org.junit.jupiter.api.Test;

class DetectorFormatoTest {

    private static final String CABECERA_TOBII_EVENTOS = "Participant;Stimulus;Eye movement type;Eye movement id;"
            + "Start timestamp;End timestamp;Duration;Fixation point X;Fixation point Y;Flagged eye movement";
    private static final String CABECERA_GAZEPOINT = "USER,TIME(2024/01/01 10:00:00.000),BPOGX,BPOGY,BPOGV,LPMM,RPMM";
    private static final String CABECERA_EYELINK =
            "RECORDING_SESSION_LABEL\tTIMESTAMP\tAVERAGE_GAZE_X\tAVERAGE_GAZE_Y\tAVERAGE_PUPIL_SIZE";
    private static final String CABECERA_GENERICA = "participante,tiempo_ms,x,y";

    private static List<String> columnas(String cabecera, char delimitador) {
        return List.of(cabecera.split(String.valueOf(delimitador), -1));
    }

    // ------------------------------------------------------------ delimitador

    @Test
    void detectaComa() {
        assertThat(DetectorFormato.detectarDelimitador("a,b,c")).isEqualTo(',');
    }

    @Test
    void detectaPuntoYComa() {
        assertThat(DetectorFormato.detectarDelimitador("a;b;c")).isEqualTo(';');
    }

    @Test
    void detectaTabulador() {
        assertThat(DetectorFormato.detectarDelimitador("a\tb\tc")).isEqualTo('\t');
    }

    @Test
    void ignoraComasDentroDeComillas() {
        // Las comas entre comillas no cuentan: gana el punto y coma
        assertThat(DetectorFormato.detectarDelimitador("\"a,b,c,d\";e;f")).isEqualTo(';');
    }

    @Test
    void lanzaSiNoHaySeparador() {
        assertThatThrownBy(() -> DetectorFormato.detectarDelimitador("encabezado_sin_separador"))
                .isInstanceOf(ArchivoInvalidoException.class);
    }

    // ------------------------------------------------------------ formatos

    @Test
    void reconoceEventosDeTobii() {
        MapeoColumnas m = DetectorFormato.detectar(columnas(CABECERA_TOBII_EVENTOS, ';'), false);

        assertThat(m.dispositivo()).isEqualTo(Dispositivo.TOBII);
        assertThat(m.nivel()).isEqualTo(NivelDatos.EVENTOS);
        assertThat(m.unidadTiempo()).isEqualTo(UnidadTiempo.MICROSEGUNDOS);
        assertThat(m.coordenadas()).isEqualTo(SistemaCoordenadas.PIXELES);
        assertThat(m.inicio()).isGreaterThanOrEqualTo(0);
    }

    @Test
    void reconoceGazepoint() {
        MapeoColumnas m = DetectorFormato.detectar(columnas(CABECERA_GAZEPOINT, ','), false);

        assertThat(m.dispositivo()).isEqualTo(Dispositivo.GAZEPOINT);
        assertThat(m.nivel()).isEqualTo(NivelDatos.MUESTRAS);
        assertThat(m.unidadTiempo()).isEqualTo(UnidadTiempo.SEGUNDOS);
        assertThat(m.coordenadas()).isEqualTo(SistemaCoordenadas.NORMALIZADO);
    }

    @Test
    void reconoceEyeLink() {
        MapeoColumnas m = DetectorFormato.detectar(columnas(CABECERA_EYELINK, '\t'), false);

        assertThat(m.dispositivo()).isEqualTo(Dispositivo.EYELINK);
        assertThat(m.nivel()).isEqualTo(NivelDatos.MUESTRAS);
        assertThat(m.unidadTiempo()).isEqualTo(UnidadTiempo.MILISEGUNDOS);
        assertThat(m.coordenadas()).isEqualTo(SistemaCoordenadas.PIXELES);
    }

    @Test
    void reconoceFormatoGenericoConUnidadPorSufijo() {
        MapeoColumnas m = DetectorFormato.detectar(columnas(CABECERA_GENERICA, ','), false);

        assertThat(m.dispositivo()).isEqualTo(Dispositivo.GENERICO);
        assertThat(m.nivel()).isEqualTo(NivelDatos.MUESTRAS);
        assertThat(m.unidadTiempo()).isEqualTo(UnidadTiempo.MILISEGUNDOS);
        // Las coordenadas del formato generico se infieren despues, en el lector
        assertThat(m.coordenadas()).isNull();
    }

    @Test
    void genericoSinColumnaXLanzaConDetalles() {
        List<String> sinX = columnas("participante,tiempo_ms,y", ',');

        assertThatThrownBy(() -> DetectorFormato.detectar(sinX, false))
                .isInstanceOf(ArchivoInvalidoException.class)
                .satisfies(ex -> assertThat(((ArchivoInvalidoException) ex).getDetalles()).isNotEmpty());
    }

    @Test
    void participanteOpcionalNoLanzaSiFaltaLaColumna() {
        MapeoColumnas m = DetectorFormato.detectar(columnas("tiempo_ms,x,y", ','), true);

        assertThat(m.participante()).isEqualTo(MapeoColumnas.AUSENTE);
        assertThat(m.x()).isGreaterThanOrEqualTo(0);
    }
}
