package com.uxbiodash.backend.service;

import com.uxbiodash.backend.domain.AreaInteres;
import com.uxbiodash.backend.metricas.CoordenadasAoi;
import com.uxbiodash.backend.metricas.GeometriaAoi;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

/** Serializa las coordenadas de un AOI a/desde la columna JSONB y construye su geometria. */
@Component
public class CodificadorAoi {

    private final ObjectMapper mapper;

    public CodificadorAoi(ObjectMapper mapper) {
        this.mapper = mapper;
    }

    public String aJson(CoordenadasAoi coordenadas) {
        return mapper.writeValueAsString(coordenadas);
    }

    public CoordenadasAoi coordenadas(AreaInteres aoi) {
        return mapper.readValue(aoi.getCoordenadasJson(), CoordenadasAoi.class);
    }

    public GeometriaAoi geometria(AreaInteres aoi) {
        return GeometriaAoi.de(aoi.getFormaGeometrica(), coordenadas(aoi));
    }

    /** Mapa id -> geometria conservando el orden de la lista. */
    public Map<Long, GeometriaAoi> geometrias(List<AreaInteres> aois) {
        Map<Long, GeometriaAoi> mapa = new LinkedHashMap<>();
        aois.forEach(a -> mapa.put(a.getId(), geometria(a)));
        return mapa;
    }
}
