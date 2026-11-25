# tariff-periods — Spec Delta

## Purpose

Permitir administrar por web los períodos tarifarios de duración flexible, preservando intervalos válidos, estados e integridad del catálogo.

**Alcance y trazabilidad.**

Fuentes: `docs/requirements/functional-requirements.md`, `docs/requirements/business-rules.md`, `docs/requirements/traceability-matrix.md` y `openspec/changes/manage-tariffs/proposal.md`, contrastadas con `v1.0.0-docs`.
ADR: `docs/architecture-decisions/ADR-004-jpa-flyway.md`, `docs/architecture-decisions/ADR-005-server-rendered-ui.md`, `docs/architecture-decisions/ADR-006-audit-history.md` y `docs/architecture-decisions/ADR-007-security-baseline.md`.
Las referencias de cada requisito se aplican a todos sus escenarios. D2, D4, D7 y D9 identifican, respectivamente, las decisiones aprobadas de diferir demandas, normalizar etiqueta, conservar estados individuales y rechazar transiciones redundantes.

Obligación posterior: RF-PER-001/002 exige bloquear cambios de fechas cuando exista cualquier demanda asociada, activa o inactiva. Su integración y verificación corresponden a los incrementos de demandas según D2; los escenarios actuales de edición se limitan a períodos sin demandas. Esta separación no elimina la regla ni acredita su implementación.

## ADDED Requirements

### Requirement: Gestión web de períodos
El sistema MUST permitir crear, consultar y editar períodos y acceder a los inactivos para su mantenimiento, con etiqueta, fechas y estado visibles. Las operaciones MUST validarse en servidor, protegerse contra CSRF, escapar salida HTML y usar Post/Redirect/Get tras éxito. Referencias: HU-001; RN-010/041; ADR-005/007.

#### Scenario: Consulta y mantenimiento de catálogo
- **WHEN** se consultan los períodos existentes, incluidos los inactivos
- **THEN** se muestran sus etiquetas, fechas y estados y se puede acceder a las operaciones de mantenimiento permitidas.

#### Scenario: Alta válida por formulario
- **WHEN** se envía un formulario válido con protección CSRF válida
- **THEN** se crea el período, se registra su auditoría y se redirige a una consulta que permite comprobar los datos guardados.

#### Scenario: Entrada inválida en servidor
- **WHEN** se envían fechas o etiqueta inválidas aunque se hayan omitido los controles del navegador
- **THEN** se rechaza el guardado, se conservan las entradas válidas en el formulario y se presentan errores comprensibles sin SQL ni trazas.

#### Scenario: Escritura sin token CSRF válido
- **WHEN** se solicita crear, editar o cambiar el estado sin protección CSRF válida
- **THEN** se rechaza la solicitud sin modificar el catálogo ni generar auditoría de negocio.

#### Scenario: Etiqueta con caracteres HTML
- **WHEN** se muestra una etiqueta que contiene caracteres de marcado HTML
- **THEN** se presenta como texto escapado sin ejecutar contenido activo.

### Requirement: Normalización y límites de etiqueta
El sistema MUST eliminar espacios exteriores de la etiqueta antes de validarla y guardarla, rechazar ausencia o resultado vacío y limitar el resultado a 100 caracteres. MUST permitir etiquetas repetidas sin imponer unicidad. Referencias: HU-001; RN-010; D4; ADR-005/007.

#### Scenario: Normalización al crear o editar
- **WHEN** se guarda la etiqueta «  Semestre especial  »
- **THEN** se conserva «Semestre especial» sin espacios exteriores.

#### Scenario: Etiqueta ausente o vacía
- **WHEN** la etiqueta falta, es vacía o contiene únicamente espacios
- **THEN** se rechaza el guardado sin mutación ni evento.

#### Scenario: Límite de longitud
- **WHEN** se guarda una etiqueta con 100 caracteres tras eliminar espacios exteriores, o una con 101
- **THEN** se admite la primera y se rechaza la segunda sin truncarla.

#### Scenario: Etiqueta repetida
- **WHEN** se crea otro período con la misma etiqueta y fechas válidas sin solapamiento
- **THEN** se admite el registro sin tratar la etiqueta como clave única.

### Requirement: Intervalos inclusivos de duración flexible
El sistema MUST exigir fechas válidas de inicio y fin, con inicio no posterior al fin y ambos extremos inclusivos. MUST admitir duración flexible sin exigir anualidad. Referencias: HU-001; RN-001/010; RF-PER-002/004; ADR-004.

#### Scenario: Duraciones permitidas
- **WHEN** se crean períodos anuales, semestrales o especiales con fechas válidas y sin solapamiento
- **THEN** se aceptan sin exigir una duración anual.

#### Scenario: Período de un día
- **WHEN** inicio y fin son la misma fecha no ocupada
- **THEN** se admite el período y esa fecha queda incluida.

#### Scenario: Fechas inválidas o incompletas
- **WHEN** falta alguna fecha, una fecha no es válida o el inicio es posterior al fin
- **THEN** se rechaza el guardado sin modificar datos ni generar evento.

### Requirement: Ausencia global de solapamientos
El sistema MUST impedir en aplicación y mediante restricción de exclusión en PostgreSQL que dos períodos compartan fechas, incluso inactivos. Al editar MUST comprobar contra los demás períodos sin considerar el propio registro como conflicto. Referencias: HU-001; RN-040; RF-PER-002/004; RF-CON-004/005; ADR-004.

#### Scenario: Solapamientos totales y parciales
- **WHEN** un alta o edición produce un intervalo idéntico, contenido, contenedor o parcialmente superpuesto a otro período activo o inactivo
- **THEN** se rechaza y se conserva el estado anterior con error funcional seguro.

#### Scenario: Extremo compartido
- **WHEN** un período termina el 2027-06-30 y otro empieza el 2027-06-30
- **THEN** se rechaza el segundo por compartir una fecha inclusiva.

#### Scenario: Adyacencia sin fecha compartida
- **WHEN** un período termina el 2027-06-30 y otro empieza el 2027-07-01 sin otros solapamientos
- **THEN** se permite la adyacencia.

#### Scenario: Edición sin conflicto consigo mismo
- **WHEN** se edita un período sin demandas conservando sus fechas y cambiando solo su etiqueta
- **THEN** el propio intervalo no impide guardar el cambio válido.

#### Scenario: Escrituras concurrentes superpuestas
- **WHEN** dos transacciones intentan confirmar altas o ediciones que dejarían períodos superpuestos
- **THEN** no se confirman ambas y la operación rechazada no deja cambios ni auditoría; se informa el conflicto sin SQL ni trazas.

### Requirement: Estados de períodos sin cascadas sobre tarifas
El sistema MUST desactivar y reactivar períodos sin borrarlos ni modificar estados individuales de sus tarifas. MUST revalidar integridad al reactivar y rechazar transiciones redundantes sin cambios ni eventos. Referencias: HU-001; RN-041; RF-PER-003; RF-TAR-003; RF-CON-005; D7/D9; ADR-006.

#### Scenario: Desactivación conservadora
- **WHEN** se desactiva un período activo con tarifas activas e inactivas
- **THEN** se conserva el período y cada tarifa mantiene su estado individual, pero ninguna es efectivamente disponible bajo el período inactivo.

#### Scenario: Reactivación válida
- **WHEN** se reactiva un período inactivo que cumple las restricciones de integridad
- **THEN** vuelve a estar activo y se conservan los estados individuales de todas sus tarifas.

#### Scenario: Transición redundante
- **WHEN** se intenta desactivar un período ya inactivo o reactivar uno ya activo
- **THEN** se rechaza con un mensaje comprensible sin modificar datos ni producir auditoría.

### Requirement: Protección de ediciones concurrentes de períodos
El sistema MUST rechazar ediciones y cambios de estado con versión obsoleta sin sobrescritura ni fusión automática. Cada cambio efectivo MUST confirmarse atómicamente con su auditoría. Referencias: HU-001; RF-CON-001/003/005; ADR-004/006; D9.

#### Scenario: Edición de fechas sin demandas
- **WHEN** se editan fechas de un período sin demandas con versión vigente y se cumplen las reglas de intervalo
- **THEN** se guardan las fechas y el evento correspondiente en una sola transacción.

#### Scenario: Edición o cambio de estado obsoleto
- **WHEN** dos operaciones parten de la misma versión y una confirma antes que la otra
- **THEN** la segunda se rechaza sin sobrescribir la primera ni dejar un evento de cambio no confirmado y se solicita recargar o reintentar.
