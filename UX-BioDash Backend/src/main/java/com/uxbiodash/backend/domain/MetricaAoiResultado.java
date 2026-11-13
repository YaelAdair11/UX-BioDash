package com.uxbiodash.backend.domain;

import jakarta.persistence.*;
import java.time.LocalDateTime;

/** Metricas precalculadas de un AOI para una sesion (participante). */
@Entity
@Table(name = "metrica_aoi_resultado")
public class MetricaAoiResultado {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "aoi_id", nullable = false)
    private AreaInteres aoi;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "sesion_id", nullable = false)
    private SesionEvaluacion sesion;

    @Column(name = "conteo_fijaciones", nullable = false)
    private Integer conteoFijaciones;

    @Column(name = "duracion_media_fijacion_ms")
    private Double duracionMediaFijacionMs;

    @Column(name = "dwell_time_ms", nullable = false)
    private Integer dwellTimeMs;

    @Column(name = "porcentaje_dwell")
    private Double porcentajeDwell;

    @Column(name = "ttff_ms")
    private Integer ttffMs;

    @Column(name = "numero_visitas", nullable = false)
    private Integer numeroVisitas;

    @Column(name = "calculado_en", nullable = false)
    private LocalDateTime calculadoEn;

    @PrePersist
    @PreUpdate
    void marcarCalculo() {
        calculadoEn = LocalDateTime.now();
    }

    public Long getId() { return id; }
    public AreaInteres getAoi() { return aoi; }
    public void setAoi(AreaInteres aoi) { this.aoi = aoi; }
    public SesionEvaluacion getSesion() { return sesion; }
    public void setSesion(SesionEvaluacion sesion) { this.sesion = sesion; }
    public Integer getConteoFijaciones() { return conteoFijaciones; }
    public void setConteoFijaciones(Integer conteoFijaciones) { this.conteoFijaciones = conteoFijaciones; }
    public Double getDuracionMediaFijacionMs() { return duracionMediaFijacionMs; }
    public void setDuracionMediaFijacionMs(Double v) { this.duracionMediaFijacionMs = v; }
    public Integer getDwellTimeMs() { return dwellTimeMs; }
    public void setDwellTimeMs(Integer dwellTimeMs) { this.dwellTimeMs = dwellTimeMs; }
    public Double getPorcentajeDwell() { return porcentajeDwell; }
    public void setPorcentajeDwell(Double porcentajeDwell) { this.porcentajeDwell = porcentajeDwell; }
    public Integer getTtffMs() { return ttffMs; }
    public void setTtffMs(Integer ttffMs) { this.ttffMs = ttffMs; }
    public Integer getNumeroVisitas() { return numeroVisitas; }
    public void setNumeroVisitas(Integer numeroVisitas) { this.numeroVisitas = numeroVisitas; }
    public LocalDateTime getCalculadoEn() { return calculadoEn; }
}
