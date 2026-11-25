# Evidencias de implementación — grupo 1

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
