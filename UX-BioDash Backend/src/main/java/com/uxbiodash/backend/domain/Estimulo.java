package com.uxbiodash.backend.domain;

import jakarta.persistence.*;

@Entity
@Table(name = "estimulo")
public class Estimulo {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "proyecto_id", nullable = false)
    private Proyecto proyecto;

    @Column(name = "nombre_archivo", nullable = false, length = 255)
    private String nombreArchivo;

    @Column(name = "ancho_original_px", nullable = false)
    private Integer anchoOriginalPx;

    @Column(name = "alto_original_px", nullable = false)
    private Integer altoOriginalPx;

    @Column(name = "url_recurso", length = 500)
    private String urlRecurso;

    public Long getId() { return id; }
    public Proyecto getProyecto() { return proyecto; }
    public void setProyecto(Proyecto proyecto) { this.proyecto = proyecto; }
    public String getNombreArchivo() { return nombreArchivo; }
    public void setNombreArchivo(String nombreArchivo) { this.nombreArchivo = nombreArchivo; }
    public Integer getAnchoOriginalPx() { return anchoOriginalPx; }
    public void setAnchoOriginalPx(Integer anchoOriginalPx) { this.anchoOriginalPx = anchoOriginalPx; }
    public Integer getAltoOriginalPx() { return altoOriginalPx; }
    public void setAltoOriginalPx(Integer altoOriginalPx) { this.altoOriginalPx = altoOriginalPx; }
    public String getUrlRecurso() { return urlRecurso; }
    public void setUrlRecurso(String urlRecurso) { this.urlRecurso = urlRecurso; }

    @Column(name = "etiqueta_dispositivo", length = 50)
    private String etiquetaDispositivo;

    public String getEtiquetaDispositivo() { return etiquetaDispositivo; }
    public void setEtiquetaDispositivo(String etiquetaDispositivo) { this.etiquetaDispositivo = etiquetaDispositivo; }
}
