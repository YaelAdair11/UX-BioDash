package com.uxbiodash.backend.service;

import com.uxbiodash.backend.domain.Estimulo;
import com.uxbiodash.backend.domain.EstimuloImagen;
import com.uxbiodash.backend.domain.Proyecto;
import com.uxbiodash.backend.exception.ArchivoInvalidoException;
import com.uxbiodash.backend.exception.RecursoNoEncontradoException;
import com.uxbiodash.backend.repository.EstimuloImagenRepository;
import com.uxbiodash.backend.repository.EstimuloRepository;
import com.uxbiodash.backend.web.dto.ApiDtos.EstimuloResponse;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.List;
import javax.imageio.ImageIO;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

/** Gestion de estimulos (capturas PNG/JPG de la interfaz evaluada), almacenadas en PostgreSQL. */
@Service
public class EstimuloService {

    public record ImagenEstimulo(String tipoMime, String nombreArchivo, byte[] contenido) {
    }

    private final EstimuloRepository estimulos;
    private final EstimuloImagenRepository imagenes;
    private final ProyectoService proyectos;

    public EstimuloService(EstimuloRepository estimulos, EstimuloImagenRepository imagenes,
                           ProyectoService proyectos) {
        this.estimulos = estimulos;
        this.imagenes = imagenes;
        this.proyectos = proyectos;
    }

    @Transactional
    public EstimuloResponse crear(Long proyectoId, MultipartFile archivo, String nombre) {
        Proyecto proyecto = proyectos.buscar(proyectoId);
        if (archivo == null || archivo.isEmpty()) {
            throw new ArchivoInvalidoException("Debe adjuntar una imagen PNG o JPG del estimulo.");
        }
        byte[] bytes;
        try {
            bytes = archivo.getBytes();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        String mime = detectarTipo(bytes);
        BufferedImage imagen;
        try {
            imagen = ImageIO.read(new ByteArrayInputStream(bytes));
        } catch (IOException e) {
            imagen = null;
        }
        if (imagen == null) {
            throw new ArchivoInvalidoException("La imagen esta danada o no se puede leer.");
        }

        String nombreFinal = nombre != null && !nombre.isBlank() ? nombre.trim() : archivo.getOriginalFilename();
        if (nombreFinal == null || nombreFinal.isBlank()) {
            nombreFinal = "estimulo" + (mime.equals("image/png") ? ".png" : ".jpg");
        }
        if (nombreFinal.length() > 255) {
            nombreFinal = nombreFinal.substring(0, 255);
        }

        Estimulo e = new Estimulo();
        e.setProyecto(proyecto);
        e.setNombreArchivo(nombreFinal);
        e.setAnchoOriginalPx(imagen.getWidth());
        e.setAltoOriginalPx(imagen.getHeight());
        e = estimulos.save(e);
        e.setUrlRecurso("/api/estimulos/" + e.getId() + "/imagen");

        EstimuloImagen img = new EstimuloImagen();
        img.setEstimulo(e);
        img.setTipoMime(mime);
        img.setTamanoBytes((long) bytes.length);
        img.setContenido(bytes);
        imagenes.save(img);
        return Mapeador.estimulo(e);
    }

    @Transactional(readOnly = true)
    public List<EstimuloResponse> listar(Long proyectoId) {
        proyectos.buscar(proyectoId);
        return estimulos.findByProyectoIdOrderByIdAsc(proyectoId).stream().map(Mapeador::estimulo).toList();
    }

    @Transactional(readOnly = true)
    public EstimuloResponse obtener(Long id) {
        return Mapeador.estimulo(buscar(id));
    }

    @Transactional(readOnly = true)
    public ImagenEstimulo imagen(Long id) {
        Estimulo e = buscar(id);
        EstimuloImagen img = imagenes.findById(id)
                .orElseThrow(() -> new RecursoNoEncontradoException("Imagen del estimulo", id));
        return new ImagenEstimulo(img.getTipoMime(), e.getNombreArchivo(), img.getContenido());
    }

    @Transactional
    public void eliminar(Long id) {
        estimulos.delete(buscar(id));
    }

    Estimulo buscar(Long id) {
        return estimulos.findById(id).orElseThrow(() -> new RecursoNoEncontradoException("Estimulo", id));
    }

    /** Valida por firma binaria (magic bytes) que el archivo sea PNG o JPG. */
    static String detectarTipo(byte[] b) {
        if (b.length >= 8 && (b[0] & 0xFF) == 0x89 && b[1] == 'P' && b[2] == 'N' && b[3] == 'G') {
            return "image/png";
        }
        if (b.length >= 3 && (b[0] & 0xFF) == 0xFF && (b[1] & 0xFF) == 0xD8 && (b[2] & 0xFF) == 0xFF) {
            return "image/jpeg";
        }
        throw new ArchivoInvalidoException("Formato de imagen no soportado. Solo se aceptan capturas PNG o JPG.");
    }
}
