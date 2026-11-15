# Motor de tinta: estado y migración posible a Jetpack Ink

## Hoy
Motor propio en `app/src/main/java/com/arsnova/ink`:
- `Nib.kt`: geometría del trazo (presión con inercia, afinado de puntas, Catmull-Rom, unión de piezas convexas). Se puede construir por tramos (`addRange`) y sabe qué parte del trazo ya es definitiva (`nodes(..., stable)`).
- `Ink.kt`: pinceles (bolígrafo, pluma, lápiz con grano e inclinación, crayón, rotulador, pincel, caligrafía, monolínea, discontinua, acuarela, resaltador, cinta) y el trazo en curso incremental (`Ink.Live`: la parte seca se guarda, cada cuadro solo arma la cola).
- `InkView.kt`: entrada (puntos históricos, predicción `androidx.input:motionprediction`, `requestUnbufferedDispatch`, rechazo de palma, `FLAG_CANCELED`), cachés en bitmap por página, repintado de la zona sucia, deshacer/rehacer.
- `Shapes.kt`: reconocimiento al mantener el lápiz (elipse/círculo, rectángulo/cuadrado, triángulo, polígonos 5-8, flecha, línea quebrada, curva) y regla de 15°.

## Por qué no se migró aún a androidx.ink
`androidx.ink` 1.0.0 es estable (dic. 2025) y la rama 1.1.0-alpha añade pinceles programables, serialización de mallas y arreglos de inclinación. Aporta `InProgressStrokesView` (front buffer de baja latencia) y geometría para borrador y lazo. No se adopta todavía porque:
1. Formato: guarda `StrokeInputBatch` + `BrushFamily`; las notas actuales (puntos x, y, presión en base64 + estilo) habría que convertirlas al abrir, y los estilos propios (cinta, acuarela, discontinua, caligrafía de plumilla) no tienen equivalente directo.
2. Riesgo: cambiaría a la vez el dibujo en vivo, el borrador, el lazo, el deshacer y la exportación; no se puede medir latencia ni el M-Pencil sin la tablet.
3. Lo más valioso (predicción de movimiento, despacho sin búfer, trazo seco/húmedo) ya está en el motor propio.

## Plan de migración (cuando se pueda probar en la tablet)
1. Añadir `androidx.ink:ink-authoring`, `ink-brush`, `ink-strokes`, `ink-rendering` (misma versión) y `androidx.graphics:graphics-core`.
2. Solo para el trazo en curso: poner un `InProgressStrokesView` encima de `InkView`, alimentarlo con los mismos `MotionEvent` y, al terminar (`onStrokesFinished`), convertir el `Stroke` de Ink a nuestro `Stroke` (x, y, presión de su `StrokeInputBatch`) y dibujarlo con `Ink.drawStroke`. Así se gana el front buffer sin tocar el formato.
3. Mapear pinceles: bolígrafo → `StockBrushes.pressurePen`, rotulador → `marker`, resaltador → `highlighter`; el resto se queda con el motor propio.
4. Medir con un vídeo a 240 fps en la tablet; si mejora, pasar el resto de pinceles con `BrushFamily` propios.
5. Mantener la lectura del formato actual con la prueba `EstabilidadTest.abreNotasAntiguas`.
