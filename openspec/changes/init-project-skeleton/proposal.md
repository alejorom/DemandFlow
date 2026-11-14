# Proposal

## Why

DemandFlow dispone de una línea base funcional y arquitectónica aprobada, pero todavía no cuenta con una aplicación que pueda compilarse y arrancar. Se necesita una base mínima verificable para construir incrementos posteriores sin anticipar funcionalidades de negocio.

## What Changes

- Preparar una aplicación Spring Boot mínima con Java 21 y Maven, capaz de compilarse y arrancar en un entorno local protegido.
- Establecer la configuración y las pruebas básicas necesarias para verificar la compilación y el arranque, sin credenciales ni secretos versionados.
- Mantener una sola aplicación y crear únicamente la estructura y las dependencias justificadas por este alcance, conforme a ADR-001 y ADR-002.
- Excluir base de datos, PostgreSQL, Docker Compose, JPA, Hibernate, Flyway, migraciones y funcionalidades de negocio. La preparación de persistencia corresponde al cambio posterior previsto `setup-database`; la administración de tarifas se abordará después.

## Capabilities

### New Capabilities

- `application-startup`: compilación y arranque local verificables de la aplicación mínima sin conexión a base de datos.

### Modified Capabilities

Ninguna; actualmente no existen especificaciones principales en OpenSpec.

## Impact

- **Arquitectura y dependencias:** se propone la base de un monolito modular según [ADR-001](../../../docs/architecture-decisions/ADR-001-architectural-style.md) y la plataforma de [ADR-002](../../../docs/architecture-decisions/ADR-002-java-spring.md). La versión concreta de Spring Boot y sus dependencias debe seleccionarse tras comprobar compatibilidad y soporte para Java 21; el uso de Maven Wrapper queda por decidir.
- **Seguridad y operación:** aplicar los límites de entorno local protegido de [ADR-007](../../../docs/architecture-decisions/ADR-007-security-baseline.md) y los [estándares de seguridad](../../../docs/engineering/security-standards.md); sin autenticación, la aplicación no debe exponerse públicamente.
- **Pruebas y evolución:** verificar compilación y arranque según la [estrategia de pruebas](../../../docs/engineering/testing-strategy.md). No se modifican las 8 HU ni las reglas RN y RF de la [línea base funcional](../../../docs/requirements/functional-requirements.md); las capacidades de negocio y persistencia se abordarán en cambios posteriores.
- **Riesgos y dependencias:** una versión o dependencia incompatible puede impedir el arranque; una configuración insegura puede exponer el entorno. La definición de `setup-database` no forma parte de esta propuesta.
- **Gobernanza:** esta propuesta no modifica decisiones aprobadas ni autoriza implementación. Conforme a [ADR-008](../../../docs/architecture-decisions/ADR-008-openspec-agents.md), el cambio requiere revisión y aprobación explícita antes de implementarse.
