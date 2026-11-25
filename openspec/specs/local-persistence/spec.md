# local-persistence Specification

## Purpose

Permitir una persistencia PostgreSQL local, reproducible y de acceso restringido, con conexión desde Spring y evolución técnica del esquema mediante Flyway, conservando el arranque predeterminado de DemandFlow sin base de datos.

### Alcance y trazabilidad

Esta especificación se redactó inicialmente como propuesta pendiente de revisión humana y de autorización independiente para implementar conforme a ADR-008. Ambas fueron obtenidas; su resolución consta en el diseño aprobado del cambio OpenSpec `setup-database`, decisiones D1–D9. Desarrolla únicamente la capacidad `local-persistence` de `openspec/changes/setup-database/proposal.md`; no modifica `openspec/specs/application-startup/spec.md` ni requisitos o ADR aprobados.

Referencias desde la raíz del repositorio:

- ADR-001: `docs/architecture-decisions/ADR-001-architectural-style.md`.
- ADR-002: `docs/architecture-decisions/ADR-002-java-spring.md`.
- ADR-003: `docs/architecture-decisions/ADR-003-postgresql-docker.md`.
- ADR-004: `docs/architecture-decisions/ADR-004-jpa-flyway.md`.
- ADR-007: `docs/architecture-decisions/ADR-007-security-baseline.md`.
- ADR-008: `docs/architecture-decisions/ADR-008-openspec-agents.md`.
- Arquitectura y límites del modelo: `docs/architecture/architecture.md` y `docs/architecture/data-model.md`.
- Seguridad y pruebas: `docs/engineering/security-standards.md` y `docs/engineering/testing-strategy.md`.

Las referencias de cada requisito se aplican también a todos sus escenarios. Según `docs/requirements/traceability-matrix.md`, este incremento técnico no satisface por sí mismo HU-001 a HU-008 ni implementa reglas RN/RF. Las reglas aprobadas de `docs/requirements/functional-requirements.md` y `docs/requirements/business-rules.md`, incluidos los refinamientos de RN-001 y RN-031, permanecen intactas y fuera de esta implementación técnica.

Persistencia habilitada significa una selección deliberada del operador. Como antecedente histórico, al redactar esta especificación estaban pendientes el mecanismo y nombre del perfil, la versión concreta de PostgreSQL, puerto, esquema técnico, contenido de migraciones, suministro de credenciales, separación de permisos y herramienta de pruebas. Su resolución consta en el diseño aprobado del cambio OpenSpec `setup-database`, decisiones D1–D9, incluidos los ajustes autorizados de D2 (Trixie) y D3 (bind mount Windows). Estas decisiones concretan los comportamientos siguientes sin modificarlos. Se conservan las 26/26 tareas completadas; queda pendiente la revisión humana de cierre del resultado.

## Requirements

### Requirement: PostgreSQL local con versión fija
El entorno MUST permitir ejecutar PostgreSQL mediante Docker Compose con una versión explícita y fija, seleccionada y documentada durante el diseño. La aplicación Java MUST poder ejecutarse fuera de Docker. Referencias: ADR-001, ADR-002 y ADR-003.

#### Scenario: Inicio reproducible del motor
- **WHEN** se inicia el entorno Compose con Docker disponible y la configuración externa válida
- **THEN** PostgreSQL queda disponible para conexiones locales utilizando la versión fija declarada, sin depender de una etiqueta de versión dinámica.

#### Scenario: Conexión desde el proceso Java local
- **WHEN** PostgreSQL está disponible y se ejecuta DemandFlow localmente con persistencia habilitada
- **THEN** la aplicación puede conectarse sin requerir que su proceso Java esté contenerizado.

### Requirement: Conservación del almacenamiento técnico
PostgreSQL MUST almacenar sus datos mediante bind mount en una carpeta persistente del anfitrión y conservar el estado técnico confirmado al recrear el contenedor reutilizando esa carpeta. El valor predeterminado MUST ser `D:/DOCKER_DATA/POSTGRES_DATA`, configurable externamente mediante `DEMANDFLOW_DATA_DIR`; las pruebas MUST utilizar carpetas temporales independientes. Referencia: ADR-003.

#### Scenario: Recreación sin pérdida del estado técnico
- **WHEN** se recrea el contenedor después de aplicar satisfactoriamente las migraciones técnicas y se reutiliza la carpeta existente sin eliminar sus datos
- **THEN** se conservan el estado técnico y el historial de migraciones confirmados y la base puede volver a utilizarse sin reinicialización destructiva.

### Requirement: Exposición restringida a loopback
El acceso a PostgreSQL publicado en el equipo anfitrión MUST limitarse a loopback. El arranque con persistencia habilitada MUST conservar la escucha local protegida de la aplicación. Referencias: ADR-003, ADR-007 y `application-startup`.

#### Scenario: Interfaces efectivamente publicadas
- **WHEN** se inspeccionan los puertos e interfaces efectivos del entorno local iniciado
- **THEN** PostgreSQL es accesible desde loopback del anfitrión y no publica su puerto en interfaces externas ni en todas las interfaces; la aplicación también mantiene su escucha en loopback.

### Requirement: Credenciales externas y privilegios mínimos
Las credenciales de PostgreSQL para el contenedor y la aplicación MUST suministrarse externamente, sin secretos reales en archivos versionados ni diagnósticos. La cuenta de ejecución de la aplicación MUST operar con permisos mínimos, sin superusuario. Referencias: ADR-003 y ADR-007.

#### Scenario: Configuración externa utilizable
- **WHEN** el operador suministra credenciales válidas por el mecanismo externo que se apruebe para el contenedor y el proceso Java
- **THEN** la conexión funciona sin editar archivos versionados ni usar una cuenta de superusuario para la ejecución ordinaria.

#### Scenario: Revisión de archivos y diagnósticos
- **WHEN** se revisan los archivos entregados, sus ejemplos y los diagnósticos de arranques exitosos y fallidos
- **THEN** no contienen contraseñas reales ni cadenas de conexión completas con secretos y los ejemplos solo contienen valores de referencia sin credenciales reales.

### Requirement: Integración de persistencia sobre la plataforma vigente
DemandFlow MUST integrar Spring Data JPA con Hibernate y Flyway sobre PostgreSQL, manteniendo Java 21, Spring Boot 4.1.2 y Maven Wrapper. La integración MUST verificarse sin introducir entidades, repositorios ni tablas de negocio. Referencias: ADR-001, ADR-002 y ADR-004.

#### Scenario: Inicialización de la integración técnica
- **WHEN** se construye el proyecto mediante Maven Wrapper con Java 21 y se inicia con persistencia habilitada y PostgreSQL correctamente configurado
- **THEN** la aplicación utiliza Spring Boot 4.1.2, inicializa la infraestructura Spring Data JPA/Hibernate y Flyway y obtiene conexión al motor sin requerir modelos ni repositorios de negocio.

### Requirement: Gestión exclusiva del esquema mediante migraciones
Flyway MUST administrar los cambios técnicos versionados del esquema. Hibernate MUST quedar configurado para validar y no crear ni actualizar automáticamente el esquema. La verificación MUST explicitar su alcance limitado mientras no existan entidades mapeadas. Referencia: ADR-004.

#### Scenario: Arranque sin generación automática de tablas
- **WHEN** se inicia la integración con las migraciones técnicas aplicadas y sin entidades de negocio
- **THEN** Hibernate se inicializa en modo de validación sin crear ni alterar tablas; la evidencia distingue esa inicialización de una validación de correspondencia de entidades que todavía no existen.

### Requirement: Migraciones técnicas versionadas y verificables
Las migraciones técnicas MUST estar versionadas en Git, ejecutarse en orden y registrar su resultado en el historial de Flyway. MUST tener un efecto técnico verificable sin tablas ficticias ni objetos de negocio. Referencias: ADR-003 y ADR-004.

#### Scenario: Aplicación sobre base de prueba vacía
- **WHEN** se ejecutan las migraciones técnicas aprobadas sobre una base PostgreSQL de prueba vacía
- **THEN** se observa su efecto técnico esperado y cada versión aplicada satisfactoriamente queda registrada en orden en el historial, sin crear entidades, repositorios ni tablas de negocio.

### Requirement: Repetición segura de la ejecución de migraciones
Reejecutar Flyway sobre un estado técnico válido MUST conservar las versiones ya aplicadas sin reaplicarlas ni borrar datos. Las correcciones de migraciones aplicadas en entornos compartidos MUST incorporarse mediante nuevas versiones, sin reescribir su historial. Referencia: ADR-004.

#### Scenario: Segundo arranque sin cambios de migración
- **WHEN** se vuelve a iniciar la aplicación con persistencia habilitada sobre la misma base ya migrada y sin nuevas versiones
- **THEN** la validación de migraciones finaliza satisfactoriamente, no se reaplican las versiones existentes y se conserva el estado técnico confirmado.

#### Scenario: Nueva versión técnica
- **WHEN** se incorpora una nueva migración técnica y se inicia sobre una base que contiene las versiones anteriores válidas
- **THEN** se aplica únicamente la versión pendiente y el historial conserva las versiones anteriores y sus resultados.

#### Scenario: Alteración de una migración aplicada
- **WHEN** la validación detecta que el contenido de una migración versionada difiere del registrado al aplicarla
- **THEN** el arranque con persistencia habilitada falla sin aceptar ni reparar automáticamente la discrepancia y sin informar éxito de inicialización.

### Requirement: Arranque deliberado con persistencia habilitada
Con persistencia habilitada, DemandFlow MUST completar el arranque solo después de obtener conexión válida, aplicar las migraciones pendientes y validar su historial e inicializar JPA/Hibernate satisfactoriamente. Referencias: ADR-004 y ADR-007.

#### Scenario: Arranque con infraestructura válida
- **WHEN** el operador habilita deliberadamente la persistencia y PostgreSQL, credenciales y migraciones son válidos
- **THEN** la aplicación completa el arranque y permanece disponible en loopback con la integración de persistencia inicializada.

### Requirement: Fallo controlado de la inicialización persistente
Si falla la conexión, la configuración requerida o una migración, el arranque con persistencia habilitada MUST finalizar sin éxito, con diagnóstico seguro y sin continuar silenciosamente en modo sin BD. Los tiempos de espera MUST ser acotados y documentarse en el diseño. Referencias: ADR-004 y ADR-007.

#### Scenario: Base no disponible o acceso rechazado
- **WHEN** se inicia con persistencia habilitada y PostgreSQL es inaccesible o rechaza las credenciales
- **THEN** el proceso termina con código distinto de cero dentro del límite configurado, informa un fallo de conexión sin secretos y no declara disponible la aplicación.

#### Scenario: Falta de configuración requerida
- **WHEN** se inicia con persistencia habilitada sin los datos externos obligatorios para establecer conexión
- **THEN** el proceso termina sin éxito con un diagnóstico de configuración incompleta, sin recurrir a credenciales reales versionadas ni a una base alternativa.

#### Scenario: Error al ejecutar una migración
- **WHEN** una migración técnica falla durante el arranque en una base de prueba aislada
- **THEN** el proceso termina con código distinto de cero, identifica un fallo de migración sin revelar secretos y no registra esa versión como aplicada satisfactoriamente ni presenta la aplicación como disponible.

### Requirement: Arranque predeterminado independiente de la base de datos
Sin habilitación deliberada de persistencia, DemandFlow MUST arrancar por defecto sin Docker, PostgreSQL ni credenciales de BD, sin intentar conexiones ni ejecutar migraciones. MUST conservar el contrato publicado de `application-startup`. Referencias: ADR-002, ADR-007 y `openspec/specs/application-startup/spec.md`.

#### Scenario: Entorno sin infraestructura de persistencia
- **WHEN** se inicia el artefacto sin habilitar persistencia, sin Docker disponible, sin PostgreSQL y sin credenciales configuradas
- **THEN** la aplicación completa el arranque en loopback y permanece disponible hasta su detención, sin intentar conexiones de persistencia ni ejecutar Flyway.

#### Scenario: Configuración de BD presente sin habilitación
- **WHEN** se inicia sin habilitar persistencia aunque existan parámetros externos de conexión y una base local disponible
- **THEN** la aplicación conserva el modo sin BD y no conecta ni aplica migraciones por la mera presencia de esos parámetros.

### Requirement: Verificaciones de integración aisladas y reproducibles
Las pruebas de persistencia MUST ejecutarse contra PostgreSQL real de versión fija, con estado y credenciales de prueba aislados del entorno del desarrollador, mediante Maven Wrapper. MUST informar resultados reproducibles y fallos de prerrequisitos sin declarar pruebas omitidas como exitosas. Referencias: ADR-002, ADR-003, ADR-004 y estrategia de pruebas.

#### Scenario: Ejecuciones independientes
- **WHEN** se ejecutan las pruebas de conexión, migraciones, fallos controlados y arranque sobre entornos PostgreSQL de prueba independientes con los prerrequisitos documentados
- **THEN** producen resultados equivalentes sin depender del orden ni de datos previos, no modifican la carpeta permanente de desarrollo y no requieren secretos reales versionados ni objetos de negocio.

#### Scenario: Regresión del modo predeterminado
- **WHEN** se ejecutan las comprobaciones de `application-startup` mediante Maven Wrapper sin infraestructura ni habilitación de persistencia
- **THEN** siguen verificándose la construcción y el arranque sin BD en loopback, sin exigir PostgreSQL para esas comprobaciones.

#### Scenario: Prerrequisito de integración ausente
- **WHEN** se solicita explícitamente la verificación de persistencia y falta el entorno PostgreSQL de prueba o un prerrequisito documentado
- **THEN** la verificación informa el impedimento y termina sin éxito, sin utilizar silenciosamente la base de desarrollo ni contabilizar los escenarios de integración como aprobados.
