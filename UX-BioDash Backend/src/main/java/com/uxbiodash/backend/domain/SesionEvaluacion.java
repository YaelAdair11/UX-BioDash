package com.uxbiodash.backend.domain;

import jakarta.persistence.*;
import java.math.BigDecimal;

@Entity
@Table(name = "sesion_evaluacion")
public class SesionEvaluacion {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "proyecto_id", nullable = false)
    private Proyecto proyecto;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "estimulo_id", nullable = false)
    private Estimulo estimulo;

    @Column(name = "codigo_sujeto", nullable = false, length = 50)
    private String codigoSujeto;

    @Column(name = "dispositivo_origen", nullable = false, length = 50)
    private String dispositivoOrigen;

    @Column(name = "duracion_total_ms")
    private Integer duracionTotalMs;

    @Column(name = "tasa_muestreo_hz", precision = 6, scale = 2)
    private BigDecimal tasaMuestreoHz;

    public Long getId() { return id; }
    public Proyecto getProyecto() { return proyecto; }
    public void setProyecto(Proyecto proyecto) { this.proyecto = proyecto; }
    public Estimulo getEstimulo() { return estimulo; }
    public void setEstimulo(Estimulo estimulo) { this.estimulo = estimulo; }
    public String getCodigoSujeto() { return codigoSujeto; }
    public void setCodigoSujeto(String codigoSujeto) { this.codigoSujeto = codigoSujeto; }
    public String getDispositivoOrigen() { return dispositivoOrigen; }
    public void setDispositivoOrigen(String dispositivoOrigen) { this.dispositivoOrigen = dispositivoOrigen; }
    public Integer getDuracionTotalMs() { return duracionTotalMs; }
    public void setDuracionTotalMs(Integer duracionTotalMs) { this.duracionTotalMs = duracionTotalMs; }
    public BigDecimal getTasaMuestreoHz() { return tasaMuestreoHz; }
    public void setTasaMuestreoHz(BigDecimal tasaMuestreoHz) { this.tasaMuestreoHz = tasaMuestreoHz; }
}
