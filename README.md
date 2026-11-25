# DemandFlow — Diseño y levantamiento integrado

**Estado:** Línea base funcional y arquitectónica v1.0 aprobada, identificada mediante el tag `v1.0.0-docs`. `setup-database` está archivado en `openspec/changes/archive/2026-11-25-setup-database/`, con sus 26 tareas completadas y la capacidad `local-persistence` sincronizada; los cambios de archivado quedan pendientes de revisión humana. D2 utiliza `postgres:17.12-trixie` con riesgo residual aceptado exclusivamente para el MVP local sin exposición a Internet; consultar [las evidencias](openspec/changes/archive/2026-11-25-setup-database/evidence.md) y [la revisión de imagen](openspec/changes/archive/2026-11-25-setup-database/image-security-review.md). El entorno permanente de desarrollo se crea manualmente según la guía.

OpenSpec se inicializó y configuró con OpenSpec CLI 1.15.0 para Antigravity, Codex, Claude Code y GitHub Copilot. La inicialización no autoriza la implementación.

## Documentación vigente (orden de lectura)

1. [Visión y alcance](docs/vision.md)
2. [Requisitos funcionales y 41 reglas RN](docs/requirements/functional-requirements.md)
3. [33 reglas RF complementarias aprobadas](docs/requirements/business-rules.md)
4. [Matriz de trazabilidad](docs/requirements/traceability-matrix.md)
5. [Arquitectura](docs/architecture/architecture.md)
6. [Modelo de datos integrado](docs/architecture/data-model.md)
7. [ADR-001 a ADR-008](docs/architecture-decisions/)
8. [Diagramas editables y PNG](docs/architecture/diagrams/)
9. [Estándares y pruebas](docs/engineering/)

## Fuente de verdad y preservación

Los documentos Markdown de `docs/requirements/` y `docs/architecture/` son la **fuente de verdad operativa de trabajo**. El [DOCX de levantamiento original](docs/reference/DemandFlow_Requisitos_Funcionales_v1.0.docx) se conserva **sin modificar** como evidencia histórica; su nombre original no implica publicación de una versión oficial. Las decisiones posteriores refinan expresamente RN-001 (duración flexible de vigencias) y RN-031 (formato JIRA estricto), con trazabilidad en los documentos vigentes.

Existe **un solo** `docs/architecture/data-model.md`; no hay archivo paralelo de refinamientos. Los ocho ADR y los diagramas originales se conservan. Los diagramas son representaciones conceptuales y pueden requerir actualización visual para reflejar todos los detalles del refinamiento; el texto vigente prevalece en esas precisiones.

## Estado y siguientes pasos

La construcción y el arranque predeterminados funcionan sin base de datos, credenciales ni Docker, incluso si existen parámetros externos de conexión. La infraestructura de persistencia pertenece a `setup-database`; las funcionalidades de negocio quedan para cambios posteriores.

## Construcción y arranque local

Se requiere JDK 21 (por ejemplo, Eclipse Temurin), con `JAVA_HOME` apuntando al JDK. No se requiere Maven instalado: el Wrapper descarga Maven 3.9.11 y comprueba la integridad de la distribución; la primera ejecución necesita acceso a Maven Central para obtener Maven y las dependencias.

En Windows, desde la raíz del repositorio:

```powershell
.\mvnw.cmd test
.\mvnw.cmd package
java -jar target/demandflow-0.0.1-SNAPSHOT.jar
```

El servidor escucha en `127.0.0.1:8080`; se detiene con Ctrl+C. Sin autenticación, debe permanecer en un entorno local protegido.

`mvnw.cmd test` y `mvnw.cmd package` no solicitan pruebas PostgreSQL. El perfil Maven `persistence-it` se reserva para la verificación explícita de integración; no activa por sí mismo el perfil Spring de ejecución. No se habilita persistencia automáticamente por detectar Docker o una URL de conexión.

La preparación de Compose, secretos y bootstrap se describe en la [guía de persistencia local](docs/engineering/local-persistence-guide.md). Los resultados realmente ejecutados están en [evidence.md](openspec/changes/archive/2026-11-25-setup-database/evidence.md).

PostgreSQL de desarrollo conserva los datos mediante bind mount en `D:\DOCKER_DATA\POSTGRES_DATA` por defecto; `DEMANDFLOW_DATA_DIR` permite otra ruta absoluta. El operador prepara/revisa esa carpeta y levanta Compose manualmente. Las pruebas usan directorios temporales separados.

La especificación canónica de persistencia está en [local-persistence](openspec/specs/local-persistence/spec.md). Los artefactos y evidencias archivados se conservan íntegros: sus referencias textuales a la ruta original `openspec/changes/setup-database/` describen la ubicación histórica, cuyo contenido se encuentra ahora en `openspec/changes/archive/2026-11-25-setup-database/`.
