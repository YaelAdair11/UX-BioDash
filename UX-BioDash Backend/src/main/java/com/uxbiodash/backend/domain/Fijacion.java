package com.uxbiodash.backend.domain;

import jakarta.persistence.*;

@Entity
@Table(name = "fijacion")
public class Fijacion {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "sesion_id", nullable = false)
    private SesionEvaluacion sesion;

    @Column(name = "orden_secuencia", nullable = false)
    private Integer ordenSecuencia;

    @Column(name = "inicio_ms", nullable = false)
    private Integer inicioMs;

    @Column(name = "fin_ms", nullable = false)
    private Integer finMs;

    @Column(name = "duracion_ms", nullable = false)
    private Integer duracionMs;

    @Column(name = "x_normalizado", nullable = false)
    private Double xNormalizado;

    @Column(name = "y_normalizado", nullable = false)
    private Double yNormalizado;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "aoi_id")
    private AreaInteres aoi;

    public Long getId() { return id; }
    public SesionEvaluacion getSesion() { return sesion; }
    public void setSesion(SesionEvaluacion sesion) { this.sesion = sesion; }
    public Integer getOrdenSecuencia() { return ordenSecuencia; }
    public void setOrdenSecuencia(Integer ordenSecuencia) { this.ordenSecuencia = ordenSecuencia; }
    public Integer getInicioMs() { return inicioMs; }
    public void setInicioMs(Integer inicioMs) { this.inicioMs = inicioMs; }
    public Integer getFinMs() { return finMs; }
    public void setFinMs(Integer finMs) { this.finMs = finMs; }
    public Integer getDuracionMs() { return duracionMs; }
    public void setDuracionMs(Integer duracionMs) { this.duracionMs = duracionMs; }
    public Double getXNormalizado() { return xNormalizado; }
    public void setXNormalizado(Double xNormalizado) { this.xNormalizado = xNormalizado; }
    public Double getYNormalizado() { return yNormalizado; }
    public void setYNormalizado(Double yNormalizado) { this.yNormalizado = yNormalizado; }
    public AreaInteres getAoi() { return aoi; }
    public void setAoi(AreaInteres aoi) { this.aoi = aoi; }
}
