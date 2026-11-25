# Evidencias de setup-database

**Estado vigente tras el ajuste bind — 2026-11-24:** 26/26 tareas completadas. El estándar permanente autorizado es `D:\DOCKER_DATA\POSTGRES_DATA`, configurable con `DEMANDFLOW_DATA_DIR`; Compose usa bind mount con `create_host_path: false`. Dos ejecuciones independientes de la única clase afectada, ComposePersistenceIT, pasaron con carpetas temporales (1 prueba por ejecución, cero fallos/errores/omitidas). Se reutilizan las pruebas anteriores no afectadas, sin repetir suites completas ni análisis de vulnerabilidades. No se tocó D:\DOCKER_DATA ni se creó desarrollo. Resultados y límites del ajuste al final; los apartados anteriores son históricos.

**Estado previo al ajuste bind — 2026-11-24:** 26/26 tareas completadas y resultado entregado a revisión humana; sin commit, push, merge ni archive. D2 actualizada a `postgres:17.12-trixie` con aprobación y aceptación humana expresa del riesgo residual solo para el MVP local sin Internet. Dos suites limpias equivalentes: 10 pruebas predeterminadas + 9 IT cada una, cero fallos/errores/omitidas; caso negativo Docker con fallo esperado y regresión/JAR sin infraestructura satisfactorios. PostgreSQL permanente y credenciales de desarrollo no creados. Véanse al final la matriz S01–S21, los límites y la observación del cambio de ID de bridge. Las secciones anteriores describen la evolución histórica y no sustituyen este estado final.

## Autorización y estado inicial — tarea 1.1

- Rama verificada antes de editar: `feature/setup-database`.
- `git status --short` sin salida: árbol inicial limpio.
- Aprobación documental D1-D9: commit `a910fa3bffb5d1f4f8ca1f97d4a71c48e7c4ede8`, `docs(openspec): aprobar planificación y diseño de setup-database`.
- Autorización independiente: instrucción expresa del usuario en esta conversación: «Se autoriza expresamente la implementación del cambio OpenSpec setup-database de DemandFlow, conforme a ADR-008». Comprende las 26 tareas y D1-D9; prohíbe cambios de decisiones aprobadas, operaciones de cierre y alteraciones a recursos Docker ajenos.
- Revisados propuesta, especificación local-persistence, diseño, tareas, application-startup, ADR-001/002/003/004/007/008 y estándares de ingeniería aplicables.
- OpenSpec: esquema `spec-driven`, implementación habilitada, 26 tareas pendientes antes de este registro. No se ha ejecutado commit, push, merge ni archive.

## Resultados técnicos

La autorización no demuestra compatibilidad, seguridad ni satisfacción de los 12 requisitos y 21 escenarios.

### Comprobación inicial de Java/Maven — tarea 1.2

- Java: Eclipse Temurin OpenJDK 21.0.13+9 LTS, Windows 11 amd64; `JAVA_HOME` apunta al JDK 21 instalado.
- `mvnw.cmd --version`: Apache Maven 3.9.11. La ejecución inicial dentro del sandbox no pudo escribir en la caché; se repitió con ejecución autorizada fuera del sandbox.
- SHA256 de Maven Wrapper JAR: `4e2fbf6554bc8a4702cdfdd3bef464f423393d784ddbb037216320ce55d5e4e1`, coincidente con la configuración.
- SHA256 de la distribución Maven 3.9.11 en caché: `0d7125e8c91097b36edb990ea5934e6c68b4440eef4ea96510a0f6815e7eeadb`, coincidente con la configuración.
- `mvnw.cmd test`: satisfactorio, 1 prueba, 0 fallos, 0 errores, 0 omitidas.
- Primer empaquetado y repetición diagnóstica: fallidos antes de modificar código por `NoClassDefFoundError: org/codehaus/plexus/interpolation/InterpolationException` en maven-jar-plugin 3.5.1. Causa identificada: POM local `org.apache.maven.shared:maven-shared-components:45` vacío, que invalidaba las dependencias transitivas de Maven Archiver.
- Reparación autorizada de caché: reposición exclusiva del POM vacío desde Maven Central por HTTPS, verificando coordenadas y versión 45 heredada del padre, y checksum SHA1 oficial publicado. SHA512 no estaba disponible (HTTP 404); ningún intento fallido modificó el POM local. No se cambiaron versiones ni archivos del proyecto para reparar este problema.
- Nuevo `mvnw.cmd package` tras reparación: satisfactorio; 1 prueba, 0 fallos, 0 errores, 0 omitidas. Artefacto ejecutable generado. Resumen local del comando en `target/setup-database-baseline-package.log`, excluido de Git.

### Entorno Docker — tarea 1.3

- Consultas de solo lectura ejecutadas fuera del sandbox tras denegación inicial de acceso a configuración/named pipe dentro de él.
- Docker Desktop 4.99.0, Engine 29.9.3, API 1.56; servidor `linux/amd64`, almacenamiento `overlayfs`; contexto local `desktop-linux`.
- Docker Compose v5.7.0; ayuda confirma `up --wait` y `--wait-timeout`.
- No se ha iniciado, detenido, eliminado ni modificado ningún recurso Docker. Inventario inicial consultado para comparación posterior; pendiente registrar comprobación de puerto antes de iniciar DemandFlow.

### Compatibilidad documental — tarea 2.1

- Consultados por HTTPS los POM oficiales de Maven Central de Boot 4.1.2, Hibernate 7.4.13.Final, Flyway Core y módulo PostgreSQL 12.4.0, pgJDBC 42.7.14, HikariCP 7.0.2 y Testcontainers 2.0.5: disponibles y con artifactId correcto.
- El BOM oficial 4.1.2 confirma las cinco versiones gestionadas indicadas en D2/D7. Metadata local de consulta en `target/setup-database-metadata/` (no versionada).
- Compatibilidad documental: PostgreSQL 17 supera el mínimo PostgreSQL 14 del dialecto Hibernate 7.4; la matriz Flyway incluye PostgreSQL 17; pgJDBC documenta JDBC 4.2/Java 8 y posteriores. La imagen oficial publica `17.12-bookworm`.
- Fuentes: [Hibernate](https://docs.hibernate.org/orm/7.4/dialect/), [Flyway](https://documentation.red-gate.com/flyway/getting-started-with-flyway/system-requirements/supported-databases-and-versions), [pgJDBC](https://jdbc.postgresql.org/documentation/), [imagen oficial](https://hub.docker.com/_/postgres), [BOM 4.1.2](https://repo.maven.apache.org/maven2/org/springframework/boot/spring-boot-dependencies/4.1.2/spring-boot-dependencies-4.1.2.pom).
- Este contraste no declara compatibilidad ejecutable: árbol efectivo y ejecución contra PostgreSQL quedan pendientes para 3.1 y 5.4.

### Seguridad preliminar — tarea 2.2

- Consulta OSV API `querybatch`, ecosistema Maven, para siete coordenadas exactas propuestas (Boot, Hibernate, Flyway Core/módulo PostgreSQL, pgJDBC, HikariCP y Testcontainers): 0 avisos devueltos para cada versión. Respuesta local en `target/setup-database-metadata/osv-preliminary.json`.
- Procedencia contrastada con POM oficiales de Maven Central. Licencias declaradas: Apache-2.0 (Boot/Hibernate/Flyway/HikariCP), BSD-2-Clause (pgJDBC), MIT (Testcontainers); la licencia de Flyway se comprobó en flyway-parent 12.4.0.
- Alcance preliminar: solo estas coordenadas y el estado del índice consultado; no equivale a garantía de ausencia de vulnerabilidades, revisión transitiva ni análisis de la imagen. No aparecieron hallazgos críticos en esta consulta que requieran una excepción. El árbol efectivo se revisará en 3.1 y la imagen en 7.1.

### Dependencias incorporadas y regresión básica — tarea 3.1

- Incorporadas únicamente las dependencias aprobadas; Testcontainers permanece en alcance test. Protección ConfigData `!persistence` incorporada sin exclusiones permanentes por anotación ni integración Boot Docker Compose.
- `mvnw.cmd package`: satisfactorio, 1 prueba, 0 fallos/errores/omitidas, sin Docker ni BD requeridos. Log local: `target/setup-database-dependencies.log`.
- Árbol efectivo generado con `mvnw.cmd dependency:tree -DoutputType=json -DoutputFile=target/setup-database-metadata/dependency-tree.json`: 115 coordenadas únicas distintas de DemandFlow. Coinciden Boot 4.1.2, Hibernate 7.4.13.Final, Flyway 12.4.0, JDBC 42.7.14, HikariCP 7.0.2 y Testcontainers 2.0.5; JUnit Jupiter API efectivo 6.0.3.
- Consulta OSV del árbol completo: 115 coordenadas consultadas, sin avisos devueltos. Licencias de los 115 POM (incluida herencia de padres) registradas sin declaraciones faltantes en `licenses-effective-tree.json`. Informes locales no versionados bajo `target/setup-database-metadata/`.
- Límite: evaluación basada en coordenadas y declaraciones POM/índice OSV, no garantía de ausencia de vulnerabilidades ni revisión binaria; imagen Docker pendiente. Sin incompatibilidades o hallazgos críticos conocidos que requieran cambiar versiones en esta fase.

### Arranque predeterminado — tareas 3.2 y 3.3

- `mvnw.cmd test`: 4 pruebas satisfactorias (3 variantes de DefaultStartupTests y la prueba de contexto existente), 0 fallos/errores/omitidas. ConfigData real comprueba modo predeterminado, perfil ajeno y parámetros de conexión presentes; sin datasource/Flyway/EntityManagerFactory y con exclusiones efectivas correctas.
- JAR ejecutado en un puerto loopback libre con `DOCKER_HOST=tcp://127.0.0.1:1`, URL PostgreSQL hacia un puerto inaccesible y sin perfil persistente: disponible (respuesta HTTP 404 del esqueleto), proceso vivo y socket exclusivamente `127.0.0.1`. Después se detuvo solo ese proceso de prueba. Log local en `target/setup-database-default-jar.log`.
- Prueba temporal deliberadamente fallida: 1 fallo JUnit propagado por Maven con código 1 y BUILD FAILURE. Fuente y clase temporal retiradas después de la comprobación; registro local en `target/setup-database-required-test-failure.log`. Es un resultado negativo esperado, no un fallo ocultado.
- README actualizado con construcción Wrapper, límites del modo predeterminado, escucha local y detención; comandos contrastados con las comprobaciones anteriores.

### Infraestructura estática — tareas 4.1 a 4.3

- `compose.yaml`: imagen fija 17.12-bookworm, servicio postgres, base demandflow, volumen nombrado por proyecto, montaje de datos de PostgreSQL 17, publicación exclusivamente 127.0.0.1, SCRAM y healthcheck TCP con los plazos aprobados. Sin contenerizar Java ni crear recursos todavía.
- `.env.example` solo contiene puerto/ruta; `.secrets/` excluido de Git (comprobado con git check-ignore). Tres consumidores de secretos y rutas definidas conforme a D4; ningún secreto real creado/versionado.
- Bootstrap con LF controlado por `.gitattributes`, secretos leídos sin eco, asignación mediante psql/variables efímeras, salida cruda descartada y mensajes fijos de error. Roles e infra transaccionales, privilegios y timeouts según diseño; la imagen oficial solo invoca initdb.d en volumen vacío. Verificación ejecutable de roles/errores pendiente en 4.4; estática sin código de negocio.

### Bootstrap ejecutable y documentación — tareas 4.4 y 4.5

- `mvnw.cmd -Ppersistence-it -Dit.test=BootstrapIT verify`: satisfactorio; 4 pruebas predeterminadas y 1 prueba PostgreSQL real, sin fallos/errores/omitidas. Primera ejecución incluyó descarga de `postgres:17.12-bookworm` y `testcontainers/ryuk:0.14.0`; log local en `target/setup-database-bootstrap.log`.
- Verificados PostgreSQL 17.12, binding Testcontainers exclusivamente 127.0.0.1, conexión de ambos roles con secretos externos, atributos no administrativos, denegación CREATE/TEMP/USAGE infra al runtime y timeouts de roles. El secreto sintético del migrador incluye una comilla para comprobar interpolación segura.
- Bootstrap reejecutado sobre roles existentes: error controlado; archivo requerido ausente/vacío: rechazo. Salidas y logs sin ninguno de los tres secretos sintéticos. Contenedor de prueba cerrado mediante Testcontainers; no se utilizó volumen de desarrollo.
- Docker inicial: inventario guardado bajo `target/setup-database-docker-*-before.jsonl`; puerto 5432 sin listeners antes de crear recursos de prueba.
- Guía `docs/engineering/local-persistence-guide.md` y enlace README documentan preparación segura, rutas, bootstrap, construcción, arranque/detención y conservación de recursos. El arranque con perfil se completará y verificará en el grupo 5.

### Perfil y validación previa — tarea 5.1

- `mvnw.cmd test`: 10 pruebas satisfactorias, 0 fallos/errores/omitidas. Las seis pruebas de parámetros vacíos utilizan ConfigData real y verifican fallo de configuración antes de conexión; las cuatro regresiones existentes siguen pasando.
- Perfil `persistence` con configtree obligatorio y consumidores diferenciados, sin importar secretos administrativos. El documento negativo de exclusiones queda inactivo al seleccionar el perfil, sin vaciar listas ni usar exclusiones por anotación.
- Error de configuración reportado con categoría estática y sin valores sensibles. Log local en `target/setup-database-profile-tests.log`.

### V1 y gestión del esquema — tarea 5.2

- `mvnw.cmd -Ppersistence-it -Dit.test=PersistenceIT verify`: satisfactorio, 10 pruebas predeterminadas y 1 integración real, sin fallos/errores/omitidas.
- Verificados Flyway/EntityManagerFactory presentes, exclusiones negativas ausentes con perfil activo, metamodelo JPA vacío y `ddl-auto=validate` efectivo. Runtime conecta como demandflow_app y no puede consultar el historial ni crear en el esquema.
- V1 crea namespace demandflow con propietario demandflow_migrator, USAGE runtime y sin tablas de negocio. Historial V1 exitoso y checksum registrados en infra. Segundo arranque conserva versión, checksum, orden y fecha de instalación sin reaplicar V1. Logs del servidor sin secretos sintéticos.
- Registro local: `target/setup-database-persistence.log`. Aún pendientes los casos de fallo nativo y de proceso JAR de las tareas siguientes.

## Recuperación de sesión — 2026-11-24

- Rama `feature/setup-database` y aprobación `a910fa3` comprobadas; autorización vigente ratificada por el usuario. Se preservan todos los archivos modificados y sin seguimiento encontrados.
- Reconciliadas 15/26 tareas: 1.1–5.2 cuentan con implementación y evidencias previas. Para 5.2 se contrastaron fuente, log con BUILD SUCCESS, reporte PersistenceIT (1 prueba, cero fallos/errores/omitidas) y resumen Failsafe. No se repite esa prueba.
- Primera tarea incompleta: 5.3. Configuración de límites y NativeFailureIT ya escritos, sin reporte previo de ejecución; no se consideran verificados. 5.4–8.2 siguen pendientes.
- Docker consultado con permisos de lectura fuera del sandbox: sin contenedores de la imagen aprobada ni contenedores/redes/volúmenes del proyecto `demandflow-local`. No se modificaron recursos ni se creó/eliminó volumen de desarrollo.
- OpenSpec local confirmado, esquema spec-driven, estado ready. Se usa `openspec.cmd` porque PowerShell bloquea el shim `.ps1`; no se cambia la política de ejecución.
- Verificación en curso: `mvnw.cmd -Ppersistence-it -Dit.test=NativeFailureIT verify`, salida local `target/setup-database-native-limits.log`. El resultado se registrará antes de marcar 5.3.
- Incidencias de recuperación: primera invocación sin comillas falló por separación de `-Dit.test` en PowerShell, antes de pruebas. Primera ejecución real: 10 regresiones correctas, NativeFailureIT falló al suponer `FlywayConfiguration.getUrl()` no nulo; Boot proporciona un DataSource. Corregida la asercción para consultar metadata de la conexión migradora efectiva; no se cambiaron límites ni diseño. Se añadieron comprobaciones de sesiones de ambos roles y captura de diagnósticos. Dos pruebas JAR preparadas para 5.4 siguen sin verificar y no justifican marcarla.

### Límites nativos — tarea 5.3

- Ejecución corregida de NativeFailureIT satisfactoria: 10 regresiones y 1 integración, cero fallos/errores/omitidas; BUILD SUCCESS. Registro: target/setup-database-native-limits.log.
- Comprobados límites Hikari efectivos, URL JDBC con los cuatro límites tanto en pool como en conexión Flyway, statement_timeout=15s y lock_timeout=5s en ambos roles, reintentos Flyway 0/3 y clean deshabilitado.
- Puerto inaccesible y autenticación runtime/migrador rechazada propagan fallo en contexto antes de 75 s; diagnósticos capturados sin secretos sintéticos ni URL JDBC. Sin guardián ni cambio de plazos. La salida espontánea de procesos JAR se verificará en 6.2.
- Siguiente tarea: 5.4, prueba del JAR positivo y segundo arranque; archivos preparados sin ejecución todavía.

### JAR positivo y repetición — tarea 5.4

- mvnw.cmd -Ppersistence-it '-Dit.test=JarStartupIT' verify: BUILD SUCCESS, 10 regresiones y 1 integración, cero fallos/errores/omitidas. JarStartupIT: 36.31 s. Log local target/setup-database-jar-startup.log.
- Dos procesos JAR independientes se hicieron disponibles antes de 75 s sobre PostgreSQL nuevo; JPA inicializada antes del mensaje de arranque, respuesta HTTP 404 y proceso vivo; netstat por PID verificó un único listener 127.0.0.1. Cierre ordinario de ambos antes de 10 s.
- V1/checksum/orden/fecha idénticos entre arranques e inventario limitado al historial; logs Java/servidor sin secretos sintéticos ni URL JDBC. Complementa las aserciones de contexto/ACL/metamodelo de PersistenceIT ya verificadas en 5.2. No se atribuye validación funcional a entidades inexistentes.

### Guía y punto de control del grupo 5 — tarea 5.5

- Guía local ampliada con activación explícita, variables en IDE sin lectura implícita de .env, conexiones diferenciadas, V1, límites, errores y retorno al modo sin BD. Contrastada con pruebas de configuración, NativeFailureIT, PersistenceIT y JarStartupIT; sin afirmaciones de negocio ni contraseñas.
- Grupo 5 completo: 5.1–5.5; total 18/26. Verificaciones de esta sesión: NativeFailureIT y JarStartupIT, ambas satisfactorias tras corregir una aserción de metadata. No hay incompatibilidad del diseño detectada.
- Recursos: solo contenedores desechables Testcontainers; cierre estándar al finalizar cada prueba, ningún volumen de desarrollo utilizado. No se ejecutaron operaciones Git de cierre.
- Siguiente: 6.1, fixtures técnicas V2/checksum/rollback; 6.2–8.2 pendientes.

### Evolución aislada y rollback — tarea 6.1

- mvnw.cmd -Ppersistence-it '-Dit.test=MigrationEvolutionIT' verify: BUILD SUCCESS, 10 regresiones y 2 integraciones, cero fallos/errores/omitidas. Registro local target/setup-database-migration-evolution.log.
- Sobre bases nuevas independientes: V2 válida comenta el namespace y conserva V1; copia temporal de V1 con checksum alterado rechazada sin alterar historial; V2 SQL fallida revierte el comentario y no registra éxito. V1 productiva intacta; fixtures solo en src/test/resources, fuera del empaquetado principal.
- Siguiente 6.2: casos negativos de proceso JAR preparados, todavía sin ejecutar.
- Primera ejecución JarFailuresIT: puerto inaccesible terminó espontáneamente en 6432 ms; caso runtime rechazado terminó con fallo, pero no con la categoría prevista. Hibernate había sustituido la causa JDBC por error de metadata. Corregido el analizador para reconocer específicamente esa condición y restringido el logger JPA que emitía el mensaje original. No se cambian credenciales, privilegios, límites ni decisiones. 6.2 sigue pendiente de nueva ejecución.

### Fallos espontáneos del JAR — tarea 6.2

- mvnw.cmd -Ppersistence-it '-Dit.test=JarFailuresIT' verify: BUILD SUCCESS, 10 regresiones y 1 caso agrupado con 13 procesos Java independientes, cero fallos/errores/omitidas. Registro target/setup-database-jar-failures.log.
- Todos salieron espontáneamente con código 1, sin disponibilidad ni fallback, sin terminación forzada: inaccesible 7653 ms; runtime rechazado 10760 ms; migrador rechazado 7292 ms; seis propiedades vacías 6513–10273 ms; directorio ausente 3204 ms; archivo vacío 5020 ms; checksum 10335 ms; ejecución SQL 11299 ms. Todos antes de 75 s.
- Diagnósticos por categoría, sin secretos originales/rechazados, URL JDBC ni SQL de asignación. Checksum e historial sin reparación; SQL fallido conserva V1 y revierte comentario.
- Incidencias corregidas: causa SQL sustituida por Hibernate; argumentos duplicados del comprobador se agregaban en Spring y daban una ruta configtree inválida. Ahora los argumentos de prueba sustituyen claves existentes. Configtree ausente produce categoría segura. Sin alterar D1-D9 ni plazos.
- Siguiente tarea: 6.3, revisión transversal de entregable/secretos y regresión de los cambios de diagnóstico.

### Revisión transversal — tarea 6.3

- mvnw.cmd -Ppersistence-it '-Dit.test=ArtifactIntegrityIT,JarStartupIT,NativeFailureIT' verify: BUILD SUCCESS, 10 regresiones y 3 integraciones, cero fallos/errores/omitidas. Registro local target/setup-database-artifact-security.log.
- ArtifactIntegrityIT comprueba JAR sin Testcontainers, fixtures V2, clases IT, directorios/archivos de secretos, marcadores sintéticos, secreto administrativo ni SQL de asignación; única migración productiva V1. JarStartupIT vuelve a comprobar dos arranques, orden JPA/disponibilidad, loopback e historial tras los cambios de logger; NativeFailureIT vuelve a comprobar límites y diagnósticos.
- Auditoría de 159 rutas Git entregables: cero coincidencias con el patrón completo de credenciales sintéticas generadas. Resumen local deliverable-secret-audit.json. Las pruebas existentes BootstrapIT/PersistenceIT ya demuestran separación de roles, denegación DDL/historial y ausencia de filtraciones de bootstrap/servidor; no se repiten sin causa.
- Guía de diagnóstico ampliada con categorías de validación/ejecución, configtree ausente y fallo de metadata por credenciales; sin sugerir fijar dialecto, repair ni ocultar falta de conexión.

### Punto de control del grupo 6 — recuperación siguiente

- Completadas 6.1–6.3; total 21/26, incluidas 5.3–5.5 en esta sesión. Se conservan 26 descripciones de tarea, D1-D9, 12 requisitos y 21 escenarios. Encabezado/pie de tasks.md reconciliados con la ejecución real sin alterar criterios.
- Primera pendiente: 7.1. ComposePersistenceIT preparado y compilado, NO ejecutado; incluye recreación y restauración técnica en proyectos temporales, sin evidencia aún. 7.2, 7.3, 8.1 y 8.2 siguen pendientes; no se han ejecutado dos suites completas ni declarado cobertura integral.
- Revisión de imagen adelantada al preparar 7.1: Scout v1.28.0, 227 paquetes, 178 avisos (4 críticos/50 altos/51 medios/67 bajos/6 sin clasificación). Digest exacto y tratamiento en image-security-review.md. Los críticos apuntan a Go stdlib de gosu; aplicabilidad no resuelta. Contraste upstream y binario insuficiente para descartarlos; no se acepta riesgo ni se cambia imagen o plataforma.
- Se detiene la parte 7.1 para revisión de seguridad humana antes de continuar el grupo 7. La preparación funcional no acredita cierre de revisión de imagen ni cumplimiento integrado de 8.1. No hay incompatibilidad funcional demostrada; tampoco se afirma explotabilidad confirmada.
- Recursos de esta sesión: contenedores Testcontainers desechables cerrados estándar; contenedor de inspección gosu creado sin iniciar y eliminado únicamente tras verificar identidad, junto con su volumen anónimo. Ningún volumen de desarrollo usado o eliminado; sin operaciones Git de cierre.
- Para reanudar: comprobar rama/Git, este punto y el informe de imagen; resolver tratamiento/aplicabilidad de hallazgos conservando D2 o revisar explícitamente cualquier cambio requerido. Después ejecutar y revisar ComposePersistenceIT antes de marcar 7.1, continuar dos suites limpias/prerrequisito ausente, guía de recuperación y verificación integrada.

### Comprobaciones al dejar el punto de recuperación

- openspec.cmd validate setup-database --strict: satisfactorio. git diff --check: satisfactorio; únicamente avisos informativos LF/CRLF en archivos existentes, sin errores de whitespace.
- Recontados 12 requisitos, 21 escenarios y 26 tareas; OpenSpec confirma 21 completas / 5 pendientes. Rama feature/setup-database, HEAD a910fa3; sin commit, push, merge ni archive.
- Estado Docker final: 11 identificadores Testcontainers extraídos de los logs de esta sesión, cero contenedores restantes. Cero contenedores con etiqueta de inspección setup-database; consultas de redes/volúmenes demandflow-local sin resultados. No se modificaron recursos ajenos ni se eliminó volumen de desarrollo.
- SHA256 del SARIF Scout local: 0f008fbaa941411583af03de6f92acb69c94a2f41c0e18af51630d2a3a397501. No se descartan ni se aceptan silenciosamente los avisos críticos; revisar image-security-review.md antes de continuar.
- Entrega parcial a revisión humana: 5.3–6.3 verificadas en esta sesión; 7.1–8.2 pendientes. El borrador ComposePersistenceIT solo compiló. No se atribuye éxito a pruebas no ejecutadas ni se marca 8.2 como entrega integral terminada.

## Revisión y operación manual — 2026-11-24

### Alcance ratificado y estado reconciliado

- El usuario administrará manualmente el proyecto permanente `demandflow-local`, su volumen y sus credenciales. Esta revisión no crea contenedores/volúmenes de desarrollo ni genera credenciales reales. Se conserva la autorización de implementación del mismo alcance; la operación manual no es aprobación de riesgos ni cierre de pruebas.
- Rama comprobada: `feature/setup-database`. OpenSpec local, esquema `spec-driven`, estado `ready`, 21/26 tareas completas. Git conserva los avances modificados y sin seguimiento; no se revierten archivos. `ready` permite trabajar y no resuelve el bloqueo técnico de seguridad.
- Comparación de las 26 descripciones de tareas contra `a910fa3`, normalizando únicamente las casillas: cero diferencias. Permanecen 12 requisitos y 21 escenarios. Proposal, design (D1–D9), delta spec y `application-startup` sin diferencias contra ese commit. No se modifican Compose, bootstrap, migraciones, configuración Spring ni dependencias en esta revisión.
- La revisión de imagen conserva los resultados anteriores: 178 avisos, incluidos cuatro críticos y 50 altos; aplicabilidad/tratamiento aún pendientes en [image-security-review.md](image-security-review.md). No se repite el escaneo ni se presume que sus datos sean una aprobación vigente. Sin sustitución de imagen ni aceptación unilateral de riesgos.

### Dependencias y verificaciones independientes

La regla aprobada de `tasks.md` exige grupos en orden y numeración dentro del grupo. El bloqueo de seguridad afecta a 7.1 y al avance formal posterior. Se distingue preparación documental/comprobación independiente de la ejecución y finalización de esas tareas; no se modifican sus criterios ni se reordenan.

| Actividad | Independencia y resultado en esta revisión | Criterio pendiente |
|---|---|---|
| Regresión y empaquetado sin BD | Ejecutados sin acceso Docker; no necesitan resolver 7.1. Complementan D1/R11/S17–S20 y `application-startup`; no sustituyen la prueba de proceso JAR previa. | Ningún cierre nuevo; no equivalen a las dos suites de 7.2 ni a 8.1. |
| Validación estática Compose | Ejecutada sin crear recursos ni secretos; comprueba el modelo existente. | No demuestra bootstrap, salud, conexión, persistencia ni restauración con Compose. |
| OpenSpec estricto, whitespace y conservación del contrato | Ejecutados; son comprobaciones independientes. | No acreditan cobertura integrada de 8.1. |
| 7.1: Compose temporal y seguridad | Informe revisado; prueba preparada/compilada previamente, sin ejecución nueva. No usa el proyecto permanente. | Resolver aplicabilidad/tratamiento de vulnerabilidades y ejecutar/revisar recreación, ACL/historial, binding y limpieza temporal. |
| 7.2: dos suites limpias y prerrequisito ausente | Las suites requieren la imagen y la comprobación Compose. La invocación negativa con endpoint inaccesible es técnicamente separable y no necesita crear PostgreSQL, pero se conserva como ejecución pendiente de 7.2 por la secuencia aprobada. No se confunde con el empaquetado predeterminado ejecutado aquí. | Tras 7.1, ejecutar ambas suites y la invocación explícita negativa con entorno saneado; exigir fallo real, sin skips ni fallback. |
| 7.3: guía y recuperación | Preparación documental posible sin levantar PostgreSQL: ampliada la guía manual. | Tras 7.2, contrastar los procedimientos y completar el ensayo aislado de restauración con propietarios/ACL/historial; la prosa no demuestra recuperación. |
| 8.1 y 8.2: integración y entrega final | Se pueden organizar evidencias y revisar trazabilidad; actualización parcial realizada aquí. | Terminar grupo 7, ejecutar verificación integrada completa y reconciliar S01–S21/D9 antes del cierre y revisión final. |

### Resultados ejecutados

- `mvnw.cmd -o package`, con variables Spring/DemandFlow/Docker/Testcontainers y opciones Java externas retiradas del proceso de comprobación; `DOCKER_HOST=tcp://127.0.0.1:1`. Se restauró el entorno del proceso al terminar. BUILD SUCCESS, 25.715 s, 10 pruebas: DefaultStartupTests 3, DemandFlowApplicationTests 1 y PersistenceConfigurationTests 6; cero fallos/errores/omitidas. JAR reempaquetado. Log local: `target/setup-database-manual-review-package.log`. No se solicitó `persistence-it` ni se ejecutaron pruebas que creen PostgreSQL.
- No se utilizó `clean`, para conservar evidencias locales previas. El XML antiguo `BuildFailureProbeTest` sigue registrando el fallo deliberado de 2026-11-23; no pertenece a las diez pruebas de esta ejecución. La fuente temporal ya fue retirada según evidencia anterior; no se contabiliza como fallo actual ni se borra para aparentar éxito.
- `docker compose -f compose.yaml -p demandflow-local config --quiet`: sin diagnóstico de error. Consultas `docker ps -a`, `docker volume ls` y `docker network ls` filtradas por `com.docker.compose.project=demandflow-local`: sin resultados. Ninguna operación `up`, `create`, `pull`, `down` ni eliminación ejecutada. `Test-Path .secrets`: falso. No se muestran ni leen credenciales de desarrollo.
- `openspec.cmd validate setup-database --strict`: satisfactorio. `git diff --check`: satisfactorio, con los avisos informativos LF/CRLF ya conocidos. Las instrucciones manuales nuevas se contrastaron estáticamente con Compose, bootstrap, perfil Spring y V1; sus consultas/comandos operativos no se ejecutaron contra desarrollo.

### Procedimiento preparado y punto de recuperación

- [Guía local](../../../docs/engineering/local-persistence-guide.md) ampliada: responsabilidad del operador y condición de seguridad, secretos protegidos creados únicamente por él, comprobaciones de contexto/puerto/recursos existentes, `config --quiet`, `up --wait`, mismo puerto/ruta para Java e IDE y activación explícita de `persistence`.
- Incluye binding/version/digest, disponibilidad Java, consultas administrativas sin secretos para roles/namespace/ACL/historial e inventario, comparación de un segundo arranque, detención conservando volumen y límites de rotación/respaldo/restauración. No propone ejecutar fixtures negativas sobre desarrollo ni habilitar repair/clean/baseline. Respaldo y restauración siguen pendientes de ensayo aislado antes de datos relevantes.
- Total sin cambio: **21/26**. Ninguna tarea nueva marcada. Pendientes: **7.1, 7.2, 7.3, 8.1, 8.2**. La primera acción de implementación pendiente sigue siendo resolver la revisión de imagen para 7.1 y verificar ComposePersistenceIT en proyectos temporales; no iniciar `demandflow-local` como parte del trabajo del agente.
- Siguiente sesión: conservar la gestión manual del entorno permanente, revisar este punto y el informe de imagen; no interpretar una puesta en marcha manual como cierre de seguridad, persistencia/recreación o recuperación. Sin recursos Docker creados/modificados por esta revisión, sin credenciales generadas y sin commit, push, merge ni archive. Entrega documental parcial a revisión humana.

## Reanudación autorizada con Trixie — 2026-11-24

- El usuario aprobó expresamente D2 para postgres:17.12-trixie y aceptó el riesgo residual documentado solo para el MVP local, sin exposición a Internet. Autorizó configuración, pruebas, documentación y completar 7.1–8.2 sin crear el entorno permanente ni ejecutar operaciones Git de cierre.
- Rama feature/setup-database; 21/26 al inicio. Cambiados únicamente el selector de imagen de Compose/pruebas y las referencias vigentes de D2/guía. Misma versión PostgreSQL, plataforma Java y dependencias; V1 intacta. Informe de seguridad actualizado con la aceptación y correcciones del análisis previo; historial conservado.
- Registros previos preservados en .cache/setup-database-evidence antes de ejecutar clean; se restituirán a target al finalizar. Un intento inicial de usar .agents/tmp fue denegado por permisos; no afectó pruebas ni recursos. La caché se ubicó en .cache, directorio de trabajo ya ignorado.
- 7.1 en curso: ComposePersistenceIT pendiente de ejecución contra el digest Trixie aprobado. No se marca hasta verificar recreación, restauración y limpieza aisladas.

- Primera ejecución de 7.1: BUILD FAILURE, 10 regresiones satisfactorias y ComposePersistenceIT fallida antes de crear recursos. Windows eliminó comillas de la plantilla Go en ProcessBuilder; se reemplazaron expresiones con claves entrecomilladas por campos directos, JSON de etiquetas y filas de montajes. No se modifican límites ni controles de identidad. Registro: compose-trixie.log. Se reintenta; tarea aún pendiente.

- Segundo intento de 7.1: recreación y V1 conservadas; restauración llegó a la comparación de catálogo. Falló la representación de ACL por defecto: pg_dump/restore deja nspacl NULL para infra cuando equivale al ACL predeterminado. El snapshot omitía esa fila al concatenar NULL. Corregido para comparar coalesce(nspacl, acldefault('n', nspowner)); no se amplían permisos ni se cambia restauración. Añadido segundo arranque sobre la copia restaurada. La limpieza se ejecutó en finally. Registro compose-trixie-retry.log; 7.1 sigue pendiente.

### 7.1 completada — Compose temporal con Trixie

- mvnw.cmd -o -Ppersistence-it '-Dit.test=ComposePersistenceIT' verify: BUILD SUCCESS, 10 regresiones y 1 integración, cero fallos/errores/omitidas. Caso Compose 121.8 s; Maven 2:38. Log compose-trixie-verified.log, guardado fuera de target durante clean.
- PostgreSQL 17.12 y digest aprobado 87c8b42f423b45d5247e6aa8fdb2e7762fec164c68a50fe3eb07e7bfcaf4dbcf comprobados. Proyecto demandflow-it-9642006a46bd4f90ad5da1ded299e5d8 y su copia -restore; puerto aleatorio solo loopback, secretos sintéticos y volumen propio.
- Aplicada V1; recreación con contenedor nuevo, mismo nombre/CreatedAt de volumen y mismo namespace/propietarios/ACL efectivas/comentario/historial completo. Bootstrap no reaplicado. pg_dump custom y pg_restore sobre otra base nueva con roles aprovisionados; dos arranques de la copia restaurada conservan historial y permisos runtime sin CREATE ni USAGE infra. Logs sin secretos.
- Limpieza comprobó nombre y etiquetas antes de down --volumes, eliminando solo ambos proyectos temporales y sus volúmenes. No se usó desarrollo. La revisión de imagen se cierra mediante aceptación humana limitada, no por afirmar ausencia de CVE.
- Punto de recuperación: 22/26 completas. Continúa 7.2: dos suites limpias con orden inverso de clases y una invocación negativa de prerrequisito, todas con entorno saneado; 7.3/8.1/8.2 siguen pendientes.

### Punto de control durante 7.2

- Primera suite limpia: mvnw.cmd -o clean -Ppersistence-it '-Dfailsafe.runOrder=alphabetical' verify, BUILD SUCCESS, 6:04. Reportes preservados como suite-1-failsafe/suite-1-surefire fuera de target. Log suite-trixie-1.log. No se marca 7.2 hasta segunda suite y caso de prerrequisito.
- JarStartupIT añadió comprobación explícita S18: tras migrar la base, dos JAR sin persistence (default y perfil ajeno), con URL/usuarios y secretos sintéticos cargados mediante configtree, quedan disponibles en loopback sin sesiones app/migrador y sin cambiar V1. La variante nueva pasó en esta suite, junto con los dos arranques persistentes.
- ComposePersistenceIT volvió a verificar recreación/restauración/limpieza en 115.5 s. Los 13 fallos JAR finalizaron espontáneamente entre 3213 y 8533 ms, todos con código 1 y sin filtraciones. Siguiente: segunda suite limpia con clases en orden inverso. 22/26 completas.

### 7.2 completada — reproducibilidad y prerrequisito ausente

- Segunda suite limpia: mvnw.cmd -o clean -Ppersistence-it '-Dfailsafe.runOrder=reversealphabetical' verify, BUILD SUCCESS, 5:24. Ambas suites: 10 pruebas Surefire y 9 Failsafe, cero fallos/errores/omitidas. Comparadas firmas de ocho clases IT (MigrationEvolutionIT contiene dos casos): equivalentes; orden de ejecución invertido. Reportes y suites-comparison.json preservados. No hay cambios en código/dependencias entre suites.
- Segunda suite: 13 procesos de fallo con salida espontánea 1, entre 2597 y 10500 ms, sin secretos ni abortos forzados. Se corrige la estimación conversacional inicial de menos de nueve segundos: el máximo medido fue 10.5 s, siempre inferior a 75 s.
- Con DOCKER_HOST=tcp://127.0.0.1:1 y entorno saneado: mvnw.cmd -o -Ppersistence-it '-Dit.test=ComposePersistenceIT' verify devolvió 1, BUILD FAILURE esperado. Una prueba fallida, cero errores/omitidas; fallo de conexión en volume ls antes de crear recursos (caso 2.059 s). Se solicitó expresamente la comprobación Compose del perfil de integración; no se atribuye ejecución negativa a las demás clases ni se utilizó una BD alternativa. Registro missing-docker.log y reportes separados.
- Regresión separada con Docker inaccesible: mvnw.cmd -o package, BUILD SUCCESS, 10 pruebas, cero fallos/errores/omitidas, 32.090 s. Sonda Java de esta sesión (.cache/DefaultStartupProbe.java, sin dependencias) ejecutó el JAR sin perfil/secretos, con daemon inaccesible: HTTP 404 y JVM viva en 6464 ms; netstat por PID confirmó único listener loopback; detención ordinaria dentro de 10 s. Sin JPA/URL JDBC en salida. Registros default-package-trixie.log, default-jar-trixie.log y default-probe-result.log.
- 7.2 completa: 23/26. Los fallos deliberados y los intentos previos fallidos se conservan, sin mezclarlos con las suites positivas ni declarar su éxito funcional.

### 7.3 completada y punto de control del grupo 7

- Guía ampliada y contrastada con el bootstrap, perfil y pruebas Compose ya ejecutadas: stop/down conservan volumen, rotación administrativa mediante psql y archivos externos, pg_dump custom/copia binaria fuera del repositorio, pg_restore solo en proyecto nuevo, roles aprovisionados y comparación de propietarios/ACL efectivas/historial y dos arranques. Incluye diagnóstico de bootstrap parcial, límites del respaldo técnico, ausencia de rollback global y correcciones mediante nuevas versiones, sin repair/clean/baseline sobre desarrollo.
- El ensayo técnico de 7.1 se repitió satisfactoriamente en las dos suites limpias; comprueba restauración y permisos sin datos de negocio. Los comandos manuales nuevos se revisaron estáticamente contra ese flujo; no se ejecutaron sobre desarrollo. No se realizó rotación ni se generaron contraseñas reales de desarrollo.
- Grupo 7 completo: 7.1–7.3; 24/26. Recursos únicamente temporales con limpieza propia. Siguiente: 8.1, reconciliación integrada S01–S21/D9 y comprobaciones estáticas finales; luego 8.2, entrega humana sin operaciones Git de cierre.

### 8.1 completada — verificación integrada y cobertura real

Las dos invocaciones completas `mvnw.cmd -o clean -Ppersistence-it verify`, con orden de clases explícito alfabético/inverso, ejecutaron la misma suite con resultado satisfactorio. El empaquetado y la sonda del JAR sin infraestructura se ejecutaron por separado. No se repite una tercera suite: desde esas ejecuciones solo se consolidaron documentación/evidencias, sin modificar código, configuración de ejecución ni dependencias.

| Escenario | Requisito / decisiones | Evidencia ejecutada con Trixie |
|---|---|---|
| S01 | R1 / D2–D3, D7–D8 | BootstrapIT y ComposePersistenceIT: 17.12, digest aprobado, disponibilidad. |
| S02 | R1 / D3–D5, D7 | JarStartupIT y ComposePersistenceIT: Java local conecta a PostgreSQL. |
| S03 | R2 / D3, D6–D7 | ComposePersistenceIT: contenedor nuevo, mismo volumen/CreatedAt, catálogo e historial conservados. |
| S04 | R3 / D1, D3, D7 | Bindings Docker y listeners Java por PID exclusivamente loopback. |
| S05 | R4 / D4–D5, D7 | BootstrapIT/PersistenceIT/Compose: archivos externos sintéticos y cuentas separadas. |
| S06 | R4 / D4–D5, D7 | BootstrapIT, NativeFailureIT, JarFailuresIT y ArtifactIntegrityIT: salidas/JAR sin secretos. Auditoría final de entregables sin coincidencias. |
| S07 | R5 / D1–D2, D5, D7 | PersistenceIT y JarStartupIT: contexto/JAR inicializan Flyway/JPA en la plataforma mantenida. |
| S08 | R6 / D5–D6 | PersistenceIT: validate, metamodelo vacío y solo tabla de historial. No se atribuye validación de negocio. |
| S09 | R7 / D4, D6–D7 | V1 sobre bases nuevas Testcontainers y Compose; namespace y propietarios correctos. |
| S10 | R8 / D6–D7 | Dos arranques persistentes, recreación y dos arranques restaurados sin reaplicar V1; checksum/orden/fecha conservados. |
| S11 | R8 / D6–D7 | MigrationEvolutionIT: V2 técnica válida aplica exclusivamente la nueva versión. |
| S12 | R8 / D5, D7–D8 | MigrationEvolutionIT/JarFailuresIT: checksum divergente rechazado, sin repair ni cambio de historial. |
| S13 | R9 / D1, D5, D7–D8 | JarStartupIT/Compose: disponibilidad después de inicialización y proceso vivo en loopback. |
| S14 | R10 / D4–D5, D7–D8 | NativeFailureIT/JarFailuresIT: puerto inaccesible y ambas autenticaciones rechazadas, salida espontánea no cero. |
| S15 | R10 / D4–D5, D8 | PersistenceConfigurationTests/JarFailuresIT: seis parámetros vacíos, directorio ausente y archivo vacío, sin fallback. |
| S16 | R10 / D6–D8 | MigrationEvolutionIT/JarFailuresIT: SQL fallido revierte su operación y no registra V2 exitosa. |
| S17 | R11 / D1, D7 | Construcción predeterminada y DefaultStartupProbe: JAR sin perfil, sin secretos y Docker inaccesible, disponible hasta detenerlo. |
| S18 | R11 / D1, D7 | DefaultStartupTests y JarStartupIT: parámetros/secretos presentes y BD disponible sin activar persistence; sin sesiones app/migrador ni cambio de historial. |
| S19 | R12 / D3–D4, D7 | Dos suites limpias con orden inverso y mismas firmas/resultados; bases y proyectos nuevos, sin desarrollo. |
| S20 | R12 / D1–D2, D7 | 10 regresiones sin Docker en cada suite y construcción separada; mismo artefacto predeterminado. |
| S21 | R12 / D7–D8 | Solicitud explícita de ComposePersistenceIT con Docker inaccesible: Maven 1, fallo real previo a crear recursos, cero omisiones. |

Contrato publicado `application-startup` sin diferencias contra `a910fa3`: los seis escenarios se conservan. Compilación/empaquetado/Wrapper: construcciones limpias y package; propagación de fallo: invocación negativa actual y prueba deliberada de construcción ya registrada; JAR sin BD/escucha protegida: sonda actual; configuración sin credenciales reales: auditoría y archivos de ejemplo. No se introdujeron entidades, repositorios, tablas ni funcionalidad de negocio.

- `openspec.cmd validate setup-database --strict` y `git diff --check`: satisfactorios; solo avisos informativos LF/CRLF conocidos. Auditoría: 159 rutas entregables, cero coincidencias de credenciales sintéticas completas; 26 descripciones de tareas idénticas a las aprobadas tras normalizar casillas y la sustitución de imagen autorizada; 12 requisitos y 21 escenarios. `.secrets` no existe. Registro `target/setup-database-verification/final-static-audit.json`.
- Inventario final: mismos 8 contenedores por ID/nombre y mismos volúmenes; cero contenedores/volúmenes `demandflow-local`. No quedan redes Compose de prueba. Se observó una sustitución del ID de la red incorporada `bridge` (`79bcb0588428` → `3fdb3b45c88b`); los nombres de redes siguen siendo bridge/host/none, la bridge final está vacía y sin etiquetas de proyecto. No se ejecutaron operaciones directas sobre bridge ni se determinó la causa del cambio; no se afirma identidad completa del inventario de redes. Las únicas mutaciones de redes emitidas por las pruebas pertenecen a sus proyectos Compose aleatorios. Registro `final-docker-audit.json` conserva las dos filas de diferencia, sin ocultarlas.
- Digest Trixie final idéntico al aceptado. No se amplió el análisis de vulnerabilidades ni se añadieron herramientas/dependencias. El riesgo residual permanece aceptado únicamente para MVP local sin Internet; no equivale a corrección de los CVE.
- Reportes positivos completos: `target/setup-database-verification/suite-1-failsafe`, `suite-2-failsafe` y sus carpetas Surefire, con logs `suite-trixie-1.log`/`suite-trixie-2.log`. El directorio raíz `target/failsafe-reports` corresponde a la última invocación negativa deliberada; no se sobrescribe para aparentar éxito. Sus reportes separados están en `missing-docker-failsafe`.
- Registros previos y SARIF restituidos a `target/setup-database-metadata` y logs anteriores a `target/`; copia adicional en `target/setup-database-verification`. Los archivos bajo target son evidencia local regenerable, excluida de Git; este documento conserva resultados, comandos, versiones, hashes y límites de forma durable.

### 8.2 completada — entrega y punto de control final

- Consolidada la trazabilidad de los 12 requisitos y 21 escenarios con D1–D9, comandos y resultados reales, incidentes corregidos, límites, aceptación humana de seguridad y ubicación de informes saneados. No se ocultan las dos primeras ejecuciones fallidas del comprobador Compose ni el fallo negativo esperado. README y guía operativa coherentes con Trixie y la administración manual del entorno permanente.
- Resultado concreto entregado para revisión y aprobación humana de cierre: configuración Compose y bootstrap, secretos externos, perfil persistence optativo, V1 técnica, pruebas y guía de integración/recuperación. No se marca como implementada funcionalidad de negocio ni se afirma ausencia de vulnerabilidades. La aceptación local no se extiende a producción, Internet o actores no confiables.
- Grupo 8 completo y total 26/26. No quedan tareas de implementación pendientes en este incremento. El paso siguiente es revisión humana de los archivos y evidencias; no crear automáticamente el contenedor permanente ni sus secretos, no sincronizar/archivar ni ejecutar commit/push/merge por este estado.
- Recuperación entre sesiones: rama feature/setup-database, HEAD a910fa3, cambios locales preservados. Conservar `target/setup-database-verification` o consultar este registro si se limpia target. Si se modifica código/configuración o cambia el digest/condiciones de uso, repetir las verificaciones afectadas y revisar la aceptación antes de dar por vigente el resultado. No se añaden guardián, herramientas, dependencias ni operaciones de limpieza global.

## Ajuste autorizado de almacenamiento Windows — 2026-11-24

- El usuario establece D:\DOCKER_DATA\POSTGRES_DATA como estándar permanente y autoriza sustituir el volumen administrado por un bind configurable. Se actualizan D3 y referencias operativas D7/D9, contrato de persistencia, tareas y guía; D2/Trixie, seguridad aceptada, roles, bootstrap, Flyway y perfil persistence no cambian.
- Compose usa DEMANDFLOW_DATA_DIR con valor predeterminado D:/DOCKER_DATA/POSTGRES_DATA y create_host_path=false. La carpeta debe ser preparada por el operador. No se accede ni se escribe/elimina nada en D:\DOCKER_DATA; tampoco se crea el contenedor permanente.
- 7.1, 7.3, 8.1 y 8.2 se reabren provisionalmente (22/26) solo para revalidar el ajuste. Las 26 tareas y los 12 requisitos/21 escenarios se conservan. Las verificaciones históricas no afectadas se reutilizan; no se repiten suites completas ni revisión de vulnerabilidades.
- ComposePersistenceIT fuerza rutas source-pgdata/restore-pgdata bajo TempDir, rechaza D:/DOCKER_DATA, verifica mount tipo bind y la correspondencia host/contenedor mediante PG_VERSION y marcador. Comprueba recreación/historial y restauración en otra carpeta. down conserva el directorio, JUnit limpia solo su TempDir. Dos ejecuciones independientes verifican la reproducibilidad del caso afectado; no se repiten otras clases de prueba.

### Bind mount: primera verificación afectada satisfactoria

- Modelo Compose validado sin levantar desarrollo: source D:/DOCKER_DATA/POSTGRES_DATA, type=bind, create_host_path=false, imagen Trixie y publicación 127.0.0.1. Un DEMANDFLOW_DATA_DIR externo se respeta. Informe config-check.json en target/setup-database-bind-review.
- Primera ejecución seleccionada de ComposePersistenceIT: 1 prueba satisfactoria, cero fallos/errores/omitidas; caso 179.7 s. Se excluyó expresamente Surefire mediante selección sin coincidencias (NoUnitTestsRequested y surefire.failIfNoSpecifiedTests=false), sin repetir ni atribuir nuevos resultados a pruebas no afectadas. Log compose-bind-1.log y reportes run-1-failsafe.
- Comprobados PG_VERSION en el anfitrión y marcador visible desde el contenedor, tipo bind/ruta efectiva idéntica tras recreación, V1/checksum/ACL/historial conservados y restauración en otro bind temporal con dos arranques Java. down conserva ambas carpetas hasta la limpieza de TempDir de JUnit. Sin volúmenes administrados del proyecto.
- 7.1 vuelve a quedar completada (23/26); se realiza una segunda ejecución independiente únicamente del mismo caso afectado para reproducibilidad, sin suites generales ni nueva revisión de seguridad.

### Bind mount: segunda ejecución y guía contrastada

- Segunda ejecución independiente del mismo ComposePersistenceIT: BUILD SUCCESS; única clase seleccionada, sin repetir otras pruebas. Maven 3:10; se conserva el XML específico run-2-ComposePersistenceIT.xml junto al run-1-ComposePersistenceIT.xml y los dos logs bajo target/setup-database-bind-review. Los demás XML antiguos de target/failsafe-reports no pertenecen a estas invocaciones seleccionadas y no se agregan como pruebas nuevas.
- 7.3 completada nuevamente (24/26): guía explica carpeta anfitrión existente, Docker Desktop/permisos, no migración automática desde volúmenes antiguos, conservación tras down, y restauración con DEMANDFLOW_DATA_DIR propio en una carpeta nueva fuera de D:/DOCKER_DATA. Guarda/restaura la variable del entorno permanente, además del puerto. Se conservaron secretos, roles y procedimiento de respaldo lógico.
- Ambas pruebas demuestran archivos reales del anfitrión, persistencia tras recrear y recuperación coherente; no prueban contenido ni permisos de la unidad D, que quedan para preparación manual del operador. No se realizaron operaciones de lectura, creación, eliminación o migración sobre D:\DOCKER_DATA ni se levantó demandflow-local.

### Cierre del ajuste — 8.1 y 8.2

- Comando ejecutado dos veces, con entorno Spring/DemandFlow/Docker/Compose saneado: `mvnw.cmd -o -Ppersistence-it '-Dtest=NoUnitTestsRequested' '-Dsurefire.failIfNoSpecifiedTests=false' '-Dit.test=ComposePersistenceIT' '-Djava.io.tmpdir=E:/Cursos Compartidos/JAVA/DemandFlow/.cache/bind-test-tmp' verify`. No se repiten Surefire ni otras clases IT. Caso 1: 179.745 s; caso 2: 179.388 s; ambas construcciones satisfactorias, una prueba y cero fallos/errores/omitidas cada una.
- Cobertura afectada actualizada: S03 usa misma fuente bind y marcador anfitrión en lugar de identidad del volumen Docker; S19 usa carpetas temporales nuevas por ejecución y copia restaurada. S01/S02/S04/S05/S07/S09/S10/S13 se vuelven a observar dentro de ese mismo flujo Compose. Recuperación conserva propietarios, ACL efectivas e historial sobre otro directorio y dos arranques. El resto de S01–S21 conserva la evidencia previa porque no se modificaron sus implementaciones, dependencias ni configuración Spring. Se mantienen 26 tareas, 12 requisitos y 21 escenarios; D2 y la aceptación de riesgo no se reabren.
- `openspec.cmd validate setup-database --strict` y `git diff --check` satisfactorios. Se validó el modelo Compose con valor predeterminado y valor externo sin crear recursos en la ruta permanente. La publicación permanece exclusivamente 127.0.0.1. Sin cambios en imagen, secretos reales, roles, bootstrap, V1, Flyway o perfil persistence.
- Auditoría Docker final `target/setup-database-bind-review/final-docker-audit.json`: cero diferencias de contenedores y volúmenes respecto al inventario de esta revisión, cero contenedores temporales restantes y cero contenedores/volúmenes demandflow-local. No se emitieron operaciones sobre carpetas de D:\DOCKER_DATA ni sobre volúmenes ajenos.
- Artefactos/documentos actualizados: Compose, `.env.example`, propuesta, diseño D3/referencias D7–D9, delta de persistencia, tareas, guía, README y esta evidencia. La guía distingue creación manual de carpeta, conservación de datos con down, recuperación en otra carpeta y ausencia de migración automática desde el volumen anterior. La prueba requiere el directorio existente y verifica que down no borra archivos anfitrión; JUnit retira únicamente su TempDir.
- Cierre: 7.1/7.3/8.1/8.2 nuevamente verificadas para el ajuste, total 26/26. Resultado entregado a revisión humana, sin commit, push, merge, sync ni archive. El operador aún debe preparar/revisar la carpeta física y sus permisos y levantar el contenedor permanente manualmente. No se afirma haber validado esa instalación ni sus datos reales.

## Integración manual del entorno permanente — 2026-11-24

Procedencia: verificaciones manuales realizadas y comunicadas por el usuario el 24 de noviembre de 2026. El agente registra estos resultados aportados; no ejecutó estas pruebas ni inspeccionó el entorno permanente para corroborarlas. Las evidencias anteriores se conservan como registro histórico; la preparación y puesta en marcha que quedaban a cargo del operador cuentan ahora con los siguientes resultados manuales.

### PostgreSQL, almacenamiento y acceso

- Contenedor `demandflow-local-postgres-1` en estado `healthy`, con imagen `postgres:17.12-trixie` y puerto publicado exclusivamente en `127.0.0.1:5432`.
- Persistencia física comprobada en `D:\DOCKER_DATA\POSTGRES_DATA`; tres archivos de credenciales externas generados. No se registran sus valores ni se incorporan credenciales a este documento.
- Conexión satisfactoria desde VS Code a la base de datos `demandflow`; PostgreSQL 17.12 confirmado mediante consulta SQL.
- Roles `postgres`, `demandflow_migrator` y `demandflow_app` existentes; migrator y app sin privilegios de superusuario.
- Esquema `infra` propiedad de `demandflow_migrator`. Antes del arranque de Spring Boot no existían tablas de aplicación.

### Construcción e integración con Spring Boot

- Maven `package`: `BUILD SUCCESS`, 10 pruebas y cero fallos, errores u omisiones.
- Spring Boot 4.1.2 ejecutado con Temurin 21.0.13 y perfil `persistence`; arranque satisfactorio en 6.578 segundos.
- Flyway creó `infra.flyway_schema_history`. Se registró V1, `initialize application namespace`, con `success=true` y checksum `1306421182`, ejecutada por `demandflow_migrator` el `2026-11-24 15:24:47` (fecha y hora aportadas; sin zona horaria especificada).
- Migrator dispone de `USAGE` y `CREATE` sobre `infra`; app no dispone de ninguno de esos privilegios sobre dicho esquema.
- La consulta posterior del historial conserva un único registro V1 con su fecha original. Este resultado acredita la observación posterior comunicada; no se atribuye a un segundo arranque, recreación o restauración manual que el usuario no haya reportado.

### Alcance y punto de control

- Estas evidencias manuales complementan las pruebas automatizadas previas y documentan la integración del entorno permanente. No sustituyen ni repiten las verificaciones anteriores de aislamiento, persistencia tras recreación o recuperación.
- Se conservan las 26/26 tareas completadas y las decisiones aprobadas. Esta actualización afecta exclusivamente a `evidence.md`; no modifica código, configuración, credenciales ni recursos Docker y no reabre la revisión de vulnerabilidades aceptada.
- Verificaciones del agente para esta actualización documental: únicamente `openspec.cmd validate setup-database --strict` y `git diff --check`; sus resultados se registran a continuación tras ejecutarlas. Sin nuevas pruebas de integración, commit, push, merge ni archive. Pendiente revisión humana de esta actualización.
- Resultado de las verificaciones documentales: OpenSpec estricto válido y git diff --check con salida 0; únicamente avisos informativos LF/CRLF sobre archivos previamente presentes en el diff.
