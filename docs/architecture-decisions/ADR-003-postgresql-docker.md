# ADR-003: PostgreSQL en Docker

**Estado:** Aceptado  
**Fecha:** 2026-11-13
**Ámbito:** DemandFlow v1.0  

## 1. Contexto

DemandFlow es una aplicación web desarrollada con Java 21 y Spring Boot, destinada a gestionar demandas de ingeniería, tarifas por vigencia, cálculos económicos y auditoría de cambios.

La aplicación requiere una base de datos relacional que garantice:

- Integridad y consistencia de los datos.
- Persistencia de información histórica.
- Transacciones confiables.
- Restricciones de unicidad y relaciones entre entidades.
- Facilidad de configuración en el entorno de desarrollo.

Se busca una solución sencilla, reproducible y adecuada para aprender Java y Spring Boot.

## 2. Decisión propuesta

Utilizar **PostgreSQL como motor de base de datos relacional**, ejecutándose en un contenedor Docker y administrado mediante Docker Compose.

La aplicación Spring Boot se ejecutará inicialmente de forma local desde el IDE, conectándose a PostgreSQL.

Se utilizarán:

- **PostgreSQL:** almacenamiento persistente.
- **Docker:** aislamiento del motor de base de datos.
- **Docker Compose:** configuración y administración del contenedor.
- **Spring Data JPA:** acceso a los datos, sujeto a la aprobación de ADR-004.
- **Flyway:** migraciones versionadas, sujeto a la aprobación de ADR-004.

No se requiere contenerizar la aplicación Java durante la primera etapa.

## 3. Alternativas consideradas

| Alternativa | Evaluación |
|---|---|
| PostgreSQL instalado directamente | Válido, pero requiere administrar manualmente la instalación y configuración. |
| MySQL | Alternativa técnicamente viable, sin ventajas determinantes para este proyecto. |
| H2 | Útil para pruebas sencillas, pero puede presentar diferencias de comportamiento respecto a PostgreSQL. |
| PostgreSQL en Docker | **Alternativa seleccionada:** facilita aislamiento, reproducibilidad y mantenimiento del entorno. |

Las alternativas no seleccionadas son únicamente documentales.

## 4. Justificación

PostgreSQL proporciona capacidades apropiadas para las necesidades de DemandFlow:

- Soporte de transacciones ACID.
- Integridad referencial.
- Restricciones de unicidad y validación.
- Tipos numéricos de precisión exacta para valores económicos.
- Consultas relacionales y agregaciones.
- Compatibilidad con Spring Boot y JPA.

Docker permite ejecutar PostgreSQL sin instalar directamente el motor en el sistema operativo.

## 5. Consecuencias y compromisos

**Beneficios**

- Entorno de desarrollo reproducible.
- Aislamiento de la base de datos.
- Persistencia de información mediante volúmenes.
- Facilidad para actualizar o recrear el contenedor.
- Menor dependencia de la configuración del equipo.

**Compromisos**

- Requiere Docker y Docker Compose.
- Es necesario administrar credenciales y volúmenes.
- Se deben controlar las versiones de PostgreSQL.
- Los volúmenes no sustituyen las copias de seguridad.

## 6. Buenas prácticas de implementación

### 6.1. Persistencia

- Utilizar volúmenes Docker para conservar los datos.
- Evitar almacenar información únicamente en el sistema de archivos temporal del contenedor.
- No eliminar volúmenes con datos importantes sin respaldo.

### 6.2. Seguridad y OWASP

- No almacenar credenciales en el código fuente ni en Git.
- Utilizar variables de entorno o mecanismos adecuados de gestión de secretos.
- Limitar la exposición de PostgreSQL al entorno local.
- Aplicar el principio de mínimos privilegios.
- No utilizar la cuenta administrativa para las operaciones habituales de la aplicación.
- Utilizar consultas parametrizadas para prevenir inyección SQL.
- Evitar registrar credenciales o información sensible en logs.

### 6.3. Integridad de datos

- Definir claves primarias y foráneas.
- Aplicar restricciones `UNIQUE`, `NOT NULL` y `CHECK` cuando corresponda.
- Garantizar la unicidad del identificador JIRA, incluso para demandas inactivas.
- Evitar superposiciones entre vigencias tarifarias.
- Utilizar tipos numéricos exactos para horas, tarifas e importes.
- Mantener las reglas críticas también protegidas a nivel de base de datos cuando sea viable.

### 6.4. Transacciones

- Ejecutar las operaciones de negocio que deban ser atómicas dentro de una misma transacción.
- Garantizar que las modificaciones de demandas y sus eventos de auditoría se confirmen o reviertan conjuntamente.
- Considerar los escenarios de concurrencia al registrar identificadores únicos.

### 6.5. Migraciones

- Administrar los cambios de esquema mediante Flyway, una vez aprobado ADR-004.
- Versionar las migraciones en Git.
- Evitar cambios manuales no documentados.
- No depender de la generación automática de esquemas JPA como mecanismo de actualización de bases de datos.

### 6.6. Pruebas y recuperación

- Realizar pruebas de integración utilizando PostgreSQL.
- Verificar restricciones, transacciones y consultas.
- Definir procedimientos básicos de respaldo y restauración.
- Comprobar que los datos persisten después de recrear el contenedor.

## 7. Criterios de verificación

Este ADR se considerará implementado cuando:

- [ ] PostgreSQL se ejecute correctamente mediante Docker Compose.
- [ ] Spring Boot pueda conectarse a la base de datos.
- [ ] Los datos persistan después de recrear el contenedor.
- [ ] Las credenciales estén excluidas del repositorio.
- [ ] La conexión esté limitada al entorno local de desarrollo.
- [ ] Las restricciones de integridad estén verificadas.
- [ ] Las transacciones funcionen correctamente.
- [ ] Las migraciones estén versionadas y se ejecuten satisfactoriamente, tras aprobar ADR-004.
- [ ] Las pruebas de integración relevantes sean satisfactorias.

## 8. Referencias internas

- ADR-001: Monolito modular y arquitectura en capas pragmática.
- ADR-002: Java 21 LTS, Spring Boot y Maven.
- ADR-004: Spring Data JPA y Flyway (Aceptado).
- `docs/architecture/data-model.md`
- `docs/engineering/security-standards.md`
- `docs/engineering/testing-strategy.md`

## 9. Estado de la decisión

**Aceptado.** La decisión de PostgreSQL y Docker Compose está aprobada. Las verificaciones técnicas y la implementación siguen pendientes.