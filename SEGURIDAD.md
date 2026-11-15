# Seguridad — ArsNova

| # | Punto | Estado |
|---|---|---|
| 1 | Ocultar API keys | ✅ No hay claves en el código; la de IA (Gemini/Claude) la pone cada usuario. |
| 2 | Secretos fuera de Git | ✅ Historial revisado. La firma vive en GitHub Secrets. |
| 3 | Key pública para DB | N/A — cuadernos locales en archivos JSON. |
| 4 | Row-Level Security | N/A. |
| 5 | Encriptar datos sensibles | ✅ La API key de IA se cifra con AES-256-GCM (Android Keystore). |
| 6 | Forzar autenticación | N/A — app local sin cuentas. |
| 7 | Restringir acceso a registros | ✅ FileProvider no exportado, solo la carpeta `exports/`. |
| 8 | Bloquear manipulación de campos | ✅ Los JSON se leen con un esquema fijo (kotlinx.serialization). |
| 9 | Session cookies | N/A. |
| 10 | Hashear contraseñas | N/A. |
| 11 | Rate limiting | N/A — cliente (lo aplica el proveedor de IA). |
| 12 | Protección contra bots | N/A. |
| 13 | Queries parametrizadas | N/A — sin SQL. |
| 14 | Validar inputs | ✅ Importar copia: solo nombres de archivo simples, sin rutas (`../`). |
| 15 | Sanitizar contenido | ✅ La respuesta de la IA se muestra como texto/LaTeX, nunca se ejecuta. |
| 16 | Restringir archivos | ✅ Importación con tope de 512 MB por archivo y 4 GB por copia. |
| 17 | Devolver solo lo necesario | ✅ La IA solo recibe la página que se le pide; la key va en cabecera, no en la URL. |
| 18 | Security headers | N/A — app nativa. |
| 19 | Forzar HTTPS | ✅ `usesCleartextTraffic=false`; todas las APIs son HTTPS. |
| 20 | Escanear dependencias | ✅ Dependabot (Gradle y Actions). |
