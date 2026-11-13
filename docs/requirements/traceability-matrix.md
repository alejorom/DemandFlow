# DemandFlow — Matriz de trazabilidad funcional integrada

**Estado:** mapeo de diseño para revisión; escenarios y pruebas todavía no implementados.

| Historia | Reglas originales relevantes | Reglas complementarias | Escenarios de aceptación a especificar |
|---|---|---|---|
| HU-001 Administrar vigencias | RN-001, RN-010, RN-040, RN-041 | RF-PER-001..004, RF-CON-003..005 | Crear intervalo válido; rechazar solape incluso inactivo; impedir cambiar fechas usadas; desactivar/reactivar sin alterar históricos; intervalos no anuales. |
| HU-002 Administrar tarifas | RN-003, RN-004, RN-026..029, RN-041 | RF-MON-001, 004..005; RF-TAR-001..004; RF-CON-002..003, 005 | Tarifa positiva y única; editar usada sin alterar snapshots; reactivar solo con período activo; concurrencia de tarifa. |
| HU-003 Registrar demanda | RN-002, RN-005..006, RN-011..012, RN-029..033 | RF-MON-001..005; RF-JIRA-001..006; RF-TAR-003..004; RF-CON-002..005 | Validar JIRA, horas y tarifa; crear snapshot y auditoría atómicamente; rechazar colisión concurrente de JIRA. |
| HU-004 Modificar demanda | RN-007, RN-013..015, RN-034..037 | RF-DEM-001, 004..005; RF-TAR-004; RF-CON-001..003, 005 | Recalcular con snapshot si solo horas; cambiar tipo/período con nueva tarifa; conflicto @Version; no editar inactiva. |
| HU-005 Desactivar/restaurar | RN-008, RN-016..017, RN-038..039 | RF-DEM-002..005; RF-CON-001, 003, 005 | Desactivar/restaurar sin recálculo; restaurar con catálogo inactivo; rechazar transición redundante. |
| HU-006 Consultar auditoría | RN-018..020, RN-028 | RF-TAR-001, 003; RF-DEM-004; RF-CON-003, 005 | Antes/después; UTC; origen; rollback si auditoría falla; no auditar guardado sin cambios. |
| HU-007 Consultar demanda | RN-023, RN-032 | RF-JIRA-001..006; RF-CONSU-001..004 | Filtros combinables; paginación 20 por defecto; orden fechaCreacion DESC/id DESC; inactivas excluidas. |
| HU-008 Consultar totales | RN-024 | RF-MON-003; RF-DEM-002; RF-CONSU-003..004 | Sumar todo el conjunto filtrado, independientemente de página, con importes persistidos. |

## Trazabilidad de los refinamientos originales
- RN-001 → RF-PER-004 (duración flexible).
- RN-031 → RF-JIRA-001, RF-JIRA-004 y RF-JIRA-005 (sintaxis y normalización).

## Matriz técnica de validación (pendiente de implementación)
| Control | Evidencia esperada |
|---|---|
| @Version | Conflicto concurrente detectado, sin pérdida de actualizaciones. |
| Bloqueo tarifa/período | Snapshot y estado coherentes al confirmar demanda. |
| Restricción PostgreSQL | Solapamiento inclusivo rechazado incluso con transacciones concurrentes. |
| NUMERIC y HALF_UP | Casos límite de precisión, escala y redondeo comprobados. |
| Auditoría atómica | Fallo de evento revierte mutación. |
| Totales paginados | Agregados idénticos para cualquier página del mismo filtro. |
