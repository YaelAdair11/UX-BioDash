package com.uxbiodash.backend.service;

import com.uxbiodash.backend.domain.Estimulo;
import com.uxbiodash.backend.domain.Fijacion;
import com.uxbiodash.backend.domain.MetricaAoiResultado;
import com.uxbiodash.backend.domain.Proyecto;
import com.uxbiodash.backend.domain.Sacada;
import com.uxbiodash.backend.domain.SesionEvaluacion;
import com.uxbiodash.backend.metricas.CalculadoraMetricas.FijacionVista;
import com.uxbiodash.backend.web.dto.ApiDtos.EstimuloResponse;
import com.uxbiodash.backend.web.dto.ApiDtos.FijacionResponse;
import com.uxbiodash.backend.web.dto.ApiDtos.MetricaAoiResponse;
import com.uxbiodash.backend.web.dto.ApiDtos.ProyectoResponse;
import com.uxbiodash.backend.web.dto.ApiDtos.SacadaResponse;
import com.uxbiodash.backend.web.dto.ApiDtos.SesionResponse;

/** Conversion entidad -> DTO. */
public final class Mapeador {

    private Mapeador() {
    }

    public static ProyectoResponse proyecto(Proyecto p, int participantes, int aois, Integer duracionMediaMs,
                                            EstimuloResponse principal) {
        return new ProyectoResponse(p.getId(), p.getNombre(), p.getDescripcion(), p.getCreadoEn(),
                p.getModificadoEn(), p.getEstado(), p.getDispositivo(), participantes, aois, duracionMediaMs,
                principal);
    }

    public static EstimuloResponse estimulo(Estimulo e) {
        return new EstimuloResponse(e.getId(), e.getProyecto().getId(), e.getNombreArchivo(),
                e.getAnchoOriginalPx(), e.getAltoOriginalPx(), e.getUrlRecurso(), e.getEtiquetaDispositivo());
    }

    public static SesionResponse sesion(SesionEvaluacion s) {
        return new SesionResponse(s.getId(), s.getProyecto().getId(), s.getEstimulo().getId(), s.getCodigoSujeto(),
                s.getDispositivoOrigen(), s.getDuracionTotalMs(), s.getTasaMuestreoHz());
    }

    public static FijacionResponse fijacion(Fijacion f) {
        return new FijacionResponse(f.getId(), f.getSesion().getId(), f.getOrdenSecuencia(), f.getInicioMs(),
                f.getFinMs(), f.getDuracionMs(), f.getXNormalizado(), f.getYNormalizado(),
                f.getAoi() != null ? f.getAoi().getId() : null);
    }

    public static SacadaResponse sacada(Sacada s) {
        return new SacadaResponse(s.getId(), s.getFijacionOrigen().getId(), s.getFijacionDestino().getId(),
                s.getAmplitudGrados(), s.getVelocidadPicoGradosS());
    }

    public static MetricaAoiResponse metrica(MetricaAoiResultado m) {
        return new MetricaAoiResponse(m.getSesion().getId(), m.getSesion().getCodigoSujeto(), m.getAoi().getId(),
                m.getAoi().getEtiqueta(), m.getConteoFijaciones(), m.getDuracionMediaFijacionMs(),
                m.getDwellTimeMs(), m.getPorcentajeDwell(), m.getTtffMs(), m.getNumeroVisitas());
    }

    public static FijacionVista vista(Fijacion f) {
        return new FijacionVista(f.getOrdenSecuencia(), f.getInicioMs(), f.getFinMs(), f.getDuracionMs(),
                f.getXNormalizado(), f.getYNormalizado());
    }
}
