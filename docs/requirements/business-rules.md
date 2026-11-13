# DemandFlow — Reglas de negocio complementarias aprobadas

**Estado:** reglas aprobadas; documentación integrada en borrador de refinamiento.
**Alcance:** integra y precisa las reglas RN-001 a RN-041 sin eliminar su redacción original.
**Precedencia:** en los puntos expresamente refinados, prevalece la decisión posterior aprobada; conservar texto original para trazabilidad.

## Catálogo de 33 reglas complementarias aprobadas

## RF-MON

### RF-MON-001
La tarifa horaria se expresa en COP con máximo dos decimales.

### RF-MON-002
Las horas estimadas y contratadas se expresan como horas decimales con máximo dos decimales; no se usa formato HH:MM.

### RF-MON-003
Importe COP = horas contratadas × tarifa horaria aplicada almacenada; aritmética decimal exacta BigDecimal; redondear una sola vez después de multiplicar a dos decimales con HALF_UP; los totales agregan importes persistidos ya redondeados.

### RF-MON-004
La tarifa debe ser estrictamente positiva; las horas estimadas y contratadas son obligatorias y no negativas; se admite importe cero; no se admiten valores negativos ni nulos obligatorios.

### RF-MON-005
Persistencia: horas NUMERIC(12,2), tarifa horaria NUMERIC(15,2), importe NUMERIC(27,2); validar límites y rechazar más de dos decimales, sin redondeo silencioso de entradas.

## RF-JIRA

### RF-JIRA-001
Clave estructurada PROYECTO-NÚMERO; normalizar mayúsculas y eliminar espacios externos; prohibir espacios internos; proyecto derivado de la clave, sin entrada independiente.

### RF-JIRA-002
Unicidad global de clave JIRA normalizada, sin distinguir mayúsculas, también para demandas inactivas; clave inmutable tras crear.

### RF-JIRA-003
Longitud máxima de la clave JIRA: 50 caracteres.

### RF-JIRA-004
Preservar ceros iniciales del número: CORE-0007 y CORE-7 son distintos; el sufijo debe contener al menos un dígito distinto de cero.

### RF-JIRA-005
El proyecto debe comenzar con una letra y continuar con letras, dígitos o guion bajo; patrón normalizado ^[A-Z][A-Z0-9_]*-[0-9]+$ y número positivo; rechazar guiones extra, espacios y sufijos no numéricos.

### RF-JIRA-006
Validar clave y unicidad en aplicación antes de insertar, reforzar con UNIQUE en PostgreSQL sobre clave normalizada y convertir colisiones concurrentes en error funcional; claves inactivas permanecen reservadas.

## RF-PER

### RF-PER-001
Una vez asociada cualquier demanda, activa o inactiva, no se permite cambiar fechas de inicio o fin del período.

### RF-PER-002
Solo se pueden editar fechas si no hay demandas asociadas; inicio <= fin; fechas inclusivas; no se admiten solapamientos con ningún otro período, activo o inactivo; validar atómicamente y auditar cambios.

### RF-PER-003
Se permite desactivar o reactivar períodos con demandas asociadas sin alterar snapshots ni reactivar tarifas individuales; períodos inactivos no se usan para nuevas demandas; reactivación verifica integridad.

### RF-PER-004
Duración flexible de períodos: intervalos válidos de cualquier longitud, incluidos anuales, semestrales y especiales; fechas inclusivas y ausencia de solapamientos conforme a RF-PER-001 a 003.

## RF-TAR

### RF-TAR-001
Se permite cambiar el valor de tarifas ya utilizadas; las demandas anteriores conservan su snapshot e importe; nuevas demandas toman el valor actualizado; auditar valor anterior y nuevo sin recalcular demandas existentes.

### RF-TAR-002
Existe una sola tarifa por combinación período/tipo, incluso si está inactiva; para cambiar valor se edita el registro existente.

### RF-TAR-003
Tarifa activa utilizable para nuevas demandas solo si el período también está activo; desactivación no altera históricos; reactivación de tarifa solo con período activo y sin reactivar automáticamente otras tarifas; cambios auditados.

### RF-TAR-004
Al modificar solo horas de una demanda se usa su snapshot aunque la tarifa esté inactiva; al cambiar tipo o período se exige tarifa activa de la combinación final; consultar y validar tarifa al guardar con protección transaccional frente a concurrencia.

## RF-DEM

### RF-DEM-001
JIRA y proyecto derivado no editables; se pueden editar tipo, período, horas estimadas y contratadas; cambio de horas contratadas recalcula con snapshot; cambio de tipo/período exige tarifa activa nueva y actualiza snapshot/importe; importe y snapshot no son campos editables directamente; horas estimadas no afectan importe.

### RF-DEM-002
Desactivación lógica conserva datos, clave JIRA, snapshot e importe; impide edición mientras inactiva; registra auditoría; no equivale a anulación económica; totales respetan filtro de estado.

### RF-DEM-003
Restaurar demanda inactiva preserva tipo, período, snapshot e importe aunque tarifa o período estén inactivos; no requiere nueva tarifa ni recálculo; auditar transición.

### RF-DEM-004
Auditar cambios efectivos con antes/después, UTC, origen LOCAL_USER/SYSTEM, misma transacción; guardados sin cambios no generan evento; rechazar desactivar ya inactiva o restaurar ya activa sin evento.

### RF-DEM-005
Demandas inactivas no pueden editarse; deben restaurarse antes de modificarlas.

## RF-CON

### RF-CON-001
Control optimista con @Version para entidades editables; ante versión obsoleta, rechazar sin sobrescritura ni fusión automática; incluir desactivación y restauración.

### RF-CON-002
La selección de tarifa y período se valida al guardar con protección transaccional y bloqueo puntual coherente para evitar snapshots basados en valores o estados obsoletos; ante conflicto, cancelar y solicitar reintento.

### RF-CON-003
Cada mutación y su auditoría se confirman atómicamente en una transacción; fallo en auditoría revierte operación de negocio.

### RF-CON-004
Solapamientos de períodos se impiden en aplicación y mediante restricción de exclusión en PostgreSQL, incluyendo períodos activos e inactivos y fechas inclusivas.

### RF-CON-005
Traducir conflictos de versión, JIRA duplicado, tarifa/estado concurrente, solapamiento y auditoría fallida a resultados funcionales seguros, sin exponer SQL ni trazas; guardados sin cambios no se auditan.

## RF-CONSU

### RF-CONSU-001
Paginación en servidor; 20 registros por página inicialmente; opciones 10, 20, 50 y 100; primera página al cambiar filtros o tamaño; inactivas excluidas por defecto; navegación de páginas.

### RF-CONSU-002
Orden predeterminado fechaCreacion DESC, id DESC; permitir ordenar por JIRA, proyecto, tipo, período, horas estimadas, horas contratadas e importe COP; desempate por identificador para orden estable.

### RF-CONSU-003
Totales de horas estimadas, contratadas y COP calculados sobre todos los registros que cumplen filtros combinables, no solo página visible; importes agregados desde valores persistidos redondeados; respetar filtro de estado.

### RF-CONSU-004
Aplicar los mismos filtros al listado y agregados; cambios de filtros o tamaño reinician página; consultas posteriores pueden reflejar cambios concurrentes, sin exigir snapshot congelado de búsqueda.

## Refinamientos controlados de reglas originales

| Regla original | Redacción histórica | Refinamiento aprobado vigente |
|---|---|---|
| RN-001 | Las tarifas corresponden a vigencias anuales definidas. | RF-PER-004 admite intervalos de duración flexible; no se exige anualidad. |
| RN-031 | ID JIRA admite letras, números, guiones y guiones bajos. | RF-JIRA-001/004/005 concretan el formato estructurado `PROYECTO-NÚMERO`, con un único separador guion y proyecto iniciado en letra. |

## Criterio de consolidación
- La especificación operativa de las 41 RN está en [functional-requirements.md](functional-requirements.md); este archivo contiene las 33 RF adicionales aprobadas, sin duplicarlas.
- El DOCX original se conserva intacto en `docs/reference/` como evidencia histórica, no como versión oficial.
- Se publicará una primera línea base solo tras aprobación formal del conjunto.
- No inicializar OpenSpec ni generar código.
- No convertir verificaciones técnicas pendientes en reglas de negocio adicionales sin aprobación.
