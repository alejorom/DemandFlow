# Persistencia local de DemandFlow

Esta guía aplica al cambio `setup-database`; no incorpora funcionalidades de negocio. La aplicación Java se ejecuta en el anfitrión, nunca en Docker. Sin perfil Spring `persistence`, no utiliza BD, secretos ni Docker. El perfil Maven `persistence-it` solicita pruebas y no habilita por sí solo persistencia en la aplicación.

## Estado y responsabilidad del operador

El 2026-11-24 el usuario aprobó D2 para `postgres:17.12-trixie` y aceptó expresamente sus riesgos residuales solo para el MVP en equipo local de confianza, sin exposición a Internet. El digest evaluado es `postgres@sha256:87c8b42f423b45d5247e6aa8fdb2e7762fec164c68a50fe3eb07e7bfcaf4dbcf`. La aprobación no declara la imagen libre de vulnerabilidades ni permite cambiarla automáticamente. Véanse el [informe de seguridad](../../openspec/changes/archive/2026-11-25-setup-database/image-security-review.md) y las [evidencias](../../openspec/changes/archive/2026-11-25-setup-database/evidence.md) para el progreso verificable.

El usuario administra la creación, puesta en marcha y detención del proyecto permanente `demandflow-local`, su carpeta de datos y sus credenciales. Los comandos siguientes son instrucciones para ejecución manual; prepararlos no acredita haberlos ejecutado. El agente no crea ese entorno ni genera sus secretos. Compose identifica un proyecto y un servicio, no un contenedor con nombre fijo: no añadir `container_name` ni añadir un volumen administrado de datos.

`ComposePersistenceIT` usa proyectos temporales distintos: se verificaron recreación, restauración y limpieza con la imagen aprobada. El estado de las verificaciones integradas se registra en las evidencias. Iniciar el entorno permanente manualmente no sustituye esas pruebas ni constituye una aprobación de cierre.

## Preparación

Requisitos: Java 21, Maven Wrapper 3.9.11 del repositorio, Docker Desktop en modo Linux y contexto local, Docker Compose con `up --wait --wait-timeout`. Conservar Spring Boot 4.1.2 y PostgreSQL `postgres:17.12-trixie`. Revisar vulnerabilidades antes de actualizar; no sustituir versiones aprobadas silenciosamente.

Solo el operador, desde PowerShell en la raíz del repositorio y después de resolver la revisión de seguridad, crea secretos locales distintos, sin mostrarlos. El bloque siguiente no se ejecutó durante esta revisión. No sobrescribir un directorio existente: sus contraseñas pueden corresponder a un volumen ya inicializado.

```powershell
if (Test-Path .secrets) { throw 'Revisar los secretos existentes; no sobrescribirlos' }
New-Item -ItemType Directory .secrets/bootstrap,.secrets/spring | Out-Null
$localAccount = "$env:USERDOMAIN\$env:USERNAME"
icacls .secrets /inheritance:r /grant:r "${localAccount}:(OI)(CI)F"
if ($LASTEXITCODE -ne 0) { throw 'No se pudieron restringir los permisos; no generar secretos' }
foreach ($secretFile in @('bootstrap/postgres.password','spring/spring.flyway.password','spring/spring.datasource.password')) {
    $randomBytes = New-Object byte[] 32
    $generator = [Security.Cryptography.RandomNumberGenerator]::Create()
    $generator.GetBytes($randomBytes)
    $generator.Dispose()
    [IO.File]::WriteAllText((Join-Path "$PWD/.secrets" $secretFile), [Convert]::ToBase64String($randomBytes))
}
git check-ignore .secrets/bootstrap/postgres.password .secrets/spring/spring.flyway.password .secrets/spring/spring.datasource.password
```

`.env.example` documenta puerto, ruta de secretos y `DEMANDFLOW_DATA_DIR`, sin valores de contraseña. `DEMANDFLOW_SECRETS_DIR` apunta al directorio padre de `bootstrap/` y `spring/`. Los secretos Compose se montan bajo `/run/secrets/`; Java desde el IDE lee los archivos del anfitrión bajo `spring/`. No copiar secretos a recursos Java, argumentos, URL, volcados de configuración o incidencias.

## Docker y conservación del entorno

El estándar permanente es `D:\DOCKER_DATA\POSTGRES_DATA`. Preparar la carpeta es una acción manual del operador; este cambio no crea ni inspecciona sus datos. No inicializar una carpeta con datos de otro clúster/versiones ni sobrescribir contenido. La sustitución del volumen administrado por un bind mount **no migra** datos existentes: conservar cualquier volumen anterior y usar respaldo/restauración explícitos en una copia separada antes de cambiar de origen. No copiar archivos de un clúster activo ni borrar el origen para resolver un fallo.

Comprobar antes el puerto; si está ocupado, elegir otro libre y comunicarlo también a Java. No detener el servicio que lo ocupa.

Antes de `up`, consultar `docker context show` y `docker context inspect` para confirmar un daemon local; revisar los recursos ya existentes con `docker compose -p demandflow-local ps -a` e inspeccionar manualmente la carpeta de datos elegida. Si contiene datos o hay un estado inesperado, conservarlos y revisar su identidad, versión y secretos correspondientes. No asumir que se trata de una instalación nueva.

```powershell
Get-NetTCPConnection -LocalPort 5432 -State Listen -ErrorAction SilentlyContinue
$env:DEMANDFLOW_DB_PORT = '5432'
$env:DEMANDFLOW_SECRETS_DIR = (Join-Path (Get-Location).Path '.secrets').Replace('\', '/')
docker compose -p demandflow-local config --quiet
if ($LASTEXITCODE -ne 0) { throw 'Configuración Compose inválida; no iniciar' }
docker compose -p demandflow-local up --wait --wait-timeout 120
if ($LASTEXITCODE -ne 0) { throw 'PostgreSQL no disponible; no iniciar Java' }
docker compose -p demandflow-local ps
```

Comprobar `$LASTEXITCODE` después de cada comando Docker; ante un código distinto de cero detener el procedimiento y diagnosticar. `config --quiet` valida el modelo sin crear recursos ni mostrar valores; no prueba la existencia/utilidad de todos los secretos ni la disponibilidad del servidor. Usar siempre la raíz del repositorio, el mismo proyecto y las mismas variables. `.env.example` sirve de referencia para puerto/ruta; no copiar contraseñas allí. Las variables de esta terminal prevalecen sobre `.env` para Compose y también llegan al proceso Java.

Solo se publica `127.0.0.1:<puerto>:5432`. El bind mount usa por defecto **`D:\DOCKER_DATA\POSTGRES_DATA`** en Windows, representado como `D:/DOCKER_DATA/POSTGRES_DATA` en Compose, y se monta en `/var/lib/postgresql/data`. `DEMANDFLOW_DATA_DIR` permite indicar otra ruta absoluta mediante el entorno o `.env`; Java no utiliza esta variable. El operador debe preparar y revisar esa carpeta antes de `up`: `create_host_path: false` impide que Compose la cree accidentalmente. Docker Desktop debe tener acceso a la unidad y PostgreSQL debe poder inicializar/usarla con sus permisos; un fallo de montaje/permisos requiere detenerse y diagnosticar, sin borrar datos. No usar `docker system prune`, `docker volume prune`, operaciones globales ni `down --volumes` en desarrollo. No cambiar imágenes o volúmenes ajenos.

La imagen crea la base `demandflow` y el administrador `postgres`. El bootstrap crea `demandflow_migrator`, `demandflow_app` e `infra` solo si el directorio de datos está vacío. El migrador posee `infra` y puede crear el namespace de aplicación; runtime solo conecta y recibirá USAGE de ese namespace mediante V1. Nunca utilizar administrador para Java. Cambiar archivos de secretos **no** cambia las contraseñas ya almacenadas: una rotación requiere intervención administrativa explícita y sincronización de consumidores, conservando la carpeta de datos.

El healthcheck comprueba disponibilidad TCP; no demuestra autenticación, permisos ni migraciones. Los errores del bootstrap se limitan a mensajes fijos sin SQL/secretos. Un directorio parcialmente inicializado requiere diagnóstico; no borrarlo automáticamente para hacer desaparecer el fallo.

## Construcción y modo predeterminado

```powershell
.\mvnw.cmd test
.\mvnw.cmd package
java -jar target/demandflow-0.0.1-SNAPSHOT.jar
```

Arranca en `127.0.0.1:8080` sin BD ni Docker. Detener Java con Ctrl+C. El esqueleto puede responder 404 en `/`; eso no implica un fallo de arranque. Las pruebas de integración se solicitan explícitamente con `mvnw.cmd -Ppersistence-it verify` y requieren Docker local; un prerrequisito ausente debe producir fallo, no éxito por pruebas omitidas.

## Activación explícita y evolución técnica

Después de preparar secretos y esperar PostgreSQL con Compose, en la misma terminal:

```powershell
$env:DEMANDFLOW_DB_PORT = '5432' # Usar el puerto realmente elegido para Compose.
$env:DEMANDFLOW_SECRETS_DIR = (Join-Path (Get-Location).Path '.secrets').Replace('\', '/')
java -jar target/demandflow-0.0.1-SNAPSHOT.jar --spring.profiles.active=persistence
```

En el IDE, establecer esas dos variables en la configuración de ejecución y añadir `--spring.profiles.active=persistence` a los argumentos. Java no carga `.env`; la ruta es la del anfitrión y el configtree `spring/` es obligatorio. No pasar contraseñas por argumentos ni por URL. La mera presencia de secretos o de una URL no activa el perfil. Para volver al modo sin BD, detener Java y ejecutar el mismo JAR sin el argumento del perfil; esto conserva el estado de PostgreSQL.

Flyway conecta como `demandflow_migrator`, aplica V1 y mantiene `infra.flyway_schema_history`. JPA/Hikari conecta como `demandflow_app`, sin DDL ni acceso al historial. Java nunca importa la contraseña administrativa. V1 crea el namespace vacío `demandflow`, con propietario migrador, sin permisos PUBLIC y con USAGE runtime. No hay tablas, entidades ni repositorios de negocio. Hibernate utiliza `validate` con metamodelo vacío: esto prueba inicialización, no correspondencia de entidades o reglas funcionales futuras. No hay `schema.sql`/`data.sql`, generación automática, baseline, clean ni repair automáticos.

El segundo arranque valida y conserva V1; las correcciones de versiones ya aplicadas requieren una nueva migración. No editar V1 para reparar un entorno compartido. Tras un error, comprobar catálogo e historial antes de reintentar: una salida fallida no implica que ninguna versión anterior haya quedado confirmada. No existe rollback global de la cadena.

## Comprobación manual de la integración

Realizar estos pasos después de resolver la revisión de seguridad y de que el operador haya iniciado PostgreSQL. Mantener Java en una terminal y las comprobaciones en otra, ambas en la raíz del repositorio. Repetir en la segunda terminal las dos variables no secretas de puerto/ruta; una nueva terminal o el IDE no heredan los cambios de la primera.

1. Construir con `mvnw.cmd package` y exigir código cero. No necesita Docker ni activa persistencia. Iniciar el JAR con el perfil `persistence` como se indica arriba, usando el puerto PostgreSQL realmente publicado. Si 8080 está ocupado, elegir un puerto Java libre mediante `--server.port=<puerto>` sin modificar `server.address` ni detener otros servicios.
2. Consultar `docker compose -p demandflow-local port postgres 5432`: debe informar exclusivamente `127.0.0.1:<puerto-elegido>`. Consultar `docker compose -p demandflow-local images` y `docker image inspect postgres:17.12-trixie --format '{{json .RepoDigests}}'`; registrar el digest efectivo y contrastarlo con el informe de seguridad. Un digest distinto requiere revisión, aunque el tag coincida. No descargar ni sustituir imágenes para eludir hallazgos.
3. Exigir que Java permanezca vivo y muestre `Started DemandFlowApplication` después de inicializar la persistencia. Los loggers JPA/Flyway están restringidos: su silencio no prueba fallo ni éxito. Comprobar `curl.exe -s -o NUL -w '%{http_code}' http://127.0.0.1:8080/` (o el puerto Java elegido): el esqueleto puede responder 404. Una respuesta HTTP por sí sola no acredita conexión ni privilegios.
4. Identificar el PID de esa JVM y comprobar su listener con `Get-NetTCPConnection -OwningProcess <PID> -State Listen`: debe ser `127.0.0.1` en el puerto elegido. No confundirlo con otro proceso ya iniciado. Inspeccionar el montaje con `docker inspect <ID-del-contenedor> --format '{{json .Mounts}}'`, obteniendo el ID de `docker compose -p demandflow-local ps -q postgres`: el montaje debe ser de tipo `bind`, apuntar a la carpeta anfitrión elegida y terminar en `/var/lib/postgresql/data` (Docker Desktop puede representar la ruta con su prefijo interno). No debe ser un volumen administrado. No inspeccionar ni compartir variables de entorno completas.
5. Ejecutar la consulta técnica siguiente por el socket administrativo local del contenedor. No introduce ni imprime contraseñas. Confirma catálogo e historial; Java usa exclusivamente las dos cuentas configuradas, nunca esa sesión administrativa.

```powershell
@'
SHOW server_version;
SELECT rolname, rolsuper, rolcreatedb, rolcreaterole, rolreplication, rolbypassrls
FROM pg_roles WHERE rolname IN ('demandflow_migrator', 'demandflow_app') ORDER BY rolname;
SELECT nspname, pg_get_userbyid(nspowner) AS owner, nspacl
FROM pg_namespace WHERE nspname IN ('infra', 'demandflow') ORDER BY nspname;
SELECT has_schema_privilege('demandflow_app', 'demandflow', 'USAGE') AS app_usage,
       has_schema_privilege('demandflow_app', 'demandflow', 'CREATE') AS app_create,
       has_schema_privilege('demandflow_app', 'infra', 'USAGE') AS history_schema_usage,
       has_table_privilege('demandflow_app', 'infra.flyway_schema_history', 'SELECT') AS history_select,
       has_database_privilege('demandflow_app', 'demandflow', 'TEMP') AS app_temp;
SELECT installed_rank, version, description, checksum, installed_on, success
FROM infra.flyway_schema_history ORDER BY installed_rank;
SELECT schemaname, tablename FROM pg_tables
WHERE schemaname IN ('infra', 'demandflow') ORDER BY schemaname, tablename;
'@ | docker compose -p demandflow-local exec -T postgres psql -X --no-password -U postgres -d demandflow -v ON_ERROR_STOP=1
if ($LASTEXITCODE -ne 0) { throw 'Comprobación técnica fallida; detener y diagnosticar' }
```

Esperado: PostgreSQL 17.12; ambos roles sin atributos administrativos; ambos esquemas propiedad de `demandflow_migrator`; `app_usage=true` y las otras cuatro comprobaciones de permisos `false`; V1 exitosa; única tabla `infra.flyway_schema_history`. V1 deja `demandflow` vacío y sin ACL PUBLIC. Estos resultados se esperan del diseño y de pruebas previas aisladas; todavía no se observaron en el entorno permanente. No ejecutar SQL de denegación deliberada ni fixtures V2 sobre desarrollo.

Guardar la salida técnica saneada antes de detener Java con Ctrl+C. Iniciar de nuevo el mismo JAR con el perfil, repetir la consulta y comparar V1: misma versión, checksum, orden y fecha de instalación; sin tablas nuevas. Registrar hora, comandos, códigos y resultado observado sin secretos. Ante diferencia, detener y revisar; no editar historial, activar `repair`, `baseline`, `clean` ni cambiar V1. El segundo arranque manual complementa las pruebas existentes; la recreación/restauración ya se comprobó exclusivamente sobre proyectos temporales.

## Detención y recuperación del operador

Detener primero Java con Ctrl+C. `docker compose -p demandflow-local stop` detiene PostgreSQL conservando el contenedor y los archivos anfitrión. `docker compose -p demandflow-local down` retira contenedor/red del proyecto y conserva los archivos de la carpeta anfitrión; al volver a `up --wait --wait-timeout 120` se vuelve a montar la misma carpeta y no se repite el bootstrap. No añadir `--volumes` ni ejecutar limpieza global. Volver al arranque Java sin perfil conserva el esquema y no requiere reiniciar PostgreSQL.

Si falla el bootstrap, conservar directorio y secretos, detener Java y revisar estado/logs localmente sin publicar contenido sensible. Inventariar roles, `infra` e historial con administración explícita antes de reintentar; un directorio de datos no vacío impide que initdb.d vuelva a ejecutarse automáticamente. No reejecutar el bootstrap ni recrear roles sobre datos existentes para ocultar el problema.

La rotación requiere detener Java, respaldar, cambiar administrativamente la contraseña de la cuenta afectada mediante un cliente interactivo seguro (por ejemplo `psql` y su comando `\password`, sin SQL con contraseñas en argumentos/historial), actualizar únicamente el archivo correspondiente protegido y renovar los consumidores/montajes cuando sea necesario. Validar de nuevo el arranque; no cambiar solo el archivo esperando que PostgreSQL actualice el rol. No repetir el generador inicial sobre `.secrets` existente.

Para la sesión de rotación, el operador puede usar `docker compose -p demandflow-local exec postgres psql -X -U postgres -d demandflow`. Dentro de psql, ejecutar `\password demandflow_app`, `\password demandflow_migrator` o `\password postgres` únicamente para la cuenta elegida; introducir el valor en los prompts, salir con `\q` y actualizar su archivo protegido correspondiente. No guardar la contraseña en un comando `ALTER ROLE`, en `.env` ni en el historial del shell. Recrear los montajes de secretos cuando corresponda conserva el directorio y no repite initdb.d. Probar la cuenta afectada mediante el arranque y las verificaciones de esta guía antes de continuar.

Antes de datos relevantes, obtener respaldo lógico de `demandflow` con `pg_dump` 17 en formato custom, almacenarlo fuera del repositorio y de `target/` con acceso restringido, y conservar de forma segura el procedimiento de aprovisionamiento de roles. No exportar hashes/contraseñas con volcados globales para adjuntarlos como evidencia. En PowerShell evitar redirección de un dump binario por la canalización de texto: usar archivo del cliente y copia binaria. Restaurar con roles ya aprovisionados en otro proyecto/directorio aislado de la misma plataforma, conservar propietarios/ACL/historial y contrastar catálogo y dos arranques Java antes de considerar válido el respaldo. No restaurar con `--clean` sobre desarrollo. El ensayo aislado técnico ya demostró esos controles; no acredita la recuperación de datos de negocio futuros ni reemplaza probar cada respaldo relevante.

### Respaldo manual

Con Java detenido, desde la raíz y con las variables de ruta/puerto de desarrollo establecidas, el operador prepara un directorio externo protegido. Los siguientes comandos no se ejecutan automáticamente sobre desarrollo:

```powershell
$backupRoot = (Resolve-Path -LiteralPath (Read-Host 'Directorio externo existente y protegido para respaldos')).Path
if ($backupRoot.StartsWith((Get-Location).Path, [StringComparison]::OrdinalIgnoreCase)) {
    throw 'Elegir un directorio fuera del repositorio y de target'
}
$backupName = 'demandflow-' + (Get-Date -Format 'yyyyMMdd-HHmmss') + '-' + [Guid]::NewGuid().ToString('N') + '.dump'
$backupPath = Join-Path $backupRoot $backupName
$sourceContainer = docker compose -p demandflow-local ps -q postgres
if ($LASTEXITCODE -ne 0 -or $sourceContainer -notmatch '^[0-9a-f]{12,64}$') { throw 'Identificar el contenedor de desarrollo antes de respaldar' }
docker compose -p demandflow-local exec -T postgres pg_dump -U postgres -d demandflow --format=custom "--file=/tmp/$backupName"
if ($LASTEXITCODE -ne 0) { throw 'Respaldo fallido; no considerarlo válido' }
docker cp "${sourceContainer}:/tmp/$backupName" $backupPath
if ($LASTEXITCODE -ne 0) { throw 'Copia binaria fallida; conservar y diagnosticar el origen' }
Get-FileHash -LiteralPath $backupPath -Algorithm SHA256
```

El dump guarda datos, propietarios y ACL de la base, pero no aprovisiona roles ni contraseñas del clúster. Conservar por separado los archivos protegidos y el procedimiento de bootstrap. El archivo temporal dentro del contenedor tiene un nombre único; el operador puede retirarlo después de verificar copia/hash, sin tocar datos ni volumen. Un hash solo comprueba identidad de la copia; no demuestra restauración.

### Restauración manual en una copia aislada

El destino debe ser un proyecto nuevo y una carpeta vacía independiente, nunca desarrollo. Mantener la ruta de secretos protegida para que el bootstrap aprovisione los mismos nombres de roles; no ejecutar el generador sobre secretos existentes. Conservar `$backupPath` del respaldo anterior y el puerto de desarrollo para recuperarlo después:

```powershell
$developmentPort = $env:DEMANDFLOW_DB_PORT
$developmentDataDir = $env:DEMANDFLOW_DATA_DIR
$restoreProject = 'demandflow-recovery-' + [Guid]::NewGuid().ToString('N')
if ($restoreProject -notmatch '^demandflow-recovery-[0-9a-f]{32}$') { throw 'Destino de recuperación inválido' }
$env:DEMANDFLOW_DB_PORT = '0'
$recoveryRoot = (Resolve-Path -LiteralPath (Read-Host 'Directorio externo protegido para la copia aislada; nunca D:\DOCKER_DATA')).Path
$recoveryPath = Join-Path $recoveryRoot $restoreProject
if ($recoveryPath.StartsWith('D:\DOCKER_DATA', [StringComparison]::OrdinalIgnoreCase)) { throw 'No restaurar sobre el almacenamiento permanente' }
if (Test-Path -LiteralPath $recoveryPath) { throw 'Elegir una carpeta de recuperación nueva' }
New-Item -ItemType Directory -Path $recoveryPath | Out-Null
$env:DEMANDFLOW_DATA_DIR = $recoveryPath.Replace('\', '/')
docker compose -p $restoreProject config --quiet
if ($LASTEXITCODE -ne 0) { throw 'Configuración de recuperación inválida' }
docker compose -p $restoreProject up --wait --wait-timeout 120
if ($LASTEXITCODE -ne 0) { throw 'Destino no disponible; no restaurar' }
$restoreContainer = docker compose -p $restoreProject ps -q postgres
if ($LASTEXITCODE -ne 0 -or $restoreContainer -notmatch '^[0-9a-f]{12,64}$') { throw 'Destino no identificado' }
docker inspect $restoreContainer --format '{{json .Config.Labels}}'
docker inspect $restoreContainer --format '{{json .Mounts}}'
# Verificar que las etiquetas/montaje pertenecen únicamente a $restoreProject antes de seguir.
docker cp $backupPath "${restoreContainer}:/tmp/demandflow-restore.dump"
if ($LASTEXITCODE -ne 0) { throw 'No se pudo copiar el respaldo' }
docker compose -p $restoreProject exec -T postgres pg_restore -U postgres -d demandflow --clean --if-exists --exit-on-error /tmp/demandflow-restore.dump
if ($LASTEXITCODE -ne 0) { throw 'Restauración fallida; conservar origen y diagnosticar la copia' }
$restoredBinding = docker compose -p $restoreProject port postgres 5432
if ($LASTEXITCODE -ne 0 -or $restoredBinding -notmatch '^127\.0\.0\.1:[0-9]+$') { throw 'Publicación de recuperación inesperada' }
$env:DEMANDFLOW_DB_PORT = ($restoredBinding -split ':')[-1]
```

Ejecutar contra `$restoreProject` la consulta técnica anterior (sustituir únicamente `-p demandflow-local`), comparar propietarios, permisos efectivos e historial con el origen y realizar dos arranques del JAR con `persistence` y el puerto restaurado. `nspacl=NULL` puede representar los permisos predeterminados: comparar privilegios efectivos o `coalesce(nspacl, acldefault('n', nspowner))`, sin inventar diferencias de seguridad. No usar `--no-owner`/`--no-acl` para hacer pasar la restauración. Ante una discrepancia, detener Java y revisar la copia, conservando el respaldo y el origen.

Al terminar, detener Java y ejecutar `docker compose -p $restoreProject down` para conservar la copia y su carpeta; restablecer `$env:DEMANDFLOW_DB_PORT = $developmentPort` y `$env:DEMANDFLOW_DATA_DIR = $developmentDataDir` antes de volver a desarrollo. La eliminación de una copia requiere identificar expresamente su proyecto y ruta; no usar limpieza global ni eliminar desarrollo. El ensayo automático equivalente usa exclusivamente datos técnicos: `mvnw.cmd -Ppersistence-it '-Dit.test=ComposePersistenceIT' verify`. Sus proyectos aleatorios y secretos son exclusivos; cada origen/restauración recibe su propio `DEMANDFLOW_DATA_DIR` dentro del TempDir de JUnit. Compose elimina solo sus contenedores/redes; JUnit limpia únicamente su carpeta temporal. No usa rutas de D:/DOCKER_DATA ni secretos permanentes.

## Límites y diagnóstico

Ambas conexiones JDBC usan `connectTimeout=5`, `loginTimeout=5`, `socketTimeout=15` y `cancelSignalTimeout=2` segundos. Hikari limita adquisición a 5000 ms y validación a 2000 ms; `initializationFailTimeout=1` exige fallo inicial, pero no promete un arranque completo en 1 ms. Ambos roles tienen `statement_timeout=15s` y `lock_timeout=5s`; Flyway usa cero reintentos de conexión y tres de bloqueo. Las pruebas exigen disponibilidad o salida espontánea fallida antes de 75 s y cierre ordinario antes de 10 s. Son límites nativos y un criterio de prueba; no hay supervisor global de la JVM.

Los errores de propiedades vacías, conexión rechazada/no disponible o inicialización de migraciones detienen el arranque; no hay fallback sin BD. Revisar primero perfil, ruta y existencia de archivos, puerto publicado, healthcheck y sincronización de contraseñas con los roles existentes. El healthcheck no comprueba credenciales. Para checksum divergente o SQL fallido, consultar el historial mediante administración local y revisar la migración sin activar debug ni imprimir configuración, URL o secretos. Los loggers de infraestructura se restringen y el reporte ofrece categorías saneadas; conservar ese nivel al compartir diagnósticos.

El reporte distingue `configuration incomplete`, `database connection unavailable or rejected`, `migration validation failed` y `migration execution failed`; estas dos últimas categorías no autorizan repair ni edición de versiones aplicadas. Un directorio configtree ausente también es configuración incompleta. Hibernate puede sustituir la causa de autenticación por un fallo de metadata: revisar la sincronización de la cuenta runtime, sin fijar un dialecto para ocultar la falta de conexión.

La revisión de la imagen y sus límites se registra en [image-security-review.md](../../openspec/changes/archive/2026-11-25-setup-database/image-security-review.md). Los resultados de arranque y privilegios no sustituyen esa revisión ni aprueban excepciones de seguridad.

## Ampliación operativa de manage-tariffs: V2 y V3

Esta sección amplía acumulativamente la base técnica V1 descrita anteriormente. Corresponde al modelo físico del catálogo de HU-001/002, según `openspec/changes/manage-tariffs/design.md` (T2/T5/T8/T9) y las tareas 2.1–2.4 de `openspec/changes/manage-tariffs/tasks.md`. No acredita todavía entidades JPA, casos de uso, pantallas ni los controles web de grupos posteriores. Los resultados reales y observaciones de seguridad se conservan en `openspec/changes/manage-tariffs/evidence.md`; no se declaran resueltos los avisos ni se aceptan riesgos adicionales.

### Migraciones y privilegios de objetos

Las migraciones productivas de `src/main/resources/db/migration/` son:

| Versión | Efecto |
|---|---|
| `V1__initialize_application_namespace.sql` | Namespace, propiedad y USAGE originales; archivo e historial inmutables |
| `V2__create_tariff_catalog.sql` | `tariff_period`, `requirement_type` y `hourly_rate`; tipos fijos Soporte/Feature, checks, FKs, unicidad global y exclusión GiST inclusiva, incluso para períodos inactivos |
| `V3__create_catalog_audit.sql` | `catalog_audit_event`, FKs/checks/índices de auditoría y grants explícitos de tablas, columnas y secuencias |

Las cuatro tablas pertenecen a `demandflow_migrator` en `demandflow`; `infra.flyway_schema_history` permanece separado. No se añaden extensiones, tablas de demandas, tarifas automáticas ni datos de ejemplo. Las tarifas usan NUMERIC(15,2); esa columna no sustituye la futura validación de escala de entrada, porque PostgreSQL puede convertir decimales antes de comprobar restricciones. Las etiquetas no son únicas y la disponibilidad efectiva de tarifas no se almacena en una columna.

| Objeto | Operaciones runtime autorizadas |
|---|---|
| Períodos | SELECT; INSERT sin id; UPDATE de etiqueta, fechas, estado y versión |
| Tarifas | SELECT; INSERT sin id; UPDATE solo de valor, estado y versión; tipo/período inmutables |
| Tipos fijos | SELECT exclusivamente |
| Auditoría | SELECT e INSERT sin id; sin UPDATE, DELETE o TRUNCATE |
| Secuencias de identidad necesarias | USAGE, sin permiso de setval |

Runtime no recibe propiedad, CREATE, DELETE de negocio ni acceso a `infra`; no hay grants globales o por defecto para objetos futuros. No resolver un error 42501 concediendo superusuario, propietario o permisos generales. Append-only protege los flujos ordinarios y los permisos runtime; no promete inviolabilidad frente al propietario/administrador. La escritura transaccional de eventos desde casos de uso sigue en su grupo de implementación.

### Verificación aislada y disponibilidad JDBC de prueba

El siguiente comando fue verificado para esta etapa y usa solo contenedores temporales, secretos sintéticos y TempDir. No requiere arrancar `demandflow-local`, modificar `.secrets` ni preparar la carpeta permanente:

```powershell
.\mvnw.cmd -B -Ppersistence-it '-Dit.test=MigrationEvolutionIT,PostgresReadinessIT,PersistenceSupportIT,BootstrapIT,NativeFailureIT,CatalogSchemaIT,CatalogAuditSchemaIT' verify
```

Requiere Java 21, Docker de prueba accesible e imagen aprobada `postgres:17.12-trixie`. Surefire ejecuta las unitarias; Failsafe ejecuta únicamente las IT seleccionadas. Los reportes quedan en `target/surefire-reports/` y `target/failsafe-reports/`, sin versionarlos ni copiar logs completos con información sensible. La selección verifica base vacía y evolución V1→V2/V3, repetición, checksums, rollback de V3/V4, esquema/permisos, bootstrap, límites nativos y soporte técnico V1 sin JPA. No equivale a aprobar la suite completa heredada ni las funciones pendientes de otros grupos.

`PostgresTestSupport` espera consultas JDBC autenticadas con migrador y runtime, no solo puerto abierto: plazo de prueba 120 s, sondeo condicionado cada 100 ms y consulta `select current_user, 1`. Los límites nativos de conexión/login/socket/cancelación y consulta del sondeo son de 1 s y pertenecen solo a pruebas. Únicamente 57P03/08001/08006 permiten seguir esperando; autenticación, SQL y estados desconocidos fallan de inmediato. Un plazo agotado falla, sin omitir la prueba ni conectar a desarrollo. Los timeouts productivos documentados arriba no cambian. No añadir pausas fijas, reintentos de migraciones ni usar la BD permanente para evitar fallos de preparación.

### Aplicación futura sobre la base permanente

Las pruebas anteriores no aplican V2/V3 al entorno permanente ni autorizan hacerlo. Antes de esa operación se requiere autorización operativa separada, revisión del incremento y de sus observaciones de seguridad, identificación del destino y un respaldo recuperable verificado según los procedimientos de esta guía. Conservar PostgreSQL 17.12-trixie, el proyecto/contenedor, secretos externos, separación de cuentas y `D:/DOCKER_DATA/POSTGRES_DATA`; no reinicializar, trasladar ni recrear almacenamiento o bootstrap.

Cuando esa operación se autorice, el arranque explícito con `persistence` y las mismas credenciales externas aplica versiones pendientes con la cuenta migradora antes de validar JPA. El modo predeterminado sigue sin BD ni migraciones; una URL presente no lo activa. Hibernate conserva `validate`, sin DDL automático, y Open Session in View sigue deshabilitado. El operador debe contrastar historial/checksums, propietarios, restricciones y privilegios efectivos con el modelo aprobado, sin imprimir secretos o cadenas JDBC completas. No ejecutar las comprobaciones antiguas de «namespace vacío» como expectativa del esquema V2/V3.

Cada versión confirma su propia transacción: un fallo de V3 puede dejar V2 confirmada y sus tipos fijos, pero debe revertir la tabla/grants de V3 y no producir un arranque disponible. No interpretar ausencia de éxito como ausencia de cambios anteriores, ni un puerto abierto/healthcheck como prueba de migración o permisos. Detener Java, conservar estado y diagnosticar; no continuar con un esquema parcial ni recurrir a un modo alternativo sin BD.

### Diagnóstico y recuperación de migraciones

Ante errores, revisar localmente perfil, archivos externos, puerto y disponibilidad JDBC; después, con una cuenta autorizada, historial de `infra`, versiones/checksums y objetos/ACL efectivos. Una violación de exclusión 23P01 o unicidad 23505 corresponde a sus restricciones, una FK 23503 a integridad referencial y 42501 a permisos; su tratamiento web seguro se implementará en su grupo. No activar logging SQL o publicar mensajes crudos para resolverlos.

No editar V1 ni migraciones ya aplicadas, borrar versiones del historial, activar repair/baseline/out-of-order o saltar versiones fallidas. Corregir archivos aún no aplicados solo dentro de la revisión autorizada; para versiones aplicadas preparar una nueva migración correctiva aprobada. Los fixtures V4 de `src/test/resources/migrations/` son exclusivamente de pruebas y no se copian a producción.

Preferir recuperación hacia adelante, conservando tablas y auditoría. Volver al artefacto anterior no revierte el esquema ni garantiza compatibilidad: ensayarlo en una copia aislada antes de considerarlo una recuperación válida. Si se requiere restaurar, hacerlo mediante operador autorizado y respaldo verificado en un destino independiente con roles, propietarios, ACL e historial conservados. No hay migración down destructiva ni limpieza/recreación automática del entorno permanente.
