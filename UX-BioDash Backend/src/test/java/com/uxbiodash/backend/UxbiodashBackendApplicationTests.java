package com.uxbiodash.backend;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.zonky.test.db.postgres.embedded.EmbeddedPostgres;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * Prueba de extremo a extremo del flujo de la arquitectura (ingesta -> normalizacion -> persistencia ->
 * consulta) contra un PostgreSQL 17 embebido, con el esquema creado por Flyway y validado por Hibernate.
 */
@SpringBootTest
class UxbiodashBackendApplicationTests {

    private static final EmbeddedPostgres POSTGRES = iniciarPostgres();

    private static EmbeddedPostgres iniciarPostgres() {
        try {
            return EmbeddedPostgres.builder().start();
        } catch (IOException e) {
            throw new IllegalStateException("No se pudo iniciar PostgreSQL embebido", e);
        }
    }

    @DynamicPropertySource
    static void baseDeDatos(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", () -> POSTGRES.getJdbcUrl("postgres", "postgres"));
        registry.add("spring.datasource.username", () -> "postgres");
        registry.add("spring.datasource.password", () -> "postgres");
    }

    @AfterAll
    static void detener() throws IOException {
        POSTGRES.close();
    }

    @Autowired
    private WebApplicationContext contexto;

    @Autowired
    private ObjectMapper json;

    private MockMvc mvc;

    @BeforeEach
    void configurar() {
        mvc = MockMvcBuilders.webAppContextSetup(contexto).build();
    }

    @Test
    void flujoCompletoDeAnalisis() throws Exception {
        long proyectoId = crearProyecto();
        long estimuloId = crearEstimulo(proyectoId, 1920, 1080);

        // Imagen almacenada en PostgreSQL y servida de vuelta
        mvc.perform(get("/api/estimulos/" + estimuloId + "/imagen"))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.IMAGE_PNG));

        // AOI sobre el cuadrante superior izquierdo
        JsonNode aoi = leer(mvc.perform(post("/api/estimulos/" + estimuloId + "/aois")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"etiqueta\":\"Boton Pagar\",\"formaGeometrica\":\"RECTANGULO\","
                                + "\"coordenadas\":{\"x\":0.0,\"y\":0.0,\"ancho\":0.5,\"alto\":0.5}}"))
                .andExpect(status().isCreated()).andReturn());
        long aoiId = aoi.get("id").asLong();

        // Muestras crudas genericas (pixeles, ms) de dos participantes -> I-VT
        JsonNode resumen = importar(estimuloId, "muestras.csv", muestrasCrudas());
        assertThat(resumen.get("dispositivoDetectado").asString()).isEqualTo("Generico");
        assertThat(resumen.get("nivelDatos").asString()).isEqualTo("MUESTRAS");
        assertThat(resumen.get("sistemaCoordenadas").asString()).isEqualTo("PIXELES");
        assertThat(resumen.get("sesiones")).hasSize(2);
        for (JsonNode s : resumen.get("sesiones")) {
            assertThat(s.get("fijaciones").asInt()).isEqualTo(2);
            assertThat(s.get("sacadas").asInt()).isEqualTo(1);
            assertThat(s.get("muestrasGuardadas").asInt()).isEqualTo(65);
        }
        long sesionId = resumen.get("sesiones").get(0).get("sesionId").asLong();

        // Metricas AOI precalculadas: primera fijacion dentro del AOI en t = 0
        JsonNode tabla = leer(mvc.perform(get("/api/estimulos/" + estimuloId + "/metricas/aoi"))
                .andExpect(status().isOk()).andReturn());
        assertThat(tabla).hasSize(2);
        for (JsonNode fila : tabla) {
            assertThat(fila.get("conteoFijaciones").asInt()).isEqualTo(1);
            assertThat(fila.get("ttffMs").asInt()).isZero();
            assertThat(fila.get("numeroVisitas").asInt()).isEqualTo(1);
        }

        JsonNode resumenAoi = leer(mvc.perform(get("/api/estimulos/" + estimuloId + "/metricas/aoi/resumen"))
                .andExpect(status().isOk()).andReturn());
        assertThat(resumenAoi.get(0).get("participantesQueLoVieron").asInt()).isEqualTo(2);

        JsonNode participantes = leer(mvc.perform(get("/api/estimulos/" + estimuloId + "/metricas/participantes"))
                .andExpect(status().isOk()).andReturn());
        assertThat(participantes.get(0).get("metricas").get("totalFijaciones").asInt()).isEqualTo(2);

        // Visualizaciones
        JsonNode heatmap = leer(mvc.perform(get("/api/estimulos/" + estimuloId + "/heatmap").param("columnas", "32"))
                .andExpect(status().isOk()).andReturn());
        assertThat(heatmap.get("totalFijaciones").asInt()).isEqualTo(4);
        assertThat(heatmap.get("filas").asInt()).isEqualTo(18);

        JsonNode scanpath = leer(mvc.perform(get("/api/sesiones/" + sesionId + "/scanpath"))
                .andExpect(status().isOk()).andReturn());
        JsonNode primera = scanpath.get("fijaciones").get(0);
        assertThat(primera.get("ordenSecuencia").asInt()).isEqualTo(1);
        assertThat(primera.get("xNormalizado").asDouble()).isCloseTo(0.25, org.assertj.core.data.Offset.offset(0.01));
        assertThat(primera.get("aoiId").asLong()).isEqualTo(aoiId);
        assertThat(scanpath.get("sacadas")).hasSize(1);

        JsonNode muestras = leer(mvc.perform(get("/api/sesiones/" + sesionId + "/muestras").param("tamano", "10"))
                .andExpect(status().isOk()).andReturn());
        assertThat(muestras.get("totalElementos").asInt()).isEqualTo(65);
        assertThat(muestras.get("contenido").get(0).get("tiempoMs").asInt()).isZero();

        mvc.perform(get("/api/estimulos/" + estimuloId + "/transiciones")).andExpect(status().isOk());
        mvc.perform(get("/api/sesiones/" + sesionId + "/metricas")).andExpect(status().isOk());
        mvc.perform(get("/api/proyectos/" + proyectoId + "/comparativa")).andExpect(status().isOk());

        // Reimportar al mismo participante reemplaza su sesion
        importar(estimuloId, "muestras.csv", muestrasCrudas());
        JsonNode sesiones = leer(mvc.perform(get("/api/estimulos/" + estimuloId + "/sesiones"))
                .andExpect(status().isOk()).andReturn());
        assertThat(sesiones).hasSize(2);

        // Al eliminar el AOI desaparecen sus metricas
        mvc.perform(delete("/api/aois/" + aoiId)).andExpect(status().isNoContent());
        JsonNode sinAoi = leer(mvc.perform(get("/api/estimulos/" + estimuloId + "/metricas/aoi"))
                .andExpect(status().isOk()).andReturn());
        assertThat(sinAoi).isEmpty();
    }

    @Test
    void importaEventosTobiiConPuntoYComa() throws Exception {
        long estimuloId = crearEstimulo(crearProyecto(), 1920, 1080);
        String tobii = "Participant;Stimulus;Eye movement type;Eye movement id;Start timestamp;End timestamp;"
                + "Duration;Fixation point X;Fixation point Y;Flagged eye movement\n"
                + "P001;checkout;Fixation;1;1000000;1250000;250;960,0;540,0;0\n"
                + "P001;checkout;Saccade;2;1250000;1290000;40;;;0\n"
                + "P001;checkout;Fixation;3;1290000;1590000;300;1800;100;0\n"
                + "P001;checkout;Fixation;4;1600000;1700000;100;100;100;1\n";
        JsonNode r = importar(estimuloId, "tobii.tsv", tobii);
        assertThat(r.get("dispositivoDetectado").asString()).isEqualTo("Tobii");
        assertThat(r.get("nivelDatos").asString()).isEqualTo("EVENTOS");
        assertThat(r.get("delimitador").asString()).isEqualTo("PUNTO_Y_COMA");
        JsonNode sesion = r.get("sesiones").get(0);
        assertThat(sesion.get("fijaciones").asInt()).isEqualTo(2);

        JsonNode fs = leer(mvc.perform(get("/api/sesiones/" + sesion.get("sesionId").asLong() + "/fijaciones"))
                .andExpect(status().isOk()).andReturn());
        assertThat(fs.get(0).get("inicioMs").asInt()).isZero();
        assertThat(fs.get(0).get("xNormalizado").asDouble()).isEqualTo(0.5);
        assertThat(fs.get(1).get("inicioMs").asInt()).isEqualTo(290);
        assertThat(fs.get(1).get("duracionMs").asInt()).isEqualTo(300);
    }

    @Test
    void rechazaArchivosIncompletosConMensajeClaro() throws Exception {
        long estimuloId = crearEstimulo(crearProyecto(), 800, 600);

        MvcResult r = mvc.perform(multipart("/api/estimulos/" + estimuloId + "/importaciones")
                        .file(new MockMultipartFile("archivo", "malo.csv", "text/csv",
                                "participante,tiempo_ms,x\nP01,0,10\n".getBytes(StandardCharsets.UTF_8))))
                .andExpect(status().isBadRequest()).andReturn();
        JsonNode error = leer(r);
        assertThat(error.get("status").asInt()).isEqualTo(400);
        assertThat(error.get("mensaje").asString()).contains("columnas minimas");
        assertThat(error.get("detalles").toString()).contains("coordenada Y");

        mvc.perform(multipart("/api/estimulos/" + estimuloId + "/importaciones")
                        .file(new MockMultipartFile("archivo", "sesion.edf", "application/octet-stream", new byte[]{1, 2})))
                .andExpect(status().isBadRequest());

        mvc.perform(multipart("/api/proyectos/999999/estimulos")
                        .file(new MockMultipartFile("imagen", "x.png", "image/png", png(10, 10))))
                .andExpect(status().isNotFound());

        mvc.perform(multipart("/api/proyectos/" + crearProyecto() + "/estimulos")
                        .file(new MockMultipartFile("imagen", "x.gif", "image/gif", "GIF89a".getBytes())))
                .andExpect(status().isBadRequest());
    }

    /** Recorre los endpoints tal como los llama el frontend (docs/API_CONTRACT.md). */
    @Test
    void contratoDelFrontend() throws Exception {
        JsonNode p = leer(mvc.perform(post("/api/proyectos").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nombre\":\"Checkout\",\"descripcion\":\"Prueba\",\"dispositivo\":\"Tobii Pro TX300\"}"))
                .andExpect(status().isCreated()).andReturn());
        assertThat(p.get("estado").asString()).isEqualTo("BORRADOR");
        assertThat(p.get("dispositivo").asString()).isEqualTo("Tobii Pro TX300");
        long proyectoId = p.get("id").asLong();

        JsonNode ingesta = leer(mvc.perform(multipart("/api/datasets")
                        .file(new MockMultipartFile("archivo", "muestras.csv", "text/csv",
                                muestrasCrudas().getBytes(StandardCharsets.UTF_8)))
                        .file(new MockMultipartFile("imagen", "checkout.png", "image/png", png(1920, 1080)))
                        .param("proyectoId", String.valueOf(proyectoId)))
                .andExpect(status().isCreated()).andReturn());
        assertThat(ingesta.get("sesionesCreadas").asInt()).isEqualTo(2);
        assertThat(ingesta.get("fijacionesCalculadas").asInt()).isEqualTo(4);
        long estimuloId = ingesta.get("estimuloId").asLong();

        JsonNode proyecto = leer(mvc.perform(get("/api/proyectos/" + proyectoId))
                .andExpect(status().isOk()).andReturn());
        assertThat(proyecto.get("estado").asString()).isEqualTo("EN_ANALISIS");
        assertThat(proyecto.get("participantes").asInt()).isEqualTo(2);
        assertThat(proyecto.get("estimuloPrincipal").get("id").asLong()).isEqualTo(estimuloId);
        assertThat(proyecto.get("estimuloPrincipal").get("urlRecurso").asString())
                .isEqualTo("/api/estimulos/" + estimuloId + "/imagen");

        JsonNode aoi = leer(mvc.perform(post("/api/estimulos/" + estimuloId + "/aois")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"estimuloId\":" + estimuloId + ",\"etiqueta\":\"Banner\","
                                + "\"formaGeometrica\":\"RECTANGULO\",\"color\":\"#ef4444\","
                                + "\"coordenadas\":{\"x\":0.0,\"y\":0.0,\"ancho\":0.5,\"alto\":0.5}}"))
                .andExpect(status().isCreated()).andReturn());
        assertThat(aoi.get("color").asString()).isEqualTo("#ef4444");
        long aoiId = aoi.get("id").asLong();

        JsonNode fijaciones = leer(mvc.perform(get("/api/estimulos/" + estimuloId + "/fijaciones"))
                .andExpect(status().isOk()).andReturn());
        assertThat(fijaciones).hasSize(4);
        assertThat(fijaciones.get(0).get("xNormalizado").asDouble()).isBetween(0.0, 1.0);

        JsonNode metricas = leer(mvc.perform(get("/api/metricas/" + proyectoId))
                .andExpect(status().isOk()).andReturn());
        assertThat(metricas.get("totalFijaciones").asInt()).isEqualTo(4);
        assertThat(metricas.get("participantes").asInt()).isEqualTo(2);
        JsonNode banner = metricas.get("porAoi").get(0);
        assertThat(banner.get("aoiId").asLong()).isEqualTo(aoiId);
        assertThat(banner.get("conteoFijaciones").asInt()).isEqualTo(2);
        assertThat(metricas.get("porAoi").get(1).get("aoiId").isNull()).isTrue();
        assertThat(metricas.get("porParticipante")).hasSize(2);

        JsonNode resumen = leer(mvc.perform(get("/api/proyectos/resumen"))
                .andExpect(status().isOk()).andReturn());
        assertThat(resumen.get("hardwareSoportado")).hasSize(3);
        assertThat(resumen.get("hardwareSoportado")).isNotEmpty();

        // Con datos y al menos una AOI el proyecto pasa a COMPLETADO
        assertThat(leer(mvc.perform(get("/api/proyectos/" + proyectoId)).andReturn())
                .get("estado").asString()).isEqualTo("COMPLETADO");

        // Poligono (pantalla 3): el backend devuelve los puntos y recalcula la caja envolvente
        JsonNode poligono = leer(mvc.perform(post("/api/estimulos/" + estimuloId + "/aois")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"etiqueta\":\"Resumen\",\"formaGeometrica\":\"POLIGONO\","
                                + "\"coordenadas\":{\"x\":0,\"y\":0,\"ancho\":0.1,\"alto\":0.1,\"puntos\":["
                                + "{\"x\":0.6,\"y\":0.6},{\"x\":0.9,\"y\":0.6},{\"x\":0.9,\"y\":0.9},{\"x\":0.7,\"y\":0.8}]}}"))
                .andExpect(status().isCreated()).andReturn());
        JsonNode c = poligono.get("coordenadas");
        assertThat(c.get("puntos")).hasSize(4);
        assertThat(c.get("x").asDouble()).isEqualTo(0.6);
        assertThat(c.get("ancho").asDouble()).isCloseTo(0.3, org.assertj.core.data.Offset.offset(1e-9));
        assertThat(c.get("alto").asDouble()).isCloseTo(0.3, org.assertj.core.data.Offset.offset(1e-9));

        // Sin AOIs vuelve a EN_ANALISIS
        mvc.perform(delete("/api/aois/" + aoiId)).andExpect(status().isNoContent());
        mvc.perform(delete("/api/aois/" + poligono.get("id").asLong())).andExpect(status().isNoContent());
        assertThat(leer(mvc.perform(get("/api/proyectos/" + proyectoId)).andReturn())
                .get("estado").asString()).isEqualTo("EN_ANALISIS");
        assertThat(leer(mvc.perform(get("/api/proyectos/resumen")).andReturn())
                .get("enAnalisis").asInt()).isGreaterThanOrEqualTo(1);

        // Error con la forma del contrato
        JsonNode error = leer(mvc.perform(get("/api/proyectos/999999")).andExpect(status().isNotFound()).andReturn());
        assertThat(error.get("status").asInt()).isEqualTo(404);
        assertThat(error.get("mensaje").asString()).contains("no existe");
    }

    /** El frontend puede abrirse como localhost o 127.0.0.1; guardar AOIs no debe dar 403 en ninguno. */
    @Test
    void corsPermiteLocalhostY127() throws Exception {
        for (String origen : new String[]{"http://localhost:5173", "http://127.0.0.1:5173"}) {
            mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options("/api/aois/1")
                            .header("Origin", origen)
                            .header("Access-Control-Request-Method", "PUT"))
                    .andExpect(status().isOk());
        }
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options("/api/aois/1")
                        .header("Origin", "http://sitio-ajeno.com")
                        .header("Access-Control-Request-Method", "PUT"))
                .andExpect(status().isForbidden());
    }

    @Test
    void publicaTarjetasDeAyuda() throws Exception {
        JsonNode tarjetas = leer(mvc.perform(get("/api/ayuda/visualizaciones")).andExpect(status().isOk()).andReturn());
        assertThat(tarjetas.size()).isGreaterThanOrEqualTo(10);
        mvc.perform(get("/api/ayuda/visualizaciones/heatmap")).andExpect(status().isOk());
        mvc.perform(get("/api/ayuda/visualizaciones/no-existe")).andExpect(status().isNotFound());
    }

    // ---------------------------------------------------------------- Ayudantes

    private long crearProyecto() throws Exception {
        return leer(mvc.perform(post("/api/proyectos").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nombre\":\"Estudio checkout\",\"descripcion\":\"Prueba de usabilidad\"}"))
                .andExpect(status().isCreated()).andReturn()).get("id").asLong();
    }

    private long crearEstimulo(long proyectoId, int ancho, int alto) throws Exception {
        JsonNode e = leer(mvc.perform(multipart("/api/proyectos/" + proyectoId + "/estimulos")
                        .file(new MockMultipartFile("imagen", "checkout.png", "image/png", png(ancho, alto))))
                .andExpect(status().isCreated()).andReturn());
        assertThat(e.get("anchoOriginalPx").asInt()).isEqualTo(ancho);
        assertThat(e.get("altoOriginalPx").asInt()).isEqualTo(alto);
        return e.get("id").asLong();
    }

    private JsonNode importar(long estimuloId, String nombre, String contenido) throws Exception {
        return leer(mvc.perform(multipart("/api/estimulos/" + estimuloId + "/importaciones")
                        .file(new MockMultipartFile("archivo", nombre, "text/csv",
                                contenido.getBytes(StandardCharsets.UTF_8))))
                .andExpect(status().isCreated()).andReturn());
    }

    /**
     * 2 participantes, 100 Hz: 30 muestras en (480, 270) px, una sacada realista de 5 muestras (50 ms)
     * y 30 muestras en (1440, 810) px. Total 65 muestras por participante.
     */
    private static String muestrasCrudas() {
        StringBuilder sb = new StringBuilder("participante,tiempo_ms,x,y\n");
        for (String p : new String[]{"P01", "P02"}) {
            for (int i = 0; i < 65; i++) {
                double avance = i < 30 ? 0 : i >= 35 ? 1 : (i - 29) / 6.0;
                double x = 480 + avance * 960;
                double y = 270 + avance * 540;
                sb.append(p).append(',').append(5000 + i * 10).append(',').append(x + (i % 2)).append(',')
                        .append(y).append('\n');
            }
        }
        return sb.toString();
    }

    private static byte[] png(int ancho, int alto) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(new BufferedImage(ancho, alto, BufferedImage.TYPE_INT_RGB), "png", out);
        return out.toByteArray();
    }

    private JsonNode leer(MvcResult r) throws Exception {
        return json.readTree(r.getResponse().getContentAsString(StandardCharsets.UTF_8));
    }
}
