package com.uxbiodash.backend.service;

import com.uxbiodash.backend.exception.RecursoNoEncontradoException;
import com.uxbiodash.backend.web.dto.ApiDtos.TarjetaAyuda;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.List;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

/** Tarjetas explicativas que acompanan a cada grafica del panel (apoyo a la interpretacion). */
@Service
public class AyudaService {

    private final List<TarjetaAyuda> tarjetas;

    public AyudaService(ObjectMapper mapper) {
        try (InputStream in = new ClassPathResource("ayuda/tarjetas.json").getInputStream()) {
            this.tarjetas = List.copyOf(mapper.readValue(in, new TypeReference<List<TarjetaAyuda>>() { }));
        } catch (IOException e) {
            throw new UncheckedIOException("No se pudieron cargar las tarjetas de ayuda", e);
        }
    }

    public List<TarjetaAyuda> listar() {
        return tarjetas;
    }

    public TarjetaAyuda obtener(String clave) {
        return tarjetas.stream().filter(t -> t.clave().equalsIgnoreCase(clave)).findFirst()
                .orElseThrow(() -> new RecursoNoEncontradoException("Tarjeta de ayuda", clave));
    }
}
