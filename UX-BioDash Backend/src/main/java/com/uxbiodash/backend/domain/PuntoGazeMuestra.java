package com.uxbiodash.backend.domain;

import jakarta.persistence.*;

/**
 * Muestra cruda ya normalizada (t=0 ms, coordenadas [0,1]).
 * Por volumen, la insercion se hace por lotes con JDBC (MuestraJdbcRepository);
 * esta entidad se usa para lectura.
 */
@Entity
@Table(name = "punto_gaze_muestra")
public class PuntoGazeMuestra {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "sesion_id", nullable = false)
    private Long sesionId;

    @Column(name = "tiempo_ms", nullable = false)
    private Integer tiempoMs;

    @Column(name = "x_normalizado", nullable = false)
    private Double xNormalizado;

    @Column(name = "y_normalizado", nullable = false)
    private Double yNormalizado;

    @Column(name = "diametro_pupila")
    private Double diametroPupila;

    public Long getId() { return id; }
    public Long getSesionId() { return sesionId; }
    public Integer getTiempoMs() { return tiempoMs; }
    public Double getXNormalizado() { return xNormalizado; }
    public Double getYNormalizado() { return yNormalizado; }
    public Double getDiametroPupila() { return diametroPupila; }
}
