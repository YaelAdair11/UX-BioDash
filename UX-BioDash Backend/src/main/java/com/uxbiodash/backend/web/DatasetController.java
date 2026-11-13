package com.uxbiodash.backend.web;

import com.uxbiodash.backend.service.DatasetService;
import com.uxbiodash.backend.web.dto.ApiDtos.ResumenIngesta;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api")
public class DatasetController {

    private final DatasetService datasets;

    public DatasetController(DatasetService datasets) {
        this.datasets = datasets;
    }

    /** Sube el archivo de eye tracking (CSV/TSV) y la captura del estimulo (PNG/JPG) en una sola peticion. */
    @PostMapping(value = "/datasets", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    public ResumenIngesta ingestar(@RequestParam Long proyectoId,
                                   @RequestPart("archivo") MultipartFile archivo,
                                   @RequestPart("imagen") MultipartFile imagen) {
        return datasets.ingestar(proyectoId, archivo, imagen);
    }
}
