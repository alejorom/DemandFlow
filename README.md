# DemandFlow — Diseño y levantamiento integrado

**Estado:** Línea base funcional y arquitectónica v1.0 aprobada, identificada mediante el tag `v1.0.0-docs`. La implementación aún no está autorizada.

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

Pendiente: autorización separada para iniciar la implementación. No hay implementación ni migraciones ejecutadas.
