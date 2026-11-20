# ADR-002: Java 21 LTS, Spring Boot y Maven

**Estado:** Aceptado  
**Fecha:** 2026-11-13  
**Ámbito:** DemandFlow v1.0  
**Tipo:** Decisión tecnológica

## 1. Contexto

DemandFlow debe ser una aplicación web sencilla y mantenible, adecuada para aprender Java desde cero, implementar reglas económicas confiables y trabajar con especificaciones OpenSpec. Se necesita una plataforma con herramientas estables de compilación, pruebas y depuración, compatible con el monolito modular aprobado en ADR-001.

## 2. Decisión aceptada

Utilizar:

- **Java 21 LTS** como lenguaje y plataforma de ejecución.
- **Spring Boot** como framework principal de la aplicación web.
- **Apache Maven** para dependencias, compilación, ejecución de pruebas y empaquetado.

La **versión concreta de Spring Boot**, sus dependencias administradas y plugins se fijarán al crear el proyecto, tras verificar compatibilidad y soporte con Java 21. No se presupone que cualquier versión futura sea compatible.

### Herramientas de pruebas incluidas en el diseño

- **JUnit Jupiter 6** (versión gestionada por Spring Boot 4.1.2; usar las versiones de sus componentes gestionadas de forma compatible).
- **Spring Boot Test** para pruebas de integración y de componentes Spring cuando corresponda.
- **Maven Surefire/Failsafe** u otros plugins de prueba según las necesidades efectivas del proyecto.

Estas herramientas son parte de la estrategia de pruebas, no una arquitectura alternativa. Su configuración detallada corresponde a `docs/engineering/testing-strategy.md`.

## 3. Alternativas consideradas (no seleccionadas)

| Alternativa | Evaluación |
|---|---|
| Gradle | Viable, pero Maven ofrece un flujo declarativo suficiente para este proyecto. |
| Java 25 LTS | Alternativa válida; Java 21 LTS es la base aprobada y evita cambios sin necesidad. |
| Spring Framework sin Spring Boot | Requiere configuración manual adicional para un beneficio limitado en este MVP. |
| Framework web distinto de Spring | Puede funcionar, pero no cumple el objetivo de aprendizaje de Spring Boot. |
| Java 21 + Spring Boot + Maven | **Seleccionada.** |

## 4. Justificación

Java 21 ofrece un lenguaje moderno y una versión de soporte prolongado. Spring Boot simplifica el arranque de la aplicación y la integración de componentes web, persistencia y pruebas. Maven aporta un ciclo de construcción predecible y un descriptor de dependencias versionable.

La combinación permite concentrarse en conceptos de Java y diseño de software antes de incorporar herramientas adicionales.

## 5. Consecuencias y compromisos

**Beneficios:** ecosistema consolidado, herramientas de depuración y pruebas, configuración relativamente simple, documentación abundante y compilaciones reproducibles.

**Compromisos:** aprender el contenedor de Spring, la inyección de dependencias, el ciclo de vida de beans y el funcionamiento de Maven. Se debe vigilar la compatibilidad y seguridad de dependencias.

## 6. Buenas prácticas de implementación

### 6.1. Java y calidad de código
- Aplicar convenciones Java: `PascalCase` para clases, `camelCase` para métodos/variables y paquetes en minúsculas.
- Preferir clases pequeñas, nombres descriptivos y métodos de responsabilidad definida.
- Usar `BigDecimal` para valores monetarios y horas; evitar `float` y `double` en cálculos económicos.
- Usar `LocalDate` para vigencias contractuales y un instante temporal consistente para auditoría.
- Manejar excepciones específicas; no ocultar errores con capturas genéricas vacías.
- Evitar dependencias accesorias y generación automática de código hasta justificar su utilidad educativa.

### 6.2. Spring Boot
- Preferir **inyección por constructor**; evitar inyección de campos.
- Mantener controladores enfocados en HTTP, validación y delegación.
- Ubicar transacciones en los casos de uso/servicios de aplicación apropiados.
- Usar validación del lado servidor y mensajes de error seguros.
- Separar configuración por entornos; no incluir secretos en `application.properties` ni en Git.
- Exponer solo endpoints necesarios y limitar información diagnóstica de Actuator si se incorpora.

### 6.3. Maven y dependencias
- Fijar una versión compatible de Spring Boot y usar su gestión de dependencias cuando proceda.
- Versionar `pom.xml` y el Maven Wrapper si se incorpora; no versionar directorios `target/`.
- Evitar versiones dinámicas o dependencias duplicadas sin necesidad.
- Revisar vulnerabilidades de dependencias y actualizar de manera controlada.
- Verificar licencias y procedencia de librerías de terceros antes de agregarlas.

### 6.4. Seguridad OWASP
- Validar toda entrada del usuario en el servidor.
- Escapar salida HTML y conservar las protecciones CSRF en formularios con cambios de estado.
- No revelar stack traces ni secretos al usuario final.
- Configurar límites razonables para entradas, formularios y registros.
- **Sin autenticación, limitar el MVP a un entorno local protegido; no publicar la aplicación en Internet.**

### 6.5. Pruebas
- **JUnit Jupiter 6** (versión gestionada por Spring Boot) para pruebas unitarias de cálculos, validaciones y reglas de negocio.
- **Spring Boot Test** para integración cuando se requiera contexto Spring.
- Pruebas de persistencia con PostgreSQL para restricciones y transacciones.
- Mantener las pruebas independientes y reproducibles; evitar que dependan del orden de ejecución.
- No usar el porcentaje de cobertura como sustituto de probar casos críticos y límites.

## 7. Criterios de verificación

La implementación de esta decisión se comprobará cuando:

- [ ] El proyecto compile y se ejecute con Java 21.
- [ ] Spring Boot utilice una versión explícita y compatible.
- [ ] Maven resuelva dependencias y genere el artefacto esperado.
- [ ] Las pruebas JUnit Jupiter 6 se ejecuten desde Maven.
- [ ] Las pruebas Spring Boot necesarias se ejecuten correctamente.
- [ ] Se utilice inyección por constructor en componentes aplicables.
- [ ] No existan secretos en Git ni dependencias críticas vulnerables sin tratamiento documentado.
- [ ] Los errores HTTP no expongan información interna.
- [ ] Se respeten los límites de despliegue local mientras no exista autenticación.

## 8. Referencias internas

- ADR-001: monolito modular con capas pragmáticas (**Aceptado**).
- ADR-003: PostgreSQL y Docker (**Aceptado**).
- ADR-004: Spring Data JPA y Flyway (**Aceptado**).
- `docs/engineering/coding-standards.md`
- `docs/engineering/testing-strategy.md`
- `docs/engineering/security-standards.md`

## 9. Estado de la decisión

**Aceptado.** La elección de Java 21 LTS, Spring Boot y Maven está aprobada. Las versiones exactas, configuración y verificación corresponden a la fase de preparación e implementación, que todavía no ha comenzado.

**Enmienda aprobada (2026-11-14):** se sustituye JUnit 5 por JUnit Jupiter 6, en la versión gestionada por Spring Boot 4.1.2. Spring Framework 7 exige JUnit Jupiter 6.0 o superior para `SpringExtension`. La decisión de usar JUnit y Spring Boot Test no cambia; solo el número de versión.
