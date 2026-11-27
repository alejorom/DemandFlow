# Uso del catálogo de períodos y tarifas

La navegación del catálogo permite acceder a **Períodos de vigencia** y **Tarifas COP/h**. Los listados incluyen registros activos e inactivos. En pantallas pequeñas, las tablas se pueden desplazar horizontalmente sin perder sus encabezados.

## Períodos de vigencia

1. Selecciona **Crear período**. Escribe una etiqueta de hasta 100 caracteres y las fechas de inicio y fin inclusivo en formato ISO, por ejemplo `2027-01-01`.
2. Elimina los espacios exteriores de la etiqueta si lo deseas; el sistema también los normaliza. La etiqueta no puede quedar vacía y puede repetirse en otro período.
3. La fecha de inicio no puede ser posterior al fin. La duración es flexible, incluso de un día. El intervalo no puede coincidir total o parcialmente con otro, aunque esté inactivo; tampoco pueden compartir un extremo.
4. Al guardar correctamente se abre el detalle. Crear un período no crea tarifas automáticamente.

Desde el detalle puedes editar el período o cambiar su estado. Desactivar un período conserva los estados individuales de sus tarifas, pero impide su disponibilidad. Reactivarlo solo restablece la disponibilidad de las tarifas que también estén activas. Las operaciones redundantes se rechazan.

## Tarifas COP/h

1. Selecciona **Crear tarifa**, un período activo y uno de los dos tipos fijos: **Soporte** o **Feature**.
2. Introduce el valor por hora en COP, usando punto decimal, sin separadores de miles. Debe ser positivo, con un máximo de dos decimales y hasta `9999999999999.99`. Ejemplos válidos: `0.01`, `125000`, `125000.50`.
3. Una combinación período/tipo solo puede registrarse una vez, incluso si su tarifa está inactiva. No se redondean entradas con decimales adicionales.
4. Al guardar correctamente se abre el detalle, que muestra el valor, ambos estados y la disponibilidad efectiva.

**Corregir valor** conserva el identificador, tipo, período y estados. Está permitido aunque el período o la tarifa estén inactivos; corregir no reactiva ni habilita la tarifa. Puedes desactivar una tarifa; para reactivarla su período debe estar activo. Los tipos y el período de una tarifa existente son inmutables.

## Errores, recarga y teclado

Los errores se muestran junto a los campos y los errores generales aparecen antes de ellos. Conserva los valores válidos y corrige lo indicado. Si falta una versión válida, se muestra **Recarga el registro**. Si otro guardado dejó obsoleta la versión, utiliza **Recargar registro**, revisa los datos actuales y vuelve a introducir conscientemente tu cambio; no se reintenta automáticamente.

Usa Tab y Mayús+Tab para recorrer enlaces, campos y botones, y Enter para activar el control enfocado. El primer enlace permite **Saltar al contenido**. Los campos tienen etiquetas y el foco es visible. Las tablas tienen título y encabezados; su región desplazable puede recibir foco con el teclado.

Esta guía cubre únicamente el catálogo. No incluye registro de demandas, consultas visuales de auditoría ni administración de tipos.
