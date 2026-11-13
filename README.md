# UX-BioDash

Sistema de Visualización y Análisis Biométrico para la Evaluación de Interfaces de Usuario.

## Descripción
UX-BioDash es un proyecto enfocado en la recopilación, procesamiento y visualización de datos biométricos provenientes de dispositivos de eye tracking, con el objetivo de apoyar a investigadores y diseñadores en el análisis de usabilidad y experiencia de usuario (UX).

## Arquitectura

Tres capas desacopladas que se comunican por una API REST (JSON sobre HTTP), según
*Definir arquitectura general de UX-BioDash*:

| Capa | Carpeta | Tecnología |
|---|---|---|
| Presentación (frontend) | [`UX-BioDash Frontend/`](UX-BioDash%20Frontend/) | React 19 + TypeScript + Vite; también como app de escritorio con Electron |
| Negocio y servicios (backend) | [`UX-BioDash Backend/`](UX-BioDash%20Backend/) | Java 17 + Spring Boot (ingesta, normalización, I-VT y métricas) |
| Persistencia | `UX-BioDash Backend/src/main/resources/db/migration` | PostgreSQL 17 con migraciones Flyway |

Además, [`datasets-prueba/`](datasets-prueba/) contiene datos reales de eye tracking para probar el sistema
(UEyes, CHI 2023).

## Puesta en marcha

1. **Base de datos y backend:** ver el [README del backend](UX-BioDash%20Backend/README.md)
   (`./mvnw spring-boot:run`; API en `http://localhost:8080/api`).
2. **Frontend:** ver el [README del frontend](UX-BioDash%20Frontend/README.md) (`pnpm install` y `pnpm dev`).

## Estado del Proyecto
En desarrollo: la carga de datasets, la normalización, el editor de AOIs y el panel de visualización ya funcionan
de extremo a extremo.
