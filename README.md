# DemandFlow — Diseño y levantamiento integrado

**Estado:** Línea base funcional y arquitectónica v1.0 aprobada, identificada mediante el tag `v1.0.0-docs`. La implementación del cambio `init-project-skeleton` cuenta con autorización independiente.

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

El esqueleto inicial puede construirse y arrancarse sin base de datos; las funcionalidades de negocio y la persistencia se abordan en cambios posteriores.

## Construcción y arranque local

Se requiere JDK 21 (por ejemplo, Eclipse Temurin), con `JAVA_HOME` apuntando al JDK. No se requiere Maven instalado: el Wrapper descarga Maven 3.9.11 y comprueba la integridad de la distribución; la primera ejecución necesita acceso a Maven Central para obtener Maven y las dependencias.

En Windows, desde la raíz del repositorio:

```powershell
.\mvnw.cmd test
.\mvnw.cmd package
java -jar target/demandflow-0.0.1-SNAPSHOT.jar
```

El servidor escucha en `127.0.0.1:8080`; se detiene con Ctrl+C. Sin autenticación, debe permanecer en un entorno local protegido.
