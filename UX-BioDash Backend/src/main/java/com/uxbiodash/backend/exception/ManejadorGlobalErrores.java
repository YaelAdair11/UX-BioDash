package com.uxbiodash.backend.exception;

import com.uxbiodash.backend.web.dto.ApiDtos.ErrorApi;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.support.MissingServletRequestPartException;

/** Traduce las excepciones al formato de error del contrato: {status, mensaje, detalles}. */
@RestControllerAdvice
public class ManejadorGlobalErrores {

    @ExceptionHandler(ArchivoInvalidoException.class)
    public ResponseEntity<ErrorApi> archivoInvalido(ArchivoInvalidoException ex) {
        return respuesta(HttpStatus.BAD_REQUEST, ex.getMessage(), ex.getDetalles());
    }

    @ExceptionHandler(SolicitudInvalidaException.class)
    public ResponseEntity<ErrorApi> solicitudInvalida(SolicitudInvalidaException ex) {
        return respuesta(HttpStatus.BAD_REQUEST, ex.getMessage(), List.of());
    }

    @ExceptionHandler(RecursoNoEncontradoException.class)
    public ResponseEntity<ErrorApi> noEncontrado(RecursoNoEncontradoException ex) {
        return respuesta(HttpStatus.NOT_FOUND, ex.getMessage(), List.of());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorApi> validacion(MethodArgumentNotValidException ex) {
        List<String> detalles = ex.getBindingResult().getFieldErrors().stream()
                .map(e -> e.getField() + ": " + e.getDefaultMessage())
                .toList();
        return respuesta(HttpStatus.BAD_REQUEST, "La solicitud contiene campos con valores no validos.", detalles);
    }

    @ExceptionHandler({MissingServletRequestPartException.class, MissingServletRequestParameterException.class,
            MethodArgumentTypeMismatchException.class})
    public ResponseEntity<ErrorApi> parametroFaltante(Exception ex) {
        return respuesta(HttpStatus.BAD_REQUEST, "Solicitud invalida: " + ex.getMessage(), List.of());
    }

    /** Cuerpo ausente o JSON mal escrito: sin esto Spring responde con su formato por defecto. */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ErrorApi> cuerpoIlegible(HttpMessageNotReadableException ex) {
        return respuesta(HttpStatus.BAD_REQUEST, "El cuerpo de la solicitud no es un JSON valido.",
                List.of("Revisa que el cuerpo este completo y bien escrito (comillas, comas y llaves)."));
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<ErrorApi> archivoMuyGrande(MaxUploadSizeExceededException ex) {
        return respuesta(HttpStatus.CONTENT_TOO_LARGE,
                "El archivo supera el tamano maximo permitido por el servidor.", List.of());
    }

    private static ResponseEntity<ErrorApi> respuesta(HttpStatus estado, String mensaje, List<String> detalles) {
        return ResponseEntity.status(estado).body(new ErrorApi(estado.value(), mensaje, detalles));
    }
}
