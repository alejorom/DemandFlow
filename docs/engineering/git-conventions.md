# Git y colaboración con agentes
**Estado:** propuesta.

- `main` estable; cambios pequeños y revisables en ramas cortas.
- Mensajes descriptivos; convención sugerida: `feat:`, `fix:`, `docs:`, `test:`, `refactor:`, `chore:`.
- Nunca confirmar `.env`, contraseñas, volcados de BD, logs ni artefactos locales.
- Cada cambio funcional debe vincularse con especificaciones/escenarios de OpenSpec.
- Un agente a la vez sobre los mismos archivos; revisar `git diff` antes de aceptar.
- Los agentes escriben/modifican directamente en el repositorio y devuelven solo resumen breve de cambios, verificaciones, incidencias y estado.
- Ningún agente debe modificar unilateralmente los ADR aprobados: proponer cambio y solicitar revisión.
