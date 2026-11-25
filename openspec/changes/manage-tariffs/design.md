# Design

## Context

Motivación: `openspec/changes/manage-tariffs/proposal.md`, sección Why. Contrato aprobado: `openspec/changes/manage-tariffs/specs/tariff-periods/spec.md`, `openspec/changes/manage-tariffs/specs/hourly-rates/spec.md`, `openspec/changes/manage-tariffs/specs/catalog-audit/spec.md` y `openspec/changes/manage-tariffs/specs/local-persistence/spec.md`: 23 requisitos, 71 escenarios, aprobados para diseñar, no para implementar.

La rama revisada es `feature/manage-tariffs`. Solo están pendientes los archivos del cambio. El código actual contiene el arranque y la configuración técnica de persistencia, sin entidades ni UI de negocio. `pom.xml` ya integra MVC, JPA, Flyway, PostgreSQL y Testcontainers; no integra Thymeleaf, Bean Validation ni Spring Security. V1 crea `demandflow` y concede USAGE; no concede operaciones sobre futuras tablas. Flyway usa `infra`, Hibernate valida `demandflow` y Open Session in View está deshabilitado.

### Autoridad y decisiones ya aprobadas

Fuentes funcionales: `docs/requirements/functional-requirements.md`, `docs/requirements/business-rules.md`, `docs/requirements/traceability-matrix.md`, contrastadas con el tag `v1.0.0-docs`. Arquitectura: `docs/architecture/architecture.md` y `docs/architecture/data-model.md`. Aplican los ADR de `docs/architecture-decisions/ADR-001-architectural-style.md`, `docs/architecture-decisions/ADR-002-java-spring.md`, `docs/architecture-decisions/ADR-003-postgresql-docker.md`, `docs/architecture-decisions/ADR-004-jpa-flyway.md`, `docs/architecture-decisions/ADR-005-server-rendered-ui.md`, `docs/architecture-decisions/ADR-006-audit-history.md`, `docs/architecture-decisions/ADR-007-security-baseline.md` y `docs/architecture-decisions/ADR-008-openspec-agents.md`.

Se mantienen las decisiones de planificación D1–D9, distintas de las decisiones técnicas T1–T9 propuestas más abajo:

| Decisión aprobada | Consecuencia para el diseño |
|---|---|
| D1 Auditoría mínima; consulta visual en HU-006 | Escritura de eventos desde los casos de uso actuales, sin controlador de historial |
| D2 Solo HU-001/002; diferir demandas | Sin tablas, entidades, repositorios ni simulación productiva de demandas |
| D3 Evolucionar local-persistence | Nuevas migraciones de negocio y validación JPA real; conservar infraestructura |
| D4 Etiqueta normalizada, no vacía, máximo 100, no única | Validación tras normalización y columna sin UNIQUE |
| D5 Tipo y período de tarifa inmutables | DTO de edición sin campos modificables de asociación; mapeo y permisos restrictivos |
| D6 No altas/reactivaciones bajo períodos inactivos | Relectura protegida del estado al guardar; corrección de valor permitida por RN-026/RF-TAR-001, sin alterar estados |
| D7 Conservar estados individuales | Disponibilidad derivada de tarifa activa y período activo; sin cascadas de estado |
| D8 Auditoría sencilla transaccional append-only | Persistencia de solo inserción ordinaria, sin procesamiento asíncrono |
| D9 Rechazar transiciones redundantes | Comparar estado y versión antes de modificar; ningún evento ficticio |

### Contraste completo de local-persistence

Se compararon los bloques completos, incluidos todos sus escenarios, de `openspec/specs/local-persistence/spec.md` y el delta aprobado. No se detecta pérdida de garantías: las referencias a ausencia de negocio se sustituyen deliberadamente por el modelo aprobado, no por una relajación de controles.

| Requisito MODIFIED | Garantías conservadas y evolución aprobada |
|---|---|
| Integración de persistencia sobre la plataforma vigente | Java 21, Boot 4.1.2, Wrapper, JPA/Hibernate/Flyway y conexión real; admite catálogo y añade comprobación de cuentas mínimas |
| Gestión exclusiva del esquema mediante migraciones | Solo Flyway cambia esquema; Hibernate valida sin DDL automático; se sustituye la evidencia de metamodelo vacío por correspondencia real y fallo por incompatibilidad |
| Migraciones técnicas versionadas y verificables | Git, orden, historial y efectos verificables sin tablas ficticias; añade negocio y evolución desde V1 sin alterarla |
| Verificaciones de integración aisladas y reproducibles | Motor real fijo, secretos sintéticos, independencia, no tocar desarrollo, regresión sin BD y fallo explícito por prerrequisito ausente; admite fixtures de negocio aislados |

Los otros ocho requisitos permanecen completos: versión fija/Java fuera de Docker, conservación de almacenamiento, loopback, credenciales externas/privilegios mínimos, repetición segura/checksums, arranque persistente deliberado, fallos controlados y arranque predeterminado sin BD. `openspec/specs/application-startup/spec.md` permanece intacto. Propósito y notas históricas de las canónicas quedan fuera de esta edición.

## Goals / Non-Goals

**Goals:** concretar persistencia, coordinación transaccional, formularios y pruebas verificables de las cuatro capacidades, manteniendo separación funcional pragmática y las garantías técnicas existentes.

**Non-Goals:** agregar arquitectura distribuida, duplicar modelos sin necesidad, incorporar demandas, consulta visual de auditoría, roles o autenticación, corregir documentación editorial o ejecutar cambios de infraestructura. No se modifica V1, el contenedor permanente ni `D:/DOCKER_DATA/POSTGRES_DATA`. Los nombres y estructuras siguientes son propuestas para aprobación humana.

## Decisions

### T1 — Módulos y dependencias pragmáticas

Dentro de `src/main/java/io/github/alejorom/demandflow/`:

| Área | Responsabilidad propuesta |
|---|---|
| `tariffs.web` | Controladores de períodos/tarifas, formularios y DTO de vista; traducción HTTP |
| `tariffs.application` | Casos de uso de alta, edición, estados y consulta; límites transaccionales; coordinación de auditoría |
| `tariffs.domain` | Período, tipo y tarifa; normalización, intervalos, cantidades y transiciones |
| `tariffs.persistence` | Repositorios JPA, proyecciones y consultas de disponibilidad/solapamiento/bloqueo |
| `audit.application` | Escritor de cambios de catálogo derivados del estado real |
| `audit.persistence` | Entidad de evento e inserción; sin API de modificación o borrado |

Flujo de ejecución Web → Aplicación → reglas de Dominio y Persistencia. La aplicación coordina repositorios; no se fuerza una dependencia del dominio hacia repositorios o controladores. El dominio no construye SQL ni conoce Thymeleaf. `tariffs.application` utiliza `audit.application`; `audit` recibe datos escalares de cambio y no depende de entidades de `tariffs`, evitando ciclos.

Se proponen entidades de catálogo con anotaciones JPA y métodos de dominio acotados, probables sin Spring. Evita duplicar tres modelos y mapeadores; las anotaciones no trasladan reglas al repositorio. Los DTO web siguen separados. No hay interfaz por servicio, segunda capa abstracta de repositorios ni módulo `shared` sin necesidad. Alternativa descartada: puertos/adaptadores por clase o modelos duplicados, sin sustitución real que los justifique. Base: ADR-001/004.

### T2 — Modelo físico e integridad PostgreSQL

Todas las tablas pertenecen al esquema `demandflow`, cuyo propietario continúa siendo `demandflow_migrator`. No se modifica `infra.flyway_schema_history`. Columnas NOT NULL salvo indicación expresa.

| Tabla | Columnas y tipos propuestos |
|---|---|
| `tariff_period` | `id BIGINT GENERATED BY DEFAULT AS IDENTITY` PK; `label VARCHAR(100)`; `start_date DATE`; `end_date DATE`; `active BOOLEAN DEFAULT TRUE`; `version BIGINT DEFAULT 0` |
| `requirement_type` | `id SMALLINT` PK sin secuencia; `code VARCHAR(7)` UNIQUE; `name VARCHAR(7)` |
| `hourly_rate` | `id BIGINT GENERATED BY DEFAULT AS IDENTITY` PK; `period_id BIGINT` FK; `type_id SMALLINT` FK; `amount_cop NUMERIC(15,2)`; `active BOOLEAN DEFAULT TRUE`; `version BIGINT DEFAULT 0` |
| `catalog_audit_event` | `id BIGINT GENERATED BY DEFAULT AS IDENTITY` PK; `mutation_id UUID`; `entity_type VARCHAR(16)`; `period_id BIGINT NULL` FK; `rate_id BIGINT NULL` FK; `operation VARCHAR(12)`; `field VARCHAR(32)`; `before_value TEXT NULL`; `after_value TEXT`; `occurred_at TIMESTAMP(6) WITH TIME ZONE`; `actor_origin VARCHAR(10)` |

Restricciones con nombres estables para traducir errores:

- `ck_tariff_period_dates`: `start_date <= end_date`; fechas finitas. `ck_tariff_period_label`: etiqueta no vacía; límite por VARCHAR. La normalización y rechazo de blancos se hacen antes de persistir; no imponer unicidad ni unicidad por estado. `version >= 0` en ambas entidades editables.
- `ex_tariff_period_dates`: exclusión GiST sobre `daterange(start_date, end_date, '[]') WITH &&`, sin predicado de estado. El rango se calcula de las fechas, no se almacena otra columna. No requiere `btree_gist`: no se combina con igualdad de escalares. Su índice GiST es suficiente para la restricción; no crear otro idéntico. La canonicalización interna de DATE no cambia la inclusión funcional de ambos extremos.
- `requirement_type`: dos filas fijas `(1, 'Soporte', 'Soporte')` y `(2, 'Feature', 'Feature')`; CHECK de correspondencia completa id/código/nombre, además de PK y UNIQUE. Runtime solo SELECT. Ningún CRUD ni alta automática de tarifas. Una tabla de referencia permite FKs desde tarifas sin enums PostgreSQL ni extensiones.
- `uq_hourly_rate_period_type`: UNIQUE `(period_id, type_id)` para todos los estados. FKs `fk_hourly_rate_period` y `fk_hourly_rate_type` sin cascadas de borrado ni actualización de claves. CHECK `amount_cop > 0 AND amount_cop <= 9999999999999.99`, que impide también valores especiales no finitos; nunca confiar en la coerción de NUMERIC para rechazar escala de entradas.
- Índices: PK y UNIQUE ya crean sus B-tree; el UNIQUE de tarifas cubre acceso por período. Añadir índice `hourly_rate(type_id)` para la FK inversa. Sin índices por estado de baja selectividad ni por etiqueta no única sin evidencia de necesidad.
- Auditoría: CHECK de entidad `TARIFF_PERIOD`/`HOURLY_RATE`, exactamente una FK presente y coherente con ese tipo. La identidad lógica de entidad es la FK no nula; no duplicar `entity_id`. FKs a `tariff_period(id)` y `hourly_rate(id)` sin cascada. CHECK de operación `CREATE`/`UPDATE`/`DEACTIVATE`/`REACTIVATE` y origen `LOCAL_USER`/`SYSTEM`. CHECK de campos permitidos por entidad y `before_value IS DISTINCT FROM after_value`; antes nulo solo para CREATE, y no nulo en las demás operaciones. Índices simples sobre cada FK, más UNIQUE `(mutation_id, field)` para no duplicar un campo de la misma mutación. No introducir índices de consultas temporales aún no implementadas.

Las asociaciones de tarifa son inmutables en aplicación y JPA; los permisos runtime de UPDATE se limitan a columnas modificables. No se agrega un trigger para escrituras administrativas externas, excluidas de la garantía de flujos ordinarios. Alternativas descartadas: UNIQUE parcial por activo, rango filtrado por activo, enum de BD o extensión adicional. Base: RN-003/004/040/041, RF-PER-002/004, RF-TAR-002, RF-MON-005, RF-CON-004 y D4/D5/D7.

### T3 — Mapeo JPA y validación antes de persistir

`TariffPeriod` y `HourlyRate` usan `Long` id, generación IDENTITY, `@Version Long`, boolean activo y métodos explícitos para edición/transición. `RequirementType` usa identificador `Short` asignado por migración y no expone mutaciones. `HourlyRate` tiene dos `@ManyToOne(fetch=LAZY, optional=false)` unidireccionales; JoinColumn de período/tipo con `updatable=false`. No hay colecciones inversas, CascadeType.REMOVE ni orphanRemoval. Las vistas reciben proyecciones resueltas dentro de transacciones de lectura; no se accede a proxies desde plantillas.

Repositorios Spring Data JPA: consulta de períodos por id, existencia de solape con otro id, carga protegida del período; consulta de tipos fijos; búsqueda/proyección de tarifas y de su periodId; existencia de combinación. Parámetros enlazados, sin concatenación SQL de entradas. El escritor de auditoría utiliza persistencia de inserción explícita, sin heredar públicamente operaciones `save`/`delete` de un JpaRepository genérico. Los nombres concretos de clases menores no obligan a generar interfaces adicionales.

Validaciones aplicadas tanto al alta como a la edición:

- Etiqueta: quitar espacios exteriores con una política común de normalización, comprobar blanco y contar puntos de código Unicode hasta 100, coherente con VARCHAR PostgreSQL. No truncar ni usar la etiqueta como identificador. Pruebas incluyen espacios exteriores y caracteres suplementarios; Bean Validation invoca la misma regla sin duplicar políticas.
- Fechas: `LocalDate`, conversión ISO, obligatoriedad, inicio/fin, representabilidad en PostgreSQL y rango inclusivo. El servidor no deduce período por fecha actual. La consulta de solape usa `existing.start <= proposed.end AND existing.end >= proposed.start` excluyendo el propio id; GiST resuelve las carreras que la consulta no puede cerrar.
- Importe: `BigDecimal` desde entrada decimal sin paso por double, sin `stripTrailingZeros` previo a validar escala. Rechazar escala superior a 2, incluso 12.340, valor no positivo o fuera del límite. Después normalizar almacenamiento a escala 2 usando conversión exacta, nunca HALF_UP para entradas. Comparar valores efectivos mediante `compareTo`; 12.3 y 12.30 no son una corrección económica distinta. El redondeo del importe de demanda no se implementa aquí.
- Estados: altas de catálogo activas por defecto; cambios de estado por acciones explícitas. Siempre exigir versión en edición/estado, incluso en solicitudes aparentemente sin cambios; si falta, rechazar. Edición de tarifa no puede cambiar estado, tipo o período de forma indirecta. Campos protegidos recibidos se rechazan con error de validación, no se enlazan a la entidad.

No existe comprobador ficticio de demandas que siempre responda falso. La edición actual opera en un sistema sin modelo de demandas; el incremento que lo incorpore debe integrar RF-PER-001 antes de admitir sus primeras referencias. Alternativa descartada: confiar solo en HTML, dirty checking o restricciones SQL para reglas de entrada. Base: RF-CON-001, RF-MON-001/004/005 y ADR-004/005/007.

### T4 — Orden transaccional, concurrencia y resultados

Cada comando se ejecuta en un servicio proxificado con `@Transactional`, aislamiento READ_COMMITTED y una sola transacción PostgreSQL. No incluir llamadas externas. Consultas de lectura usan transacción readOnly. Para una mutación de período existente se obtiene `PESSIMISTIC_WRITE` de su fila y se comprueba la versión enviada antes de aplicar reglas. La versión optimista sigue siendo necesaria: esperar un bloqueo no convierte un formulario viejo en válido.

Todas las escrituras de tarifas siguen el mismo orden: **período → tarifa → inserciones de auditoría**. En edición/estado, recuperar inicialmente solo una proyección escalar de periodId, bloquear el período y después cargar de nuevo la tarifa en el contexto transaccional; no reutilizar una entidad cargada antes del bloqueo. La asociación inmutable permite esta búsqueda sin una carrera de reasignación. Para alta, bloquear directamente el período seleccionado. Nunca bloquear primero la tarifa ni incrementar la versión del período por el mero bloqueo.

| Operación | Comprobaciones bajo protección |
|---|---|
| Crear período | Validar etiqueta/fechas; preconsulta de solape; insertar; exclusión decide carreras de filas nuevas |
| Editar período | Bloqueo período; versión; validar datos y solape; detectar cambios efectivos |
| Desactivar/reactivar período | Bloqueo período; versión; transición no redundante; integridad de fechas y referencias; cambiar solo su estado |
| Crear tarifa | Bloqueo período; existencia y estado activo; tipo fijo; valor; combinación libre; insertar con UNIQUE |
| Corregir tarifa | Bloqueo período, carga tarifa, versión; valor y asociación inmutable; permitir período inactivo; no tocar estados |
| Desactivar/reactivar tarifa | Bloqueo período, carga tarifa, versión; transición no redundante; período activo requerido solo para reactivar |

Con tarifas del mismo período se serializan escrituras cortas, aunque pertenezcan a tipos distintos: coste aceptable en MVP local y orden fácil de verificar. Si la desactivación del período confirma primero, un alta/reactivación posterior relee inactivo y falla; si confirma después, mantiene el estado individual y hace indisponible la tarifa. Consultas ordinarias derivan `rate.active AND period.active` en una consulta coherente, sin columna de disponibilidad ni actualizaciones en cascada.

Después de validar versión y entradas, un guardado sin cambios retorna sin modificar entidad/versión ni insertar evento. En cambios efectivos, capturar antes/después, persistir/flush de negocio, agregar auditoría y flush; el éxito HTTP solo ocurre después de que el proxy transaccional confirme. Excepciones de auditoría no se silencian; no usar REQUIRES_NEW, eventos asíncronos ni auditoría en listeners JPA que desconozcan el caso de uso.

Traducir fuera de la transacción fallida: versión obsoleta, redundancia, duplicado o exclusión a HTTP 409 con mensaje funcional; validaciones del formulario a HTTP 200 con errores; referencia inexistente a 404; CSRF a 403; fallo inesperado o de auditoría a 500 seguro. No reutilizar transacciones rollback-only ni responder éxito antes del commit. Identificar SQLSTATE 23505, 23P01 y 23503 junto con nombres de restricciones; no traducir toda violación a un único duplicado. Bloqueos agotados/deadlock se cancelan y solicitan reintento sin reejecución automática. Conservar lock_timeout 5 s, statement_timeout 15 s y límites JDBC existentes.

Alternativas descartadas: solo @Version (no protege estado de otra entidad), locks globales o aislamiento SERIALIZABLE general (más contención/reintentos). El bloqueo pesimista no sustituye UNIQUE ni exclusión. Base: RF-CON-001..005, D6/D7/D9 y ADR-004/006.

### T5 — Auditoría por campo y permisos append-only

Un evento físico por campo cambiado, con `mutation_id` UUID generado por el servidor para agrupar la operación. Una creación registra los valores iniciales de campos funcionales; una edición, solo diferencias; una transición, `active`. No se auditan incrementos técnicos de versión ni bloqueos. Período: label/start_date/end_date/active. Tarifa: period_id/type_id/amount_cop/active, asociaciones solo en CREATE. El identificador funcional afectado queda en la FK del evento.

El caso de uso captura estados inmutables antes/después; el escritor construye filas desde esos datos, no desde parámetros web arbitrarios. Serialización escalar estable: fechas ISO, booleanos true/false, decimales exactos con dos cifras y etiquetas normalizadas. Un `Clock` inyectable produce un único Instant UTC por mutación, truncado a microsegundos para corresponder al tipo de BD; no depender de la zona del anfitrión. LOCAL_USER se asigna a formularios; SYSTEM solo a operaciones de sistema efectivamente auditables, sin agregar automatismos. El seed de tipos fijos no es una operación de períodos/tarifas ni requiere eventos ficticios.

Runtime: SELECT/INSERT y UPDATE limitado a columnas editables en períodos y tarifas; SELECT en tipos; SELECT/INSERT en auditoría y USAGE de las secuencias necesarias. Sin DELETE en tablas de negocio ni UPDATE/DELETE/TRUNCATE en auditoría; sin CREATE en esquemas, sin acceso al historial `infra`, sin propiedad de tablas. Los INSERT se limitan a columnas necesarias para no admitir identificadores/metadatos arbitrarios desde el flujo ordinario; las identidades las obtiene JPA. El migrador conserva propiedad y DDL. No conceder privilegios globales ni futuros por defecto.

El permiso mínimo y la ausencia de API de alteración hacen append-only los flujos de aplicación; no prometen protección frente al propietario/administrador. Los fallos de una inserción de varios campos revierten toda la mutación, incluso si alguna fila previa ya se había enviado a PostgreSQL. Alternativas: JSONB requeriría decisiones de serialización adicionales; una fila por campo encaja con el modelo aprobado sin nuevas bibliotecas. Triggers o event sourcing no aportan cobertura requerida a las escrituras centralizadas del MVP. Base: RN-018..020/028, RF-CON-003/005, ADR-006, D1/D8/D9.

### T6 — MVC, DTO, vistas y dependencias justificadas

Rutas propuestas dentro del mismo proceso; GET no modifica estado:

| Recurso | GET | POST |
|---|---|---|
| Períodos | `/tariffs/periods`, `/tariffs/periods/new`, `/tariffs/periods/{id}`, `/tariffs/periods/{id}/edit` | `/tariffs/periods`, `/tariffs/periods/{id}/edit`, `/tariffs/periods/{id}/deactivate`, `/tariffs/periods/{id}/reactivate` |
| Tarifas | `/tariffs/rates`, `/tariffs/rates/new`, `/tariffs/rates/{id}`, `/tariffs/rates/{id}/edit` | `/tariffs/rates`, `/tariffs/rates/{id}/edit`, `/tariffs/rates/{id}/deactivate`, `/tariffs/rates/{id}/reactivate` |

No añadir controlador de `/`: puede continuar respondiendo 404, preservando el probe actual. Los listados incluyen activos e inactivos para mantenimiento; no importar filtros ni tamaños RF-CONSU propios de demandas. Orden técnico estable por id para listas pequeñas; no constituye una nueva política funcional de consultas. Los enlaces permiten recorrer listado, detalle y edición.

Formularios separados: alta período (label/startDate/endDate), edición período (los mismos y expectedVersion), alta tarifa (periodId/typeId/amountCOP), edición tarifa (amountCOP/expectedVersion) y estado (expectedVersion). Los identificadores de ruta son la referencia del comando; tipos/período de tarifa se muestran como texto en edición. Detectar parámetros protegidos como typeId/periodId/active en edición y metadatos de auditoría; no enlazar entidades directamente. DTO de lectura presenta valor, estado individual y disponibilidad efectiva sin permitir editarlos como campos derivados.

Vistas en `src/main/resources/templates/tariffs/periods/` y `src/main/resources/templates/tariffs/rates/`: list/form/detail, con fragmentos de navegación y errores compartidos solo donde haya repetición real. Etiquetas asociadas, foco visible, mensajes por campo, tablas semánticas, navegación por teclado y Bootstrap CSS responsive. Tras escritura exitosa redirigir a detalle y mostrar resultado; ante validación mantener valores válidos; ante conflicto conservar entrada del usuario y ofrecer recarga, sin actualizar silenciosamente la versión para reenviar un formulario viejo.

Añadir únicamente: `spring-boot-starter-thymeleaf` (renderizado ADR-005), `spring-boot-starter-validation` (validación servidor ADR-005/007), `spring-boot-starter-security` (CSRF y cabeceras ADR-007), y `spring-security-test` en test (comprobación de la cadena real). Versiones administradas por Boot 4.1.2; no fijar versiones divergentes. Bootstrap 5.3.8 CSS compilado local en `src/main/resources/static/vendor/bootstrap/5.3.8/`, con licencia MIT y procedencia verificadas durante implementación; sin CDN, Node, WebJars, JavaScript ni otra cadena de construcción. No se declara libre de vulnerabilidades por esta selección; revisar dependencias según estándares antes de implementar.

Alternativas descartadas: SPA/API independiente, motor de validación casero o CSRF artesanal; contradicen simplicidad o duplican controles existentes del stack. Base: ADR-002/005/007 y ambas especificaciones web.

### T7 — Seguridad web sin autenticación

Configurar una `SecurityFilterChain` servlet explícita con acceso permitido al MVP, CSRF habilitado, sin form login, HTTP Basic, logout ni credenciales/usuarios de demostración. Excluir explícitamente `org.springframework.boot.security.autoconfigure.UserDetailsServiceAutoConfiguration`: una cadena personalizada por sí sola no impide el usuario/contraseña generados. La exclusión se aplica en ambos modos sin reemplazar las exclusiones de persistencia del perfil predeterminado. Probar ausencia de pantalla login, desafío 401 y contraseña generada.

Thymeleaf incorpora token en formularios POST. El token puede usar sesión local; sesión técnica no implica identidad. No ignorar rutas del catálogo en CSRF. Escapar con th:text, sin th:utext para entradas; no incluir entrada de usuario en scripts. Aplicar CSP compatible con recursos propios (`default-src 'self'; style-src 'self'; script-src 'none'; object-src 'none'; base-uri 'self'; frame-ancestors 'none'; form-action 'self'`), nosniff y denegación de frames. Sin estilos inline ni manejadores inline. HSTS se reserva a HTTPS, no se fuerza en HTTP loopback.

Errores web sin stack trace, SQL, mensajes crudos del driver o valores de secretos. Plantilla de error segura y manejo de excepciones transaccionales específicas; logs sin valores contractuales sensibles, contraseñas o URL JDBC completa. Mantener los niveles saneados de infraestructura. La cadena de seguridad funciona también sin persistencia, sin necesitar DataSource ni servicios de catálogo. No se atribuye autorización a CSRF o loopback. Base: ADR-007 y `docs/engineering/security-standards.md`.

### T8 — Perfil persistence y regresión técnica

Un único conjunto explícito de configuración de catálogo, repositorios y escaneo de entidades condicionado por `@Profile("persistence")`. Servicios/escritor y controladores persistentes se registran solo bajo ese perfil; no crear beans con dependencias JPA incondicionales. Configuración de seguridad y recursos estáticos no requieren BD. Sin perfil, las rutas de catálogo no se registran y responden 404, sin catálogo falso ni almacenamiento en memoria. Conservar las exclusiones existentes de DataSource/Hibernate/Flyway y repositorios deshabilitados; la presencia de una URL no activa nada.

Con perfil: mismas URLs loopback, configtree y cuentas; Flyway aplica pendientes antes de inicializar/validar JPA, sin fallback ante errores. Si falta tabla, mapeo o permiso no se enmascara el fallo con DDL automático. Mantener `ddl-auto=validate`, `open-in-view=false`, SQL init deshabilitado, `clean-disabled=true`, sin baseline/repair automático, fuera de orden deshabilitado y mismos tiempos acotados. El analizador de fallos existente deberá comprobarse para el nuevo caso de incompatibilidad de esquema y dar diagnóstico saneado si no lo clasifica; no activar logging SQL para resolverlo.

No modificar Compose, imagen `postgres:17.12-trixie`, credenciales, bootstrap de roles ni montaje. Los nuevos permisos de objetos se entregan por Flyway, no con la cuenta administrativa de Java. Alternativa descartada: activar persistencia por defecto o sustituirla por H2 en modo sin BD. Base: las dos canónicas, ADR-003/004/007 y D3.

### T9 — Estrategia de pruebas y cobertura de todos los escenarios

Usar JUnit Jupiter 6/Spring Boot Test y Testcontainers existentes; Surefire para unitarias/MVC y Failsafe con `persistence-it` para PostgreSQL/JAR. Las clases siguientes son agrupaciones propuestas, no archivos creados. Cada escenario aprobado se convertirá en al menos un caso verificable; los escenarios con alternativas se parametrizan, no se cuentan como cubiertos por una sola rama.

La nomenclatura TP/HR/CA/LP numera requisitos en el orden de sus respectivos archivos; S1..Sn numera sus escenarios locales en ese mismo orden. No cambia identificadores ni contenido de las especificaciones. Los nombres de requisito y conteos permiten localizar inequívocamente los 71 escenarios sin añadir artefactos de prueba.

| Clave y requisito aprobado | Escenarios | Pruebas propuestas y evidencia |
|---|---:|---|
| TP1 Gestión web de períodos | S1–S5 (5) | PeriodMvcTests: listado con inactivos, alta/PRG, errores conservados, todos los POST sin CSRF y HTML escapado; flujo completo persistente |
| TP2 Normalización y límites de etiqueta | S1–S4 (4) | PeriodDomainTests + MVC: normalización, null/blancos, 100/101 y duplicados; PG prueba que no hay UNIQUE de etiqueta |
| TP3 Intervalos inclusivos de duración flexible | S1–S3 (3) | PeriodDomainTests: anual/semestral/especial, un día, ausencias/fecha inválida/invertida |
| TP4 Ausencia global de solapamientos | S1–S5 (5) | PeriodPersistenceIT: variantes con activos/inactivos, extremo, adyacencia, edición propia y carrera contra exclusión real |
| TP5 Estados de períodos sin cascadas sobre tarifas | S1–S3 (3) | CatalogStateIT: dos estados de tarifa, baja/reactivación y redundancia; comprobar filas/eventos |
| TP6 Protección de ediciones concurrentes de períodos | S1–S2 (2) | PeriodConcurrencyIT: edición válida sin demandas y versiones obsoletas para edición/estado |
| HR1 Gestión web de tarifas y tipos fijos | S1–S4 (4) | RateMvcTests + RatePersistenceIT: referencias válidas/ilegales, consulta inactiva y ausencia de seed de tarifas |
| HR2 Unicidad global de combinación tarifaria | S1–S3 (3) | RatePersistenceIT: duplicados activos/inactivos, tipo distinto y carrera real con UNIQUE |
| HR3 Combinación inmutable tras el alta | S1–S2 (2) | RateMvcTests + integración: parámetros protegidos, conservación de id y antes/después |
| HR4 Valor COP positivo y decimal exacto | S1–S4 (4) | RateValueTests: mínimo/máximo, null/cero/negativo/no numérico, 12.345/12.340, exceso; PG exacto |
| HR5 Corrección de valores independiente del estado del período | S1–S2 (2) | CatalogStateIT: tarifa activa/inactiva bajo período activo/inactivo; comprobar valor, ambos estados y eventos |
| HR6 Estados y disponibilidad efectiva de tarifas | S1–S5 (5) | CatalogStateIT: altas prohibidas, baja, reactivación permitida/prohibida, cuatro combinaciones y redundancias |
| HR7 Consistencia concurrente de tarifas y períodos | S1–S2 (2) | RateConcurrencyIT: correcciones/estados con misma versión y ambos órdenes de competencia contra baja del período |
| HR8 Formularios de tarifa seguros | S1–S3 (3) | RateMvcTests: PRG sin login, validación manipulada, POST sin CSRF y salida HTML |
| CA1 Cobertura de cambios efectivos del catálogo | S1–S4 (4) | CatalogAuditIT: altas de ambas entidades, etiqueta/fechas, valor bajo período inactivo y transiciones reales |
| CA2 Instante y origen de auditoría | S1–S3 (3) | AuditWriterTests + integración: Clock fijo, UTC/microsegundos, LOCAL_USER/SYSTEM, metadatos manipulados sin secretos |
| CA3 Atomicidad entre mutación y evento | S1–S3 (3) | CatalogAuditIT: éxito, fallo insert evento incluso intermedio y fallo/commit de negocio; comprobar en nueva transacción |
| CA4 Ausencia de auditorías ficticias | S1–S3 (3) | CatalogAuditIT: no-op, estados redundantes y rechazos por cada familia de validación/concurrencia; sin versiones ni eventos nuevos |
| CA5 Eventos de solo adición en flujos ordinarios | S1–S2 (2) | AuditPermissionsIT: correcciones sucesivas conservadas, UPDATE/DELETE/TRUNCATE runtime denegados y ausencia de API web |
| LP1 Integración de persistencia sobre la plataforma vigente | S1–S2 (2) | PersistenceIT/JarStartupIT: metamodelo real, plataforma conservada, cuentas separadas y permisos efectivos |
| LP2 Gestión exclusiva del esquema mediante migraciones | S1–S2 (2) | SchemaValidationIT: validate real sin DDL; romper mapeo en copia de prueba y exigir fallo saneado |
| LP3 Migraciones técnicas versionadas y verificables | S1–S2 (2) | MigrationEvolutionIT: base vacía, V1→V2/V3, tipos fijos, checks/índices, historial/checksum V1 intactos |
| LP4 Verificaciones de integración aisladas y reproducibles | S1–S3 (3) | Ejecuciones independientes/reordenadas, DefaultStartupTests sin Docker y prerrequisito explícitamente ausente debe fallar |
| **Total** | **71 en 23 requisitos** | **TP 22/6; HR 25/8; CA 15/5; LP 9/4** |

Las pruebas concurrentes usan transacciones y conexiones independientes, barreras/latches y esperas acotadas, sin depender de sleeps para ordenar carreras. No rodear todo un test de concurrencia o rollback con una única transacción de prueba. Confirmar resultados desde otra conexión para comprobar realmente commit/rollback. Para probar UNIQUE de forma concurrente aun con serialización por período, cubrir también dos inserciones JDBC parametrizadas bajo el rol autorizado en BD aislada; su auditoría se valida por separado en los casos de uso.

Adaptar expectativas existentes, no borrarlas: `PersistenceIT` debe comprobar entidades/tablas esperadas y permisos; `JarStartupIT` y `ComposePersistenceIT` conservan loopback, recreación e historial pero ya no exigen una única tabla. `ArtifactIntegrityIT` admite exactamente migraciones productivas V1/V2/V3 y continúa rechazando fixtures/secretos; sustituir prohibición indiscriminada de V2__ por lista de rutas permitidas. `MigrationEvolutionIT` y `JarFailuresIT` deben separar preparación V1 técnica de arranque con entidades: aplicar V1 mediante Flyway/JDBC aislado y luego actualizar con V2/V3; los fixtures de evolución/fallo se numeran después de la última versión productiva, no sustituyen V2. Esto evita que Hibernate falle por falta de tablas antes del escenario que se intenta probar. El probe `/` puede seguir esperando 404 en ambos modos.

Conservar además regresiones de checksum alterado, migración fallida, arranque/tiempos, secretos, cuentas, publicación loopback y restauración de la infraestructura; los 71 escenarios no reemplazan esas garantías canónicas. Reutilizar la imagen fija, cuentas sintéticas y TempDir; no usar carpetas ni secretos de desarrollo. Cuando la prueba Compose se solicite, proyectos y carpetas serán temporales como en el soporte actual. Este documento no ejecuta ninguna prueba PostgreSQL o Docker.

Referencias de prueba: `docs/engineering/testing-strategy.md`, `docs/engineering/coding-standards.md`, `docs/engineering/security-standards.md` y `docs/requirements/traceability-matrix.md`. Los RN/RF/ADR de cada requisito aprobado se heredan en todos los casos de su fila. La integración de snapshots, preservación de importes y bloqueo por demandas asociadas sigue expresamente diferida, no cubierta por dobles ni por fixtures de catálogo.

## Risks / Trade-offs

- **Contención por período** → transacciones cortas, orden de bloqueo único, timeouts existentes y reintento solicitado al usuario; suficiente para MVP local. No introducir lotes multientidad sin revisar orden.
- **Precisión NUMERIC puede redondear entradas directas** → validar escala antes de persistir en todos los casos de uso; constraints aseguran positividad/límites pero no sustituyen esa validación. Escrituras administrativas no están cubiertas por formularios.
- **Fechas y Unicode tienen límites de representación diferentes** → pruebas de conversión Java/PostgreSQL y longitud por puntos de código; rechazar entradas no representables con error funcional, sin inventar anualidad ni normalizar silenciosamente fechas.
- **Privilegios de columna frente a SQL generado por Hibernate** → probar INSERT/UPDATE y bloqueo con el rol runtime real. Mapeos excluyen asociaciones de UPDATE y no actualizan columnas inmutables; no solucionar fallos concediendo propietario/superusuario.
- **Error de esquema puede eludir el analizador actual** → prueba de arranque con incompatibilidad y saneamiento explícito del diagnóstico; nunca revelar SQL/credenciales ni continuar disponible.
- **Evolución de pruebas que solo conocen V1** → distinguir verificación técnica aislada y migración del esquema completo; preservar evidencias de checksums y fallos sin reparar historia.
- **Histórico sin protección criptográfica ni retención definida** → append-only ordinario y permisos mínimos; sin purgas, promesas administrativas ni controles ajenos a ADR-006.
- **Incorporación futura de demandas** → antes de permitirlas integrar bloqueo de fechas utilizadas, snapshot y resolución protegida bajo el mismo orden período→tarifa; no afirmar ahora cumplimiento integrado de RF-PER-001, RF-TAR-004 o RN-027.

## Migration Plan

Plan propuesto para implementación futura autorizada; no contiene SQL ejecutado ni autoriza intervenir el entorno permanente.

1. Conservar V1 byte a byte y su historial. Proponer `V2__create_tariff_catalog.sql`: tablas de períodos/tipos/tarifas, checks, FKs, UNIQUE/exclusión y dos tipos fijos, sin tarifas de ejemplo. `V3__create_catalog_audit.sql`: tabla de eventos, restricciones/índices y grants explícitos de todos los objetos/secuencias a runtime. Ambas migraciones transaccionales; V3 depende de V2. Son nombres propuestos, no archivos creados.
2. Verificar primero base vacía y base V1 en PostgreSQL temporal con roles reales; validar efectos, permisos, repetición e incompatibilidad JPA. La aplicación solo se declara disponible cuando ambas versiones y validación terminan. Fallo V3 puede dejar V2 ya confirmada: no es éxito de arranque, no hay operaciones de negocio y se conserva el historial para diagnóstico.
3. Tras aprobación de diseño, tareas, implementación y autorización operativa separada, el operador revisa respaldo recuperable según `docs/engineering/local-persistence-guide.md`; no se cambia almacenamiento, contenedor, secretos ni bootstrap para desplegar el código. Este cambio no hace esa operación por adelantado.
4. Una migración aplicada no se edita ni se elimina ni se repara automáticamente. Un fallo exige diagnóstico y corrección versionada apropiada, sin saltar una versión fallida ni habilitar out-of-order. Durante implementación de archivos aún no aplicados se corrigen antes de su primera aplicación autorizada; si ya quedaron aplicados, usar una versión correctiva adicional.
5. Reversión de aplicación: detener nuevas escrituras; conservar tablas y auditoría. La versión anterior puede no validar entidades nuevas pero no debe suponerse compatible por ello: ensayar el artefacto previo en copia aislada. Preferir corrección hacia adelante; restauración solo por operador autorizado en destino independiente y respaldo verificado. No hay migración down destructiva ni borrado/recreación del directorio permanente.

## Revisión humana y dependencias

Las decisiones T1–T9 son nuevas propuestas técnicas para aprobar este diseño conforme a ADR-008; no sustituyen ADR ni modifican las especificaciones. La aprobación debe cubrir especialmente modelo por campo y permisos de auditoría, bloqueo por período, migraciones V2/V3, registro condicionado al perfil y dependencias web con recursos Bootstrap locales. No se detectaron contradicciones que requieran reemplazar un ADR.

No quedan decisiones funcionales bloqueantes nuevas. La implementación futura deberá verificar compatibilidad efectiva de APIs/dependencias y de permisos con SQL generado; cualquier incompatibilidad que exija variar el enfoque o el alcance vuelve a revisión, sin cambiar silenciosamente decisiones. HU-006 y las dependencias de demandas conservan los incrementos previstos en la propuesta. Las correcciones editoriales pendientes siguen fuera de alcance.

Contraste técnico primario utilizado para concretar estas propuestas: documentación PostgreSQL 17 de rangos y bloqueos (`https://www.postgresql.org/docs/17/rangetypes.html`, `https://www.postgresql.org/docs/17/explicit-locking.html`), bloqueo JPA (`https://docs.spring.io/spring-data/jpa/reference/jpa/locking.html`), CSRF y configuración Boot (`https://docs.spring.io/spring-security/reference/servlet/exploits/csrf.html`, `https://docs.spring.io/spring-boot/reference/web/spring-security.html`) y distribución oficial Bootstrap (`https://getbootstrap.com/docs/5.3/getting-started/download/`). Estas referencias técnicas no agregan requisitos funcionales ni cambian versiones del stack existente.
