# Registro idempotente de avances — tickets #495 y #565

Estado actualizado 2026-11-05: compilado y tres pruebas correctas en JDK 21 y
JDK 25; falta prueba en ejecución. No desplegado.

La integración encontró JaCoCo 0.8.7 instrumentando clases modernas con
`Unsupported class file major version 65`, incluso con `BUILD SUCCESS`.
Actualizarlo a 0.8.14 elimina esas excepciones conservando la cobertura.
Esta versión soporta Java 25 según el
[historial oficial](https://www.jacoco.org/jacoco/trunk/doc/changes.html).
Dos `clean package` protegidos: 18,007 s en JDK 21 y 19,421 s en JDK 25;
3 pruebas por ejecución, cero fallos, errores, omitidas o errores de instrumentación.

Artefacto final `target/sfadvancements.jar`: 144282 bytes,
SHA-256 `23b71ab3f80752a619837c0afef56527000d28b6fdf9f3663f3dd3bdfe848c9d`,
62 clases, bytecode máximo 60 (target 16 conservado); CRC correcto y guarda
`getAdvancement` presente. Compilar con JDK 25 no demuestra compatibilidad
funcional con Paper 26.2.
Faltan los dos arranques reales y comprobar conservación del progreso.

El resto de este documento conserva la evidencia de la pasada anterior.
Pasada del 2026-11-04, iniciada a las 12:06 CLT (UTC-4).

## Evidencia y causa

- El arranque actual de T2 contiene 17 avisos `Advancement already exists`.
- El JAR activo tiene SHA-256 `1a255b6f98e80b2ad27acd309a21be82431563a4aff0150fd4536be03f4cbe2d`.
- `javap` confirma que `init()` llama a `reload(true)`, evitando borrar avances
  y recargar todos los datapacks al iniciar. `loadAdvancement()` intenta cargar
  las mismas claves sin consultar antes el registro de Bukkit.
- La configuración observada del monolito tiene `auto-update` y
  `reload-data-on-adv-remove` activados; el staging modular los tiene desactivados.
  Cambiar esos indicadores no hace idempotente el registro inicial.

La API documenta que `loadAdvancement` guarda avances y que quitarlos totalmente
puede requerir una recarga de datos:
[Paper UnsafeValues](https://jd.papermc.io/paper/1.21.11/org/bukkit/UnsafeValues.html).
La traza y el bytecode locales confirman la ruta concreta del fallo.

## Cambio y alcance

`VanillaHook.loadAdvancement()` reutiliza una clave que ya existe en Bukkit y
la incluye en `loadedKeys`, para que también se reconozca como padre registrado.
Si no existe, conserva el registro anterior.

No sustituye definiciones persistidas ni modifica progreso. La recarga manual y
los listeners `PlayerJoinListener` y `AdvancementListener` permanecen intactos.
Los cambios de definición de avances quedan fuera de este parche.

## Validación y bloqueo

Se añadieron tres pruebas: conservar una instancia persistida sin operaciones
unsafe, registrar una clave nueva y reutilizarla desde una segunda instancia del
hook que simula otro arranque. **No se han ejecutado.**

Comando intentado una sola vez:

```bash
~/ai-hub/scripts/build_seguro.sh 21 "$PWD" mvn -o -B -Djacoco.skip=true clean package
```

El guard esperó 60 intervalos de 10 segundos y terminó con código **75**:
`VPS con carga alta, build omitido`. Maven no llegó a iniciarse. La última
carga observada por el guard fue 8.41, superior al límite de 5.
No hay un artefacto nuevo que pueda declararse BUILT o instalarse.

`git diff --check`, escaneo de secretos y actualización AST/merge del grafo
completados. T2 respondió a 20.0 TPS en las cuatro ventanas; el conteo fue de
cero jugadores. Dallas no se modificó. No se reiniciaron ni recargaron servidores.

## Continuación y reversión

1. Con carga inferior a 5 y al menos 4 GiB disponibles, repetir el comando protegido.
2. Exigir las tres pruebas verdes y comprobar el contenido del JAR.
3. Antes de instalar en T2: respaldo verificable en Drive, lock apropiado y
   comprobación de tamaños y SHA-256. No instalar artefactos antiguos de `target/`.
4. Probar un arranque y otro posterior con avances persistidos, sin borrar datos;
   exigir ausencia de duplicados y conservación del progreso.
5. Dallas requiere un despliegue separado bajo su protocolo; este trabajo no lo autoriza.

Reversión del código: revertir el commit de este cambio. No hay cambios de
ejecución ni de datos que restaurar en esta pasada.
