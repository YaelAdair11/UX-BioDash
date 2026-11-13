package com.uxbiodash.backend.domain;

import jakarta.persistence.*;

@Entity
@Table(name = "sacada")
public class Sacada {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "sesion_id", nullable = false)
    private SesionEvaluacion sesion;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "fijacion_origen_id", nullable = false)
    private Fijacion fijacionOrigen;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "fijacion_destino_id", nullable = false)
    private Fijacion fijacionDestino;

    @Column(name = "amplitud_grados")
    private Double amplitudGrados;

    @Column(name = "velocidad_pico_grados_s")
    private Double velocidadPicoGradosS;

    public Long getId() { return id; }
    public SesionEvaluacion getSesion() { return sesion; }
    public void setSesion(SesionEvaluacion sesion) { this.sesion = sesion; }
    public Fijacion getFijacionOrigen() { return fijacionOrigen; }
    public void setFijacionOrigen(Fijacion fijacionOrigen) { this.fijacionOrigen = fijacionOrigen; }
    public Fijacion getFijacionDestino() { return fijacionDestino; }
    public void setFijacionDestino(Fijacion fijacionDestino) { this.fijacionDestino = fijacionDestino; }
    public Double getAmplitudGrados() { return amplitudGrados; }
    public void setAmplitudGrados(Double amplitudGrados) { this.amplitudGrados = amplitudGrados; }
    public Double getVelocidadPicoGradosS() { return velocidadPicoGradosS; }
    public void setVelocidadPicoGradosS(Double velocidadPicoGradosS) { this.velocidadPicoGradosS = velocidadPicoGradosS; }
}
