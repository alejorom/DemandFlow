# local-persistence — Spec Delta

## MODIFIED Requirements

### Requirement: Integración de persistencia sobre la plataforma vigente
DemandFlow MUST integrar Spring Data JPA con Hibernate y Flyway sobre PostgreSQL, manteniendo Java 21, Spring Boot 4.1.2 y Maven Wrapper. La integración MUST admitir entidades, repositorios y tablas de períodos, tipos fijos, tarifas y auditoría del catálogo. Referencias: ADR-001/002/004; HU-001/002; RN-003/004; RF-TAR-002; D3.

#### Scenario: Inicialización de la integración técnica
- **WHEN** se construye el proyecto mediante Maven Wrapper con Java 21 y se inicia con persistencia habilitada y PostgreSQL correctamente configurado
- **THEN** la aplicación utiliza Spring Boot 4.1.2, inicializa Spring Data JPA/Hibernate y Flyway y obtiene conexión al motor admitiendo los modelos y repositorios del catálogo sobre el esquema migrado.

#### Scenario: Operación con privilegios mínimos
- **WHEN** se ejecutan operaciones válidas de catálogo con las cuentas y credenciales externas configuradas
- **THEN** la cuenta runtime puede realizar las operaciones autorizadas sobre el modelo migrado sin superusuario ni privilegios de creación de esquema, y las migraciones usan la cuenta de migración separada.

### Requirement: Gestión exclusiva del esquema mediante migraciones
Flyway MUST administrar los cambios técnicos y de negocio versionados del esquema. Hibernate MUST validar la correspondencia de las entidades mapeadas sin crear ni actualizar automáticamente tablas. Referencias: ADR-004; HU-001/002; RF-MON-005; RF-TAR-002; RF-CON-004; D3.

#### Scenario: Arranque sin generación automática de tablas
- **WHEN** se inicia la integración con las migraciones aplicadas y las entidades de catálogo mapeadas
- **THEN** Hibernate valida su correspondencia con el esquema sin crear ni alterar tablas; la evidencia comprueba el modelo real y no se limita a inicializar un metamodelo vacío.

#### Scenario: Modelo y esquema incompatibles
- **WHEN** las migraciones han finalizado pero el esquema no corresponde a las entidades mapeadas
- **THEN** el arranque persistente falla sin modificar automáticamente el esquema ni declarar disponible la aplicación y presenta diagnóstico sin secretos.

### Requirement: Migraciones técnicas versionadas y verificables
Las migraciones técnicas y de negocio MUST estar versionadas en Git, ejecutarse en orden y registrar su resultado en Flyway. MUST tener efectos verificables, admitir el catálogo y su auditoría sin tablas ficticias y conservar las versiones ya aplicadas. Referencias: ADR-003/004; HU-001/002; RN-003/004/040; RF-CON-003/004; D3.

#### Scenario: Aplicación sobre base de prueba vacía
- **WHEN** se ejecutan las migraciones aprobadas sobre una base PostgreSQL de prueba vacía
- **THEN** se observan sus efectos técnicos y el modelo de períodos, tipos fijos Soporte/Feature, tarifas y auditoría con las restricciones correspondientes; cada versión aplicada satisfactoriamente queda registrada en orden sin tablas ficticias.

#### Scenario: Evolución desde la base técnica existente
- **WHEN** una base de prueba contiene V1 válida y recibe las nuevas migraciones de catálogo
- **THEN** solo se aplican versiones pendientes, se preservan contenido y registro de V1 y se incorpora el modelo sin recrear almacenamiento, reinicializar la base ni reparar el historial automáticamente.

### Requirement: Verificaciones de integración aisladas y reproducibles
Las pruebas de persistencia MUST ejecutarse contra PostgreSQL real de versión fija, con estado y credenciales de prueba aislados del entorno del desarrollador, mediante Maven Wrapper. MUST cubrir infraestructura y modelo de catálogo e informar fallos de prerrequisitos sin declarar pruebas omitidas como exitosas. Referencias: ADR-002/003/004; HU-001/002; RF-CON-003/004; D3.

#### Scenario: Ejecuciones independientes
- **WHEN** se ejecutan las pruebas de conexión, migraciones, fallos controlados y arranque sobre entornos PostgreSQL de prueba independientes con los prerrequisitos documentados
- **THEN** producen resultados equivalentes sin depender del orden ni de datos previos y sin modificar la carpeta permanente de desarrollo ni usar secretos reales versionados; las pruebas de catálogo utilizan únicamente objetos y datos de negocio de prueba aislados.

#### Scenario: Regresión del modo predeterminado
- **WHEN** se ejecutan las comprobaciones de `application-startup` mediante Maven Wrapper sin infraestructura ni habilitación de persistencia
- **THEN** siguen verificándose la construcción y el arranque sin BD en loopback, sin exigir PostgreSQL para esas comprobaciones.

#### Scenario: Prerrequisito de integración ausente
- **WHEN** se solicita explícitamente la verificación de persistencia y falta el entorno PostgreSQL de prueba o un prerrequisito documentado
- **THEN** la verificación informa el impedimento y termina sin éxito, sin utilizar silenciosamente la base de desarrollo ni contabilizar los escenarios de integración como aprobados.

Trazabilidad de la verificación de este delta: `openspec/specs/local-persistence/spec.md`, `docs/requirements/functional-requirements.md`, `docs/requirements/business-rules.md`, `docs/requirements/traceability-matrix.md`, `docs/architecture/data-model.md` y `docs/engineering/testing-strategy.md`. Las referencias de cada requisito se aplican a todos sus escenarios. D3 es la decisión aprobada de admitir el modelo de negocio mediante Flyway y D2 difiere la integración con demandas, según `openspec/changes/manage-tariffs/proposal.md`; HU-001/002, RN-003/004/040/041, RF-MON-005, RF-TAR-002 y RF-CON-003/004 son las obligaciones funcionales soportadas, sin atribuir su verificación completa a la infraestructura.

Los ADR referenciados se encuentran en `docs/architecture-decisions/ADR-001-architectural-style.md`, `docs/architecture-decisions/ADR-002-java-spring.md`, `docs/architecture-decisions/ADR-003-postgresql-docker.md`, `docs/architecture-decisions/ADR-004-jpa-flyway.md` y `docs/architecture-decisions/ADR-007-security-baseline.md`. La comprobación conserva los contratos no modificados, incluidos PostgreSQL 17.12 Trixie, Compose, bind mount, secretos externos, loopback, privilegios mínimos, historial, fallos controlados y perfil `persistence`. La regresión se contrasta con `openspec/specs/application-startup/spec.md` sin modificarlo. No se eliminan requisitos ni se actualizan propósito o referencias históricas de la especificación canónica.
