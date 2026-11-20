# Design

## Context

El repositorio aún no contiene una aplicación Java. La motivación y el alcance se describen en [proposal.md](proposal.md); los comportamientos verificables están en [application-startup](specs/application-startup/spec.md). La línea base aprobada exige una sola aplicación Spring Boot con Java 21 y Maven (ADR-001: `docs/architecture-decisions/ADR-001-architectural-style.md`; ADR-002: `docs/architecture-decisions/ADR-002-java-spring.md`).

## Goals / Non-Goals

- Preparar una construcción y un arranque verificables, sin infraestructura externa y con escucha local restringida.
- Mantener el esqueleto pequeño y facilitar su extensión posterior por funcionalidades.
- No introducir persistencia, base de datos, migraciones, formularios ni reglas de negocio. PostgreSQL, JPA y Flyway corresponden al cambio posterior `setup-database`.

## Decisions

### Una aplicación y estructura mínima

Crear un único proyecto Maven con una clase de arranque Spring Boot y el mínimo de paquetes necesario. Se evita crear paquetes vacíos por funcionalidad: ADR-001 (`docs/architecture-decisions/ADR-001-architectural-style.md`) los reserva para cuando exista código que los justifique. La alternativa de varios módulos o capas preconstruidas añade complejidad sin comportamiento que la requiera.

El proyecto utilizará `groupId` `io.github.alejorom`, `artifactId` `demandflow` y paquete base `io.github.alejorom.demandflow`.

### Construcción y dependencias

Fijar Spring Boot **4.1.2** en el descriptor Maven y usar su gestión de dependencias y plugins. Los [requisitos oficiales de Spring Boot 4.1.2](https://docs.spring.io/spring-boot/4.1.2/system-requirements.html) indican compatibilidad con Java 17 a 27 y Maven 3.6.3 o posterior; Java 21 está incluido en ese intervalo. Usar Maven 3.9.11 mediante Maven Wrapper y verificar JUnit Jupiter 6 y Spring Boot Test conforme a `docs/architecture-decisions/ADR-002-java-spring.md`. No se adopta una versión dinámica. Incluir y versionar Maven Wrapper para que los equipos utilicen una versión de Maven fijada; frente a depender de una instalación local, esto mejora la reproducibilidad de la construcción.

La sustitución de JUnit 5 por JUnit Jupiter 6 fue aprobada expresamente. El [BOM oficial de Spring Boot 4.1.2](https://repo.maven.apache.org/maven2/org/springframework/boot/spring-boot-dependencies/4.1.2/spring-boot-dependencies-4.1.2.pom) gestiona JUnit Jupiter 6.0.3 y Spring Framework 7.0.10. La [documentación oficial de Spring Framework](https://docs.spring.io/spring-framework/reference/testing/testcontext-framework/support-classes.html) exige JUnit Jupiter 6.0 o superior para `SpringExtension`; el [starter de pruebas de Spring Boot](https://docs.spring.io/spring-boot/4.1/reference/testing/index.html) incluye JUnit Jupiter y los módulos de prueba de Spring Boot. La prueba de contexto se ejecutó satisfactoriamente con Java 21, Spring Boot 4.1.2 y JUnit Jupiter 6; los resultados se registran en `openspec/changes/init-project-skeleton/evidence.md`.

Incluir únicamente lo necesario para el arranque web y sus pruebas. No añadir dependencias de persistencia ni una base de datos embebida: ocultarían la ausencia deliberada de infraestructura y dificultarían comprobar el arranque independiente.

### Límite local y configuración

Configurar explícitamente el servidor para escuchar en loopback (`127.0.0.1`) y verificar la interfaz efectiva. No incorporar secretos reales ni exponer diagnósticos innecesarios. No se implementan operaciones que modifiquen estado en este incremento; cuando existan formularios se aplicará la protección CSRF de ADR-007 (`docs/architecture-decisions/ADR-007-security-baseline.md`). El aislamiento de red no equivale a autenticación y no autoriza exposición pública.

### Verificación

Ejecutar la construcción y pruebas mediante Maven Wrapper con Java 21; verificar el empaquetado ejecutable, un arranque real sin base de datos y la interfaz de escucha. Comprobar que un fallo de compilación o prueba produce un resultado no exitoso y revisar configuración y dependencias por secretos y vulnerabilidades relevantes. Esto cubre los escenarios de la especificación sin anticipar pruebas de persistencia ni reglas RN/RF.

## Risks / Trade-offs

- **Dependencias incompatibles o sin soporte** → verificar las versiones de Maven y de las dependencias adicionales y revisar su soporte antes de implementar.
- **Exposición accidental del servicio** → enlace local explícito y comprobación de la interfaz efectiva; no usar este aislamiento como sustituto de identidad.
- **Acoplamiento prematuro a la persistencia** → mantener el arranque y sus pruebas sin base de datos; integrar PostgreSQL y Flyway en `setup-database`.
- **Disponibilidad o integridad de la distribución Maven del Wrapper** → fijar su versión, verificar su procedencia e integridad y documentar los requisitos para obtenerla.

## Migration Plan

Tras la aprobación independiente del cambio, incorporar el esqueleto, ejecutar las verificaciones y documentar cómo arrancarlo localmente. No hay datos ni esquema que migrar. Si se revierte el cambio antes de incorporar trabajo dependiente, revertir sus archivos mediante el flujo normal de Git; después, evaluar las dependencias antes de revertir.
