package com.uxbiodash.backend.domain;

import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "area_interes")
public class AreaInteres {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "estimulo_id", nullable = false)
    private Estimulo estimulo;

    @Column(nullable = false, length = 100)
    private String etiqueta;

    @Enumerated(EnumType.STRING)
    @Column(name = "forma_geometrica", nullable = false, length = 30)
    private FormaGeometrica formaGeometrica;

    /** JSON con las coordenadas normalizadas [0.0, 1.0] de la geometria. */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "coordenadas_json", nullable = false)
    private String coordenadasJson;

    public Long getId() { return id; }
    public Estimulo getEstimulo() { return estimulo; }
    public void setEstimulo(Estimulo estimulo) { this.estimulo = estimulo; }
    public String getEtiqueta() { return etiqueta; }
    public void setEtiqueta(String etiqueta) { this.etiqueta = etiqueta; }
    public FormaGeometrica getFormaGeometrica() { return formaGeometrica; }
    public void setFormaGeometrica(FormaGeometrica formaGeometrica) { this.formaGeometrica = formaGeometrica; }
    public String getCoordenadasJson() { return coordenadasJson; }
    public void setCoordenadasJson(String coordenadasJson) { this.coordenadasJson = coordenadasJson; }

    @Column(length = 20)
    private String color;

    public String getColor() { return color; }
    public void setColor(String color) { this.color = color; }
}
