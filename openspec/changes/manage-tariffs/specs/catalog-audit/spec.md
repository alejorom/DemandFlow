# catalog-audit — Spec Delta

## Purpose

Conservar trazabilidad de las operaciones del catálogo mediante eventos de solo adición, confirmados o revertidos junto con cada cambio de negocio.

**Alcance y trazabilidad.**

Fuentes: `docs/requirements/functional-requirements.md`, `docs/requirements/business-rules.md`, `docs/requirements/traceability-matrix.md`, `docs/architecture/data-model.md` y `openspec/changes/manage-tariffs/proposal.md`, contrastadas con `v1.0.0-docs`.
ADR: `docs/architecture-decisions/ADR-004-jpa-flyway.md`, `docs/architecture-decisions/ADR-006-audit-history.md` y `docs/architecture-decisions/ADR-007-security-baseline.md`.
Las referencias de cada requisito se aplican también a sus escenarios. D1/D8/D9 son las decisiones aprobadas de incluir escritura mínima transaccional, auditoría sencilla append-only y rechazo de transiciones redundantes.

Este incremento cubre la escritura de auditoría necesaria para HU-001/002. La consulta visual corresponde a HU-006 en `audit-changes`; no se especifican aquí pantallas de historial, auditoría de demandas, retención, purga ni garantía de inviolabilidad frente a administradores de BD. RF-DEM-004 describe demandas; los comportamientos equivalentes del catálogo se sustentan en ADR-006, RF-CON-003/005 y D1/D8/D9.

## ADDED Requirements

### Requirement: Cobertura de cambios efectivos del catálogo
El sistema MUST auditar altas, ediciones y transiciones efectivas de períodos y tarifas. Cada registro MUST permitir identificar entidad, identificador, operación y campos relevantes con antes/después; en altas el estado anterior puede estar ausente. Referencias: HU-001/002; RN-018/019/028; RF-PER-002; RF-TAR-001/003; ADR-006; D1/D8.

#### Scenario: Alta de entidad de catálogo
- **WHEN** se confirma el alta de un período o una tarifa
- **THEN** su auditoría permite identificar la entidad creada, operación y valores iniciales relevantes sin exigir un estado anterior inexistente.

#### Scenario: Edición de etiqueta o fechas permitidas
- **WHEN** se confirma un cambio efectivo de etiqueta o de fechas de un período sin demandas
- **THEN** quedan registrados los valores anteriores y nuevos de los campos cambiados.

#### Scenario: Corrección de tarifa bajo cualquier estado permitido
- **WHEN** se confirma una corrección de valor de tarifa, incluido el caso de período inactivo
- **THEN** se conserva la trazabilidad de valor anterior y nuevo sin registrar una reactivación inexistente.

#### Scenario: Desactivación y reactivación
- **WHEN** se confirma una desactivación o reactivación válida de período o tarifa
- **THEN** se registra la transición de estado de la entidad realmente modificada sin atribuir cambios a tarifas cuyos estados se conservaron.

### Requirement: Instante y origen de auditoría
El sistema MUST registrar instantes UTC y origen LOCAL_USER o SYSTEM según corresponda, determinados por el servidor. MUST NOT atribuir identidad personal autenticada inexistente ni incorporar secretos o datos sensibles innecesarios. Referencias: HU-001/002; RN-018/020; ADR-006/007; D1/D8.

#### Scenario: Operación iniciada por usuario local
- **WHEN** se confirma una operación del catálogo enviada desde el formulario local
- **THEN** el evento incluye instante UTC y origen LOCAL_USER, sin presentarlo como identidad verificada.

#### Scenario: Origen de sistema
- **WHEN** una operación auditable tiene origen de sistema
- **THEN** se registra SYSTEM con instante UTC; esto no exige incorporar automatismos de catálogo fuera del alcance.

#### Scenario: Manipulación de metadatos y secretos
- **WHEN** una solicitud intenta imponer instante, origen o valores auditados e incluye datos ajenos a los campos permitidos
- **THEN** esos datos no controlan la auditoría, que se deriva del cambio real y no conserva secretos ni información innecesaria.

### Requirement: Atomicidad entre mutación y evento
El sistema MUST confirmar cada mutación de catálogo y su auditoría en una misma transacción. Si falla cualquiera MUST revertir toda la operación y comunicar un resultado seguro, sin afirmar que el cambio fue guardado. Referencias: HU-001/002; RN-028; RF-CON-003/005; ADR-004/006; D1/D8.

#### Scenario: Confirmación conjunta
- **WHEN** son válidas y se persisten correctamente la mutación y su auditoría
- **THEN** ambas quedan confirmadas y el resultado de éxito corresponde a esos datos.

#### Scenario: Fallo al guardar auditoría
- **WHEN** falla la persistencia del evento durante un alta, edición o transición
- **THEN** no queda confirmada ninguna parte del cambio de catálogo ni de su auditoría y se informa el fallo sin SQL ni trazas.

#### Scenario: Fallo de mutación o confirmación
- **WHEN** una restricción, conflicto de versión u otro fallo de persistencia impide confirmar la mutación
- **THEN** no queda un evento que presente esa mutación como confirmada ni datos parciales.

### Requirement: Ausencia de auditorías ficticias
El sistema MUST omitir eventos de negocio para guardados sin cambios efectivos y para operaciones rechazadas. MUST rechazar transiciones de estado redundantes sin modificar la entidad ni generar eventos. Referencias: HU-001/002; RF-CON-005; ADR-006; D9.

#### Scenario: Guardado sin cambios
- **WHEN** se guarda una entidad con los mismos valores efectivos tras la normalización y validación
- **THEN** no se produce modificación efectiva ni evento de cambio.

#### Scenario: Cambio de estado redundante
- **WHEN** se pide desactivar una entidad ya inactiva o reactivar una ya activa
- **THEN** se rechaza sin modificar datos ni añadir un evento de transición.

#### Scenario: Rechazo por validación o concurrencia
- **WHEN** una operación se rechaza por valor inválido, duplicado, solapamiento, estado incompatible o versión obsoleta
- **THEN** no se añade auditoría que la registre como cambio confirmado.

### Requirement: Eventos de solo adición en flujos ordinarios
Los eventos MUST conservarse sin edición ni borrado desde los flujos ordinarios de la aplicación. Las nuevas operaciones MUST agregar su trazabilidad sin sustituir eventos anteriores; los logs técnicos MUST NOT ser el único historial. Referencias: HU-001/002; RN-019/028; RF-CON-003; ADR-006/007; D8.

#### Scenario: Correcciones sucesivas
- **WHEN** se confirman dos correcciones distintas de una tarifa
- **THEN** se conserva la trazabilidad de ambas y la segunda no altera los eventos de la primera.

#### Scenario: Intento de alterar el historial desde la aplicación
- **WHEN** se intenta modificar o eliminar un evento a través de los flujos ordinarios
- **THEN** el evento permanece sin cambios y no existe una operación de mantenimiento ordinario que permita alterarlo.
