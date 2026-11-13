package com.uxbiodash.backend.repository;

import com.uxbiodash.backend.procesamiento.MuestraNormalizada;
import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/** Insercion masiva de muestras crudas (cientos de miles de filas) mediante JDBC batch. */
@Repository
public class MuestraJdbcRepository {

    private static final String SQL = "INSERT INTO punto_gaze_muestra "
            + "(sesion_id, tiempo_ms, x_normalizado, y_normalizado, diametro_pupila) VALUES (?, ?, ?, ?, ?)";

    private final JdbcTemplate jdbcTemplate;

    public MuestraJdbcRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public void insertarLote(long sesionId, List<MuestraNormalizada> muestras, int tamanoLote) {
        jdbcTemplate.batchUpdate(SQL, muestras, tamanoLote, (ps, m) -> {
            ps.setLong(1, sesionId);
            ps.setInt(2, (int) Math.round(m.tiempoMs()));
            ps.setDouble(3, m.x());
            ps.setDouble(4, m.y());
            if (m.pupila() == null) {
                ps.setNull(5, java.sql.Types.DOUBLE);
            } else {
                ps.setDouble(5, m.pupila());
            }
        });
    }
}
