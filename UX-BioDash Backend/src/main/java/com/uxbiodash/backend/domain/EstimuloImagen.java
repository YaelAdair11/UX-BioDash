package com.uxbiodash.backend.domain;

import jakarta.persistence.*;

/** Contenido binario (PNG/JPG) de un estimulo, almacenado en PostgreSQL como BYTEA. */
@Entity
@Table(name = "estimulo_imagen")
public class EstimuloImagen {

    @Id
    @Column(name = "estimulo_id")
    private Long estimuloId;

    @MapsId
    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "estimulo_id")
    private Estimulo estimulo;

    @Column(name = "tipo_mime", nullable = false, length = 50)
    private String tipoMime;

    @Column(name = "tamano_bytes", nullable = false)
    private Long tamanoBytes;

    @Column(name = "contenido", nullable = false)
    private byte[] contenido;

    public Long getEstimuloId() { return estimuloId; }
    public Estimulo getEstimulo() { return estimulo; }
    public void setEstimulo(Estimulo estimulo) { this.estimulo = estimulo; }
    public String getTipoMime() { return tipoMime; }
    public void setTipoMime(String tipoMime) { this.tipoMime = tipoMime; }
    public Long getTamanoBytes() { return tamanoBytes; }
    public void setTamanoBytes(Long tamanoBytes) { this.tamanoBytes = tamanoBytes; }
    public byte[] getContenido() { return contenido; }
    public void setContenido(byte[] contenido) { this.contenido = contenido; }
}
