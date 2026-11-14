# ADR-007 — Seguridad OWASP desde el diseño

**Estado:** Aceptado  
**Fecha:** 2026-11-13  
**Ámbito:** DemandFlow v1.0  
**Relacionados:** ADR-001 a ADR-006

## 1. Contexto y problema

DemandFlow gestiona tarifas, horas contratadas e importes económicos, con auditoría e histórico. Aunque es un MVP educativo, procesa entradas del navegador, consulta una base de datos y utiliza credenciales de infraestructura. El ADR original propone validación en servidor, CSRF, escape de salida, secretos fuera del repositorio, revisión de dependencias y ejecución local protegida. La ausencia de autenticación limita expresamente su uso.

El objetivo es definir controles mínimos, proporcionados y comprobables sin transformar el MVP en un producto de identidad o una plataforma de seguridad compleja.

## 2. Decisión

1. **Límite de despliegue.** DemandFlow funcionará únicamente en equipo local o red aislada de confianza. No se expondrá a Internet ni a usuarios no confiables. Sin autenticación no existe autorización por identidad; el aislamiento de red es una condición de uso, no una defensa equivalente a un sistema de control de acceso.
2. **Entradas.** Controladores Spring MVC con DTO de entrada y Bean Validation; validación de negocio adicional en aplicación/dominio. No se confía en controles del navegador ni en JavaScript para imponer invariantes.
3. **Salidas.** Thymeleaf con escape contextual para texto y atributos; no usar salida HTML sin escape para contenido de usuario. Evitar concatenar datos no confiables en JavaScript, CSS o URL.
4. **CSRF.** Proteger operaciones que modifican estado. Si se incorpora Spring Security, definir y probar explícitamente una configuración apropiada para el MVP sin autenticación; no desactivar CSRF por comodidad. CSRF no protege contra accesos directos de un actor no autorizado.
5. **Persistencia.** Utilizar JPA/consultas parametrizadas, sin construir SQL con concatenación de entrada; restricciones de base de datos para invariantes críticas. Separar, cuando sea viable, privilegios de migración y ejecución; evitar superusuario para la aplicación.
6. **Secretos y configuración.** Credenciales mediante variables de entorno o mecanismos locales no versionados. Archivos de ejemplo sin secretos reales; `.gitignore` y revisión de cambios para prevenir filtraciones. Nunca registrar contraseñas o cadenas de conexión completas.
7. **HTTP y errores.** Respuestas de error sin stack traces ni detalles internos para el usuario; registros técnicos locales con acceso restringido. Aplicar cabeceras de seguridad pertinentes y evaluar su compatibilidad con Bootstrap/Thymeleaf.
8. **Dependencias.** Versiones fijadas y mantenidas de Java, Spring Boot y dependencias Maven; revisión periódica de vulnerabilidades y actualizaciones compatibles.
9. **Auditoría.** Conservar la auditoría transaccional del ADR-006; excluir secretos y datos innecesarios. La auditoría de negocio no reemplaza el registro de errores de seguridad.
10. **Red y despliegue.** Restringir interfaces/puertos expuestos tanto para la aplicación como para PostgreSQL en Docker Compose; verificar la configuración efectiva. Evitar valores por defecto inseguros.

## 3. Alternativas consideradas

| Alternativa | Evaluación |
|---|---|
| Aplazar la seguridad hasta publicar | Rechazada: acumula deuda y permite errores evitables en formularios y datos. |
| Añadir autenticación y roles completos ahora | No adoptada en MVP: complejidad no justificada; imprescindible reconsiderarla antes de exposición o multiusuario. |
| Desactivar CSRF para simplificar formularios | Rechazada: sacrifica un control de bajo coste relativo. |
| Separar frontend SPA/API | No aporta por sí mismo seguridad; contradice la simplicidad elegida en ADR-005. |

## 4. Justificación

Los controles seleccionados siguen el principio de defensa en profundidad: validar, escapar, parametrizar, restringir y verificar. Su aplicación se distribuye entre presentación, casos de uso, persistencia y operación, sin trasladar las reglas económicas al frontend. La seguridad se integra desde el inicio y se mantiene alineada con el alcance local del MVP.

## 5. Consecuencias y compromisos

**Positivas:** reducción de riesgos de inyección, XSS, CSRF, exposición accidental de secretos y configuraciones inseguras; criterios de prueba explícitos; mejor preparación para evolución posterior.

**Costes:** configuración y pruebas adicionales; disciplina en dependencias y despliegue; posible complejidad de integración de Spring Security.

**Limitación no resuelta:** sin identidad/autorización no se puede atribuir una acción a una persona autenticada ni proteger operaciones frente a cualquier usuario con acceso al servicio. `LOCAL_USER` representa un origen técnico, no una identidad verificada. Si se requiere acceso remoto o multiusuario, debe redactarse un nuevo ADR antes del despliegue.

## 6. Controles y relación con riesgos OWASP

- **Control de acceso:** restricción de entorno; limitación explícita por ausencia de autenticación.
- **Configuración insegura:** puertos, errores, cabeceras, credenciales y ajustes explícitos.
- **Inyección:** consultas parametrizadas, validación y restricciones de datos.
- **Integridad de software y dependencias:** Maven, versiones controladas y revisión de vulnerabilidades.
- **Fallos de registro y monitorización:** auditoría de negocio y logs técnicos apropiados sin secretos.
- **Diseño inseguro:** validación de invariantes económicas y pruebas de casos negativos.

La clasificación exacta de riesgos deberá alinearse con la versión de OWASP Top 10 vigente al ejecutar la revisión de seguridad; los controles aquí definidos no dependen de la numeración de sus categorías.

## 7. Impacto en la arquitectura

Actualizar de manera **aditiva** `docs/architecture/architecture.md` y referenciar el diagrama único de límites de seguridad en `docs/architecture/diagrams/security-boundaries.excalidraw` y `.png`. ADR-001 a ADR-006 permanecen intactos. La estrategia de pruebas y los estándares de seguridad podrán ampliarse posteriormente, partiendo de sus versiones vigentes y sin sobrescribirlos a ciegas.

## 8. Criterios de verificación

- [ ] Un formulario de escritura sin token CSRF válido es rechazado en la configuración final.
- [ ] Un formulario válido con token CSRF supera ese control y llega a validaciones de negocio.
- [ ] Cadenas de prueba con caracteres HTML se muestran escapadas, no se interpretan como contenido activo.
- [ ] Entradas inválidas se rechazan en el servidor; las invariantes económicas no dependen del cliente.
- [ ] Las consultas con entrada no confiable usan parámetros; no existe concatenación insegura.
- [ ] Los errores visibles no exponen stack traces, credenciales ni SQL interno.
- [ ] No hay secretos reales versionados ni registrados en logs.
- [ ] Se verifican puertos e interfaces efectivamente publicados para aplicación y PostgreSQL.
- [ ] Se revisan dependencias y se documenta la gestión de vulnerabilidades relevantes.
- [ ] Las pruebas de integración confirman que auditoría y cambios de negocio siguen siendo atómicos.
- [ ] La aplicación no exige un login accidental ni deja sin protección los formularios por una configuración no revisada.

## 9. Restricciones y asuntos pendientes

- Definir la configuración concreta de Spring Security durante el diseño técnico y verificarla con pruebas, sin anticipar código ahora.
- Seleccionar versiones compatibles de Spring Boot y dependencias al inicializar el proyecto.
- Establecer el mecanismo local de gestión de secretos y la política de copias/recuperación antes de operar con datos relevantes.
- Evaluar autenticación, autorización, HTTPS y controles de acceso por rol **antes** de cualquier despliegue fuera del entorno local protegido.

## 10. Referencias internas

- [Arquitectura consolidada](../architecture/architecture.md)
- [ADR-001](ADR-001-architectural-style.md), [ADR-002](ADR-002-java-spring.md), [ADR-003](ADR-003-postgresql-docker.md)
- [ADR-004](ADR-004-jpa-flyway.md), [ADR-005](ADR-005-server-rendered-ui.md), [ADR-006](ADR-006-audit-history.md)
- [Estándares de seguridad](../engineering/security-standards.md) y [estrategia de pruebas](../engineering/testing-strategy.md)

**Aprobación:** aceptada. OpenSpec está inicializado; la implementación y las pruebas de seguridad siguen pendientes y requieren autorización independiente. Los bloqueos funcionales que se identifiquen deben resolverse antes de implementar.
