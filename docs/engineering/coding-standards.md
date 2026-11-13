# Estándares de programación Java
**Estado:** propuesta.

- Aplicar KISS, YAGNI, DRY y SOLID con criterio; evitar abstracciones sin uso real.
- Nombres de clases `PascalCase`, métodos/variables `camelCase`, constantes `UPPER_SNAKE_CASE`; paquetes minúsculos.
- Separar DTO de entrada, modelo de dominio y entidades de persistencia cuando haya una razón clara.
- Controladores delgados; lógica de cálculo y reglas fuera de Thymeleaf y controladores.
- Preferir inyección por constructor; evitar estado mutable global y lógica de negocio en entidades JPA cuando dificulte pruebas.
- Usar `BigDecimal` para dinero y horas; no `float`/`double`; aplicar escala de dos decimales y redondeo final `HALF_UP` conforme a RF-MON-001..005.
- Fechas contractuales con `LocalDate`; eventos de auditoría con instante UTC.
- Validación de entrada en servidor y restricciones de BD como segunda línea.
- Operaciones que cambian demanda y auditoría en una transacción.
- Excepciones específicas y respuestas sin stack traces ni secretos.
- No introducir Lombok, MapStruct ni bibliotecas accesorias sin justificar su aporte educativo.
- Documentar decisiones no obvias, no comentar lo evidente.
- No registrar PII, credenciales ni datos contractuales sensibles en logs.
- Formato consistente y revisión automatizada de compilación, tests y dependencias.
