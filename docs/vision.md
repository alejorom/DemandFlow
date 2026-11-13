# Visión del producto — DemandFlow
**Estado:** Línea base funcional y arquitectónica v1.0 aprobada; implementación aún no autorizada.

## Problema
El registro manual en Excel de demandas de ingeniería Full Stack dificulta validar unicidad, conservar cambios y mantener tarifas históricas.

## Objetivo
Registrar requerimientos de Soporte y Feature, estimaciones y horas contratadas, calcular demanda COP por tarifa de una vigencia elegida explícitamente, mantener auditoría y consultar totales.

## Usuario inicial
Responsable de registrar y mantener la demanda. Los nombres «administrador» y «usuario» son responsabilidades funcionales, no roles autenticados en el MVP.

## Incluye
- CRUD de vigencias y tarifas con desactivación sin pérdida histórica.
- Registro y mantenimiento de demandas, cálculo y restauración.
- Auditoría de cambios de tarifas y demandas.
- Consulta filtrada por proyecto, tipo, vigencia y estado; totales de horas y COP.

## Excluye
- Importar Excel histórico, exportación, sincronización con JIRA.
- Autenticación/roles, dashboard avanzado, microservicios.

## Criterio de éxito
Las reglas de aceptación del documento funcional integrado en revisión se verifican con pruebas y el usuario puede completar el flujo de registro → cálculo → edición → auditoría → consulta.
