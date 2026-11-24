# Design

## Context

La motivación y los límites están en [proposal.md](proposal.md); el contrato propuesto comprende los 12 requisitos y 21 escenarios de [local-persistence](specs/local-persistence/spec.md). Actualmente `pom.xml` solo incluye arranque web y pruebas; el artefacto escucha en `127.0.0.1` y no necesita BD. `openspec/specs/application-startup/spec.md` conserva íntegramente su vigencia.

**Decisiones ya aprobadas:** monolito modular, Java 21, Spring Boot 4.1.2, Maven Wrapper 3.9.11, PostgreSQL en Compose con Java fuera del contenedor, JPA/Hibernate, Flyway y operación local protegida. Fuentes: `docs/architecture-decisions/ADR-001-architectural-style.md`, `docs/architecture-decisions/ADR-002-java-spring.md`, `docs/architecture-decisions/ADR-003-postgresql-docker.md`, `docs/architecture-decisions/ADR-004-jpa-flyway.md` y `docs/architecture-decisions/ADR-007-security-baseline.md`.

La propuesta cuenta con aprobación para continuar la planificación, según la instrucción de revisión; **las elecciones concretas de este diseño siguen siendo propuestas para revisión humana**, sin autorización de implementación. Conforme a `docs/architecture-decisions/ADR-008-openspec-agents.md`, revisar este documento no aprueba sus decisiones ni permite implementar. Las elecciones siguientes no requieren alterar un ADR ni el contrato publicado: concretan alternativas que las fuentes dejaron abiertas. Si una verificación futura demuestra que alguna exige cambiar esas fuentes, se detendrá esa parte para revisión; no se sustituirá una versión ni se relajará un requisito silenciosamente.

También aplican `docs/architecture/architecture.md`, `docs/architecture/data-model.md`, `docs/engineering/security-standards.md` y `docs/engineering/testing-strategy.md`. Las reglas de `docs/requirements/business-rules.md`, `docs/requirements/functional-requirements.md` y `docs/requirements/traceability-matrix.md` permanecen intactas.

## Goals / Non-Goals

- Separar la habilitación de persistencia de la mera presencia de dependencias o credenciales, usando un solo artefacto ejecutable.
- Verificar cambios técnicos reales y permisos, sin necesitar mapeos ni objetos del dominio.
- Utilizar herramientas de prueba estándar para el aislamiento y reservar una comprobación Compose para la configuración de desarrollo, con recursos independientes y resultados observables.
- No crear entidades, repositorios, tablas de negocio, capas genéricas, funcionalidades ni infraestructura distribuida. No comprobar aquí cálculos, snapshots, unicidad JIRA, solapamientos, concurrencia de entidades ni auditoría funcional: siguen perteneciendo a sus cambios funcionales y a `docs/architecture-decisions/ADR-006-audit-history.md`.

## Decisions

### D1. Perfil optativo y un único artefacto

Se propone el perfil Spring **`persistence`**, activado deliberadamente con `--spring.profiles.active=persistence` o la propiedad externa equivalente. No se establecerá como perfil predeterminado ni se activará por grupos, por presencia de URL, secretos o Docker. La activación corresponde a Spring, no a un perfil de Compose ni de Maven.

**Hallazgo de revisión:** la [documentación etiquetada v4.1.2](https://github.com/spring-projects/spring-boot/blob/v4.1.2/documentation/spring-boot-docs/src/docs/antora/modules/reference/pages/features/external-config.adoc) establece precedencia de propiedades y sustitución de listas. El [selector de autoconfiguraciones v4.1.2](https://github.com/spring-projects/spring-boot/blob/v4.1.2/core/spring-boot-autoconfigure/src/main/java/org/springframework/boot/autoconfigure/AutoConfigurationImportSelector.java) obtiene la propiedad mediante Binder y une su resultado a las exclusiones de anotación. Por tanto, un perfil puede sustituir exclusiones de propiedades, pero no eliminar las declaradas por anotación. La referencia anterior a una «lista vacía» no especificaba su representación ni probaba el resultado efectivo con ConfigData; se elimina esa dependencia del diseño.

Se propone conservar `server.address=127.0.0.1` en el documento común de `application.properties` y declarar en un documento separado por `#---`, activado con **`spring.config.activate.on-profile=!persistence`**, las exclusiones `spring.autoconfigure.exclude` siguientes:

- `org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration`.
- `org.springframework.boot.hibernate.autoconfigure.HibernateJpaAutoConfiguration`.
- `org.springframework.boot.flyway.autoconfigure.FlywayAutoConfiguration`.

En ese mismo documento negativo se deshabilitarán Flyway y la detección de repositorios JPA. Al activar `persistence`, ese documento completo queda inactivo: sus exclusiones no entran en el Environment y no hay una lista que vaciar. `application-persistence.properties` habilitará las integraciones e importará secretos y propiedades de conexión. No habrá exclusiones de esas clases en anotaciones ni en el documento común. Este mecanismo utiliza las expresiones de perfil de ConfigData documentadas para 4.1.2; no necesita filtros, selectores ni procesadores de entorno propios.

Antes de aceptar implementación se comprobarán cuatro contextos cargados mediante **ConfigData real**, no `@TestPropertySource` sobre archivos multidocumento: sin perfil; con un perfil ajeno a persistencia; con `persistence`; y con parámetros de conexión presentes sin habilitación. Se contrastarán `ConditionEvaluationReport`, exclusiones efectivas y ausencia/presencia de datasource, Flyway y EntityManagerFactory. No basta con afirmar que la propiedad se reemplaza ni con simular dos mapas de propiedades. Sin datasource, las autoconfiguraciones JDBC dependientes no deben crear conexiones.

La configuración será base segura para cualquier perfil distinto de `persistence`, no solo para el perfil implícito `default`. No se aceptará como configuración soportada la sobrescritura externa de las exclusiones para eludir la habilitación deliberada. Los parámetros externos de conexión, por sí solos, no cambian el modo. No se añadirá la integración automática Spring Boot Docker Compose, que podría inspeccionar Docker al arrancar.

**Alternativas:** habilitar por URL vuelve implícita la decisión; un perfil Maven produciría artefactos distintos; configurar manualmente toda la infraestructura JPA/Flyway duplica trabajo de Boot. Vaciar una propiedad de lista es innecesario cuando la exclusión puede no declararse para ese modo. El documento negativo conserva un artefacto y hace explícita la condición. Las rutas de autoconfiguración se comprobarán contra los módulos 4.1.2 resueltos, sin copiar paquetes de Boot 3. Si los contextos anteriores no confirman el resultado, se detendrá esa integración para revisión, sin habilitar conexiones por defecto.

### D2. Versiones y compatibilidad documental

Se propone **PostgreSQL 17.12**, imagen oficial **`postgres:17.12-bookworm`**, idéntica en desarrollo y pruebas. El tag fija versión y variante; no se utilizarán `latest`, `17` ni sustitución automática de imagen. La política oficial identifica 17 como soportada hasta noviembre de 2029 y la imagen publica ese tag. [Política de versiones](https://www.postgresql.org/support/versioning/), [imagen oficial](https://hub.docker.com/_/postgres).

Se conserva la gestión de dependencias del parent 4.1.2, sin sobrescribir versiones transitivas:

| Componente | Versión fijada por la plataforma |
|---|---|
| Java / Spring Boot / Maven Wrapper | 21 / 4.1.2 / 3.9.11 |
| Hibernate ORM | 7.4.13.Final |
| Flyway Core y módulo PostgreSQL | 12.4.0 |
| pgJDBC | 42.7.14 |
| HikariCP | 7.0.2 |

Estas versiones se contrastaron con el [BOM oficial 4.1.2](https://repo.maven.apache.org/maven2/org/springframework/boot/spring-boot-dependencies/4.1.2/spring-boot-dependencies-4.1.2.pom), además del BOM disponible en la caché Maven local. No se tomaron las versiones de la documentación móvil de Boot 4.2 o 4.1.3 como si fueran 4.1.2.

Compatibilidad documental: la familia Hibernate 7.4 documenta PostgreSQLDialect desde PostgreSQL 14; Flyway incluye PostgreSQL 17 entre sus motores soportados y exige el módulo específico; pgJDBC documenta Java 8/JDBC 4.2 o superiores y protocolo PostgreSQL compatible con 17. Esto respalda la selección con Java 21, pero no demuestra por sí solo la ejecución de la combinación exacta. [Dialectos Hibernate](https://docs.hibernate.org/orm/7.4/dialect/), [soporte Flyway](https://documentation.red-gate.com/flyway/getting-started-with-flyway/system-requirements/supported-databases-and-versions), [módulo PostgreSQL de Flyway](https://documentation.red-gate.com/flyway/reference/database-driver-reference/postgresql-database), [pgJDBC](https://jdbc.postgresql.org/documentation/).

Dependencias propuestas: `spring-boot-starter-data-jpa`, `spring-boot-starter-flyway`, `org.flywaydb:flyway-database-postgresql` y `org.postgresql:postgresql` en runtime, evitando duplicar lo incluido transitivamente. Antes de autorizar implementación se revisarán dependencias y vulnerabilidades; durante ella, el árbol efectivo, conexión, migración y arranque confirmarán la combinación. Una incompatibilidad con Boot 4.1.2 detiene esa integración para revisión, sin actualizar la plataforma unilateralmente.

**Alternativas:** PostgreSQL 19 acaba de publicarse y no figura en la matriz Flyway consultada; 18 también es viable, pero 17 permite mantener el montaje de datos conocido anterior a 18 y ofrece soporte suficiente. Usar 14 dejaría una versión ya fuera de soporte. No se propone actualizar Hibernate, Flyway o JDBC separadamente del BOM.

### D3. Compose y disponibilidad

Se propone `compose.yaml` con un único servicio **`postgres`**, base **`demandflow`**, red del proyecto Compose y volumen nombrado **`postgres-data`**, montado en **`/var/lib/postgresql/data`** para la imagen 17. No habrá `container_name`, nombres globales de volumen ni red externa. El nombre de proyecto de desarrollo será `demandflow-local`; el volumen efectivo queda asociado a ese proyecto y no a un contenedor desechable.

La publicación será **`127.0.0.1:${DEMANDFLOW_DB_PORT:-5432}:5432`**. Solo el puerto del anfitrión podrá variar para resolver colisiones; la dirección de publicación permanece fija en loopback. No se habilitarán red del anfitrión ni publicaciones IPv6 generales. La URL Java apunta a `127.0.0.1`, puerto elegido y base `demandflow`; el nombre de servicio Compose no se usa como hostname desde el IDE.

Se utilizará autenticación de red SCRAM mediante `POSTGRES_HOST_AUTH_METHOD=scram-sha-256`; queda prohibido `trust` para conexiones de red. El proceso administrativo de inicialización se ejecuta dentro del contenedor y no constituye la cuenta de la aplicación.

Healthcheck: `pg_isready` contra TCP `127.0.0.1:5432` y base `demandflow`, intervalo 5 s, timeout 3 s, período inicial 10 s y 12 reintentos. Usar TCP evita considerar disponible el servidor temporal de inicialización que solo escucha por socket. `docker compose up --wait --wait-timeout 120` esperará disponibilidad; el servicio no se reiniciará automáticamente en bucle ante un bootstrap fallido. Java se inicia después desde el IDE y conserva su propia comprobación de conexión. `pg_isready` solo prueba disponibilidad del servidor, no contraseñas, permisos ni migraciones. [Disponibilidad en Compose](https://docs.docker.com/compose/how-tos/startup-order/), [límite de espera](https://docs.docker.com/reference/cli/docker/compose/up).

**Alternativas:** un bind mount del directorio de datos depende más del anfitrión Windows; el volumen nombrado evita esa dependencia. Contenerizar Java o añadir `depends_on` para él contradice la topología inicial; un healthcheck no sustituye la conexión efectiva de la aplicación.

### D4. Secretos y separación de privilegios

Se propone un directorio local **`.secrets/`**, excluido expresamente de Git, con permisos del sistema de archivos restringidos al operador. Su ruta podrá cambiar mediante `DEMANDFLOW_SECRETS_DIR`, sin versionar su contenido. Se generarán valores diferentes y no se distribuirán contraseñas por defecto.

- `bootstrap/postgres.password`: contraseña administrativa usada por la imagen mediante `POSTGRES_PASSWORD_FILE` y un secret Compose.
- `spring/spring.flyway.password`: contraseña de `demandflow_migrator`.
- `spring/spring.datasource.password`: contraseña de `demandflow_app`.

El flujo de suministro queda definido sin asumir soporte automático de sufijos `_FILE` en Java o en los roles personalizados:

| Consumidor | Archivo anfitrión relativo a `DEMANDFLOW_SECRETS_DIR` | Entrada y uso |
|---|---|---|
| Imagen oficial / administrador | `bootstrap/postgres.password` | Secret Compose `postgres_admin_password`, montado en `/run/secrets/postgres_admin_password`; `POSTGRES_PASSWORD_FILE` apunta a esa ruta. `POSTGRES_USER=postgres` y `POSTGRES_DB=demandflow` son identificadores no secretos. No se establece a la vez `POSTGRES_PASSWORD`. |
| Bootstrap / migrador | `spring/spring.flyway.password` | Secret Compose `postgres_migrator_password`, montado en `/run/secrets/postgres_migrator_password`; el script de bootstrap lo lee explícitamente para asignar contraseña a `demandflow_migrator`. |
| Bootstrap / runtime | `spring/spring.datasource.password` | Secret Compose `postgres_runtime_password`, montado en `/run/secrets/postgres_runtime_password`; el script lo lee explícitamente para asignar contraseña a `demandflow_app`. |
| Flyway en Java | `spring/spring.flyway.password` | Importación ConfigData del directorio `spring/`; el nombre de archivo se enlaza a `spring.flyway.password`. En el perfil se fija `spring.flyway.user=demandflow_migrator` y la URL PostgreSQL sin credenciales. |
| Datasource en Java | `spring/spring.datasource.password` | La misma importación enlaza `spring.datasource.password`. En el perfil se fija `spring.datasource.username=demandflow_app` y su URL sin credenciales. |

Compose monta archivos de solo lectura; Java desde el IDE lee los archivos del anfitrión, no las rutas `/run/secrets/` del contenedor. En `application-persistence.properties` se propone **`spring.config.import=configtree:${DEMANDFLOW_SECRETS_DIR:./.secrets}/spring/`**, sin `optional:`, conservando la barra final y documentando la ruta relativa al directorio de trabajo. El archivo ausente/vacío, URL incompleta o cualquier contraseña/usuario obligatorio ausente impide construir las conexiones: no se permite que Flyway caiga silenciosamente en el datasource runtime. El perfil validará ambos juegos de propiedades antes de inicializar datasources, sin listener global de arranque.

El IDE recibirá explícitamente la ruta y el puerto; no se presupone que lea `.env`. Fuera de `persistence` no se importa ese directorio. No se pasará el directorio padre `bootstrap/` a Java, no se copiarán secretos a recursos empaquetados y no se utilizarán contraseñas en argumentos de JVM, `psql --set`, URL ni configuración Compose versionada. Los ejemplos solo contienen rutas y marcadores. Las propiedades externas de mayor precedencia siguen las reglas de Spring: cualquier sobrescritura en pruebas será explícita y no se heredará del desarrollador. [Secrets Compose](https://docs.docker.com/compose/how-tos/use-secrets/), [ConfigData/configtree en v4.1.2](https://github.com/spring-projects/spring-boot/blob/v4.1.2/documentation/spring-boot-docs/src/docs/antora/modules/reference/pages/features/external-config.adoc).

La imagen crea la base y el administrador; un único script ejecutable, no interactivo, montado en `/docker-entrypoint-initdb.d/` aprovisiona roles e `infra` solo con volumen vacío. El soporte `_FILE` de la imagen se limita al administrador: no crea automáticamente `demandflow_migrator` ni `demandflow_app`. El script comprueba legibilidad y contenido de los dos archivos y los lee sin imprimirlos. Para la sesión `psql` se usarán variables de entorno efímeras del proceso hijo, cargadas desde esos archivos; `\getenv` las incorpora a variables psql y la interpolación `:'variable'` las trata como literales SQL escapados. No se interpolará contenido del secreto mediante shell, concatenación SQL o argumentos visibles; las variables se descartan al terminar. [Imagen oficial](https://hub.docker.com/_/postgres), [variables e interpolación psql 17](https://www.postgresql.org/docs/17/app-psql.html).

El script usará `psql -X`, `ON_ERROR_STOP`, `ECHO=none`, sin opciones `-a`, `-b`, `-e` ni `set -x`; tampoco archivos temporales con SQL ya expandido. Durante la asignación de contraseñas se deshabilita el log de sentencias y de sentencias fallidas en esa sesión administrativa (`log_statement=none`, `log_min_error_statement=panic`), sin contexto SQL detallado en errores (`log_error_verbosity=terse`, `SHOW_CONTEXT=never`). Los identificadores de roles son fijos. En la invocación que asigna contraseñas, stdout y stderr crudos se descartan, sin guardarlos en un archivo; el script conserva el código de salida y emite únicamente una etapa y un mensaje fijo. `ECHO=none` por sí solo no protege una sentencia que el servidor incluya en un error. Se comprobará la ausencia de filtraciones con secretos sintéticos reconocibles, incluidos errores de bootstrap, autenticación runtime y autenticación Flyway. La configuración normal de logs se conserva fuera de esa sesión de provisión.

Esta transferencia utiliza brevemente memoria/entorno de procesos locales de confianza; no promete ocultación al administrador del anfitrión o del contenedor. La propia imagen oficial también carga el secreto administrativo en su entorno interno. No se persisten contraseñas en `Config.Env` de Docker ni se imprimen volcados de entorno. El bootstrap es administración inicial, no evolución del esquema; editar un archivo secreto no rota las contraseñas de un volumen existente. La rotación exige una operación administrativa separada y sincronizar ambos consumidores, sin reinicializar el volumen.

| Identidad | Privilegios propuestos |
|---|---|
| Administrador de bootstrap | Aprovisiona roles y base; no se configura en Java. Propietario administrativo de la base. |
| `demandflow_migrator` | LOGIN, CONNECT y CREATE solo en la base `demandflow`; propietario del esquema técnico `infra` y de los objetos que cree. Sin SUPERUSER, CREATEDB, CREATEROLE, REPLICATION ni BYPASSRLS. |
| `demandflow_app` | LOGIN, CONNECT y posteriormente USAGE del esquema `demandflow`; sin CREATE de base/esquema, sin TEMP, sin pertenencia al rol migrador ni privilegios sobre el historial. Sin atributos administrativos. |

El bootstrap revocará CONNECT/TEMP de PUBLIC en la base y CREATE de PUBLIC en el esquema `public`, concederá CONNECT a los dos roles y creará `infra` propiedad del migrador. No otorgará permisos DML globales o privilegios por defecto sobre futuras tablas. Tales permisos se diseñarán con cada funcionalidad. El permiso CREATE del migrador está limitado a esta base y es necesario para crear el namespace de V1; no autoriza crear otras bases. [Permiso para crear esquemas](https://www.postgresql.org/docs/17/sql-createschema.html).

**Límite:** el proceso Java importa ambas contraseñas y no se garantiza su borrado de memoria después de migrar; la separación protege la cuenta/datasource de ejecución, no aísla ambos roles en procesos distintos. Un migrador externo sería más fuerte, pero añadiría coordinación operativa innecesaria aquí. Los secrets Compose son archivos locales montados, no una bóveda cifrada; permisos del anfitrión y exclusión de Git son necesarios.

### D5. Inicialización JPA/Flyway y diagnósticos

La configuración persistente usará un datasource Hikari con `demandflow_app`; Flyway tendrá conexión propia a la misma base con `demandflow_migrator`. Se configurarán explícitamente `spring.jpa.hibernate.ddl-auto=validate`, `spring.jpa.open-in-view=false`, SQL logging desactivado y `spring.sql.init.mode=never`. No coexistirán `schema.sql`, `data.sql` ni inicializaciones JPA paralelas.

Flyway utilizará `classpath:db/migration`, `defaultSchema=infra`, `schemas=infra`, `createSchemas=false`, `validateOnMigrate=true`, `baselineOnMigrate=false`, `outOfOrder=false`, `cleanDisabled=true` y fallo ante ubicación de migraciones ausente. No habrá reparación, baseline ni limpieza automáticos. `infra` ya existe por bootstrap y contiene `flyway_schema_history`, administrada exclusivamente por Flyway. La autoconfiguración hará depender la inicialización JPA de la migración; se comprobará esa secuencia en integración.

Hibernate usará `demandflow` como esquema predeterminado y detección de dialecto por metadata. Se inicializará una unidad de persistencia sin entidades y se comprobará que el metamodelo esté vacío. Esto verifica configuración y conexión, **no** correspondencia de tablas/columnas, índices, exclusiones o reglas económicas inexistentes. Los permisos del runtime impiden DDL y el inventario antes/después permite detectar generación automática accidental. [Integración SQL de Boot](https://docs.spring.io/spring-boot/4.1/reference/data/sql.html).

Un fallo detendrá el arranque con código distinto de cero; nunca se reintentará arrancar sin BD. Se emitirá una categoría segura: configuración incompleta, conexión rechazada/no disponible, validación de migraciones, ejecución de migración o límite de espera. El reporte de fallos y los loggers de infraestructura se configurarán para no imprimir contraseñas, propiedades de secretos, URL completas ni excepciones sin sanear. No se habilitará debug de configuración en el flujo entregado. Se probarán los diagnósticos reales con contraseñas de prueba reconocibles; no basta con sanear el mensaje propio si un logger del framework ya filtró datos.

**Alternativas:** `ddl-auto=update` o inicialización SQL alternativa romperían la propiedad de Flyway; usar una sola cuenta con DDL ampliaría privilegios; agregar una entidad de prueba al runtime falsearía el alcance aprobado.

### D6. Migración inicial útil sin tablas

Se propone **`V1__initialize_application_namespace.sql`**: crear el esquema vacío **`demandflow`**, propiedad de `demandflow_migrator`; revocar permisos de PUBLIC en ese esquema y conceder únicamente USAGE a `demandflow_app`. Es una frontera real de nombres y permisos para la aplicación, no un objeto de dominio ni una tabla de demostración. La operación queda en una transacción PostgreSQL controlada por Flyway y no incluye contraseñas ni creación de roles.

Evidencias: existencia y propietario en `pg_namespace`, USAGE verdadero y CREATE falso para runtime, ausencia de privilegios sobre `infra`, ausencia de tablas de negocio y fila V1 exitosa con checksum en `infra.flyway_schema_history`. El historial se consulta con migrador/administrador durante las pruebas, no concediendo SELECT al runtime. En un segundo arranque se compararán versión, checksum, orden y fecha de instalación para demostrar que no se reaplicó.

V1 no utilizará `IF NOT EXISTS` para ocultar una base preparada por fuera del proceso; ante namespace preexistente inesperado se detendrá y revisará. Flyway solo gestiona su historial automáticamente; no se escriben filas manuales. Las correcciones posteriores serán V2 y siguientes. No se añaden migraciones repetibles `R__`: repetición segura significa volver a ejecutar la cadena versionada sin reaplicar versiones exitosas.

**Alternativas:** `SELECT 1` no deja un cambio técnico útil; una tabla de ping o de ejemplo sería ficticia; dejar todo al bootstrap no verifica evolución mediante Flyway. El namespace y su ACL resuelven necesidades técnicas reales sin anticipar tablas, columnas, tipos ni restricciones funcionales.

### D7. Pruebas estándar aisladas y comprobación Compose acotada

Se propone JUnit Jupiter 6/Spring Boot Test, Surefire para pruebas por defecto y Failsafe dentro del perfil **Maven `persistence-it`**, independiente del perfil Spring `persistence`. `mvnw.cmd test` y `mvnw.cmd package` conservarán construcción y regresión sin Docker; `mvnw.cmd -Ppersistence-it verify` solicitará explícitamente la suite PostgreSQL y fallará si falta Docker, Compose, imagen o algún prerrequisito. Ningún escenario se marcará aprobado por haber sido omitido.

**Simplificación propuesta:** Testcontainers **2.0.5**, gestionado por el BOM 4.1.2 y limitado a dependencias de prueba, se encargará del arranque, espera, puertos, identidad y limpieza de contenedores para las pruebas PostgreSQL. Se usará su `GenericContainer` estándar con `postgres:17.12-bookworm`, para conservar `POSTGRES_PASSWORD_FILE`, los mismos archivos de secretos y el mismo script de bootstrap de D4, sin los usuarios por defecto ni configuraciones adicionales del módulo JDBC. Los archivos sintéticos se copian a las mismas rutas del contenedor antes de arrancarlo; no se versionan ni se empaquetan en el JAR. Ciclo de vida estándar con cierre garantizado, sin extensión JUnit propia, descubridor de recursos ni administrador de proyectos Compose por escenario. No se habilita reutilización entre ejecuciones.

La configuración de contenedor fijará publicación `127.0.0.1:0` para 5432 mediante el modificador de creación soportado por Testcontainers; no se aceptará la publicación genérica en todas las interfaces. Se comprobará el binding efectivo. El host de Docker debe ser local; no se sustituirá por un daemon remoto que eluda loopback. La espera de puerto TCP y una conexión SQL válida quedan a cargo de las utilidades estándar y de las aserciones, sin implementar un health monitor propio. Se fijan un intento de arranque (`withStartupAttempts(1)`) y espera máxima de 120 s; los reintentos de la biblioteca no deben multiplicar silenciosamente ese presupuesto. [Ciclo de vida y esperas](https://java.testcontainers.org/features/startup_and_waits/), [red y puertos](https://java.testcontainers.org/features/networking/).

Las pruebas de propiedades, fallos del contexto y catálogo usan Spring Boot Test y contenedores nuevos; el arranque real y el código de salida conservan un pequeño conjunto de pruebas del JAR con `ProcessBuilder` y `waitFor` con límite, sin framework propio de procesos. Argumentos separados, secretos sintéticos bajo directorios temporales de JUnit y entorno de Java saneado para no heredar URL, perfiles ni credenciales del usuario. No se introducen SQL o modelos de negocio para verificar conexiones.

**Una sola comprobación Compose** reutilizará el `compose.yaml` entregado para cubrir versión, secrets/bootstrap, binding y volumen: proyecto único `demandflow-it-<identificador>`, `DEMANDFLOW_DB_PORT=0` y directorio de secretos temporal. Se descubre el puerto publicado; se aplica V1, se recrea el contenedor conservando el volumen y se comparan namespace, ACL e historial. En esa misma prueba se elimina finalmente solo el volumen del proyecto temporal, comprobando identidad antes de hacerlo. No se crea una capa general para administrar proyectos o simular Docker; esta comprobación complementa Testcontainers porque la persistencia del volumen y la configuración efectiva de Compose son parte del contrato.

Las secuencias V1/segundo arranque/V2 son pasos explícitos de un caso aislado, no dependencias entre métodos; dos ejecuciones limpias comprueban reproducibilidad. La falta de prerrequisitos se verifica solicitando la suite en una ejecución separada con un endpoint Docker deliberadamente inaccesible, sin detener servicios del usuario. El resultado debe fallar, sin `disabledWithoutDocker`, assumptions ni skips contabilizados como éxito.

Fixtures negativas: V1 alterada se copia a una ubicación temporal; V2 válida solo de prueba cambia un comentario técnico del namespace; V2 fallida introduce un error SQL después de una operación transaccional sobre ese comentario. No se modifica la migración productiva ni se empaquetan fixtures en el artefacto entregable. Se verificará que la versión fallida no esté registrada como exitosa y que su operación transaccional no quede confirmada. No se presupone rollback de migraciones no transaccionales futuras.

Los arranques positivos/negativos del JAR se ejecutarán como procesos Java independientes, con comprobación de disponibilidad, interfaz, salida y plazo de terminación. Las comprobaciones de contexto verificarán presencia/ausencia de datasource, Flyway y EntityManagerFactory; no dependerán exclusivamente de una línea de log. El caso predeterminado con parámetros de conexión presentes comprobará además que no se crea conexión ni se toca el historial. El caso sin Docker se verificará en un entorno sin daemon accesible, sin modificar servicios del equipo.

**Alternativas:** orquestar todo con Compose por clase requería mantener creación, descubrimiento, limpieza y control de errores propios; se sustituye por una biblioteca de pruebas gestionada y una sola prueba Compose. Testcontainers por sí solo no cubre la configuración Compose ni la recreación de su volumen; por eso esa comprobación permanece. H2 o una base compartida reducen infraestructura a costa de perder garantías exigidas y no se adoptan. La dependencia adicional es solo de pruebas; no introduce un componente del runtime de DemandFlow.

### D8. Presupuestos de espera y terminación

| Operación | Límite propuesto y efecto |
|---|---|
| Disponibilidad de PostgreSQL de prueba / Compose | 120 s mediante espera Testcontainers o `--wait-timeout`; error no se interpreta como disponibilidad. |
| Descarga de imagen en verificación | 300 s por proceso de descarga; falta de red/imagen falla como prerrequisito, antes del presupuesto de arranque. |
| JDBC | `connectTimeout=5`, `loginTimeout=5`, `socketTimeout=15` y `cancelSignalTimeout=2`, en segundos, en ambas conexiones. |
| Hikari | `connectionTimeout=5000`, `validationTimeout=2000`, `initializationFailTimeout=1`, en ms. El valor positivo exige inicialización fallida con excepción; no significa que el arranque total falle en 1 ms. |
| Sesiones PostgreSQL de ambos roles | `statement_timeout=15s`, `lock_timeout=5s`, fijados en el bootstrap; aplican también a conexiones Flyway. |
| Flyway | `connectRetries=0`, `lockRetryCount=3`; conflicto no se espera indefinidamente. |
| Aceptación del arranque en pruebas | 75 s desde lanzar Java para observar disponibilidad o terminación espontánea con código no cero en los fallos definidos. No es un timeout global implementado en el runtime. |
| Detención de procesos de prueba | 10 s de cierre ordinario, después terminación forzada y fallo si no se libera. |

**Se retiran el guardián, el listener temporal y la promesa de un plazo global interno de 60 s.** Para el alcance actual bastan operaciones finitas y mecanismos nativos: una URL local con un solo host, conexión/login/lectura acotados tanto para Flyway como para Hikari, reintentos Flyway finitos y SQL/bloqueos limitados por PostgreSQL. La validación de propiedades pertenece a la configuración del perfil; no necesita observar todo el ciclo de arranque. Los errores nativos deben propagarse a Spring Boot, cerrar el contexto fallido y producir salida no cero, sin segundo arranque ni continuación sin BD. [Parámetros pgJDBC](https://jdbc.postgresql.org/documentation/use/), [semántica Hikari 7.0.2](https://github.com/brettwooldridge/HikariCP/blob/HikariCP-7.0.2/README.md), [reintentos de bloqueo Flyway](https://documentation.red-gate.com/flyway/reference/configuration/flyway-namespace/flyway-lock-retry-count-setting).

Las opciones JDBC se proporcionarán explícitamente a **ambas** conexiones mediante la URL local sin usuario/contraseña o propiedades de su driver. Los ajustes `spring.datasource.hikari.*` solo afectan al pool runtime: no limitan el datasource independiente de Flyway. `connectionTimeout` limita adquirir del pool y no sustituye `connectTimeout`/`loginTimeout` del driver. `statement_timeout`/`lock_timeout` se asignan a los dos roles en el bootstrap; la espera del bloqueo Flyway también mantiene contador finito. Ni estas cifras ni su suma informal constituyen una garantía de tiempo total para toda la JVM.

La prueba de proceso distingue **fallo controlado del producto** de **aborto del comprobador**: solo aprueba si Java termina por sí mismo con código no cero y diagnóstico saneado antes de 75 s. Si el comprobador tiene que matar el proceso, el escenario falla, aunque la limpieza termine correctamente. Así se verifica el requisito de salida acotada sin simular cumplimiento con el timeout de JUnit. Los tests de contexto pueden tener `@Timeout` como protección de la suite, pero no sustituyen esta aserción sobre un proceso independiente.

**Límite y condición de revisión:** estos mecanismos cubren los fallos de conexión/configuración/migración definidos, no bloqueos arbitrarios del sistema de archivos, de la JVM ni caídas posteriores al arranque. Si alguno de los casos exigidos no finaliza espontáneamente dentro del límite de aceptación, la integración queda bloqueada: primero se corrige el timeout/reintento nativo que falta y se repite el caso. Solo una evidencia reproducible de que eso es insuficiente justificaría proponer supervisión adicional para aprobación humana. No se añadirá preventivamente un guardián ni se ampliarán plazos para ocultar el fallo.

### D9. Cobertura de los 12 requisitos y los 21 escenarios

La numeración siguiente sigue el orden exacto de la especificación; no añade requisitos. Cada fila remite a un escenario existente.

| Requisito / escenario | Evidencia prevista | Decisiones |
|---|---|---|
| R1 / Inicio reproducible del motor | Versión de servidor y tag inspeccionados; Compose saludable. | D2-D3 |
| R1 / Conexión desde el proceso Java local | JAR fuera de Docker obtiene conexión runtime. | D3-D5 |
| R2 / Recreación sin pérdida del estado técnico | UUID/nombre de volumen, namespace, ACL e historial idénticos tras recrear el contenedor y reiniciar Java. | D3, D6-D7 |
| R3 / Interfaces efectivamente publicadas | Inspección Docker y sockets del anfitrión: solo loopback para BD/Java; publicación externa ausente. | D3, D7 |
| R4 / Configuración externa utilizable | Conexión con secrets externos; consulta de atributos de roles y denegación de DDL al runtime. | D4-D5 |
| R4 / Revisión de archivos y diagnósticos | Revisar archivos entregables, configuración efectiva saneada y salidas de casos positivos/negativos buscando secretos de prueba. | D4-D5, D7 |
| R5 / Inicialización de la integración técnica | Plataforma efectiva, contexto con datasource/Flyway/JPA, metamodelo vacío y conexión real. | D1-D2, D5 |
| R6 / Arranque sin generación automática de tablas | `validate` efectivo, metamodelo vacío e inventario sin tablas nuevas fuera del historial. | D5-D6 |
| R7 / Aplicación sobre base de prueba vacía | V1, namespace, propietario, permisos e historial exitoso en volumen nuevo. | D4, D6-D7 |
| R8 / Segundo arranque sin cambios de migración | Historial y timestamps sin reaplicación; ACL conservadas. | D6-D7 |
| R8 / Nueva versión técnica | Fixture V2 pendiente aplicada una vez preservando V1. | D7 |
| R8 / Alteración de una migración aplicada | Checksum divergente produce salida no cero, sin reparación ni disponibilidad. | D5, D7-D8 |
| R9 / Arranque con infraestructura válida | Evento de disponibilidad solo tras Flyway/JPA, proceso vivo y escucha local. | D1, D5, D7-D8 |
| R10 / Base no disponible o acceso rechazado | Puerto inaccesible, contraseña runtime errónea y contraseña migrador errónea por separado; salida acotada y saneada. | D4-D5, D7-D8 |
| R10 / Falta de configuración requerida | Archivos ausentes/vacíos o propiedad requerida incompleta; fallo antes de inicializar conexiones. | D4-D5, D8 |
| R10 / Error al ejecutar una migración | Fixture SQL inválida: salida no cero, ningún éxito falso en historial y rollback de su operación transaccional. | D6-D8 |
| R11 / Entorno sin infraestructura de persistencia | JAR predeterminado disponible sin acceso a Docker/BD, sin secretos ni beans de persistencia. | D1, D7 |
| R11 / Configuración de BD presente sin habilitación | Parámetros externos presentes, sin conexiones ni cambios de historial, arranque local disponible. | D1, D7 |
| R12 / Ejecuciones independientes | Dos ejecuciones limpias con contenedores Testcontainers propios y prueba Compose temporal, resultados equivalentes y cero escrituras en desarrollo. | D3-D4, D7 |
| R12 / Regresión del modo predeterminado | Escenarios completos de `application-startup`, incluido empaquetado Wrapper, sin PostgreSQL. | D1-D2, D7 |
| R12 / Prerrequisito de integración ausente | Solicitud explícita de suite sin Docker/Compose/imagen da salida no cero y no marca escenarios aprobados. | D7-D8 |

## Risks / Trade-offs

- **Compatibilidad documental no ejecutada:** BOM y matrices respaldan versiones; falta resolver dependencias adicionales, ejecutar la combinación exacta y revisar vulnerabilidades. Un fallo bloquea esa integración para revisión, sin sustituir la plataforma.
- **Activación accidental por autoconfiguración o propiedades externas:** exclusiones base y pruebas de contexto/proceso en ambos modos; el mero secreto o URL no habilita persistencia.
- **Complejidad de soporte de pruebas:** Testcontainers gestiona contenedores y una única prueba Compose verifica el contrato operativo; no se crea un framework propio. Se comprobará el binding local también en las pruebas y la compatibilidad del soporte de pruebas gestionado antes de aceptar la implementación.
- **Bootstrap incompleto o credenciales desincronizadas:** inicialización solo en volumen vacío, validación explícita y rotación administrativa separada; no borrar ni reinicializar automáticamente un volumen para corregir autenticación.
- **Permisos de migración disponibles durante el arranque Java:** conexión runtime separada y sin DDL; se acepta el límite del mismo proceso local de confianza, sin atribuir aislamiento que no existe.
- **Tiempo agotado después de un commit:** tras una interrupción se consulta historial y catálogo antes de reintentar; código no cero no garantiza ausencia de cambios ya confirmados. No hay rollback global de toda la cadena.
- **Publicación local y SCRAM no equivalen a autenticación funcional ni TLS:** no se autoriza acceso remoto, entorno multiusuario no confiable ni producción. El administrador del anfitrión y Docker conserva acceso a archivos/datos.
- **Falsos positivos de validación:** healthcheck no prueba autenticación y Hibernate sin entidades no valida tablas futuras. Se comprobarán ACL/catálogos directamente y se documentará el límite.
- **Fijar tag no congela todos los bytes de la imagen:** registrar el digest resuelto en la evidencia de ejecución y revisar cambios de imagen; no inventar un digest no consultado. No se cambia automáticamente la versión fijada.
- **Datos persistentes no son respaldo:** evitar eliminación de volúmenes de desarrollo; copia y restauración comprobadas antes de datos relevantes.

## Migration Plan

Este es un orden de despliegue y recuperación propuesto, no una lista de tareas autorizadas ni una ejecución en esta etapa.

1. Tras aprobación humana del cambio y autorización independiente, conservar la plataforma, añadir dependencias/configuración técnica y comprobar primero la regresión sin BD. No introducir tablas ni funcionalidades.
2. Preparar secretos locales protegidos y el proyecto Compose; comprobar puerto libre y volumen objetivo. Inicializar el volumen vacío, roles e `infra`; esperar disponibilidad. No reutilizar volúmenes ajenos sin inspección previa.
3. Habilitar deliberadamente `persistence`; aplicar V1 con migrador, inicializar Hibernate en validación y confirmar disponibilidad local. Verificar namespace, permisos e historial; repetir arranque y recrear contenedor conservando el volumen. Ejecutar la suite aislada y registrar resultados.
4. Si falla bootstrap en un volumen nuevo desechable de prueba, eliminar solo ese proyecto identificado y repetir desde cero. En desarrollo con datos, detenerse y diagnosticar con respaldo previo; no borrar el volumen ni ejecutar `clean`, `repair` o baseline para silenciar errores.
5. Si falla V1 transaccional, confirmar el estado real e historial antes del reintento. Si una migración ya se confirmó y necesita corrección, generar una nueva versión; no modificar V1 aplicada. Detener Java antes de operaciones administrativas.
6. Para retirar temporalmente la integración, iniciar el mismo artefacto sin `persistence` y detener Compose conservando el volumen. Esto no revierte cambios de esquema. La reversión de código seguirá revisión normal de Git; ninguna operación Git se ejecuta como parte de este diseño.
7. Antes de datos relevantes, obtener respaldo lógico con cliente PostgreSQL de la misma versión, preservar el aprovisionamiento de roles sin exponer secretos y probar restauración en otro proyecto/volumen aislado: migraciones, ACL y arranque deben ser coherentes. Ante corrupción o cambio de versión mayor, restaurar/migrar sobre un volumen nuevo; no montar datos incompatibles ni sobreescribir el volumen original.

**Estado de revisión técnica:** se contrastaron D1 con documentación y selector de autoconfiguración etiquetados v4.1.2, D4 con imagen oficial/psql/configtree, D7 con utilidades estándar de pruebas y D8 con los límites de driver, pool y migrador. Se mantienen los 12 requisitos, 21 escenarios y PostgreSQL 17.12 propuesto. La aprobación de la propuesta permite continuar planificación, pero el mecanismo de perfil condicional, el suministro concreto de secretos, la dependencia Testcontainers solo de pruebas y la estrategia sin guardián siguen pendientes de aprobación humana como decisiones de diseño. No se han añadido dependencias, ejecutado pruebas de integración, descargado imágenes, levantado contenedores ni aplicado migraciones. No se detectó necesidad de modificar un ADR o la especificación publicada; una evidencia contraria detendrá esa parte para revisión.
