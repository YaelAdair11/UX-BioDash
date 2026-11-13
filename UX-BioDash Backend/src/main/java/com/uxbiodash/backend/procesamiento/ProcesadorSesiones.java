package com.uxbiodash.backend.procesamiento;

import com.uxbiodash.backend.config.ProcesamientoProperties;
import com.uxbiodash.backend.ingesta.MapeoColumnas;
import com.uxbiodash.backend.ingesta.RawFixationEventDTO;
import com.uxbiodash.backend.ingesta.RawGazeSampleDTO;
import com.uxbiodash.backend.procesamiento.Normalizador.Resolucion;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Etapa 5 del procesamiento: consolida, por participante, las fijaciones obtenidas por cualquiera de las
 * dos vias (eventos del fabricante o I-VT sobre muestras crudas), ya normalizadas en espacio y tiempo.
 */
public class ProcesadorSesiones {

    private final ConversorAnguloVisual conversor;
    private final AlgoritmoIVT ivt;
    private final double velocidadMaxima;

    public ProcesadorSesiones(ProcesamientoProperties p) {
        this.conversor = new ConversorAnguloVisual(p.distanciaVisualCm(), p.anchoPantallaCm(), p.altoPantallaCm());
        this.ivt = new AlgoritmoIVT(conversor, p.ivtUmbralVelocidadGradosS(), p.ivtDuracionMinimaFijacionMs(),
                p.ivtSeparacionMaximaMs(), p.ivtFusionMaximaMs(), p.ivtFusionMaximaGrados());
        this.velocidadMaxima = p.velocidadMaximaFisiologicaGradosS();
    }

    public ConversorAnguloVisual getConversor() {
        return conversor;
    }

    /** Resultado del procesamiento de un participante. */
    public record SesionProcesada(
            String codigoSujeto,
            List<MuestraNormalizada> muestras,
            List<FijacionCalculada> fijaciones,
            List<SacadaCalculada> sacadas,
            int duracionTotalMs,
            Double tasaMuestreoHz,
            int registrosRecibidos,
            int registrosDescartados) {
    }

    public List<SesionProcesada> procesarMuestras(List<RawGazeSampleDTO> muestras, MapeoColumnas mapeo,
                                                  Resolucion resolucion) {
        Map<String, List<RawGazeSampleDTO>> porParticipante = new LinkedHashMap<>();
        for (RawGazeSampleDTO m : muestras) {
            porParticipante.computeIfAbsent(m.participante(), k -> new ArrayList<>()).add(m);
        }
        List<SesionProcesada> sesiones = new ArrayList<>();
        porParticipante.forEach((sujeto, crudas) -> {
            Normalizador.ResultadoNormalizacion norm = Normalizador.normalizarMuestras(crudas,
                    mapeo.unidadTiempo(), mapeo.coordenadas(), resolucion, conversor, velocidadMaxima);
            List<MuestraNormalizada> limpias = norm.muestras();
            AlgoritmoIVT.Resultado r = ivt.clasificar(limpias);
            int duracion = limpias.isEmpty() ? 0 : (int) Math.round(limpias.get(limpias.size() - 1).tiempoMs());
            Double tasa = limpias.size() > 1 && duracion > 0 ? (limpias.size() - 1) / (duracion / 1000.0) : null;
            sesiones.add(new SesionProcesada(sujeto, limpias, r.fijaciones(), r.sacadas(), duracion,
                    tasa != null ? Math.min(tasa, 9999.99) : null, crudas.size(),
                    norm.descartadasInvalidas() + norm.descartadasVelocidad()));
        });
        return sesiones;
    }

    public List<SesionProcesada> procesarEventos(List<RawFixationEventDTO> eventos, MapeoColumnas mapeo,
                                                 Resolucion resolucion) {
        Map<String, List<RawFixationEventDTO>> porParticipante = new LinkedHashMap<>();
        for (RawFixationEventDTO e : eventos) {
            porParticipante.computeIfAbsent(e.participante(), k -> new ArrayList<>()).add(e);
        }
        List<SesionProcesada> sesiones = new ArrayList<>();
        porParticipante.forEach((sujeto, crudos) -> {
            List<RawFixationEventDTO> validos = crudos.stream()
                    .filter(e -> e.valido() && !Normalizador.coordenadaInvalida(e.xRaw())
                            && !Normalizador.coordenadaInvalida(e.yRaw()))
                    .sorted(Comparator.comparingDouble(RawFixationEventDTO::inicioRaw))
                    .toList();
            List<FijacionCalculada> fijaciones = new ArrayList<>();
            if (!validos.isEmpty()) {
                double t0 = validos.get(0).inicioRaw();
                int orden = 1;
                for (RawFixationEventDTO e : validos) {
                    int inicio = (int) Math.round(mapeo.unidadTiempo().aMilisegundos(e.inicioRaw() - t0));
                    int duracion;
                    if (e.duracionMs() != null) {
                        duracion = (int) Math.round(e.duracionMs());
                    } else if (e.finRaw() != null) {
                        duracion = (int) Math.round(mapeo.unidadTiempo().aMilisegundos(e.finRaw() - e.inicioRaw()));
                    } else {
                        duracion = 0;
                    }
                    if (duracion < 0) {
                        continue;
                    }
                    double x = Normalizador.normalizarCoordenada(e.xRaw(), mapeo.coordenadas(), resolucion.anchoPx());
                    double y = Normalizador.normalizarCoordenada(e.yRaw(), mapeo.coordenadas(), resolucion.altoPx());
                    fijaciones.add(new FijacionCalculada(orden++, inicio, inicio + duracion, duracion, x, y));
                }
            }
            List<SacadaCalculada> sacadas = new ArrayList<>();
            for (int k = 0; k + 1 < fijaciones.size(); k++) {
                FijacionCalculada a = fijaciones.get(k);
                FijacionCalculada b = fijaciones.get(k + 1);
                sacadas.add(new SacadaCalculada(k, k + 1, conversor.grados(a.x(), a.y(), b.x(), b.y()), null));
            }
            int duracionTotal = fijaciones.stream().mapToInt(FijacionCalculada::finMs).max().orElse(0);
            sesiones.add(new SesionProcesada(sujeto, List.of(), fijaciones, sacadas, duracionTotal, null,
                    crudos.size(), crudos.size() - fijaciones.size()));
        });
        return sesiones;
    }
}
