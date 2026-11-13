# DemandFlow — Diseño y levantamiento integrado

**Estado:** borrador único en refinamiento. Las reglas de negocio y ADR-001 a ADR-008 se aprobaron individualmente; la documentación consolidada aún **no** es una línea base oficial. No se ha inicializado OpenSpec ni generado código.

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

Pendiente: revisión y aprobación formal del conjunto documental; posteriormente, autorización separada para iniciar SDD + OpenSpec. No hay implementación ni migraciones ejecutadas.
