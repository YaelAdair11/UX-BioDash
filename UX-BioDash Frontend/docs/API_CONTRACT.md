# Contrato de API REST — UX-BioDash

Endpoints que consume el frontend (`src/api/services.ts`). Base: `/api`. JSON en **camelCase** (serialización por defecto de Jackson).
Convenciones: coordenadas normalizadas en `[0.0, 1.0]`, tiempos en milisegundos con `t = 0` al inicio de cada sesión, fechas ISO-8601.

Los tipos TypeScript completos están en [`src/api/types.ts`](../src/api/types.ts). Esta tabla es la referencia para alinear los controladores de Spring Boot.

## Proyectos

| Método | Ruta | Cuerpo | Respuesta |
|---|---|---|---|
| GET | `/proyectos` | — | `Proyecto[]` |
| GET | `/proyectos/resumen` | — | `ResumenGlobal` (KPIs de la pantalla de inicio) |
| GET | `/proyectos/{id}` | — | `Proyecto` |
| POST | `/proyectos` | `ProyectoNuevo` | `201` + `Proyecto` |
| DELETE | `/proyectos/{id}` | — | `204` |
| GET | `/proyectos/{id}/estimulos` | — | `Estimulo[]` (el dashboard usa el primero) |
| GET | `/proyectos/{id}/sesiones` | — | `SesionEvaluacion[]` |

```jsonc
// Proyecto
{
  "id": 1, "nombre": "Estudio Checkout E-Commerce", "descripcion": "…",
  "creadoEn": "2026-10-21T10:00:00Z", "modificadoEn": "2026-11-10T08:00:00Z",
  "estado": "BORRADOR | EN_INGESTA | EN_ANALISIS | COMPLETADO",
  "dispositivo": "Tobii Pro TX300",          // null si no se ha asignado
  "participantes": 12, "aoisConfiguradas": 2, "duracionMediaMs": 8500,
  "estimuloPrincipal": { /* Estimulo */ }     // opcional, para la miniatura
}
// ResumenGlobal
{ "estudiosActivos": 3, "enAnalisis": 1, "sujetosEvaluados": 35, "promedioPorEstudio": 12,
  "muestrasBiometricas": 1420000, "hardwareSoportado": ["Tobii Pro", "EyeLink 1000", "Gazepoint GP3"] }
// Estimulo
{ "id": 11, "proyectoId": 1, "nombreArchivo": "checkout.png", "anchoOriginalPx": 1920, "altoOriginalPx": 1080,
  "urlRecurso": "/api/estimulos/11/imagen", "etiquetaDispositivo": "Escritorio" }
// SesionEvaluacion
{ "id": 1001, "proyectoId": 1, "estimuloId": 11, "codigoSujeto": "P01", "dispositivoOrigen": "Tobii Pro TX300",
  "duracionTotalMs": 8400, "tasaMuestreoHz": 300.0 }
```

> `urlRecurso` debe ser una URL que el navegador pueda cargar en `<img>` (p. ej. un endpoint que sirva el binario). Para exportar PNG desde el navegador la imagen debe servirse desde el mismo origen o con CORS.

## Ingesta

| Método | Ruta | Cuerpo | Respuesta |
|---|---|---|---|
| POST | `/datasets` | `multipart/form-data`: `proyectoId`, `archivo` (CSV/TSV), `imagen` (PNG/JPG) | `201` + `ResumenIngesta` |

```jsonc
// ResumenIngesta
{ "proyectoId": 1, "estimuloId": 11, "sesionesCreadas": 12, "filasLeidas": 102400, "filasDescartadas": 37,
  "fijacionesCalculadas": 289, "advertencias": ["37 filas con valores vacíos fueron descartadas."] }
```

Columnas que el frontend valida antes de subir (sinónimos aceptados en `src/lib/tabular.ts`): `participante`, `tiempo` (ms), `x`, `y`; opcionales `pantalla`, `duracion`.
Si existe `duracion`, cada fila es una fijación ya calculada por el fabricante; si no, el backend agrupa muestras en fijaciones (I-VT/I-DT).

## Fijaciones y AOIs

| Método | Ruta | Cuerpo | Respuesta |
|---|---|---|---|
| GET | `/estimulos/{id}/fijaciones?sesionId=` | — | `Fijacion[]` (sin `sesionId` = todas las sesiones) |
| GET | `/estimulos/{id}/aois` | — | `AreaInteres[]` |
| POST | `/estimulos/{id}/aois` | `AreaInteres` sin `id` | `201` + `AreaInteres` |
| PUT | `/aois/{id}` | `AreaInteres` | `AreaInteres` |
| DELETE | `/aois/{id}` | — | `204` |

```jsonc
// Fijacion
{ "id": 5001, "sesionId": 1001, "ordenSecuencia": 1, "inicioMs": 120, "finMs": 380, "duracionMs": 260,
  "xNormalizado": 0.27, "yNormalizado": 0.24, "aoiId": 101 }
// AreaInteres — en BD, `coordenadas` corresponde a la columna coordenadas_json (JSONB)
{ "id": 101, "estimuloId": 11, "etiqueta": "Banner Hero", "formaGeometrica": "RECTANGULO",
  "coordenadas": { "x": 0.05, "y": 0.18, "ancho": 0.905, "alto": 0.185 }, "color": "#ef4444" }
// AreaInteres poligonal — `coordenadas` siempre incluye la caja envolvente; `puntos` son los vértices en orden
{ "id": 103, "estimuloId": 11, "etiqueta": "Resumen de Compra", "formaGeometrica": "POLIGONO",
  "coordenadas": { "x": 0.52, "y": 0.31, "ancho": 0.32, "alto": 0.37,
                   "puntos": [{"x":0.52,"y":0.31},{"x":0.84,"y":0.31},{"x":0.84,"y":0.68},{"x":0.52,"y":0.68}] },
  "color": "#0284C7" }
```

- La caja envolvente (`x`, `y`, `ancho`, `alto`) se envía siempre y debe coincidir con el mínimo y el máximo de `puntos`.
- Para `POLIGONO`, una fijación pertenece a la AOI si cae **dentro del polígono** (algoritmo de ray casting / point-in-polygon), no solo dentro de su caja envolvente.
- Los vértices van en orden de dibujo; un polígono tiene como mínimo 3.

Tras crear, mover o eliminar una AOI el backend debe reasignar `aoiId` en las fijaciones y recalcular `metrica_aoi_resultado`; el frontend vuelve a pedir `/metricas` después de cada guardado.

## Métricas

| Método | Ruta | Respuesta |
|---|---|---|
| GET | `/metricas/{proyectoId}?sesionId=` | `MetricasProyecto` |

```jsonc
{
  "proyectoId": 1, "estimuloId": 11, "sesionId": null, "totalFijaciones": 289, "participantes": 12, "duracionMaximaMs": 10114,
  "porAoi": [
    { "aoiId": 101, "etiqueta": "Banner Hero", "ttffMs": 618, "dwellTimeMs": 37013, "dwellPct": 42.2,
      "conteoFijaciones": 123, "revisitas": 64, "duracionMediaFijacionMs": 301 },
    { "aoiId": null, "etiqueta": "Fuera de las AOIs", "ttffMs": null, "dwellTimeMs": 42401, "dwellPct": 48.3,
      "conteoFijaciones": 141, "revisitas": null, "duracionMediaFijacionMs": 301 }
  ],
  "porParticipante": [
    { "sesionId": 1001, "codigoSujeto": "P01", "conteoFijaciones": 19, "duracionMediaFijacionMs": 290,
      "duracionTotalMs": 6840, "ttffPorAoi": { "101": 450, "102": null } }
  ]
}
```

Definiciones usadas por el modo simulado (`src/lib/metrics.ts`), para que ambos lados coincidan:
- **TTFF**: `inicioMs` de la primera fijación dentro de la AOI, promediado entre los participantes que la vieron.
- **Dwell time**: suma de `duracionMs` de las fijaciones dentro de la AOI; `dwellPct` = dwell / suma total de duraciones × 100.
- **Revisitas**: por sesión, número de entradas a la AOI menos 1; se suman las sesiones.
- Una fijación pertenece a la AOI **más pequeña** que la contiene (AOIs anidadas); el tamaño se mide por el área de la caja envolvente (`ancho × alto`).

## Errores

Cualquier respuesta 4xx/5xx debería tener la forma `{ "status": 422, "mensaje": "Texto para el evaluador", "detalles": ["…"] }`.
El frontend muestra `mensaje` y la lista `detalles` tal cual.
