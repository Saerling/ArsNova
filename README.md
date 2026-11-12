# ArsNova

App de notas a mano para Android (tablet y teléfono), en la línea de Notability, Noteshelf, Goodnotes o StarNote. Kotlin + Jetpack Compose, con un motor de tinta nativo.

**Funciones:** biblioteca de cuadernos con miniaturas · plantillas (en blanco, rayado, cuadrícula, puntos) · bolígrafo sensible a la presión (contorno de ancho variable) · resaltador · borrador por trazo (también con el botón del lápiz) · lazo para mover, recolorear o borrar · mantener el trazo quieto lo convierte en línea recta · deshacer/rehacer · zoom y desplazamiento con dos dedos · modo "solo lápiz" con rechazo de palma · páginas múltiples · exportar a PDF.

**Descarga:** pestaña *Releases* (cada push a `main` compila un APK firmado).

**Más funciones:** baja latencia (predicción de trazo + entrada sin búfer) · vista previa del grosor al flotar el lápiz · rueda de herramientas con doble toque del lápiz (configurable, pensado para Huawei M-Pencil) · importar y anotar PDF · modo oscuro · audio ligado a los trazos (toca lo escrito y escucha ese momento) · figuras (línea, triángulo, rectángulo, círculo) al mantener el trazo · cuadros de texto e imágenes · reconocimiento de escritura en el dispositivo para buscar y convertir a texto · miniaturas de páginas · resumen y tarjetas con IA (opcional, clave propia de Claude).

**Tinta:** cada lápiz tiene su carácter (bolígrafo, pluma, lápiz, pincel, monolínea, caligrafía de plumilla plana…) y lo que ves al escribir es lo que queda al levantar el lápiz. El lienzo se mantiene nítido con mucho zoom, la mano apoyada no estorba al lápiz, y un toque con dos dedos deshace (tres, rehace).

**Fórmulas:** inserta LaTeX con vista previa en vivo (Insertar → Fórmula) o convierte tu escritura a fórmula desde la selección (necesita tu clave de API de Claude).

**Rueda del lápiz:** el doble toque abre un dial bajo la punta: se gira para cambiar de herramienta o de grosor, con colores, deshacer y rehacer.

**Barra:** estilo Apple (cada lápiz es su propio botón), editable, acoplable a los cuatro bordes y, minimizada, libre. Favoritos de lápiz, ajuste fino de grosor/estabilización/opacidad, mantener para borrar, zooms guardados y página a LaTeX.

**Buscar y LaTeX:** lupa para buscar palabras dentro de la nota (tu letra se reconoce en el dispositivo y se resalta dónde está) y Vista LaTeX por página con cambio entre Manuscrito, Código, Vista previa y Dividida.

**Seguridad de tus notas:** papelera con restaurar, y copia de seguridad de todo en un .zip (Ajustes).

**Diseño:** estética monocroma de Mochy Music (Inter, esquinas continuas, pulsación con rebote) e iconos propios. Ver `CREDITOS.md`.

**Desarrollo:** `gradle testDebugUnitTest` genera imágenes de los lápices e iconos (`app/build/ink`) y capturas de las pantallas (`app/build/shots`).

Licencia: GPL-3.0.
