# Archivo de setup-database

Archivado el 2026-11-25 conforme a ADR-008, por autorización expresa del usuario. Esquema OpenSpec: `spec-driven`. Se conservan 26/26 tareas completadas, 12 requisitos y 21 escenarios; no se repitieron pruebas de integración durante el archivado.

Los siete archivos originales del cambio, incluida `.openspec.yaml`, se trasladaron sin modificar su contenido. Sus hashes SHA256 se contrastaron antes y después del traslado. Este índice es un documento adicional de navegación; no sustituye ni modifica las evidencias históricas.

- [Propuesta](proposal.md), [diseño y decisiones D1–D9](design.md) y [tareas](tasks.md).
- [Delta original de local-persistence](specs/local-persistence/spec.md) y [especificación canónica sincronizada](../../../specs/local-persistence/spec.md).
- [Evidencias de ejecución e integración manual](evidence.md) y [revisión de imagen y aceptación humana de riesgos](image-security-review.md).
- [Guía operativa vigente](../../../../docs/engineering/local-persistence-guide.md).

Las rutas textuales `openspec/changes/setup-database/` de los documentos originales identifican su ubicación histórica; su contenido se encuentra ahora bajo `openspec/changes/archive/2026-11-25-setup-database/`. El enlace relativo `../../../docs/engineering/local-persistence-guide.md` conservado en `evidence.md` correspondía a la ubicación anterior y no resuelve desde el archivo. Utilizar el enlace a la guía operativa de este índice. Se conserva el original para mantener íntegra la evidencia.

La sincronización conserva literalmente el propósito, los requisitos y los escenarios del delta; solo adapta el título y la cabecera de requisitos al formato canónico. `application-startup` no se modifica. El archivado no añade verificaciones sobre el entorno permanente ni amplía la aceptación de riesgos de la imagen.
