# UX-BioDash — Backend

API REST para cargar, normalizar y analizar datos de eye tracking sobre capturas estáticas de interfaces.
Implementa la documentación técnica en [`Documentación Tecnica/`](Documentación%20Tecnica/).

**Stack:** Java 17 · Spring Boot 4.1 · Spring Data JPA (Hibernate 7) · PostgreSQL 17 · Flyway · Maven Wrapper

## Arranque

1. Crea la base de datos en tu PostgreSQL 17 local:
   ```bash
   createdb -U postgres uxbiodash
   ```
   (Alternativa: `docker compose up -d` levanta PostgreSQL 17 con usuario/contraseña `postgres`.)
2. Configura las credenciales por variables de entorno (por defecto `postgres` / `postgres`):

   | Variable | Defecto |
   |---|---|
   | `DB_URL` | `jdbc:postgresql://localhost:5432/uxbiodash` |
   | `DB_USERNAME` / `DB_PASSWORD` | `postgres` / `postgres` |
   | `CORS_ORIGINS` | `http://localhost:*,http://127.0.0.1:*` (patrones; admiten `*` en el puerto) |
   | `PORT` | `8080` |

   Para Supabase basta con apuntar `DB_URL` a su cadena JDBC.
3. Ejecuta:
   ```bash
   ./mvnw spring-boot:run
   ```
   Flyway crea el esquema (`src/main/resources/db/migration`) y Hibernate lo valida al iniciar.

**Pruebas:** `./mvnw test` — unitarias + integración de extremo a extremo contra un PostgreSQL 17 embebido (no requiere Docker ni credenciales). Incluye binarios para Windows, Linux y macOS (Intel y Apple Silicon).

### macOS

```bash
brew install openjdk@17 postgresql@17
brew services start postgresql@17
createdb uxbiodash
```

Homebrew crea el superusuario con tu nombre de usuario de macOS y sin contraseña (no `postgres`/`postgres`), así que exporta las credenciales antes de arrancar:

```bash
export DB_USERNAME="$USER"
export DB_PASSWORD=""
./mvnw spring-boot:run
```

Alternativa: `docker compose up -d` (Docker Desktop o OrbStack) usa `postgres`/`postgres` y no requiere variables.
Si al ejecutar `./mvnw` aparece `permission denied`, da permiso de ejecución con `chmod +x mvnw` (en el repositorio ya está marcado como ejecutable).

## Arquitectura

```
web (controladores REST, DTOs) ──► service (orquestación, transacciones) ──► repository (Spring Data JPA / JDBC batch)
                                        │
                ingesta ──► procesamiento ──► metricas      (lógica pura, sin dependencias de BD)
```

| Paquete | Responsabilidad (documento) |
|---|---|
| `ingesta` | Lectura en streaming, detección de delimitador (`,` `;` `\t`), validación de cabeceras (HTTP 400), detección de fabricante y mapeo a `RawGazeSampleDTO` — *Diseño de la Estructura de Procesamiento*, *Mecanismo de Normalización §2* |
| `procesamiento` | Normalización espacial [0,1] con clamping, temporal t=0 ms, filtrado (nulos, negativos, parpadeos, >1000 °/s), I-VT y sacadas — *Mecanismo de Normalización §3–4* |
| `metricas` | Conteo, duración media, dwell time, TTFF, visitas, transiciones, coeficiente K — *Catálogo de Métricas* |
| `domain` + `db/migration` | Modelo relacional 3FN — *Estructura de Datos Común* |

### Formatos soportados

| Fabricante | Detección por cabecera | Nivel | Tiempo | Coordenadas |
|---|---|---|---|---|
| Tobii (eventos) | `Eye movement type`, `Fixation point X/Y`, `Start/End timestamp` | Fijaciones del fabricante | µs | píxeles |
| Tobii (muestras) | `Recording timestamp`, `Gaze point X/Y`, `Validity left/right` | Muestras → I-VT | µs (o unidad entre corchetes) | píxeles |
| EyeLink | `TIMESTAMP`, `AVERAGE_GAZE_X/Y`, `RECORDING_SESSION_LABEL` / `CURRENT_FIX_*` | Muestras o fijaciones | ms | píxeles (`.` y `-1` = sin señal) |
| Gazepoint | `TIME`, `BPOGX/BPOGY` (o `FPOG*`), `BPOGV` | Muestras → I-VT | s | normalizadas |
| Genérico | `participante`, `tiempo_ms`, `x`, `y` (+ alias en inglés) | Muestras o fijaciones (`inicio_ms`, `duracion_ms`) | ms (o inferida) | inferidas |

Las coordenadas en píxeles se dividen por la resolución de la pantalla de la prueba (parámetros `anchoPantallaPx`/`altoPantallaPx`; por defecto, el tamaño de la imagen del estímulo). Los parámetros de I-VT y la geometría de visualización (distancia y tamaño de monitor, usados para convertir a grados) están en `application.properties` bajo `uxbiodash.procesamiento.*`.

## API

Todas las rutas cuelgan de `/api`. Los errores se devuelven como `ProblemDetail` (RFC 9457) con `detail` en español y, cuando aplica, una lista `errores`.

| Método | Ruta | Descripción |
|---|---|---|
| GET / POST | `/proyectos` | Listar / crear proyecto `{nombre, descripcion}` |
| GET / PUT / DELETE | `/proyectos/{id}` | Detalle / editar / borrar (en cascada) |
| GET | `/proyectos/{id}/sesiones` | Sesiones del proyecto |
| GET | `/proyectos/{id}/comparativa` | Comparación entre condiciones (un resumen por estímulo) |
| POST | `/proyectos/{id}/estimulos` | Subir captura PNG/JPG (multipart `imagen`, opcional `nombre`); se guarda en PostgreSQL |
| GET | `/proyectos/{id}/estimulos` | Estímulos del proyecto |
| GET / DELETE | `/estimulos/{id}` | Detalle / borrar |
| GET | `/estimulos/{id}/imagen` | Bytes de la imagen (`url_recurso`) |
| POST | `/estimulos/{id}/importaciones` | Subir CSV/TSV (multipart `archivo`). Opcionales: `codigoSujeto`, `anchoPantallaPx`, `altoPantallaPx`, `unidadTiempo` (`MICROSEGUNDOS`/`MILISEGUNDOS`/`SEGUNDOS`), `sistemaCoordenadas` (`PIXELES`/`NORMALIZADO`), `filtroEstimulo`, `reemplazarExistentes` (true), `guardarMuestras` (true) |
| GET | `/estimulos/{id}/sesiones` | Sesiones (una por participante) |
| GET / POST | `/estimulos/{id}/aois` | Listar / crear AOI |
| PUT / DELETE | `/aois/{id}` | Editar / borrar AOI (recalcula métricas) |
| GET | `/estimulos/{id}/metricas/aoi?codigoSujeto=` | Tabla por participante × zona |
| GET | `/estimulos/{id}/metricas/aoi/resumen` | Comparación entre áreas |
| GET | `/estimulos/{id}/metricas/participantes` | Comparación entre participantes |
| GET | `/estimulos/{id}/transiciones?sesionIds=` | Matriz de transiciones entre AOIs |
| GET | `/estimulos/{id}/heatmap?sesionIds=&columnas=64&sigma=1.5` | Rejilla de calor normalizada [0,1] |
| GET | `/estimulos/{id}/fijaciones?sesionIds=` | Distribución de fijaciones |
| GET / DELETE | `/sesiones/{id}` | Detalle / borrar |
| GET | `/sesiones/{id}/scanpath` | Fijaciones numeradas + sacadas |
| GET | `/sesiones/{id}/fijaciones`, `/sacadas` | Datos de la sesión |
| GET | `/sesiones/{id}/muestras?pagina=0&tamano=5000` | Muestras crudas normalizadas (paginadas) |
| GET | `/sesiones/{id}/metricas` | Resumen de la sesión + métricas AOI |
| GET | `/ayuda/visualizaciones[/{clave}]` | Tarjetas de interpretación de cada gráfica |

**Cuerpo de un AOI** (coordenadas normalizadas [0, 1]):
```json
{ "etiqueta": "Botón Pagar", "formaGeometrica": "RECTANGULO",
  "coordenadas": { "x": 0.62, "y": 0.80, "ancho": 0.15, "alto": 0.06 } }
```
Para `POLIGONO`: `"coordenadas": { "puntos": [ {"x":0.1,"y":0.1}, {"x":0.3,"y":0.1}, {"x":0.2,"y":0.3} ] }`.

## Decisiones fuera de la documentación

- **`estimulo_imagen`**: tabla 1:1 con `estimulo` que guarda la captura como `BYTEA`, separada para no cargar los bytes en cada consulta.
- **`punto_gaze_muestra`** y **`metrica_aoi_resultado`** se mencionan en la documentación sin diccionario de datos; sus columnas están definidas en `V1__esquema_inicial.sql`.
- **Una sesión por participante y estímulo**; reimportar al mismo participante reemplaza su sesión (desactivable con `reemplazarExistentes=false`).
- **Fijaciones en AOIs solapados**: `fijacion.aoi_id` apunta al AOI más pequeño que la contiene; las métricas por AOI sí cuentan la fijación en cada área que la contiene.
- **Coeficiente K**: se usa la fórmula de Krejtz et al. (2016), `K = media(z(duración) − z(amplitud siguiente))`, donde K > 0 es atención focal y K < 0 ambiental.
