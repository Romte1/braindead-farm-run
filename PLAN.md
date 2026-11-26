# Braindead Farm Run — Plan inicial

> Estado: **aprobado; Fase 1 en curso**.
> Fecha: 2026-11-26.

## 1. Objetivo

Crear un plugin de RuneLite **nuevo, independiente y a medida** para planificar y ejecutar
farm runs: qué plantar, qué llevar, en qué orden visitar los patches, cuándo volver y qué
hacer en cada parada.

No es un fork del código de Farm Run Optimizer. Se parte de la plantilla oficial
`runelite/example-plugin` y se reconstruye el dominio desde cero, reutilizando **ideas,
requisitos y lecciones** del plugin anterior, no su implementación.

La prioridad es que el plugin sea:

- **Predecible**: mismo estado, mismo plan.
- **Rápido**: sin escaneos de escena por tick ni trabajo pesado en el hilo del cliente.
- **Testeable**: el cálculo de rutas, suministros y timers es Java puro.
- **Mantenible**: el plugin no es una clase gigante; cada cosa tiene un dueño claro.
- **Seguro para el Plugin Hub**: sin automatización, sin inyección de input y sin features
  rechazadas por Jagex/RuneLite.

## 2. Identidad del plugin

| Campo | Valor propuesto |
|---|---|
| Nombre visible | **Braindead Farm Run** |
| Repositorio | `Romte1/braindead-farm-run` (público, Plugin Hub) |
| Paquete Java | `com.romte.braindeadfarmrun` |
| Clase principal | `BraindeadFarmRunPlugin` |
| Plugin ID resultante | `braindeadfarmrunplugin` |
| Config group | `braindeadfarmrun` |
| Licencia | BSD-2-Clause, copyright del autor del plugin |
| Versión inicial | `0.1.0` |
| `build` del Hub | `standard` (sin dependencias extra) |
| Idioma | English en UI, README, CHANGELOG y comentarios de código |
| Alcance v1 | Allotment, herbs, tree y fruit tree |

Notas:

- El **plugin ID** y el **config group** deben ser nuevos. Reutilizar `herbrunhelper` haría
  que RuneLite mezcle la configuración con el plugin original y que ambos compitan por el
  mismo estado.
- Se empieza **limpio**: no se importa config, timers ni historial de `herbrunhelper`.
- El `icon.png` debe ser PNG real, pequeño (máx. 48×72) y con licencia propia.

### Decisiones fijadas

- Público para el **Plugin Hub**.
- **English** en todo lo visible y en la documentación.
- v1 solo con **allotment, herbs, tree y fruit tree**.
- Solo lo esencial: sin historial, profit, yield, XP, contratos, enlaces sociales ni banner.
- Sin migración desde el plugin anterior.
- Repositorio definitivo: `Romte1/braindead-farm-run`.

## 3. Principios de ingeniería

Sacados de `AGENTS.md` y de los problemas reales del plugin anterior:

- Java 11 y estructura de example-plugin.
- Constantes de `net.runelite.api.gameval` siempre que existan; nada de números mágicos.
- **Todo acceso a `Client`/widgets en el hilo del cliente.** Si el plugin se activa desde el
  panel de configuración (AWT), el trabajo se difiere con `clientThread.invokeLater`.
- Nunca `Thread.sleep()`, ni I/O de red/disco en el hilo del cliente.
- Nada de reflexión, JNI/JNA, memoria nativa ni procesos externos.
- Solo OkHttp y el `Gson` inyectado; no añadir dependencias transitivas de RuneLite.
- Seguimiento de objetos/NPCs por eventos de spawn/despawn, no escaneando la escena.
- Overlays pequeños y sin asignaciones innecesarias por frame.
- `log.debug()` para diagnóstico; INFO solo para arranque/parada o eventos raros.
- Limpieza total en `shutDown()`: overlays, listeners, suscripciones, ejecutores.
- No mezclar reformateos masivos con cambios funcionales.
- Cumplir las restricciones del Plugin Hub (ver §14).

## 4. Estructura del repositorio

Generado desde `runelite/example-plugin` (o su script `create_new_plugin.py`):

```
braindead-farm-run/
├── build.gradle
├── settings.gradle
├── gradle/wrapper/...
├── gradlew
├── gradlew.bat
├── runelite-plugin.properties
├── icon.png
├── README.md
├── LICENSE
├── CHANGELOG.md
├── AGENTS.md
├── .gitignore
├── .github/workflows/build.yml
└── src/
    ├── main/java/com/romte/braindeadfarmrun/...
    ├── main/resources/...
    ├── test/java/com/romte/braindeadfarmrun/...
    └── test/resources/logback-test.xml
```

`build.gradle` sigue el template:

- `runeLiteVersion = 'latest.release'`.
- `compileOnly net.runelite:client`, Lombok `compileOnly`/`annotationProcessor`.
- `testImplementation` JUnit 4.12 + client + jshell.
- `options.release = 11`.
- Tarea `run` con `pluginMainClass` apuntando a `BraindeadFarmRunPluginTest`.
- Tarea `shadowJar` del template (opcional, pero se conserva).
- `runelite-plugin.properties` con `build=standard`.

## 5. Arquitectura

El plugin se divide en capas con dependencias en una sola dirección:

```
UI / Overlays / Bank  ->  Runtime services  ->  Domain (Java puro)
                              |
                              v
                         Data tables
```

### 5.1. Paquetes

```
com.romte.braindeadfarmrun
├── BraindeadFarmRunPlugin          // ciclo de vida y cableado de eventos
├── BraindeadFarmRunConfig          // configuración
├── BraindeadFarmRunPanel           // panel lateral (vista)
├── domain/                       // Java puro, sin RuneLite Client
│   ├── patch/                    // PatchType, Crop, PatchDefinition, PatchState
│   ├── run/                      // RunOptions, RunPlan, RunStop, RouteLeg
│   ├── supply/                   // SupplyLine, SupplyGroup, Payment, Tool
│   └── travel/                   // Teleport, TravelItem, RuneRequirement
├── data/                         // tablas estáticas y validación
│   ├── Crops
│   ├── Patches
│   ├── Teleports
│   ├── TravelItems
│   ├── Payments
│   └── Tools
├── runtime/                      // estado vivo y servicios
│   ├── PatchTracker
│   ├── TimerService
│   ├── InventorySnapshot
│   ├── BankSnapshot
│   ├── EquipmentSnapshot
│   ├── LeprechaunStore
│   ├── RunePouch
│   ├── ContractService
│   ├── HistoryService
│   └── PlanService
├── planning/                     // cálculo puro
│   ├── RoutePlanner
│   ├── SupplyPlanner
│   └── PlanValidator
├── ui/
│   ├── panel/                    // secciones y filas del panel
│   └── overlay/                  // overlays del juego
├── bank/                         // pestaña del banco
│   ├── BankTab
│   ├── BankTabButton
│   ├── BankTabButtonOverlay
│   └── BankWithdrawActions
├── integration/
│   ├── ShortestPathIntegration
│   ├── TimeTrackingImport
│   └── ChatNotifier
├── store/                        // persistencia
│   ├── StateStore
│   └── ConfigStateStore
└── util/
```

### 5.2. Responsabilidades

- **`BraindeadFarmRunPlugin`**: solo `startUp`, `shutDown`, `@Provides` y reenvío de eventos a
  los servicios. Objetivo: menos de ~300 líneas.
- **`domain`**: enums, records/clases inmutables y reglas. Cero imports de `net.runelite.api`.
- **`data`**: tablas estáticas (cultivos, patches, teleports, pagos, herramientas). Se
  validan con tests.
- **`runtime`**: traduce eventos del cliente a estado de dominio y captura snapshots.
- **`planning`**: recibe snapshots inmutables y devuelve un `RunPlan`. Java puro.
- **`ui`**: solo pinta el plan y manda intenciones (botones); no calcula.
- **`bank`**: módulo aislado; no conoce el planner salvo por `SupplyLine`.
- **`integration`**: integraciones blandas, sin reflexión.
- **`store`**: persistencia abstraída; la implementación inicial usa `ConfigManager`.

## 6. Modelo de dominio

### 6.1. Patches y cultivos

- `PatchType`: `HERB`, `ALLOTMENT`, `TREE`, `FRUIT_TREE`. La arquitectura deja el enum y
  las tablas preparados para añadir hardwood, bushes, hops, cactus, calquat, spirit tree,
  celastrus y redwood en el futuro, pero v1 no los incluye.
- `Crop`: nombre, tipo, nivel, semilla/sapling, etapas, ticks, pago, icono. Tabla validada
  contra RuneLite Time Tracking / OSRS Wiki.
- `PatchDefinition`: id estable, tipo, región, tile, IDs de objeto, varbit(s), farmer,
  métodos de limpieza y teleports candidatos.
- `PatchState`: estado (`EMPTY`, `PLANTED`, `GROWING`, `READY`, `DISEASED`, `DEAD`,
  `CHECKED`), cultivo, `plantedAt`, compost, protegido, contrato, última observación.
- `GrowthClock`: cálculo de próxima etapa y de "listo" a partir de `plantedAt`, tipo y
  cultivo. Debe funcionar offline y tras relog.

### 6.2. Run y suministros

- `RunOptions`: tipos incluidos, cultivos permitidos, patches deshabilitados, teleports
  deshabilitados, compost, pagos, contrato, perfil de ruta, etc.
- `RunPlan`: paradas ordenadas, piernas de viaje, suministros, advertencias y motivo de cada
  decisión.
- `RunStop`: patch, acción esperada (plantar, cosechar, pagar, limpiar, revisar salud),
  teleport de llegada, items requeridos.
- `SupplyLine`: nombre, IDs de item, cantidad necesaria, `noted`, `wear`, grupo, estado
  (`CARRIED`, `IN_STORAGE`, `MISSING`), dónde está.
- `SupplyGroup`: `EQUIPMENT`, `TOOLS`, `SEEDS`, `RUNES`, `TRAVEL`, `PAYMENTS`, `OTHER`.
- `Teleport`: tipo (`ITEM`, `TABLET`, `SPELL`, `FAIRY_RING`, `OTHER`), destino, requisitos,
  coste de runas, items aceptados (variantes con cargas).
- `Payment`: item y cantidad por patch.
- `Tool`: herramienta necesaria y si es reutilizable.

### 6.3. Reglas clave

- Las runas de un teleport pertenecen a **la pierna concreta**, no al run entero. Civitas:
  `2 Law, 1 Earth, 1 Fire`. Kourend: `2 Law, 1 Water, 1 Fire`.
- Sustitución de runas por staves/tomos/runas combinadas en un único `RuneSupply`.
- `noted` se decide por tipo de suministro y por el sitio donde se usa.
- Los wearables van a `EQUIPMENT`; el resto de viaje no equipado va a `TRAVEL`.
- Los placeholders del banco se conservan y se etiquetan; no se convierten en item real.

## 7. Datos estáticos

Todo lo que es "tabla" vive en `data/` y tiene tests:

- Cultivos: nivel, semilla, etapas, tick, pago, icono.
- Patches: coordenadas, IDs de objeto, varbits, farmer, tipo.
- Teleports: destino, tipo, requisitos, runas, items.
- Travel items: items que se llevan pero no se equipan.
- Payments y tools.

Reglas:

- Usar `ItemID`, `ObjectID`, `VarbitID`, `InterfaceID`, `SpriteID`, etc. de `gameval`.
- Si un ID no existe en gameval, declararlo en la tabla con comentario y fuente.
- Un test de integridad comprueba duplicados, IDs inválidos, growth times y costes de runas.
- No copiar tablas del plugin viejo sin revisarlas; se reconstruyen y se validan.

## 8. Runtime, threading y rendimiento

### 8.1. Hilo del cliente

- Todos los eventos de RuneLite llegan en el hilo del cliente salvo que se indique lo
  contrario.
- `startUp`/`shutDown` pueden ejecutarse desde AWT: **nunca** tocar widgets directamente.
- Cualquier inicialización de UI/banco se difiere con `clientThread.invokeLater`.
- El estado compartido se publica con snapshots inmutables o `volatile`.

### 8.2. Snapshots

`InventorySnapshot`, `BankSnapshot`, `EquipmentSnapshot`, `PatchSnapshot`, `PlayerSnapshot`
son inmutables y se capturan en el hilo del cliente. El planner nunca ve `Client`.

### 8.3. PlanService

- Un único ejecutor de un hilo para planificar.
- Debounce (p. ej. 200–300 ms) para no recalcular en cada evento.
- Cancelación de planes obsoletos con un número de generación.
- Resultado aplicado en el hilo del cliente.
- Si el cálculo falla, se conserva el último plan válido y se registra en `debug`.

### 8.4. Seguimiento de patches

- NPCs y objetos por `NpcSpawned`/`NpcDespawned`, `GameObjectSpawned`/`GameObjectDespawned`.
- Estados por `VarbitChanged` y `ItemContainerChanged`.
- Nada de recorrer la escena cada tick.
- Los patches se indexan por `PatchDefinition.id`.

### 8.5. Presupuesto

- Tracking: < 1 ms por tick en condiciones normales.
- Overlays: < 0.2 ms por frame; sin `new` en `render` salvo casos justificados.
- Planner: fuera del hilo del cliente; sin bloquear el tick.
- Sin logs por tick/frame.

## 9. UI

### 9.1. Panel lateral

Secciones:

1. **Run**: perfil de run, tipos incluidos, estado general.
2. **Supplies**: equipment, tools, seeds, runes, travel, payments.
3. **Route**: paradas ordenadas y siguiente parada.
4. **Timers**: patches listos, en crecimiento, enfermos.
5. **Settings**: accesos a la config relevante.

El panel es una vista: recibe un `PanelViewModel` inmutable y pinta. Los botones solo
publican intenciones.

### 9.2. Overlays

- **NextStepOverlay**: qué hacer ahora y en la siguiente parada.
- **PatchHighlightOverlay**: resalta el patch objetivo.
- **InventoryHighlightOverlay**: resalta los items del paso actual.
- **ReadyOverlay**: patches listos.
- **MinimapArrowOverlay**: dirección al siguiente stop.
- **BankTabButtonOverlay**: efecto alrededor del botón del banco mientras está cerrado.

Reglas: colores/fuentes cacheados, sin asignaciones por frame, sin cálculos de ruta en
`render`.

## 10. Pestaña del banco (módulo clave)

Se replica el enfoque que funciona en Quest Helper / Farm Run Autopilot, no Bank Tags.

### 10.1. Botón

- Hijo de `InterfaceID.Bankmain.UNIVERSE`.
- `x = 380`, `y = 5`, tamaño 25; icono 19 en `+3, +3`.
- `380` deja libre el botón de Quest Helper (`408`) y queda dentro del área útil.
- Acción `View tab` y callback de click.
- Sonido de pestaña al abrir/cerrar.
- Aviso si la tienda de pociones está abierta.

### 10.2. Ciclo de vida

- `setEnabled` siempre difiere a `clientThread`.
- `WidgetLoaded(BANKMAIN)` → `reload()`: descarta referencias viejas y recrea.
- `BANKMAIN_FINISHBUILDING` → `init()` solo si el botón no es hijo real del banco actual.
- `isChildOf(parent, child)` por identidad, no por `getParent()`.
- `destroy()` oculta y limpia referencias.
- Nunca asumir que el widget del banco sobrevive al cierre.

### 10.3. Layout

Al construir el banco (`BANKMAIN_FINISHBUILDING`):

1. Ocultar widgets de items y divisores de pestañas del banco.
2. Reutilizar los widgets dinámicos de `Bankmain.ITEMS`.
3. Secciones, en orden:
   - `Deposit first` (si hay items a depositar).
   - `Equipment`.
   - `Inventory`: `Tools` → `Seeds` → `Runes` → `Travel` → `Payments`.
   - `Everything else`.
4. Placeholders visibles, con opacidad y `Release`/`Examine`.
5. Etiqueta de cantidad + `noted` cuando aplique.
6. Icono de estado: llevado (check), en almacenamiento (sin icono), falta (cruz).
7. Ajustar `scrollHeight` y `UPDATE_SCROLLBAR`.

### 10.4. Interacción

- `Details` consume el click y muestra el detalle en chat.
- Corregir `param0` del menú al slot real del banco (los widgets se reutilizan).
- Acciones de retirada según varbits:
  `WITHDRAW_QUANTITY_VARBIT`, `WITHDRAW_X_VARBIT`, `CHARGES_VARBIT`, `PLACEHOLDER_VARBIT`.
- No usar Bank Tags ni tags propios.

### 10.5. Criterios de aceptación

- Abrir/cerrar el banco 10 veces mantiene el botón.
- Cambiar de pestaña y volver mantiene el botón.
- Activar/desactivar el plugin con el banco abierto no lanza errores.
- Los placeholders no se pierden ni se convierten en items reales.
- Retirar desde la pestaña funciona con cantidad 1/5/10/X/All.
- Sin errores de "must be called on client thread".

## 11. Configuración y persistencia

### 11.1. Config

Secciones: `Run`, `Route`, `Supplies`, `Bank`, `UI`, `Notifications`, `Advanced`.

- Perfiles de run como enum (`HERBS`, `TREES`, `FRUIT_TREES`, `ALLOTMENTS`, `ALL`,
  `CUSTOM`) en vez de booleanos sueltos.
- Listas de patches/cultivos/teleports deshabilitados como strings parseadas y validadas.
- Un flag `debug` para logs detallados.
- No dejar claves muertas ni duplicadas (el plugin viejo tenía `animateBankButton` y
  `bankTabGlow`, `highlightBank` obsoleto, etc.).

### 11.2. Persistencia

- v1: timers y estado de patches en `ConfigManager` como JSON pequeño por perfil. Sin I/O de
  archivos.
- Si el JSON crece demasiado: `StateStore` con implementación de archivo en
  `~/.runelite/braindead-farm-run/`, escritura en un ejecutor y lectura al arrancar.
- Guardado con debounce; nunca en el hilo del cliente.
- Sin migración desde `herbrunhelper`: se empieza limpio.

## 12. Integraciones

- **Shortest Path**: `PluginMessage` en el event bus (`shortestpath` / `path`, `clear`).
  Sin reflexión. Si no está instalado, se muestra un aviso y el plan sigue funcionando.
- **Leprechaun**: almacén de herramientas y pagos.
- **Rune pouch**: contenido para el cálculo de runas.
- **Chat**: mensajes de "listo" y de error, sin spam.

Fuera de v1: importación de Time Tracking, contratos, historial, profit, yield y XP.

## 13. Testing y CI

### 13.1. Tests

Java puro, sin `Client`:

- Crecimiento de cada cultivo y tipo de patch.
- Orden de ruta y elección de teleport.
- Coste de runas por pierna (Civitas, Kourend y resto).
- Agregación de suministros: noted/unnoted, wear, placeholders.
- Pagos y herramientas.
- Parseo de listas de config y migración.
- Serialización/deserialización de estado.

Tests de integración ligeros:

- `BraindeadFarmRunPluginTest` para arrancar el cliente en desarrollo.
- Fixtures de planes (JSON) para detectar regresiones.

### 13.2. CI

- GitHub Actions: `./gradlew build --no-daemon` en push/PR.
- Sin dependencias nuevas; si alguna vez hace falta, usar verificación de dependencias del
  Plugin Hub.
- Build local con `.\gradlew.bat build --no-daemon`.

## 14. Cumplimiento del Plugin Hub

Checklist desde el día uno:

- Sin inyección de input, autotyping ni automatización de combate.
- Sin predicciones de combate/boss ni scouting PvP.
- Sin acciones de menú que envíen acciones al servidor por sí solas.
- Sin desocultar componentes ocultos de la interfaz.
- Sin mover zonas de click de combate/inventario/equipo/spellbook.
- Sin crowdsourcing ni exposición de datos de jugadores.
- Sin dependencias externas no verificadas.
- Código abierto, licencia BSD-2, README claro.
- Nada de features que RuneLite haya rechazado o revertido.

## 15. Roadmap por fases

### Fase 0 — Esqueleto (0.1.0)

- Generar repo desde el template.
- Renombrar paquete, plugin, config y test.
- `runelite-plugin.properties`, `icon.png`, README, LICENSE, CHANGELOG, AGENTS.md.
- CI de build.
- `run` task funcionando con un plugin vacío.

**Aceptación:** `gradlew build` verde y el cliente arranca con el plugin vacío.

### Fase 1 — Dominio y timers (0.2.0)

- `PatchType`, `Crop`, `PatchDefinition`, `PatchState`, `GrowthClock`.
- Tablas de cultivos y patches con tests de integridad.
- `PatchTracker` por eventos y varbits.
- Persistencia de timers.
- Panel mínimo de timers.

**Aceptación:** plantar/cosechar actualiza el estado; los timers sobreviven a relog.

### Fase 2 — Planner y suministros (0.3.0)

- `RunOptions`, `RunPlan`, `SupplyPlanner`, `RoutePlanner`.
- Teleports, runas, pagos, tools, travel items, wearables.
- Tests de rutas, runas y agregación.

**Aceptación:** un run de prueba produce un plan coherente y testeado.

### Fase 3 — UI y overlays (0.4.0)

- Panel completo con secciones.
- Next step, patch highlight, inventory highlight, ready, minimap arrow.
- Notificaciones.

**Aceptación:** el plan se puede seguir en juego sin abrir el banco.

### Fase 4 — Pestaña del banco (0.5.0)

- Botón, ciclo de vida, layout, secciones, placeholders, noted, retiradas.
- Overlay del botón.
- Checklist manual de abrir/cerrar/cambiar pestaña.

**Aceptación:** §10.5 completo.

### Fase 5 — Integraciones y rendimiento (0.6.0)

- Shortest Path, leprechaun y rune pouch.
- Ajustes de rendimiento y pruebas de estrés.
- Sin Time Tracking, contratos, historial ni estadísticas en v1.

**Aceptación:** integraciones opt-in y sin errores si no están instaladas.

### Fase 6 — Pulido y Hub (1.0.0)

- README con capturas, guía de configuración y atribuciones.
- Revisión de rendimiento y limpieza.
- Checklist del Hub.
- Envío a `runelite/plugin-hub` (si es público).

## 16. Riesgos y mitigaciones

| Riesgo | Mitigación |
|---|---|
| Scope creep (el plugin viejo tiene 49 ajustes y 421 KB) | MVP por fases; cada fase con aceptación cerrada. |
| Datos de cultivos/teleports incorrectos | Tablas con fuente y tests de integridad. |
| Widgets del banco frágiles entre updates | Módulo aislado, `reload()` en `WidgetLoaded`, pruebas manuales. |
| Trabajo pesado en el hilo del cliente | Snapshots + planner en ejecutor + debounce. |
| Conflictos de config/ID con el plugin viejo | Grupo e ID nuevos; sin migración. |
| Rechazo del Hub por ser un fork | Código y branding propios; no copiar código; revisar §14. |

## 17. Decisiones fijadas y pendientes menores

Fijadas:

- Público para el **Plugin Hub**.
- **English** en UI, README, CHANGELOG y comentarios.
- v1 con **allotment, herbs, tree y fruit tree**.
- Solo lo esencial: sin historial, profit, yield, XP, contratos, enlaces sociales ni banner.
- Sin migración desde `herbrunhelper`.
- Repositorio: `Romte1/braindead-farm-run`.

Pendientes menores (se resuelven durante la implementación):

- Estilo visual del banco: se replica el enfoque probado, con branding propio.
- Shortest Path: recomendado pero opcional; sin errores si falta.
- Icono y capturas del README: se hacen en la fase de pulido.

## 18. Criterios de aceptación de v1.0

- `gradlew build` y tests verdes.
- Cero errores de hilo del cliente en arranque, banco y relog.
- Timers persistentes y correctos tras logout/login.
- Plan de run reproducible y testeado para allotment, herbs, tree y fruit tree.
- Pestaña del banco estable en apertura/cierre repetidos.
- Runas de Civitas y Kourend correctas.
- Sin escaneos de escena por tick.
- Sin features prohibidas por el Hub.
- README, LICENSE, CHANGELOG y AGENTS.md completos, en inglés.
- Sin migración ni dependencia del plugin anterior.

## 19. Siguiente paso

Plan aprobado. Fase 0 en curso:

1. Generar el repo `Romte1/braindead-farm-run` desde `runelite/example-plugin`.
2. Renombrar identidad y dejar el esqueleto compilando.
3. CI, README, LICENSE, CHANGELOG y AGENTS.md.
4. Después, Fase 1 (dominio + timers) sin tocar aún el banco.
