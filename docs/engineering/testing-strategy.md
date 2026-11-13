# Estrategia de pruebas
**Estado:** estrategia de validación propuesta para decisiones funcionales y ADR aprobados; implementación pendiente.

## Pirámide práctica
1. **Unitarias (JUnit 5):** cálculo de demanda, cero horas, decimales, cambio de tipo/vigencia, preservación de tarifa histórica.
2. **Integración (Spring Boot + PostgreSQL de prueba):** unicidad JIRA incluso inactiva, solape de vigencias, transacciones de auditoría, restricciones y migraciones Flyway.
3. **Web/MVC:** validación de formularios, errores seguros, filtros, sumas, CSRF y escape HTML.
4. **Pruebas de aceptación:** escenarios OpenSpec vinculados a HU-001..HU-008.

## Casos críticos
- Duplicado de JIRA con diferencias de mayúsculas.
- Duplicado concurrente.
- Desactivación y restauración sin pérdida histórica.
- Cambio de horas sin adoptar tarifa corregida.
- Cambio explícito de tipo/vigencia con nueva tarifa y auditoría.
- Ausencia de tarifa activa impide registro.
- Totales sobre todo el conjunto filtrado, no solo la página.
- Fallo de auditoría revierte actualización.
- Entrada maliciosa no ejecuta SQL ni scripts.
- Clave JIRA con patrón estructurado, sufijo positivo y ceros iniciales preservados.
- Vigencias de duración flexible; bloqueo de fechas usadas y solapamientos incluso con períodos inactivos.
- Tarifa estrictamente positiva, máximo dos decimales y redondeo único `HALF_UP`.
- Optimistic locking y cambios concurrentes de tarifa/período con protección transaccional.
- Paginación 10/20/50/100, orden estable y totales invariantes entre páginas para un mismo filtro.
- Restauración histórica sin recálculo aunque catálogo asociado esté inactivo.
- Guardado sin cambios no genera auditoría.

## Criterios de finalización
Compilación, pruebas unitarias y de integración relevantes, migraciones verificadas y escenarios de aceptación satisfechos; ninguna vulnerabilidad crítica conocida sin decisión documentada.
