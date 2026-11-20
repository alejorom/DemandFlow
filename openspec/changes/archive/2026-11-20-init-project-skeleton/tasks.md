# Tasks

La ejecución de estas tareas requiere la aprobación independiente del cambio conforme a ADR-008 (`docs/architecture-decisions/ADR-008-openspec-agents.md`).

## 1. Construcción y dependencias

- [x] 1.1 Verificar y registrar una versión compatible de Maven para el Wrapper y la compatibilidad de JUnit Jupiter 6 y Spring Boot Test con Spring Boot 4.1.2 y Java 21, con referencias oficiales revisables; comprobar también procedencia e integridad de la distribución Maven.
- [x] 1.2 Crear el proyecto Maven con Java 21, Spring Boot 4.1.2, `groupId` `io.github.alejorom`, `artifactId` `demandflow` y dependencias mínimas sin persistencia; verificar el descriptor y la resolución de dependencias.
- [x] 1.3 Incorporar y versionar Maven Wrapper con la versión fijada y verificación de integridad; ejecutar el Wrapper con Java 21 sin Maven global y comprobar su versión y funcionamiento.
- [x] 1.4 Añadir una prueba de arranque del contexto con JUnit Jupiter 6 y Spring Boot Test; ejecutar compilación, pruebas y empaquetado con el Wrapper y verificar que producen un artefacto ejecutable.
- [x] 1.5 Comprobar en un entorno de prueba aislado que un fallo de compilación o prueba hace que la construcción devuelva un resultado no exitoso, sin conservar cambios deliberadamente fallidos.

## 2. Arranque local protegido

- [x] 2.1 Crear el punto de entrada bajo `io.github.alejorom.demandflow` y configurar la escucha en `127.0.0.1`; arrancar el artefacto sin base de datos configurada ni disponible y verificar que permanece en ejecución y escucha solo en loopback.
- [x] 2.2 Revisar configuración, dependencias y archivos entregados para verificar ausencia de secretos reales, servicios de persistencia y exposición de diagnósticos innecesarios; registrar la revisión de vulnerabilidades relevantes conforme a `docs/engineering/security-standards.md`.
- [x] 2.3 Documentar requisitos de Java y Wrapper y comandos de construcción, pruebas y arranque local; ejecutar los comandos documentados y comprobar que funcionan sin Maven global.

## 3. Verificación integral

- [x] 3.1 Ejecutar los escenarios de [application-startup](specs/application-startup/spec.md), revisar las diferencias y registrar los resultados; confirmar que el alcance excluye PostgreSQL, JPA, Hibernate, Flyway y funcionalidades de negocio.
