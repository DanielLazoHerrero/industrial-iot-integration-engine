# 🏭 Industrial IoT Integration Engine (Java Legacy 1.4)

¡Bienvenido al núcleo técnico de mi **Motor de Integración Backend e IoT Industrial**! Este repositorio contiene la lógica transaccional de un sistema middleware de alto rendimiento desarrollado bajo especificaciones estrictas de **Java Legacy (1.4.2_06)**. Su función principal es actuar como puente de comunicación en tiempo real entre hardware crítico de planta (básculas automáticas de pesaje masivo), el núcleo del ERP **Oracle E-Business Suite (R12)** y el sistema de etiquetado industrial **Zebra (ZPL-II)** mediante conexiones por sockets TCP/IP puros.

Para garantizar estándares de diseño de nivel Senior, la lógica de negocio ha sido **desacoplada al 100% de las interfaces gráficas (Swing/AWT)** de la aplicación original, transformándola en un motor de servicios invisible optimizado para entornos de manufactura pesada.

---

## 📂 Estructura del Repositorio

El proyecto está estructurado de manera modular eliminando componentes visuales y aislando las capas de red, parseo y persistencia:

### ⚙️ 1. Núcleo del Motor de Código Fuente (`src/com/industry/engine`)
* **`ScaleReader.java`**: Hilo de ejecución concurrente (`Runnable`) que gestiona el Socket TCP raw de la báscula. Implementa el handshake industrial ASCII (`user admin`, `callback`), lee el stream de datos carácter a carácter, filtra el ruido mecánico para aislar el peso estable e invoca mediante JDBC el procedimiento almacenado transaccional en Oracle.
* **`TokenProcessor.java`**: Motor de interpolación manual de strings altamente eficiente para Java 1.4. Utiliza búferes indexados (`StringBuffer`) para buscar y reemplazar tokens `$#$TOKEN$#$` del layout físico sin sobrecargar el recolector de basura (*Garbage Collector*).
* **`ZebraPrinter.java`**: Controlador de red binario para hardware Zebra. Abre el puerto RAW estándar **9100**, inyecta retornos de carro para limpiar búferes pendientes y ejecuta una rutina de transcodificación manual hacia el juego de caracteres **IBM Code Page 850 / ISO-8859-1** para imprimir caracteres especiales sin corromper la etiqueta.

### 📄 2. Diseños de Etiquetas Físicas (`templates`)
* **`industrial_label_template.zpl`**: Código nativo ZPL-II real extraído de producción, sanitizado y estructurado con tokens dinámicos. Incluye la carga de bloques gráficos y mapas de bits de mapas de memoria (`~DGALMACEN1`) junto con los identificadores variables de pesaje.

---

## ⚙️ Especificaciones del Entorno de Validación
Para asegurar la compatibilidad con infraestructuras heredadas críticas, el motor se ha validado bajo el siguiente entorno:
* **Runtime de Ejecución:** Java Virtual Machine (JVM) v1.4.2_06 (Sistemas Legados)
* **Compatibilidad ERP:** Oracle Applications E-Business Suite R12 (Esquema transaccional `APPS`)
* **Capa de Transporte de Red:** Protocolo TCP/IP puro mediante Sockets nativos de Java
* **Puertos de Red Homologados:** Puerto propietario para terminales de pesaje / Puerto 9100 RAW para impresión industrial

---

## ⚠️ Buenas Prácticas y Gestión de Recursos
* **Abstracción Estricta de Infraestructura:** Se han purgado del código fuente todas las direcciones IP estáticas reales, credenciales de accesos productivos y nombres de esquemas propietarios de la base de datos, reemplazándolos por nomenclaturas universales de la industria (`PKG_ERP_INTEGRATION`).
* **Prevención de Fugas de Memoria (*Memory Leaks*):** Al carecer de estructuras automáticas modernas, la clausura de Sockets, Streams (`BufferedReader`/`PrintWriter`) y sentencias preparadas de base de datos (`CallableStatement`) se realiza de manera imperativa y explícita dentro de bloques `finally`, garantizando la estabilidad ininterrumpida del servidor en entornos de producción 24/7.