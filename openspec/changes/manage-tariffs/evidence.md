# Evidencias de implementación — manage-tariffs

## Autorización y punto de partida

El usuario autorizó expresamente solo 1.1, 1.2 y 1.3 conforme a ADR-008, con detención obligatoria al terminar 1.3 o ante impedimento. No autorizó grupos 2–10 ni commit/push. Se conservan D1–D9 y T1–T9; la aprobación documental no se interpreta como permiso general de implementación.

Tarea 1.1 completada: rama `feature/manage-tariffs`, directorio inicialmente limpio, HEAD y referencia local/remota en `ea3118576642b50712c5868b02400e8ab2c9e357` (`docs(openspec): aprobar planificacion y diseno de manage-tariffs`). `git ls-remote --heads origin feature/manage-tariffs` confirmó ese mismo hash remoto; divergencia local 0/0. OpenSpec `spec-driven`: cuatro artefactos completos, 0/33 tareas hechas, todas las casillas inicialmente sin marcar.

SHA-256 inicial de `src/main/resources/db/migration/V1__initialize_application_namespace.sql`: `E67492D16CFB2E92CD85CF3A2DEB76417DB1833B1002071D349B13427F8D3D61`.

Se revisaron propuesta, cuatro especificaciones, diseño y tareas del cambio, fuentes funcionales HU-001/002 y RN/RF aplicables en `docs/requirements/`, ADR aplicables y estándares de ingeniería. T6 limita las nuevas dependencias; T9/LP4.S1/S3 justifican soporte aislado. D2 excluye demandas y HU-006; no se incorporan correcciones editoriales. Ningún escenario funcional de los 71 se declara implementado por preparar herramientas.

## Comandos y resultados iniciales

- `git status --short --branch`, `git branch --show-current`, `git log -1`, `git rev-list --left-right --count HEAD...origin/feature/manage-tariffs`: estado inicial descrito arriba.
- `openspec list --json`, `openspec status --change manage-tariffs --json`, `openspec instructions apply --change manage-tariffs --json`: raíz local y estado ready, 33 tareas pendientes. La instrucción general de continuar queda limitada por la autorización del usuario al grupo 1.
- `mvnw.cmd --version`: Maven 3.9.11, Java 21.0.13 Eclipse Adoptium. La primera ejecución en sandbox falló por acceso a la caché; la ejecución autorizada fuera del sandbox finalizó con código 0. La consulta remota también se repitió con autorización tras bloqueo de red del sandbox.

## Tarea 1.2 — Dependencias y compatibilidad

Único cambio productivo: `pom.xml` agrega `spring-boot-starter-thymeleaf`, `spring-boot-starter-validation`, `spring-boot-starter-security` y `spring-security-test` (test), sin versiones propias. T6 y ADR-002/005/007 aplicados; no se añade otra biblioteca directa, plugin ni configuración de seguridad del grupo 7.

`mvnw.cmd -B dependency:tree -DoutputFile=target/manage-tariffs-dependency-tree.txt test-compile` terminó con código 0, release Java 21, tres fuentes productivas y trece fuentes de prueba compiladas. El árbol efectivo confirma Boot 4.1.2, Spring Framework 7.0.10, Thymeleaf 3.1.5.RELEASE, Hibernate Validator 9.1.5.Final/Jakarta Validation 3.1.1, Spring Security 7.1.2, JUnit Jupiter 6.0.3 y Testcontainers 2.0.5. `mvnw.cmd -B test` terminó con código 0: 10 pruebas, cero fallos/errores/omitidas (DefaultStartupTests 3, DemandFlowApplicationTests 1, PersistenceConfigurationTests 6). Reportes locales en `target/surefire-reports/` y resumen de ejecución en `target/manage-tariffs-unit.log`, no versionados.

### Revisión de seguridad (2026-11-25)

Revisión manual acotada de avisos primarios y condiciones de uso; no es un escaneo exhaustivo ni certifica ausencia de vulnerabilidades transitivas. No se actualizan versiones gestionadas ni se acepta una excepción de seguridad unilateralmente.

- Hibernate Validator: GHSA-gv36-fx3r-29p5/CVE-2026-93879 declara 9.1.5.Final corregida; coincide con la versión resuelta. Fuente: `https://github.com/hibernate/hibernate-validator/security/advisories/GHSA-gv36-fx3r-29p5`.
- Thymeleaf: GHSA-c9ph-gxww-7744 afecta versiones hasta 3.1.4; se resolvió 3.1.5. Fuente: `https://github.com/thymeleaf/thymeleaf/security/advisories/GHSA-c9ph-gxww-7744`.
- Spring Security 7.1.2 figura en rangos afectados de avisos del 19 de noviembre: CVE-2026-59273 (binding de ELRequestMatcher), 59329 (autenticación reactiva), 59330 (WebAuthn), 59331 (autenticación RSocket), 59333 (matchers IP) y 59400 (OAuth2 ClientRegistration). Fuentes: `https://spring.io/security/cve-2026-59273/`, `https://spring.io/security/cve-2026-59329/`, `https://spring.io/security/cve-2026-59330/`, `https://spring.io/security/cve-2026-59331/`, `https://spring.io/security/cve-2026-59333/`, `https://spring.io/security/cve-2026-59400/`. No se encontraron esos usos en `src/` ni dependencias OAuth2/WebAuthn/RSocket; el diseño no incorpora esos mecanismos. Esto limita la exposición observada, pero no elimina los avisos del componente ni autoriza excepciones o cambios de versión. La configuración web sin autenticación aún corresponde al grupo 7; el grupo 1 no acredita su cumplimiento ni prepara un despliegue utilizable.

## Tarea 1.3 — Preparación de pruebas aisladas

Archivos de soporte modificados: `src/test/java/io/github/alejorom/demandflow/PostgresTestSupport.java`, `src/test/java/io/github/alejorom/demandflow/MigrationEvolutionIT.java` y `src/test/java/io/github/alejorom/demandflow/JarFailuresIT.java`. Nuevo `src/test/java/io/github/alejorom/demandflow/PersistenceSupportIT.java`. Los fixtures técnicos de `src/test/resources/migrations/valid/` y `src/test/resources/migrations/failing/` se renumeraron de V2 a V4, actualizando referencias y expectativa de versión; no son migraciones productivas ni crean tablas. Las futuras V2/V3 siguen sin existir.

El soporte copia exclusivamente V1 a TempDir y expone un Flyway técnico sin Spring/JPA, con cuenta migradora y controles de esquema, validación, clean deshabilitado y sin baseline/out-of-order. La aplicación completa mantiene su método de arranque separado. El nuevo smoke usa el contenedor temporal, roles/secrets sintéticos y binding loopback del soporte existente, comprueba V1 exacta, PostgreSQL 17.12, propiedad migradora, permisos runtime, cero tablas de negocio y segunda migración sin cambios de historial. No anticipa las adaptaciones de catálogo de los grupos 2/3/9.

Comandos reproducibles desde la raíz, con Java 21 y Maven Wrapper; no configurar credenciales reales ni rutas permanentes:

```powershell
# Positivo: necesita Docker accesible e imagen fija disponible o descargable.
.\mvnw.cmd -B -Ppersistence-it '-Dit.test=PersistenceSupportIT' verify

# Negativo: V1 de prueba deliberadamente ausente. Debe terminar distinto de cero.
# Confirmar antes que target/prerequisite-absent/V1.sql no exista.
.\mvnw.cmd -B -Ppersistence-it '-Dit.test=PersistenceSupportIT' '-Ddemandflow.test.v1-source=target/prerequisite-absent/V1.sql' failsafe:integration-test failsafe:verify

# Regresión de soporte/fixtures, con el JAR ya empaquetado por el primer comando.
.\mvnw.cmd -B -Ppersistence-it '-Dit.test=PersistenceSupportIT,MigrationEvolutionIT,JarFailuresIT' test-compile failsafe:integration-test failsafe:verify
```

El parámetro `demandflow.test.v1-source` solo selecciona el archivo fuente del fixture técnico; no configura conexiones. Su valor predeterminado es la V1 productiva sin modificar. La ruta inexistente falla antes de generar secretos o iniciar contenedores. Un Docker ausente tampoco se omite ni cambia el destino a desarrollo: la excepción de Testcontainers se propaga.

Resultados ya verificados:

- Positivo `verify`: código 0; 10 unitarias y 1 smoke, cero fallos/errores/omitidas. Registro local `target/manage-tariffs-smoke.log`.
- Negativo: Maven código 1, error `Isolated persistence prerequisite missing: V1 migration source`, sin omisión exitosa ni uso de otro destino. El envoltorio de comprobación terminó 0 al confirmar el fallo esperado. Registro local `target/manage-tariffs-prerequisite-negative.log`.
- Regresión de soporte/fixtures: código 0, 4 pruebas, cero fallos/errores/omitidas: JarFailuresIT (1, 81.96 s, con sus casos de configuración/conexión/checksum/SQL), MigrationEvolutionIT (2, 13.43 s) y PersistenceSupportIT (1, 5.523 s). Registro local `target/manage-tariffs-support-regression.log` y reportes `target/failsafe-reports/`. Solo se verificaron fixtures técnicos ya existentes y soporte del grupo 1; no las futuras migraciones V2/V3 ni el modelo de negocio.

Los logs/reportes de `target/` no se versionan; contienen evidencia técnica de pruebas y no deben copiarse completos. No se inspeccionó ni intervino el contenedor permanente; los contenedores son creados/cerrados por Testcontainers y los secretos pertenecen a TempDir.

## Validaciones finales

`openspec status --change manage-tariffs`: 4/4 artefactos documentales completos. `openspec instructions apply --change manage-tariffs --json`: 3/33 tareas completas, solo 1.1–1.3, 30 pendientes. `openspec validate manage-tariffs --strict`: válido. `git diff --check`: código 0; únicamente advertencia de conversión LF/CRLF según Git, sin error de espacios. Se comprobaron además los cuatro archivos nuevos sin seguimiento para espacios finales y marcadores de conflicto: cero incidencias.

`git diff --exit-code` confirmó que `src/main`, `docs/`, canónicas, propuesta, diseño, especificaciones aprobadas y `compose.yaml` permanecen intactos. Hash V1 idéntico al inicial. `git status --short` muestra exclusivamente las modificaciones del grupo 1 enumeradas aquí; las eliminaciones V2 técnicas y los archivos V4 sin seguimiento corresponden a renumeraciones de fixtures, no a migraciones productivas. No se ejecutó staging, commit, push, merge, sync ni archive.

## Estado y recuperación

1.1, 1.2 y 1.3 verificadas: 3/33 completas; las otras 30 permanecen sin marcar. Se detiene la ejecución al finalizar el grupo 1 aunque OpenSpec indique ready. No hay impedimento técnico observado en estas verificaciones; los avisos del componente Security registrados arriba deben acompañar la revisión humana, sin inferir que se autoriza actualizar versiones o desplegar la configuración incompleta. No se ejecutó un escáner exhaustivo de vulnerabilidades ni se acreditan controles web del grupo 7.

Para recuperar otra sesión: HEAD sigue siendo `ea3118576642b50712c5868b02400e8ab2c9e357`, con cambios locales sin staging/commit/push. Revisar `git status`, este archivo y `openspec instructions apply --change manage-tariffs --json`. Los cambios esperados son `pom.xml`, tres fuentes de soporte/pruebas existentes, el nuevo smoke, dos fixtures renumerados, tres casillas de `tasks.md` y este archivo. V1 sigue con el SHA-256 inicial. No reejecutar la prueba negativa esperando éxito Maven ni confundir sus reportes con el positivo posterior. No continuar al grupo 2 sin revisión y autorización humana expresa; tampoco crear el commit de cierre sin autorización.

## Grupo 2 — Autorización y estado inicial

Registro acumulativo posterior al cierre aprobado del grupo 1 en `df796a6b4a9c45899b7897c0e426c1f6ce3883db`. El usuario autorizó exclusivamente 2.1–2.4, incluida la ampliación operativa de 2.4; no grupos 3–10 ni commit/push. Git inicialmente limpio en `feature/manage-tariffs`, divergencia 0/0; `git ls-remote --heads origin feature/manage-tariffs` confirmó el mismo commit remoto. OpenSpec spec-driven, raíz local, ready, 3/33 tareas hechas únicamente 1.1–1.3. Se releyeron propuesta, cuatro specs, diseño, tareas y evidencias; se conservan D1–D9/T1–T9 y las observaciones de seguridad previas sin resolverlas ni aceptar excepciones.

V1 conserva SHA-256 `E67492D16CFB2E92CD85CF3A2DEB76417DB1833B1002071D349B13427F8D3D61`. No se inspeccionó, conectó ni operó la BD/contenedor permanente o su almacenamiento. Las pruebas reutilizan exclusivamente `PostgresTestSupport`, imagen `postgres:17.12-trixie`, roles/secretos sintéticos y TempDir; no usan Compose de desarrollo.

### 2.1 — V2 y catálogo físico

Creado `src/main/resources/db/migration/V2__create_tariff_catalog.sql`: tres tablas exactamente de T2, versiones/identidades, checks, exclusión GiST global inclusiva sin extensión, unicidad de combinación y FKs sin cascada; únicamente seed Soporte/Feature. Nuevo `src/test/java/io/github/alejorom/demandflow/CatalogSchemaIT.java` para metadatos, tipos, restricciones y extremos. No implementa validación de entrada, reglas transaccionales de estados, JPA ni concurrencia de grupos posteriores.

`mvnw.cmd -B -Ppersistence-it -Dit.test=CatalogSchemaIT verify`: código 0; 10 unitarias sin fallos/errores/omitidas y 3 pruebas PostgreSQL sin fallos/errores/omitidas (15.86 s). Registro local `target/manage-tariffs-group2-v2.log`; reportes Surefire/Failsafe en target. Cobertura física: TP2/TP3/TP4 (etiquetas repetidas y límites, duración flexible, un día, solapes totales/parciales/extremo, adyacencia, propio id), HR1/HR2/HR4 (tipos, ausencia de tarifas automáticas, combinación incluso inactiva, valores mínimos/máximos, NaN/infinidades/null/cero/negativos y FKs). El rechazo de escala de entradas sigue en grupo 3: NUMERIC puede convertirla y no se atribuye a estos checks.

2.1 marcada tras evidencia; progreso 4/33 confirmado por instrucciones apply. 2.2–2.4 aún pendientes en este punto del registro.

### 2.2 — V3, auditoría física y privilegios

Creado `src/main/resources/db/migration/V3__create_catalog_audit.sql` según T2/T5: eventos por campo con UUID de mutación, FKs alternativas coherentes, antes/después distintos, operación/campo/origen restringidos, instante TIMESTAMP(6) WITH TIME ZONE e índices aprobados. Grants explícitos sobre columnas y secuencias; sin grants por defecto/globales, DELETE de negocio, cambio de asociaciones o alteración ordinaria del historial. No hay triggers ni escritor de auditoría anticipado.

Nuevo `src/test/java/io/github/alejorom/demandflow/CatalogAuditSchemaIT.java`. `mvnw.cmd -B -Ppersistence-it -Dit.test=CatalogAuditSchemaIT test-compile failsafe:integration-test failsafe:verify`: código 0; 3 pruebas, cero fallos/errores/omitidas (17.27 s). Registro local `target/manage-tariffs-group2-v3.log`. Se verificaron columnas/tipos exactos de las cuatro tablas, once índices aprobados, propietarios migradores, nullabilidad/FKs, UTC/microsegundos, checks y unicidad de campo/mutación. Runtime insertó catálogo/eventos y actualizó columnas permitidas; obtuvo bloqueo FOR UPDATE y recibió SQLSTATE 42501 en alteración/borrado/truncate de historial, reasignaciones, ids explícitos, CRUD de tipos, DDL, acceso a infra y setval. Los eventos/tipos permanecieron intactos tras rechazos. Cobertura física de CA5/LP1.S2; atomicidad de casos de uso y validación de JPA siguen pendientes en sus grupos.

### 2.3 — Implementación parcial y detención por impedimento

Se adaptó `src/test/java/io/github/alejorom/demandflow/MigrationEvolutionIT.java` para preparar V1 con Flyway/JDBC sin JPA, comprobar evolución V1→V2/V3, base vacía, repetición, checksum modificado, fixture V4 fallido y fallo deliberado de V3. Este último modifica únicamente una copia temporal pendiente de V3 y exige rechazo del arranque persistente, rollback de tabla/grants de auditoría y conservación de V2 confirmada.

`mvnw.cmd -B -Ppersistence-it -Dit.test=MigrationEvolutionIT test-compile failsafe:integration-test failsafe:verify`: **código 1**, 4 pruebas, 0 fallos de aserción, **2 errores**, 0 omitidas. Registro local `target/manage-tariffs-group2-evolution.log`, detalle `target/failsafe-reports/TEST-io.github.alejorom.demandflow.MigrationEvolutionIT.xml`.

Resultados reales por caso:

- `newVersionPreservesV1AndChecksumChangesAreRejectedWithoutRepair`: pasó, incluida preservación de V1, aplicación V2/V3 y fixture válido V4, y rechazo de checksum alterado sin repair.
- `failedV3RollsBackItsDdlAndGrantsButKeepsCommittedV2AndPreventsStartup`: pasó; no se obtuvo una aplicación disponible, V2 quedó confirmada, tabla de auditoría ausente y grants de V3 revertidos.
- `emptyDatabaseMigratesExactlyV1V2V3AndSecondRunDoesNothing`: error al obtener conexión migradora, PostgreSQL temporal respondió `FATAL: the database system is starting up`; no se acredita este caso.
- `failedV4RollsBackItsCommentAndDoesNotRecordSuccess`: mismo error de conexión inicial; no se acredita este caso en esta ejecución.

El soporte existente espera puerto abierto (`Wait.forListeningPort`), lo que en esta ejecución no aseguró disponibilidad JDBC del servidor tras el arranque. Los resultados apuntan a una carrera de disponibilidad de la infraestructura temporal; no demuestran un defecto de V2/V3. No se cambió la estrategia de espera, no se añadieron reintentos ni se repitió la suite para ocultar el fallo. Se aplica la condición expresa del usuario de detenerse ante un impedimento. **2.3 permanece sin marcar; 2.4 no se inició y la guía operativa no fue modificada.** Tampoco se ejecutaron grupos 3–10.

### Recuperación del grupo 2

HEAD de referencia: `df796a6b4a9c45899b7897c0e426c1f6ce3883db`, sin nuevos commits ni staging. Solo 1.1–1.3 y 2.1–2.2 están marcadas: **5/33 completas**, 28 pendientes. Cambios locales esperados: V2/V3 productivas nuevas, dos nuevas clases IT de esquema/permisos, `MigrationEvolutionIT.java`, las dos casillas y esta ampliación de evidencia. Propuesta/specs/diseño, dependencias/configuración, V1, Docker/secretos y guía permanecen intactos.

La próxima sesión debe leer esta sección, verificar Git e instrucciones apply y esperar resolución/revisión humana del impedimento antes de retomar 2.3. Evaluar una espera verificable de disponibilidad de PostgreSQL temporal dentro del alcance que se autorice; no usar la base permanente, pausas arbitrarias o relajación de timeouts productivos. Después de resolver y acreditar 2.3 corresponderá 2.4 (guía), nunca iniciar grupo 3 por inercia. Las pruebas de JAR/metamodelo del grupo 9 que aún presuponen solo V1 siguen pendientes según el plan; no se declara verde la suite completa ni correspondencia JPA del catálogo.

Todas las conexiones de esta sesión procedieron del puerto asignado por los contenedores de prueba; no se ejecutó Compose ni se conectó a la BD permanente. Los contenedores se cierran por try-with-resources, incluidos los casos fallidos; no hubo limpieza global ni eliminación de recursos ajenos. No se leyó ni modificó `D:/DOCKER_DATA/POSTGRES_DATA` ni credenciales permanentes.

Validaciones tras la detención: `openspec status --change manage-tariffs` informa 4/4 artefactos; `openspec validate manage-tariffs --strict` válido; instrucciones apply confirman 5/33, sin casillas posteriores marcadas; `git diff --check` código 0 y cuatro archivos nuevos revisados sin espacios finales ni conflictos. SHA-256 de V1 idéntico al inicial. `git diff --exit-code` confirmó intactos código Java productivo, configuración, pom, Compose/bootstrap, docs (incluida la guía), canónicas y artefactos aprobados salvo casillas de tareas. `git status --short` contiene únicamente los cambios enumerados en recuperación. Estas validaciones documentales/Git satisfactorias no convierten la ejecución fallida de 2.3 en éxito.

## Reanudación autorizada — espera JDBC y tareas 2.3–2.4

El usuario autorizó expresamente resolver la disponibilidad del PostgreSQL temporal en `PostgresTestSupport`, reejecutar 2.3 y regresiones afectadas y, después de acreditarlas, completar únicamente 2.4. Se verificó `feature/manage-tariffs`, HEAD `df796a6b4a9c45899b7897c0e426c1f6ce3883db`, estado 5/33 y conservación de los cambios locales anteriores. Los registros anteriores se conservan como resultados históricos, incluido el fallo real; esta sección documenta la continuación, no los sustituye.

Hashes SHA-256 de las migraciones al retomar: V1 `E67492D16CFB2E92CD85CF3A2DEB76417DB1833B1002071D349B13427F8D3D61`; V2 `4BA8864DA9AACAC7F9F331E5C23461FE355AF15DD0B0466B525C7E25D19D090B`; V3 `97618275A8A7A59EB9D1FA455F92AEFCEA2FE85694FC2F33BCD7344848DDA424`. Ninguna se modifica en esta continuación.

Corrección del soporte: `PostgresTestSupport.JdbcReadiness` sustituye el puerto abierto por consultas JDBC `select current_user, 1` autenticadas con los dos roles sintéticos, sobre el puerto loopback asignado al contenedor de prueba. Reutiliza Awaitility 4.3.0, ya presente en el árbol de dependencias de test, sin cambiar pom ni introducir dependencias. Plazo de arranque de prueba 120 s conservado; sondeos condicionados cada 100 ms sin demora inicial, con límites nativos de conexión/login/socket/cancelación y consulta de 1 s específicos del sondeo. No se añade ninguna pausa fija de arranque ni se cambian URL/timeouts productivos.

Solo SQLSTATE 57P03, 08001 y 08006 permiten otro sondeo de disponibilidad. Autenticación, SQL o estados desconocidos abortan con SQLSTATE saneado, sin contraseña/URL; el plazo agotado también falla. Las operaciones Flyway y de aplicación mantienen su política anterior, sin reejecuciones para ocultar sus errores. Nuevos `PostgresReadinessTests` (plazo agotado sin Docker en puerto aislado) y `PostgresReadinessIT` (ambos roles consultan y contraseña de prueba rechazada falla inmediatamente). Compilación y regresiones seleccionadas en ejecución; todavía no se marca 2.3 por este cambio de código solo.

### Resultados de la reanudación de 2.3

Comando ejecutado desde la raíz con Maven Wrapper/Java 21:

```powershell
.\mvnw.cmd -B -Ppersistence-it '-Dit.test=MigrationEvolutionIT,PostgresReadinessIT,PersistenceSupportIT,BootstrapIT,NativeFailureIT,CatalogSchemaIT,CatalogAuditSchemaIT' verify
```

Código **0**, BUILD SUCCESS: **11 unitarias y 14 de integración**, cero fallos/errores/omitidas. Registro local `target/manage-tariffs-group2-jdbc-readiness.log`, reportes Surefire/Failsafe de target, no versionados. Casos: DefaultStartupTests 3, DemandFlowApplicationTests 1, PersistenceConfigurationTests 6 y PostgresReadinessTests 1; BootstrapIT 1, CatalogAuditSchemaIT 3, CatalogSchemaIT 3, MigrationEvolutionIT 4, NativeFailureIT 1, PersistenceSupportIT 1 y PostgresReadinessIT 1.

Los cuatro casos de MigrationEvolutionIT pasaron (21.63 s), incluidos los dos anteriormente fallidos: base vacía V1/V2/V3 y repetición sin reaplicar; evolución desde V1 con historial/checksum original conservado y rechazo del checksum manipulado en copia; rollback del fixture V4; rechazo del arranque ante V3 fallida con V2 confirmada y DDL/grants de V3 revertidos. NativeFailureIT verificó además los límites productivos Hikari/JDBC/roles y las credenciales/conexión rechazadas sin filtraciones. BootstrapIT y las suites físicas volvieron a verificar roles, aislamiento, restricciones y permisos con el soporte ajustado.

La prueba de plazo agotado pasó sin Docker (1.341 s de caso completo); la de ambos roles y rechazo de credenciales pasó (3.311 s). La ejecución fallida anterior sigue documentada y no se contabiliza como éxito: se resolvió su causa mediante la espera JDBC verificable, no mediante repetir migraciones indiscriminadamente. **2.3 se marca completada tras estos resultados; progreso esperado 6/33 antes de 2.4.** No se ejecutó la suite completa heredada que todavía requiere adaptaciones previstas en grupo 9; sus escenarios no se declaran acreditados por esta selección.

### 2.4 — Ampliación de la guía operativa

Se añadió únicamente una ampliación al final de `docs/engineering/local-persistence-guide.md`: efectos de V2/V3, propiedad y privilegios por objeto/columna/secuencia, restricciones de catálogo, alcance append-only, comando aislado realmente ejecutado y espera JDBC de pruebas. Distingue ese comando de una aplicación futura sobre la BD permanente, que requiere autorización operativa separada, identificación del destino y respaldo recuperable. Describe V2 confirmada ante fallo V3, diagnóstico sin secretos, prohibición de editar versiones aplicadas o reparar/saltar historial, recuperación hacia adelante y restauración en destino aislado sin recrear almacenamiento permanente.

Aceptación comprobada: el texto previo de la guía se conserva íntegro como prefijo (comparado con HEAD, normalizando solo saltos de línea), las referencias internas existen y el contenido se contrastó con V2/V3, T2/T5/T8/T9 y las pruebas 2.1–2.3. El comando documentado es el mismo que pasó en esta reanudación. No se ejecutaron procedimientos permanentes ni se corrigieron notas históricas/editoriales. No se atribuyen UI, reglas de aplicación o JPA aún pendientes. Se marca 2.4 tras esta revisión.

### Punto de recuperación tras completar el grupo 2

Se detiene aquí: **7/33 completas (1.1–1.3 y 2.1–2.4), 26 pendientes**, grupo 3 sin iniciar. HEAD sigue en `df796a6b4a9c45899b7897c0e426c1f6ce3883db`; cambios locales conservados y sin staging/commit/push. La espera JDBC y sus dos pruebas se añaden a los cambios previos del grupo 2; también la guía y este registro acumulativo. Ninguna migración V1/V2/V3 cambió respecto a los hashes anotados al retomar.

El impedimento de disponibilidad quedó resuelto y acreditado por la ejecución exitosa descrita arriba. Quedan la revisión humana del grupo 2, el cierre Git cuando se autorice y una autorización independiente para grupo 3. Las observaciones de seguridad y las adaptaciones de regresión/JPA/web de grupos posteriores permanecen pendientes según el plan. No afirmar que se verificaron las 33 tareas, los 71 escenarios funcionales o toda la suite heredada.

Para otra sesión: revisar este último punto, `git status` e instrucciones apply; comprobar 7/33 y que no se haya concedido autorización adicional. No migrar ni inspeccionar el entorno permanente. Durante esta continuación solo se iniciaron recursos de prueba mediante Testcontainers, con roles/secretos sintéticos y TempDir; no hubo comandos Compose de desarrollo, operaciones sobre su contenedor/BD ni acceso a `D:/DOCKER_DATA/POSTGRES_DATA`. No se cambiaron imagen, dependencias, configuración/timeouts productivos, requisitos, ADR ni diseño.

Validaciones finales de esta continuación: `openspec status --change manage-tariffs` 4/4 artefactos completos; `openspec validate manage-tariffs --strict` válido; instrucciones apply 7 completas/26 pendientes, sin casillas posteriores a 2.4 marcadas. `git diff --check` código 0; seis archivos nuevos revisados sin espacios finales ni marcadores de conflicto. Se verificaron hashes V1/V2/V3, preservación acumulativa del texto previo de la guía y existencia de sus referencias. Diff de archivos protegidos sin cambios; `git status --short` contiene solo guía/evidencia/tareas, soporte/pruebas del grupo 2 y las dos migraciones conservadas. No se ejecutaron commit, push, merge, sync ni archive.
