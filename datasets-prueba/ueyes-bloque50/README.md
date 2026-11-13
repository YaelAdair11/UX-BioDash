# Dataset de prueba: UEyes, bloque 50

Datos reales de eye tracking para probar UX-BioDash. Ya están cargados en la BD como el proyecto
**"UEyes – Interfaces reales (CHI 2023)"** (id 2).

## Origen

- **UEyes** — Jiang, Leiva, Tavakoli, Houssel, Kylmälä y Oulasvirta. *UEyes: Understanding Visual Saliency
  across User Interface Types*. CHI 2023. https://doi.org/10.1145/3544548.3581096
- Descarga: https://zenodo.org/record/8010312 · Licencia **CC BY 4.0** (hay que citar a los autores).
- Equipo: Gazepoint GP3 a 60 Hz. Tarea: vista libre de 7 s por imagen en una pantalla de 1920×1200.

## Contenido

Bloque 50 del experimento (el que tiene más participantes, 22), con una imagen de cada tipo de interfaz:

| Archivo | Tipo | Tamaño | Fijaciones |
|---|---|---|---|
| `web_de022a` | Web | 720×800 | 320 |
| `mobile_3d29ac` | Móvil | 1080×1920 | 348 |
| `desktop_8ed8ac` | Escritorio | 1151×667 | 361 |
| `poster_4d0fc2` | Póster | 808×515 | 435 |

Cada CSV usa el formato genérico de fijaciones: `participante,x,y,inicio_ms,duracion_ms`.

## Conversión

Script: [`convertir_ueyes.py`](convertir_ueyes.py) (`python convertir_ueyes.py <carpeta_logs_e_imágenes> <salida>`, requiere Pillow).

Los logs originales (`50_khNNN_fixations.csv`) traen una fila por fijación de Gazepoint, con `FPOGX/FPOGY`
normalizados a la **pantalla**. Para pasarlos a píxeles de la imagen se aplicó la misma transformación que
`data_processing/generate_heatmaps.py` de los autores: la imagen se muestra centrada y escalada con relleno
(*letterbox*) en 1920×1200. Además:

- Solo se conservan las fijaciones válidas (`FPOGV = 1`) con duración mayor que 0.
- Se descartan las fijaciones que caen fuera de la imagen, es decir, sobre el relleno.
- `inicio_ms = FPOGS × 1000` y `duracion_ms = FPOGD × 1000`, ambos relativos a la aparición de la imagen.
- Participante: `P` + el número `khNNN` del archivo original.

## Reimportar

Crear el estímulo con la imagen y luego enviar el CSV a `POST /api/estimulos/{id}/importaciones`
con `sistemaCoordenadas=PIXELES` y `unidadTiempo=MILISEGUNDOS`.
