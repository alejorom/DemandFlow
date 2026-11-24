# Proposal

## Why

DemandFlow dispone de un esqueleto ejecutable, pero todavía carece de la infraestructura local de persistencia aprobada en ADR-003 y ADR-004. Se propone preparar una base técnica reproducible para los siguientes cambios funcionales, conservando el arranque sin base de datos y el entorno local protegido definidos en `openspec/specs/application-startup/spec.md`.

## What Changes

- Preparar PostgreSQL local mediante Docker Compose, con versión fija por seleccionar, volumen persistente y exposición restringida a loopback; la aplicación Java seguirá ejecutándose localmente desde el IDE o como artefacto ejecutable.
- Externalizar las credenciales del contenedor y de la aplicación, con ejemplos sin secretos reales y mínimos privilegios; evaluar la separación entre permisos de migración y ejecución.
- Integrar Spring Data JPA/Hibernate y Flyway, manteniendo Java 21, Spring Boot 4.1.2 y Maven Wrapper. Flyway administrará el esquema; Hibernate quedará configurado para validarlo, sin creación ni actualización automática.
- Incorporar migraciones técnicas versionadas, revisables y verificables, con contenido útil por concretar durante el diseño; no crear tablas de negocio ficticias para demostrar la integración ni modificar migraciones ya aplicadas en entornos compartidos.
- Prever pruebas contra PostgreSQL aislado para conexión, ejecución y validación de migraciones, repetición del arranque sin reaplicar versiones y conservación del volumen al recrear el contenedor. Verificar también fallos de conexión/migración sin exposición de secretos y la regresión del arranque sin BD.
- Preservar todos los requisitos de `application-startup`: construcción con Maven Wrapper, arranque sin base de datos configurada ni disponible y escucha local protegida. La integración no debe imponer Docker ni credenciales de PostgreSQL a ese arranque.

**Exclusiones:** entidades, repositorios y tablas de negocio; CRUD, vigencias, tarifas, demandas, cálculos, consultas, auditoría funcional, autenticación y despliegue productivo. Tampoco se propone contenerizar la aplicación Java ni crear módulos o capas sin una necesidad concreta.

## Capabilities

### New Capabilities

- `local-persistence`: infraestructura PostgreSQL local persistente y de acceso restringido, conexión desde Spring Data JPA/Hibernate y evolución técnica del esquema mediante Flyway con credenciales externas y verificaciones reproducibles, compatible con el arranque independiente de la base de datos.

### Modified Capabilities

Ninguna. `application-startup` permanece vigente sin cambios de requisitos; sus escenarios se utilizarán como comprobaciones de regresión.

## Impact

**Áreas afectadas en una futura implementación:** dependencias de `pom.xml`, configuración de Spring, Docker Compose, migraciones técnicas, pruebas de integración y documentación de operación local. Se conserva un único monolito modular y la organización por funcionalidad; este cambio no introduce modelos ni repositorios compartidos ni satisface por sí solo HU-001 a HU-008.

**Dependencias y decisiones pendientes de revisión humana:**

- Evaluar la habilitación de persistencia mediante un perfil explícito frente a otros mecanismos compatibles con el contrato vigente. Ni el mecanismo ni un nombre de perfil están aprobados; su elección y la configuración asociada se resolverán en el diseño antes de implementar.
- Seleccionar una versión fija de PostgreSQL y verificar compatibilidad del driver, Hibernate, Flyway y su soporte PostgreSQL con Spring Boot 4.1.2, utilizando su gestión de dependencias cuando proceda.
- Concretar el esquema técnico y el contenido de las migraciones sin tablas de negocio; definir el alcance comprobable de Hibernate mientras no existan entidades mapeadas.
- Definir suministro externo de credenciales tanto al contenedor como al proceso Java, permisos, disponibilidad de Docker/Compose, puerto, volumen y aislamiento de las pruebas.

**Riesgos y límites de verificación:** la integración puede activar conexiones no deseadas y romper el arranque sin BD; se exige comprobar ambos modos de arranque. La ausencia de entidades limita lo que puede demostrar la validación de Hibernate, y una prueba de conexión no verifica las restricciones futuras de negocio. Deben evitarse exposición de puertos, filtraciones de credenciales, uso administrativo ordinario y pérdida de datos por eliminación de volúmenes; el volumen no sustituye un respaldo. El procedimiento local de respaldo/recuperación deberá concretarse antes de operar con datos relevantes.

**Trazabilidad:** este incremento técnico se fundamenta en `docs/architecture-decisions/ADR-001-architectural-style.md`, `docs/architecture-decisions/ADR-002-java-spring.md`, `docs/architecture-decisions/ADR-003-postgresql-docker.md`, `docs/architecture-decisions/ADR-004-jpa-flyway.md` y `docs/architecture-decisions/ADR-007-security-baseline.md`; aplica `docs/engineering/security-standards.md` y `docs/engineering/testing-strategy.md`. Las reglas de `docs/requirements/functional-requirements.md`, `docs/requirements/business-rules.md` y `docs/requirements/traceability-matrix.md`, junto con `docs/architecture/architecture.md` y `docs/architecture/data-model.md`, permanecen intactas: precisión decimal, unicidad, exclusión de solapamientos, snapshots, concurrencia y auditoría transaccional corresponden a futuros cambios funcionales y no se declaran implementados aquí.

**Gobernanza:** propuesta pendiente de revisión humana. No modifica requisitos ni ADR aprobados y no autoriza implementación. Conforme a `docs/architecture-decisions/ADR-008-openspec-agents.md`, se requiere aprobación explícita del cambio y autorización independiente antes de implementar; cualquier contradicción con las fuentes debe resolverse previamente. En esta etapa se genera exclusivamente este documento; especificaciones, diseño y tareas quedan pendientes.
