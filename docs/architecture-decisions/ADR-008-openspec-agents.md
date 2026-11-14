# ADR-008: OpenSpec como contrato de cambios y gobernanza de agentes

**Estado:** Aceptado
**Fecha:** 2026-11-13
**Ámbito:** DemandFlow v1.0  

## 1. Contexto y problema
DemandFlow es un MVP educativo y funcional con reglas económicas, histórico auditable y arquitectura documentada mediante requisitos, ADR y diagramas. Se pretende aprender Java y SDD sin que los agentes generen cambios incompatibles o implementen decisiones aún pendientes. La portabilidad entre agentes requiere un contrato compartido, no solo prompts independientes.

## 2. Decisión aceptada
Adoptar **Specification-Driven Development (SDD)** mediante **OpenSpec**, en el repositorio de DemandFlow, **solo después** de aprobar la arquitectura y resolver las reglas funcionales bloqueantes. Las propuestas y especificaciones de cambios guían la implementación; los agentes no sustituyen la aprobación humana.

### 2.1. Autoridad y trazabilidad documental
- `docs/requirements/`: especificación funcional integrada aprobada y criterios de aceptación.
- `docs/architecture/` y `docs/architecture-decisions/`: arquitectura vigente y decisiones técnicas, incluidos diagramas Excalidraw.
- `docs/engineering/`: estándares de pruebas, seguridad, código y Git.
- `openspec/`: propuestas, especificaciones, diseño y tareas **del cambio concreto**; no reemplaza silenciosamente las líneas base anteriores.
- Si surge una contradicción, **detener la implementación**, identificar el conflicto y solicitar resolución. Una propuesta OpenSpec no puede modificar por sí sola un ADR aceptado o una regla funcional aprobada.
- Mantener identificadores y referencias cruzadas entre requisitos, ADR, escenarios, tareas y pruebas cuando corresponda.

### 2.2. Ciclo de cambio
1. Identificar alcance y requisitos afectados; registrar incertidumbres.
2. Crear propuesta OpenSpec con motivación, alcance, impacto y exclusiones.
3. Definir escenarios verificables, diseño técnico cuando corresponda y tareas pequeñas.
4. Revisar y **aprobar explícitamente** el cambio antes de implementar.
5. Implementar solo lo aprobado; ejecutar pruebas y verificaciones pertinentes.
6. Revisar diferencias, documentar resultados, actualizar documentación afectada y cerrar/archivar el cambio conforme a la versión de OpenSpec instalada.

OpenSpec se inicializó con OpenSpec CLI 1.15.0 y se configuraron las integraciones para Antigravity, Codex, Claude Code y GitHub Copilot. Las convenciones y capacidades deben verificarse con la versión utilizada; no se presupone que todos los agentes soporten las mismas integraciones.

### 2.3. Reglas para agentes
- Leer requisitos, ADR y estándares pertinentes antes de editar.
- Escribir y modificar archivos **directamente en el repositorio**, no volcar código o archivos completos en el chat.
- Responder con **resumen breve**: archivos/cambios, verificaciones realizadas, incidencias y estado de tareas.
- No ejecutar cambios fuera del alcance ni alterar decisiones aceptadas sin autorización.
- No afirmar que las pruebas pasaron si no fueron ejecutadas; señalar bloqueos.
- No registrar secretos, datos sensibles ni resultados extensos de comandos.
- Para evitar colisiones, **un responsable de escritura por cambio o rama**; no ejecutar agentes distintos modificando simultáneamente los mismos archivos. Usar ramas y revisión de diffs cuando sea apropiado.
- Mantener instrucciones comunes en documentación del repositorio y adaptadores mínimos para Antigravity, Codex, Claude Code y GitHub Copilot; verificar rutas reales antes de crearlas.

## 3. Límites arquitectónicos
OpenSpec, los agentes y Git son **herramientas del proceso de ingeniería**; no forman parte del runtime ni del despliegue Java/Spring Boot. No introducir servicios, microservicios, buses o infraestructura de ejecución por adoptar SDD.

## 4. Alternativas consideradas
- **Prompts directos sin especificaciones:** menos preparación inicial, pero menor reproducibilidad, trazabilidad y control de alcance.
- **Documentación y tareas manuales sin OpenSpec:** viable, pero mayor esfuerzo de coordinación entre agentes.
- **OpenSpec con revisiones humanas (elegida):** añade disciplina y mantenimiento documental a cambio de verificabilidad y portabilidad.

## 5. Consecuencias y compromisos
**Beneficios:** decisiones explícitas, cambios acotados, pruebas derivadas de escenarios, posibilidad de cambiar de agente y revisión auditable.

**Costes/riesgos:** mantenimiento de especificaciones, divergencia entre instrucciones de agentes, cambios concurrentes, falsas aprobaciones implícitas y dependencia de la versión de OpenSpec. Se mitigan mediante aprobaciones explícitas, revisión de diffs, un responsable de escritura y verificación de compatibilidad.

## 6. Seguridad y calidad
Aplican ADR-007 y los estándares de ingeniería: sin secretos en prompts ni repositorio, revisión de dependencias y validación de salidas del agente. Los agentes no deben desactivar controles de seguridad ni alterar migraciones o datos sin un cambio aprobado. Se deben ejecutar pruebas apropiadas a los escenarios, incluyendo integración cuando afecte persistencia o auditoría.

## 7. Criterios de verificación
- [ ] Requisitos funcionales bloqueantes y arquitectura aprobados antes de inicializar OpenSpec.
- [ ] Versión y convenciones de OpenSpec verificadas y documentadas.
- [ ] Cada cambio dispone de propuesta, escenarios, tareas y aprobación explícita.
- [ ] Se mantiene trazabilidad con requisitos y ADR afectados.
- [ ] Los agentes escriben en el repositorio y devuelven únicamente un resumen breve.
- [ ] Se evita edición concurrente de los mismos archivos y se revisan diferencias.
- [ ] Pruebas ejecutadas y resultados documentados, sin declarar éxito no verificado.
- [ ] Los cambios documentales se integran **acumulativamente**, sin sobrescribir secciones anteriores.

## 8. Impacto en arquitectura y diagramas
Añadir a `docs/architecture/architecture.md` una sección de **gobernanza SDD** y referencia a `docs/architecture/diagrams/sdd-workflow.excalidraw` y su PNG. El diagrama describe el proceso de ingeniería, no los componentes desplegados. Conservar las secciones de ADR-001 a ADR-007 y sus diagramas sin modificaciones.

## 9. Estado y condiciones para iniciar
**Aceptado:** ADR-001 a ADR-008 y la línea base funcional y arquitectónica v1.0 están aprobados. OpenSpec fue inicializado y configurado con OpenSpec CLI 1.15.0 para Antigravity, Codex, Claude Code y GitHub Copilot. La implementación aún requiere aprobación independiente; la inicialización de OpenSpec no la autoriza.

## 10. Referencias internas
- [Arquitectura acumulativa](../architecture/architecture.md)
- [Requisitos funcionales](../requirements/functional-requirements.md)
- [Estrategia de pruebas](../engineering/testing-strategy.md)
- [Estándares de seguridad](../engineering/security-standards.md)
- [Diagrama SDD editable](../architecture/diagrams/sdd-workflow.excalidraw)
