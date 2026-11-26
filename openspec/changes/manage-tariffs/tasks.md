# Tasks

Plan de implementación futura de `manage-tariffs`, esquema `spec-driven`. La aprobación expresa de `design.md` cubre T1–T9 y conserva D1–D9; autoriza crear esta planificación, no ejecutarla. Todas las casillas comienzan pendientes. Antes de realizar trabajo de implementación se requiere autorización expresa independiente conforme a `docs/architecture-decisions/ADR-008-openspec-agents.md`.

Fuentes: `openspec/changes/manage-tariffs/proposal.md`, `openspec/changes/manage-tariffs/design.md`, las cuatro especificaciones del cambio y la línea base `v1.0.0-docs`. Aplican `docs/requirements/functional-requirements.md`, `docs/requirements/business-rules.md`, `docs/requirements/traceability-matrix.md`, `docs/architecture/architecture.md`, `docs/architecture/data-model.md` y ADR-001 a ADR-008 de `docs/architecture-decisions/`. Las referencias RN/RF/ADR de cada requisito se heredan a sus tareas y pruebas.

TP, HR, CA y LP identifican requisitos en el orden de los archivos `specs/tariff-periods/spec.md`, `specs/hourly-rates/spec.md`, `specs/catalog-audit/spec.md` y `specs/local-persistence/spec.md` de este cambio. S1..Sn identifica sus escenarios locales, conforme a T9; no modifica las especificaciones. Cada escenario requiere evidencia automatizada identificable; sus alternativas deben parametrizarse o tener casos separados. La matriz final asigna cobertura, no acredita pruebas ejecutadas.

Todas las pruebas PostgreSQL previstas se ejecutarán exclusivamente en entornos temporales aislados con imagen `postgres:17.12-trixie`, roles y secretos sintéticos y directorios de prueba. No se utilizarán la BD de desarrollo, su contenedor ni `D:/DOCKER_DATA/POSTGRES_DATA`. Las referencias a migraciones en tareas describen su creación futura y aplicación en pruebas, nunca un despliegue permanente implícito. No hay tareas de commit, push, merge, sync ni archive.

## 1. Verificaciones previas y dependencias aprobadas

Dependencia: autorización independiente de implementación. Este grupo no la concede.

- [x] 1.1 Verificar rama `feature/manage-tariffs`, cambios locales, aprobación de los artefactos y límites D1–D9/T1–T9 antes de implementar; registrar como evidencia el estado inicial y el hash de V1, y detenerse si hay conflictos o falta autorización. Aceptación: revisión trazable a ADR-008 sin cambios ajenos, demandas, HU-006 ni correcciones editoriales incorporadas.
- [x] 1.2 Incorporar únicamente los starters Thymeleaf, Validation y Security y `spring-security-test` de T6, gestionados por Boot 4.1.2; verificar con Maven Wrapper el árbol efectivo, Java 21/JUnit Jupiter 6 y compilación, y registrar revisión de compatibilidad y seguridad sin nuevas dependencias o excepciones no aprobadas. Aceptación: versiones conservadas y cualquier incompatibilidad que exija cambiar T6 se devuelve a revisión.
- [x] 1.3 Preparar el soporte de pruebas reutilizando Testcontainers, cuentas sintéticas y TempDir existentes; separar preparación V1 por Flyway/JDBC del arranque JPA completo y numerar fixtures después de V3. Verificar una prueba de humo aislada y el fallo explícito por prerrequisito ausente; documentar cómo invocarlas sin rutas o secretos permanentes. T9; LP4.S1/S3.

## 2. Migraciones, restricciones y permisos

Dependencia: grupo 1. Las pruebas de este grupo usan Flyway/JDBC sin necesitar todavía entidades o casos de uso.

- [x] 2.1 Crear `V2__create_tariff_catalog.sql` con `tariff_period`, `requirement_type` y `hourly_rate`, columnas, identidades, versiones, claves, checks, índices y exclusión GiST inclusiva para todos los estados exactamente según T2/T9; insertar solo `(1, 'Soporte', 'Soporte')` y `(2, 'Feature', 'Feature')`. Verificar mediante pruebas PostgreSQL metadatos, FKs sin cascada, valores monetarios finitos positivos dentro de NUMERIC(15,2), etiqueta no única, tipos fijos y ausencia de tarifas automáticas; cubrir solapes, extremos compartidos, adyacencia y combinaciones reservadas incluso inactivas. V1 debe conservar su hash. TP2–TP4; HR1/HR2/HR4; LP3.
- [x] 2.2 Crear `V3__create_catalog_audit.sql` con `catalog_audit_event`, FKs alternativas coherentes con entidad, checks de operación/campo/origen/antes-después, índices y UNIQUE `(mutation_id, field)` de T2/T5. Incluir grants explícitos de tablas, columnas y secuencias: runtime sin DELETE, sin UPDATE/DELETE/TRUNCATE de auditoría, tipos solo SELECT, sin propiedad/CREATE ni acceso a `infra`; migrador separado. Aceptación: pruebas con roles reales permiten inserciones autorizadas y deniegan alteración del historial, reasignaciones y privilegios excluidos sin ampliar permisos. CA5; LP1.S2.
- [x] 2.3 Adaptar `MigrationEvolutionIT` y las verificaciones de migración para base vacía y V1 existente; verificar orden V1→V2→V3, historial/checksum V1 preservados, segunda ejecución sin reaplicar y fallo transaccional de V3 sin disponibilidad aunque V2 quede confirmada. Conservar pruebas de checksum alterado y SQL fallido con fixtures posteriores a V3, sin repair/baseline/out-of-order ni tablas ficticias. LP3.S1–S2; garantías canónicas de Flyway.
- [x] 2.4 Ampliar acumulativamente la guía operativa `docs/engineering/local-persistence-guide.md` con el alcance técnico V2/V3, permisos y diagnóstico, recuperación hacia adelante y prohibición de editar migraciones aplicadas; distinguir ejecución aislada de despliegue permanente y respaldo recuperable previo. Aceptación: instrucciones contrastadas con 2.1–2.3, sin ejecutar el procedimiento permanente, modificar garantías canónicas ni corregir referencias históricas de `setup-database` o `data-model.md`.

## 3. Dominio, JPA y registro condicionado

Dependencia: grupo 2. Las reglas unitarias no requieren Spring ni PostgreSQL.

- [x] 3.1 Implementar reglas de período: normalización común de espacios exteriores, rechazo de blanco, máximo 100 puntos de código, fechas obligatorias ISO representables e inicio ≤ fin, duración flexible y transiciones explícitas. Verificar con pruebas unitarias null/blancos, 100/101, caracteres suplementarios, duraciones anual/semestral/especial, un día, fechas inválidas y redundancias; no exigir etiqueta única ni anualidad. T1/T3; TP2.S1–S4, TP3.S1–S3, TP5.S3.
- [x] 3.2 Implementar reglas de tarifa con BigDecimal sin paso por double: obligatoriedad, positividad, límites, escala previa a normalización y combinación inmutable; comparar cambios económicos con `compareTo`. Verificar 0.01/máximo exactos, null/no numérico/cero/negativos, 12.345/12.340 y exceso de precisión; 12.3 y 12.30 deben ser equivalentes sin corrección ficticia. T3; HR3/HR4; CA4.S1.
- [x] 3.3 Mapear `TariffPeriod`, `RequirementType` y `HourlyRate` según T3: ids asignados/IDENTITY, `@Version Long` en editables, relaciones LAZY unidireccionales no opcionales y columnas de asociación no actualizables, sin cascadas destructivas. Implementar repositorios parametrizados de solape excluyendo propio id, combinación, tipos, proyección de periodId, lecturas y bloqueo del período. Aceptación: integración PostgreSQL con Hibernate validate prueba metamodelo real, consulta de inactivos, exactitud decimal, bloqueo y SQL compatible con permisos runtime. T1–T4; LP1/LP2, TP4.S4, HR1–HR4.
- [x] 3.4 Condicionar configuración, escaneo de entidades, repositorios y futuros componentes persistentes al perfil `persistence`, sin beans JPA incondicionales. Verificar inmediatamente `DefaultStartupTests` sin BD, incluso con URLs presentes, y arranque persistente con tablas reales; conservar exclusiones, Flyway antes de JPA, `ddl-auto=validate`, `open-in-view=false`, SQL init deshabilitado y tiempos/secretos existentes. Documentar el contrato de activación sin editar canónicas. T8; LP1.S1, LP2.S1, LP4.S2; `application-startup` completo.

## 4. Escritor de auditoría transaccional

Dependencia: grupo 3. `audit` recibe cambios escalares y no depende de entidades de `tariffs`; aún no se requieren controladores.

- [x] 4.1 Implementar entidad e inserción explícita de auditoría sin exponer API genérica de actualización/borrado; construir un evento por campo funcional cambiado, agrupado por UUID servidor, con un Clock UTC inyectable y un instante por mutación truncado a microsegundos. Verificar unitariamente campos de alta/edición/estado, antes/después, serialización decimal/ISO/booleanos, LOCAL_USER/SYSTEM y exclusión de versión y secretos. T1/T5; CA1/CA2.S1–S3.
- [x] 4.2 Verificar el escritor en PostgreSQL con rol runtime: persistencia exacta del instante y metadatos, agrupación por mutación, eventos sucesivos conservados y denegación de UPDATE/DELETE/TRUNCATE; inspeccionar que no exista API ordinaria de edición/borrado. Aceptación: CA5.S1–S2 y almacenamiento de CA2 verificados sin afirmar inviolabilidad administrativa ni añadir automatismos SYSTEM.
- [x] 4.3 Establecer participación del escritor en la transacción del llamador sin REQUIRES_NEW, asincronía ni listeners que construyan cambios; probar con un llamador transaccional de prueba que fallo de evento, incluido uno intermedio, revierte negocio y filas previas. Verificar desde otra conexión tras finalizar la transacción y documentar el mecanismo de fallo aislado, sin añadir API productiva de pruebas. T4/T5; CA3.S1–S3.

## 5. Casos de uso de períodos y tarifas

Dependencia: grupo 4. Cada comando nace con versión, bloqueos, transacción y auditoría; el grupo 6 verifica carreras reales, no posterga estos controles.

- [ ] 5.1 Implementar alta, consulta y edición de períodos en `tariffs.application`, con lecturas readOnly, validación de dominio, preconsulta de solape, bloqueo de fila existente, versión esperada y auditoría atómica. Verificar altas sin tarifas automáticas, etiquetas repetidas, edición de etiqueta/fechas sin demandas, intervalos de todas las variantes y exclusión del propio id mediante pruebas de aplicación e integración. T1/T3/T4/T5; TP2–TP4, TP6.S1, HR1.S4, CA1.S1–S2; sin comprobador ficticio de demandas.
- [ ] 5.2 Implementar desactivación/reactivación de períodos con integridad revalidada, versión vigente y cambio exclusivo del estado del período. Verificar en integración conservación de tarifas activas/inactivas, disponibilidad anulada/restablecida solo según ambos estados y rechazo de transiciones redundantes sin cambiar versión ni eventos. T4; D7/D9; TP5.S1–S3, CA1.S4, CA4.S2.
- [ ] 5.3 Implementar alta y consultas de tarifas con tipos fijos/referencias existentes, bloqueo previo del período activo, validación del valor y unicidad global; derivar disponibilidad mediante una consulta coherente `rate.active AND period.active`. Verificar referencias ausentes/ilegales, altas bajo período inactivo con cualquier estado solicitado, combinaciones ocupadas activas/inactivas, combinación distinta y lectura de inactivos. T3/T4; HR1/HR2, HR6.S1/S4.
- [ ] 5.4 Implementar corrección y cambios de estado de tarifa: recuperar periodId escalar, bloquear período y después cargar tarifa/versionar; corregir valor con cualquier estado de período/tarifa sin cambiar asociaciones ni estados; reactivar solo bajo período activo. Verificar identificador preservado, cuatro combinaciones, baja válida, reactivación permitida/prohibida, estado redundante y rechazo de reasignación. T3/T4; D5/D6/D7/D9; HR3, HR5.S1–S2, HR6.S2–S5; CA1.S3–S4.
- [ ] 5.5 Completar pruebas integradas de todos los comandos para no-op tras normalización, versión ausente/obsoleta aun en no-op, antes/después reales, fallo de negocio/commit/auditoría y ausencia de auditoría/versiones nuevas en rechazos por cada familia de reglas. Aceptación: commit o rollback comprobado en conexión independiente para ambas entidades, sin capturar excepciones dentro de una transacción rollback-only ni comunicar éxito previo al commit. T4/T5; CA1.S1–S4, CA3.S1–S3, CA4.S1–S3.

## 6. Carreras y resultados transaccionales

Dependencia: grupo 5. Usar conexiones y transacciones independientes, barreras/latches y esperas acotadas; no sleeps de ordenación ni una única transacción de test.

- [ ] 6.1 Probar carreras reales de altas/ediciones de períodos superpuestos y altas de tarifas duplicadas, incluidos registros inactivos; comprobar que como máximo confirma una y no quedan eventos parciales. Cubrir UNIQUE también con inserciones JDBC parametrizadas bajo rol runtime en BD temporal, sin que el bloqueo de aplicación oculte la restricción física. T2/T4/T9; TP4.S5, HR2.S3, CA3.S3/CA4.S3.
- [ ] 6.2 Probar versiones obsoletas en edición y estado de ambas entidades y los dos órdenes de alta/reactivación de tarifa contra desactivación de período. Aceptación: orden período→tarifa respetado, relectura tras bloqueo, sin incremento de versión por mero bloqueo, sin sobrescrituras/fusión ni reintento automático, estados y auditorías coherentes al confirmar. T4; TP6.S2, HR7.S1–S2.
- [ ] 6.3 Implementar clasificación funcional de conflictos fuera de la transacción fallida: SQLSTATE y restricción para unicidad, exclusión y FK, versión, redundancia, timeout/deadlock y auditoría fallida. Verificar con pruebas de traductor y de integración errores distinguibles y saneados, sin SQL/trazas/secretos, conservando lock_timeout 5 s y statement_timeout 15 s; dejar su adaptación HTTP al grupo 8. T4/T7; RF-CON-005, TP4/TP6, HR2/HR7, CA3/CA4.

## 7. Seguridad web sin autenticación

Dependencia: grupo 1; se realiza después del 6 para una secuencia de entrega única. No depende de controladores del grupo 8.

- [ ] 7.1 Configurar SecurityFilterChain permitAll con CSRF habilitado, sin form login/HTTP Basic/logout; excluir `UserDetailsServiceAutoConfiguration` en ambos modos preservando exclusiones anteriores. Verificar con pruebas de la cadena real acceso sin login/401/contraseña generada, 403 ante POST sin token/inválido y configuración operativa sin DataSource; no excluir rutas de catálogo de CSRF. T7/T8; TP1.S4, HR8.S1/S3.
- [ ] 7.2 Aplicar CSP exacta de T7 compatible con CSS local, nosniff y denegación de frames; configurar manejo y plantillas de errores seguros sin datos sensibles, ni HSTS forzado en HTTP local. Aceptación: pruebas MVC comprueban cabeceras, ausencia de trazas/SQL/secretos y compatibilidad con recursos propios; registrar revisión de seguridad según `docs/engineering/security-standards.md`. T7; TP1.S3/S5, HR8.S2–S3, CA2.S3.

## 8. MVC, formularios y vistas locales

Dependencias: grupos 5–7 y registro condicionado del grupo 3.

- [ ] 8.1 Implementar DTO/controladores delgados y todas las rutas GET/POST de períodos de T6, bajo `persistence`, con campos/versiones explícitos y validación servidor. Incorporar list/form/detail Thymeleaf con consulta de inactivos, navegación, tokens CSRF, escape y PRG. Aceptación: pruebas MVC cubren TP1.S1–S5, normalización/límites/fechas, acciones de estado y conflictos; GET no muta y `/` conserva 404. T1/T6/T7/T8.
- [ ] 8.2 Implementar DTO/controladores y vistas equivalentes de tarifas, mostrando tipo/período inmutables, COP/h, estado individual y disponibilidad derivada; rechazar parámetros protegidos, metadatos de auditoría y valores inválidos sin binding directo de entidades. Aceptación: pruebas MVC cubren HR1.S1–S4, HR3.S1–S2, HR8.S1–S3 y CA2.S3, con token válido/inválido/ausente en cada POST, tipos exclusivamente fijos y ausencia de rutas CRUD de tipos/historial. T6/T7; D1/D5/D6.
- [ ] 8.3 Entregar Bootstrap 5.3.8 CSS compilado local con licencia MIT y procedencia en `src/main/resources/static/vendor/bootstrap/5.3.8/`; completar navegación, errores por campo, tablas semánticas, foco/teclado y presentación responsive sin CDN/Node/WebJars/JavaScript ni estilos inline. Verificar renderizado real y recursos sin red externa, escape HTML y tokens generados; registrar evidencia de revisión visual/accesibilidad y guía de uso de períodos/tarifas sin ampliar funciones. T6/T7; TP1/HR8.
- [ ] 8.4 Adaptar resultados de aplicación a HTTP según T4: validación 200 con entradas válidas conservadas, conflictos/redundancias 409 con recarga y sin reemplazar versión, referencia inexistente 404, CSRF 403 y fallo inesperado/auditoría 500 seguro; PRG solo tras commit exitoso. Verificar con MVC y flujo persistente completo período→tarifa→corrección bajo período inactivo que la respuesta corresponde a datos/eventos confirmados y no hay escritura por solicitudes rechazadas. TP1, TP6, HR5/HR7/HR8, CA3/CA4.

## 9. Regresión de arranque, empaquetado e infraestructura

Dependencia: grupos 2–8. Las pruebas de reglas y MVC ya deben acompañar a sus grupos; aquí se adaptan las regresiones técnicas existentes al conjunto completo.

- [ ] 9.1 Adaptar `PersistenceIT`, `JarStartupIT`, `SchemaValidationIT` y diagnóstico de arranque al modelo real y permisos efectivos; provocar incompatibilidad de esquema solo en copia temporal y verificar fallo saneado sin DDL automático ni disponibilidad. Mantener DefaultStartupTests/JAR sin perfil libres de DataSource/Flyway/EntityManagerFactory y rutas de catálogo (404), aun con URLs presentes. T8/T9; LP1.S1–S2, LP2.S1–S2, LP4.S2 y `application-startup` completo.
- [ ] 9.2 Adaptar `ArtifactIntegrityIT` a lista exacta V1/V2/V3 sin fixtures/secretos y `JarFailuresIT` a fixtures posteriores a V3; preservar regresiones de configuración, conexión, checksum, migración fallida, arranque/cierre acotados y logs saneados. Aceptación: pruebas conservan sus fallos buscados sin quedar ocultos por falta de tablas y hash V1 permanece idéntico. T8/T9; LP2/LP3 y garantías no modificadas de `local-persistence`.
- [ ] 9.3 Adaptar `ComposePersistenceIT` al modelo de negocio y ejecutar sus pruebas únicamente con proyectos y carpetas temporales: loopback, persistencia al recrear contenedor de prueba, respaldo/restauración aislados y cuentas/ACL/historial conservados. Verificar ejecuciones independientes y falta deliberada de prerrequisitos con salida fallida, nunca fallback a desarrollo ni éxito por omisión; documentar comandos y evidencias acumulativamente en la guía operativa. T8/T9; LP4.S1/S3 y garantías canónicas de almacenamiento/aislamiento; ninguna operación sobre el entorno permanente.

## 10. Verificación integrada y entrega para revisión

Dependencia: todos los grupos anteriores. Este cierre reúne evidencias existentes y comprueba el sistema integrado; no sustituye pruebas pendientes de cada bloque.

- [ ] 10.1 Ejecutar con Java 21 `mvnw.cmd package` sin infraestructura y `mvnw.cmd -Ppersistence-it verify` con infraestructura exclusivamente temporal; reunir reportes Surefire/Failsafe y evidencias por escenario de la matriz inferior, incluidas alternativas parametrizadas, estado final de permisos y flujo web integrado. Aceptación: 23 requisitos/71 escenarios acreditados por casos concretos, regresiones canónicas conservadas y ningún prerrequisito/prueba omitido contabilizado como éxito; resultados y limitaciones registrados sin secretos.
- [ ] 10.2 Revisar diffs y documentación operativa/evidencias contra D1–D9/T1–T9, HU/RN/RF/ADR; ejecutar `openspec status --change manage-tariffs`, `openspec validate manage-tariffs --strict` y `git diff --check`, incluyendo comprobación de espacios de archivos nuevos. Aceptación: cambios limitados al incremento, V1/canónicas/ADR/Compose/almacenamiento preservados, sin entidades de demandas ni UI de HU-006 ni correcciones editoriales; entregar resumen para revisión humana ADR-008 sin sync/archive ni aplicación de migraciones permanentes.

### Matriz de cobertura verificable

Los números de tarea asignan implementación y evidencia; varias capas pueden cubrir un escenario sin duplicar su conteo. La evidencia de ejecución futura debe identificar clase/caso parametrizado y resultado para cada escenario, no solo el nombre de una suite.

| Requisito | Escenarios aprobados | Tareas responsables |
|---|---|---|
| TP1 Gestión web de períodos | S1–S5 (5) | 7.1–7.2, 8.1, 8.3–8.4 |
| TP2 Normalización y límites de etiqueta | S1–S4 (4) | 2.1, 3.1, 5.1, 8.1 |
| TP3 Intervalos inclusivos de duración flexible | S1–S3 (3) | 2.1, 3.1, 5.1, 8.1 |
| TP4 Ausencia global de solapamientos | S1–S5 (5) | 2.1, 3.3, 5.1, 6.1, 6.3 |
| TP5 Estados de períodos sin cascadas sobre tarifas | S1–S3 (3) | 3.1, 5.2, 8.1 |
| TP6 Protección de ediciones concurrentes de períodos | S1–S2 (2) | 5.1, 5.5, 6.2–6.3, 8.4 |
| HR1 Gestión web de tarifas y tipos fijos | S1–S4 (4) | 2.1, 3.3, 5.1, 5.3, 8.2 |
| HR2 Unicidad global de combinación tarifaria | S1–S3 (3) | 2.1, 5.3, 6.1, 6.3 |
| HR3 Combinación inmutable tras el alta | S1–S2 (2) | 2.2, 3.2–3.3, 5.4, 8.2 |
| HR4 Valor COP positivo y decimal exacto | S1–S4 (4) | 2.1, 3.2–3.3, 5.3–5.4 |
| HR5 Corrección de valores independiente del estado del período | S1–S2 (2) | 5.4–5.5, 8.4 |
| HR6 Estados y disponibilidad efectiva de tarifas | S1–S5 (5) | 5.2–5.4 |
| HR7 Consistencia concurrente de tarifas y períodos | S1–S2 (2) | 5.4–5.5, 6.2–6.3, 8.4 |
| HR8 Formularios de tarifa seguros | S1–S3 (3) | 7.1–7.2, 8.2–8.4 |
| CA1 Cobertura de cambios efectivos del catálogo | S1–S4 (4) | 4.1, 5.1–5.2, 5.4–5.5 |
| CA2 Instante y origen de auditoría | S1–S3 (3) | 4.1–4.2, 7.2, 8.2 |
| CA3 Atomicidad entre mutación y evento | S1–S3 (3) | 4.3, 5.5, 6.1, 8.4 |
| CA4 Ausencia de auditorías ficticias | S1–S3 (3) | 3.2, 5.2, 5.5, 6.1, 8.4 |
| CA5 Eventos de solo adición en flujos ordinarios | S1–S2 (2) | 2.2, 4.2 |
| LP1 Integración de persistencia sobre la plataforma vigente | S1–S2 (2) | 1.2, 2.2, 3.3–3.4, 9.1 |
| LP2 Gestión exclusiva del esquema mediante migraciones | S1–S2 (2) | 3.3–3.4, 9.1–9.2 |
| LP3 Migraciones técnicas versionadas y verificables | S1–S2 (2) | 2.1–2.3, 9.2 |
| LP4 Verificaciones de integración aisladas y reproducibles | S1–S3 (3) | 1.3, 3.4, 9.1, 9.3 |
| Total | 23 requisitos / 71 escenarios | Cierre integrado 10.1–10.2 |

Trazabilidad de decisiones técnicas: T1 → 3.1–3.4, 4.1, 5.1–5.4, 8.1–8.2; T2 → 2.1–2.3, 6.1; T3 → 3.1–3.3, 5.1–5.4; T4 → 4.3, 5.1–5.5, 6.1–6.3, 8.4; T5 → 2.2, 4.1–4.3, 5.5; T6 → 1.2, 8.1–8.3; T7 → 7.1–7.2, 8.1–8.4; T8 → 3.4, 7.1, 9.1–9.3; T9 → pruebas de cada grupo y 10.1. D1/D8 → grupo 4; D2 → exclusiones de 1.1/10.2; D3 → grupos 2–3/9; D4 → 3.1; D5 → 3.2–3.3/5.4/8.2; D6 → 5.3–5.4/6.2; D7 → 5.2–5.4; D9 → 5.2/5.4–5.5. El cierre 10.2 revisa todas las decisiones.

### Dependencias posteriores y límite operativo

El bloqueo de fechas por demandas asociadas (RF-PER-001/002), selección protegida de tarifa/snapshots/importes (RN-006/027/029, RF-TAR-001/004, RF-CON-002) corresponde a `register-demand`/`maintain-demand`. La consulta visual de auditoría corresponde a HU-006/`audit-changes`. No se acreditan aquí esos comportamientos mediante dobles, entidades o tablas de demanda.

La futura aplicación de V2/V3 a la BD permanente queda fuera de las tareas automatizadas y del progreso de este cambio: requiere autorización operativa separada, identificación del destino y respaldo recuperable conforme a la guía y al plan de migración aprobado. No es prerrequisito para completar pruebas aisladas ni autoriza recrear contenedor, almacenamiento o secretos. Cualquier incompatibilidad técnica que obligue a variar T1–T9 debe volver a revisión humana, sin resolverla ampliando permisos, dependencias o alcance unilateralmente.
