# hourly-rates — Spec Delta

## Purpose

Permitir mantener por web las tarifas COP/h de los tipos fijos y sus vigencias, con unicidad, precisión y disponibilidad coherentes.

**Alcance y trazabilidad.**

Fuentes: `docs/requirements/functional-requirements.md`, `docs/requirements/business-rules.md`, `docs/requirements/traceability-matrix.md` y `openspec/changes/manage-tariffs/proposal.md`, contrastadas con `v1.0.0-docs`.
ADR: `docs/architecture-decisions/ADR-004-jpa-flyway.md`, `docs/architecture-decisions/ADR-005-server-rendered-ui.md`, `docs/architecture-decisions/ADR-006-audit-history.md` y `docs/architecture-decisions/ADR-007-security-baseline.md`.
Las referencias de cada requisito se extienden a sus escenarios. D2/D5/D6/D7/D9 son las decisiones aprobadas de diferir demandas, inmovilizar tipo/período, impedir altas/reactivaciones bajo períodos inactivos, conservar estados individuales y rechazar transiciones redundantes. La interpretación aprobada de RN-026 y RF-TAR-001 permite corregir valores bajo períodos inactivos sin habilitar su uso.

Dependencias posteriores según D2: `register-demand` y `maintain-demand` verificarán RN-006/027/029, RF-TAR-001/004 y RF-CON-002 respecto a snapshots, importes históricos y selección protegida al guardar demandas. Los escenarios actuales verifican el catálogo y no acreditan esa integración ni introducen entidades de demanda.

## ADDED Requirements

### Requirement: Gestión web de tarifas y tipos fijos
El sistema MUST permitir crear, consultar y mantener tarifas mostrando tipo, período, COP/h y estado, incluidos registros inactivos. MUST admitir exclusivamente Soporte y Feature y exigir referencias existentes; no ofrecerá CRUD de tipos ni generará tarifas automáticamente al crear períodos. Referencias: HU-002; RN-003; RF-TAR-002; ADR-005; D2.

#### Scenario: Alta con referencias válidas
- **WHEN** se registra una tarifa válida para Soporte o Feature en un período activo y sin tarifa previa para esa combinación
- **THEN** se conserva la asociación y se permite consultar su valor y estado.

#### Scenario: Tipo o período inválido
- **WHEN** se envía un tipo diferente de Soporte/Feature, falta una referencia o el período no existe
- **THEN** se rechaza el alta sin crear tarifa ni evento.

#### Scenario: Consulta de inactivos
- **WHEN** se accede al mantenimiento de una tarifa inactiva
- **THEN** se muestran tipo, período, valor y estado y se permite acceder a las operaciones autorizadas.

#### Scenario: Período sin tarifas iniciales
- **WHEN** se crea un período válido
- **THEN** no se crean automáticamente tarifas ni se exige que existan las dos combinaciones.

### Requirement: Unicidad global de combinación tarifaria
El sistema MUST conservar como máximo una tarifa por combinación período/tipo, incluso inactiva, validando en aplicación y reforzando unicidad en PostgreSQL. Una colisión MUST devolver un error funcional seguro sin datos parciales. Referencias: HU-002; RN-003/004; RF-TAR-002; RF-CON-005; ADR-004.

#### Scenario: Duplicado activo o inactivo
- **WHEN** se intenta crear otra tarifa para una combinación ocupada por un registro activo o inactivo
- **THEN** se rechaza el duplicado y se conserva la tarifa existente.

#### Scenario: Tipos diferentes en una vigencia
- **WHEN** existe tarifa Soporte y se registra Feature para el mismo período activo
- **THEN** se admite la combinación distinta si cumple las demás validaciones.

#### Scenario: Altas duplicadas concurrentes
- **WHEN** dos transacciones intentan crear una tarifa para la misma combinación libre
- **THEN** se confirma como máximo una y la rechazada no deja tarifa ni evento de alta confirmado.

### Requirement: Combinación inmutable tras el alta
El sistema MUST impedir modificar tipo o período de una tarifa existente, incluso mediante solicitudes manipuladas. La corrección de valor MUST realizarse sobre el registro existente sin sustituir su identidad. Referencias: HU-002; RN-003/026; RF-TAR-001/002; ADR-004/007; D5.

#### Scenario: Intento de reasignación
- **WHEN** una solicitud de edición intenta cambiar tipo o período
- **THEN** se rechaza sin reasignar ni duplicar la tarifa y sin registrar una modificación ficticia.

#### Scenario: Corrección del registro existente
- **WHEN** se corrige un valor válido conservando tipo y período
- **THEN** se mantiene el identificador de la tarifa y se auditan los valores anterior y nuevo.

### Requirement: Valor COP positivo y decimal exacto
El sistema MUST exigir valor COP/h obligatorio, estrictamente positivo, con máximo dos decimales y dentro de NUMERIC(15,2). MUST usar precisión decimal exacta y rechazar exceso de escala o límite sin redondear ni truncar entradas. Referencias: HU-002; RF-MON-001/004/005; ADR-004.

#### Scenario: Límites válidos
- **WHEN** se guarda 0.01 o 9999999999999.99 como tarifa
- **THEN** se conserva exactamente el valor admitido, sin error binario de precisión.

#### Scenario: Valor ausente o no positivo
- **WHEN** falta el valor, no es numérico, es cero o negativo
- **THEN** se rechaza el alta o corrección sin mutación ni evento.

#### Scenario: Escala excesiva
- **WHEN** se envía 12.345 o 12.340
- **THEN** se rechaza la entrada con más de dos decimales sin redondearla ni eliminar silenciosamente el exceso.

#### Scenario: Precisión excedida
- **WHEN** se intenta guardar 10000000000000.00
- **THEN** se rechaza por exceder el límite monetario sin alterar el registro previo.

### Requirement: Corrección de valores independiente del estado del período
El sistema MUST permitir corregir valores válidos de tarifas existentes, incluso con período inactivo, conservando estados y combinación y auditando antes/después. La corrección MUST NOT reactivar tarifa o período ni habilitar por sí misma su disponibilidad. Referencias: HU-002; RN-026/027/028; RF-TAR-001; ADR-006; interpretación aprobada de D6.

#### Scenario: Corrección bajo período activo
- **WHEN** se modifica el valor de una tarifa existente en un período activo
- **THEN** se conserva el nuevo valor auditado sin cambiar el estado individual de la tarifa.

#### Scenario: Corrección bajo período inactivo
- **WHEN** se corrige el valor de una tarifa activa o inactiva cuyo período está inactivo
- **THEN** se guarda el valor con auditoría, se conservan ambos estados y la tarifa sigue sin disponibilidad efectiva.

### Requirement: Estados y disponibilidad efectiva de tarifas
El sistema MUST considerar disponible una tarifa solo si ella y su período están activos. MUST impedir altas y reactivaciones bajo períodos inactivos, permitir baja lógica y rechazar cambios de estado redundantes sin mutación ni evento. Referencias: HU-002; RN-041; RF-TAR-003; RF-PER-003; RF-CON-005; ADR-006; D6/D7/D9.

#### Scenario: Alta bajo período inactivo
- **WHEN** se intenta crear una tarifa bajo un período inactivo, cualquiera que sea el estado solicitado para ella
- **THEN** se rechaza sin crear tarifa ni auditoría de alta.

#### Scenario: Desactivación de tarifa
- **WHEN** se desactiva una tarifa activa
- **THEN** se conserva su identidad, valor y combinación, cambia a inactiva y deja de estar disponible sin borrado físico.

#### Scenario: Reactivación permitida y prohibida
- **WHEN** se intenta reactivar una tarifa inactiva
- **THEN** se admite solo con período activo e integridad válida; con período inactivo se rechaza sin cambiar estados ni generar evento.

#### Scenario: Combinaciones de disponibilidad
- **WHEN** se consulta una tarifa en cualquiera de las cuatro combinaciones de estados tarifa/período
- **THEN** solo la combinación activa/activo se considera disponible, sin modificar los estados almacenados.

#### Scenario: Transición redundante
- **WHEN** se intenta desactivar una tarifa ya inactiva o reactivar una ya activa
- **THEN** se rechaza sin cambios ni auditoría ficticia.

### Requirement: Consistencia concurrente de tarifas y períodos
El sistema MUST rechazar ediciones y transiciones con versión obsoleta sin sobrescritura ni fusión. MUST proteger la comprobación del estado del período al confirmar altas y reactivaciones frente a cambios concurrentes; ante conflicto MUST cancelar la operación afectada y solicitar reintento seguro. Referencias: HU-001/002; RF-CON-001/002/003/005; RF-TAR-003; ADR-004/006; D6/D7.

#### Scenario: Correcciones o transiciones concurrentes de tarifa
- **WHEN** dos operaciones parten de la misma versión de una tarifa y una confirma antes que la otra
- **THEN** la obsoleta se rechaza sin sobrescribir el cambio ni dejar un evento de modificación confirmada.

#### Scenario: Desactivación concurrente del período
- **WHEN** un alta o reactivación compite con la desactivación del período
- **THEN** el resultado respeta un orden transaccional coherente: si la desactivación precede al guardado, este se rechaza; si el guardado precede, la posterior desactivación conserva el estado individual y anula la disponibilidad efectiva; un conflicto cancelado exige reintento.

### Requirement: Formularios de tarifa seguros
El sistema MUST validar operaciones de tarifa en servidor, exigir protección CSRF, escapar salidas y mostrar errores sin SQL ni trazas. Tras éxito MUST usar Post/Redirect/Get; ante validación fallida MUST conservar entradas válidas. Referencias: HU-002; RF-CON-005; ADR-005/007.

#### Scenario: Formulario válido
- **WHEN** se envía una operación válida con protección CSRF válida
- **THEN** se procesa el cambio con auditoría y se redirige a una consulta verificable sin exigir autenticación fuera del alcance del MVP.

#### Scenario: Validación omitida en navegador
- **WHEN** una solicitud manipulada contiene un valor inválido o referencias no permitidas
- **THEN** el servidor la rechaza con errores comprensibles y sin cambios de negocio.

#### Scenario: CSRF inválido y salida HTML
- **WHEN** se intenta escribir sin token CSRF válido o mostrar contenido con caracteres HTML
- **THEN** la escritura se rechaza sin mutación y las salidas se presentan escapadas sin ejecutar contenido activo.
