# Auditoría y hoja de ruta de mejoras

**Fecha:** 2026-08-30
**Estado:** diseño aprobado, pendiente de plan de implementación
**Alcance:** endurecimiento del repositorio, rediseño de UI/UX, seguridad de credenciales y motor de sincronización

---

## 1. Contexto

`AnimalesDeAsis_DesktopApp` es el sistema de gestión de la Asociación de Asís:
JavaFX 17 sobre SQLite local con sincronización bidireccional offline-first contra
Google Cloud Firestore. Alrededor de 6.700 líneas de Java, 13 vistas FXML,
2.654 líneas de CSS y 478 líneas de test.

Esta auditoría recorrió el código, la configuración de CI y los ajustes del
repositorio en GitHub. Cada hallazgo cita el archivo y la línea, o el comando que
lo produjo. Lo que no se pudo verificar está marcado como tal.

---

## 2. Hallazgos

### 2.1 Seguridad de credenciales

**Passphrase hardcodeada en repositorio público — riesgo alto.**
`Config/CredentialsManager.java:52` declara una constante
`LEGACY_PASSPHRASE` con el passphrase literal.

> El valor no se reproduce aquí a propósito. Repetirlo en documentación lo
> propaga a un archivo más, y sobre todo lo deja sobreviviendo al arreglo: una
> vez rotada la credencial y borrada esa línea del fuente, un documento que la
> cite seguiría publicándola. Para reproducir el hallazgo, leer la línea 52.

`resolvePassphrase()` (líneas 62-72) consulta la variable de entorno
`ANIMALESDEASIS_CRED_KEY`, luego la propiedad de sistema
`animalesdeasis.cred.key`, y **si ninguna está presente cae silenciosamente en esa
constante**. El repositorio es público y los instaladores se publican en GitHub
Releases con el bundle cifrado embebido.

Consecuencia concreta: cualquiera que descargue un instalador y clone el repo
puede derivar la clave AES, descifrar `firebase-credentials.enc` y obtener la
service-account de Firebase. Como el Admin SDK **ignora por completo las reglas de
seguridad de Firestore**, eso concede lectura y escritura totales sobre la base de
datos de la asociación — incluido borrar la colección entera.

**Derivación de clave débil.** `generateKey()` (líneas 78-88) usa
`SHA-256(passphrase)` truncado a 16 bytes. Sin sal y sin función de derivación
iterada, el esquema es vulnerable a diccionario y a tablas precalculadas.

**Cifrado sin autenticación.** `TRANSFORMATION = "AES/CBC/PKCS5Padding"`
(línea 40). CBC sin MAC no detecta manipulación del ciphertext.

**Constante muerta y engañosa.** `Config/FirebaseCredentialsEncryptor.java:50`
declara `TRANSFORMATION = "AES/ECB/PKCS5Padding"`. No se usa —la clase delega en
`CredentialsManager.encrypt()`— pero anuncia ECB, que es exactamente lo que no se
debe usar.

**Fallback silencioso.** `getDecryptedCredentials()` (líneas 139-153) devuelve
`null` ante cualquier fallo y la app arranca en modo offline sin avisar. Una clave
mal rotada se manifiesta como "la sincronización dejó de andar", no como un error.

### 2.2 Configuración del repositorio

Verificado con `gh api repos/Isma-L154/AnimalesDeAsis_DesktopApp`:

| Control | Estado | Evidencia |
|---|---|---|
| Dependabot alerts | Deshabilitado | `GET /vulnerability-alerts` → 404 |
| Dependabot security updates | Deshabilitado | `security_and_analysis.dependabot_security_updates.status` |
| Secret scanning | Deshabilitado | `security_and_analysis.secret_scanning.status` |
| Push protection | Deshabilitado | `security_and_analysis.secret_scanning_push_protection.status` |
| Required status checks | **Ausente** | El ruleset `MainRuleSet` (id 6976721) no incluye regla `required_status_checks` |
| Aprobaciones requeridas | 0 | `rules[].parameters.required_approving_review_count` |
| Resolución de conversaciones | No exigida | `required_review_thread_resolution: false` |
| Protección de tags | **Ausente** | `GET /rulesets` solo devuelve un ruleset con `target: "branch"` |
| CODEOWNERS | No existe | — |
| Análisis de código (CodeQL) | No configurado | Solo existe `workflow-CI.yml` |

Lo más grave de esta tabla es la ausencia de **required status checks**: el ruleset
exige PR, pero no exige que el PR esté en verde. Se puede mergear a `main` con la
suite de tests fallando, y `main` dispara automáticamente una release `latest` con
instaladores.

La ausencia de **protección de tags** significa que un tag `v1.0.0` ya publicado
puede reescribirse para apuntar a otro commit, cambiando qué código representa una
release que la gente ya descargó.

**Actions sin fijar.** `workflow-CI.yml` referencia `actions/checkout@v4`,
`actions/setup-java@v4`, `softprops/action-gh-release@v2`. Las etiquetas son
móviles: quien controle el repositorio de la acción puede cambiar qué código corre
en el pipeline.

### 2.3 Sincronización

**El batch de Firestore se puede desbordar.** `Service/SyncService.java:135-160`
acumula todos los animales y todas las vacunas pendientes en un único
`WriteBatch`. Firestore corta en 500 operaciones por batch. Con 500 registros sin
sincronizar acumulados —el escenario normal tras una temporada sin conexión— el
`commit()` lanza excepción y **ningún** cambio sube.

**El pull no es incremental.** `PullChanges()` (línea 82) ejecuta
`db.collection("animals").get()` sin filtro: descarga la colección completa en
cada sincronización, más una subconsulta de vacunas por cada animal. El costo
crece linealmente con el histórico y se paga cada 24 horas.

**Los borrados offline resucitan.** `deleteVaccineAndSync()` (líneas 230-246),
cuando no hay Firebase disponible, borra solo en local sin registrar la intención.
El siguiente `pullVaccines()` no encuentra ese id en el conjunto local, lo ve en
Firebase y lo reinserta. La vacuna vuelve. No hay tombstones.

**Los fallos se tragan.** `sync()` (líneas 49-64) captura `Exception` y hace
`System.out.println`. Una sincronización rota es indistinguible de una exitosa
desde la interfaz.

**Sin logging estructurado.** 76 ocurrencias de `System.out.print`,
`System.err.print` o `printStackTrace` en `src/main/java`, sin ningún framework de
logging en el `pom.xml`. En una app empaquetada con `jpackage` esa salida no va a
ningún lado consultable, así que ante un problema en la asociación no hay rastro.

### 2.4 Rendimiento y ciclo de vida en JavaFX

**Consulta bloqueando el hilo de UI.**
`Controller/Animal/AnimalManagementController.java:76` llama a
`ServiceFactory.getAnimalService().getActiveAnimals()` directamente dentro de
`initialize()`. Viola la regla de oro del `CLAUDE.md`. Además carga la tabla
completa en memoria y pagina con `subList()` (línea 130), de modo que la
paginación no reduce el trabajo de base de datos.

**Limpieza de controllers incompleta.**
`Controller/PortalController.java:78-82` solo invoca `cleanup()` si el controller
saliente es `instanceof AnimalManagementController`. Cualquier otro —
`StatisticsController`, que registra listeners de sincronización— se descarta sin
liberar sus suscripciones. Cada navegación deja listeners vivos.

### 2.5 UI/UX

**Los tokens de diseño existen pero no se usan.** `css/theme.css` define 15
looked-up colors, y aun así los otros 12 archivos CSS contienen **411 valores
hexadecimales hardcodeados** (`AnimalManagement.css` 88, `CreateForm.css` 49,
`EditForm.css` 49, `StatisticsDashboard.css` 46…) — 426 contando los 15
literales legítimos de `theme.css`. Cambiar el naranja de marca hoy exige editar
13 archivos. Además 2 de las 13 vistas ni siquiera importan `theme.css`.

**Todo es un diálogo modal.** 62 llamadas a `NavigationHelper.show*Alert`, de las
cuales 49 son `showErrorAlert`. Cada validación fallida bloquea la aplicación con
`showAndWait()` y obliga a memorizar el mensaje, cerrar, y encontrar a mano el
campo culpable. No hay validación inline en ningún formulario.

**El sidebar no es alcanzable sin mouse.** `fxml/Sidebar.fxml` define los ítems de
navegación como `Label` con `setOnMouseClicked` (`SidebarController.java:27-40`).
Los `Label` no son enfocables: no hay forma de navegar la aplicación con teclado.

**Emojis como iconografía.** `📊`, `🐶`, `➕`, `🔍`, `📷`, `✅`, `❌` en FXML y en
código. Renderizan distinto en Windows, macOS y Linux, y no heredan color del
tema. `ikonli-fontawesome5-pack` ya está en el `pom.xml` y solo se usa en 2 vistas.

**Dependencias de UI sin usar.** `controlsfx`, `formsfx-core`, `validatorfx` y
`bootstrapfx-core` están declaradas en el `pom.xml` con **cero referencias** en
`src/`. Peso muerto en el instalador y superficie de dependencias innecesaria.

**Colapsar el sidebar borra la navegación.** `PortalController.toggleSidebar()`
(líneas 58-68) hace `setPrefWidth(0)` y `setVisible(false)`: no queda ningún
punto de entrada visible a las secciones.

**Estados ausentes.** No hay estado vacío ("todavía no hay animales"), ni
indicador de carga, ni indicador de conectividad o de última sincronización en
ninguna pantalla.

### 2.6 Lo que está bien

**SQL parametrizado en todas partes.** Los cinco DAO usan exclusivamente
`PreparedStatement` con placeholders; la única construcción dinámica
(`AnimalDAO.java:145-165`) concatena fragmentos fijos y sigue vinculando los
valores con `?`. **Sin hallazgos de inyección SQL.**

**Gestión de secretos en CI correcta.** El bundle cifrado se inyecta desde
`secrets.FIREBASE_CREDENTIALS_ENC` y el `.gitignore` cubre
`**/FireConfig/firebase-credentials.enc`, `*-firebase-adminsdk*.json`,
`service-account*.json` y `google-services.json`.

### 2.7 Controles no aplicables

Del cuestionario de auditoría base, tres controles no aplican por razones
estructurales:

- **CORS** — N/A. La aplicación no expone ningún servidor HTTP; es un cliente de
  escritorio. No hay origen que restringir.
- **Rate limiting** — N/A a nivel de aplicación. No hay endpoints propios. El
  límite relevante es la cuota de Firestore, que administra Google.
- **Content Security Policy** — N/A. No hay superficie web servida.

**Row Level Security** — aplicable en un sentido y **ausente**: la app se conecta
a Firestore con una service-account de administrador, que por diseño evade toda
regla de seguridad. No existe aislamiento por usuario porque no existe el concepto
de usuario. Se documenta como decisión arquitectónica en el Bloque 3.

### 2.8 Lo que no se pudo verificar

- **Las reglas de seguridad de Firestore.** Viven en la consola de Firebase, no en
  este repositorio. Haría falta acceso al proyecto para revisarlas. Son, de todos
  modos, irrelevantes mientras el cliente use credenciales de administrador.
- **Si la passphrase de producción fue rotada.** Requiere ver el valor del secreto
  `FIREBASE_CREDENTIALS_ENC` y la variable de entorno de las máquinas de la
  asociación. **Hasta confirmarlo, hay que asumir que el bundle publicado está
  cifrado con la clave legacy.**
- **El historial de git no fue barrido en busca de secretos ya eliminados.** Se
  revisó el árbol actual. Un escaneo del historial completo queda pendiente para
  el Bloque 3.

---

## 3. Decisiones de diseño acordadas

Cada una se validó con maquetas antes de escribirse aquí.

**Orden de trabajo:** repositorio → UI/UX → credenciales → sincronización. El
repositorio va primero porque es rápido, no toca código y protege todo lo que
venga después.

**Rigor del ruleset:** estricto pero **sin aprobaciones requeridas**. Ismael es el
único maintainer; exigir reviews lo bloquearía a sí mismo. El candado real es que
el CI tiene que estar en verde.

**Alcance de UI/UX:** sistema de diseño **más** reestructuración de la navegación.
Sin modo oscuro en esta etapa; los tokens lo dejan viable más adelante.

**Navegación:** rail lateral con grupos *Gestión* (Animales, Vacunas) y *Análisis*
(Estadísticas), más *Inicio*. Colapsable a 72px. Al colapsar, los encabezados de
grupo se degradan a una regla divisoria y el tooltip pasa a mostrar la ruta
completa ("Gestión › Animales").

Las maquetas incluían además un ítem *Reportes* bajo Análisis. **Se descarta**: hoy
la exportación a CSV y PDF vive dentro de Estadísticas, y promoverla a sección
propia sería inventar una pantalla que nadie pidió. Si más adelante la exportación
crece, el rail ya tiene el hueco donde encajarla.

**Control de colapso:** botón circular flotante montado sobre el borde exterior
del rail, estilo VS Code. Se eligió sobre las alternativas más simples sabiendo
que en JavaFX exige superponer con `StackPane` y cuidar el z-order — es la
variante más delicada de implementar y así se asume.

**Pantalla de Inicio:** panel operativo con tres KPIs (en el albergue, adoptados
en el año, sin vacunas registradas), lista de últimos ingresos, y panel "Requieren
atención" cuyos ítems **navegan a la lista ya filtrada**. Una alerta que no lleva
a ninguna parte es decoración, y la gente aprende a ignorarla.

**Patrón de feedback**, aplicado a los 62 casos:

| Situación | Patrón |
|---|---|
| Validación de formulario | Inline, junto al campo. Guardar deshabilitado mientras haya errores |
| Éxito e información | Toast abajo a la derecha, se desvanece a los 3 s |
| Acción destructiva | Modal — aquí frenar **sí** es correcto. Nombra lo que se va a borrar |
| Fallo real (red, Firebase) | Banner persistente en pantalla con botón Reintentar |

---

## 4. Bloque 1 — Endurecimiento del repositorio

Un solo PR. No toca código de aplicación.

**Ruleset de `main`** (actualizar `MainRuleSet`, id 6976721):
- Añadir `required_status_checks` sobre el job `test`, con
  `strict_required_status_checks_policy` activo.
- Activar `required_review_thread_resolution`.
- Conservar `non_fast_forward`, `deletion` y `pull_request` con 0 aprobaciones.

**Ruleset nuevo de tags** con `target: "tag"` sobre el patrón `v*`:
`deletion`, `non_fast_forward` y `update` bloqueados. Los tags de release pasan a
ser inmutables.

**Ajustes de seguridad del repositorio:** habilitar Dependabot alerts, Dependabot
security updates, secret scanning y push protection.

**Archivos nuevos:**
- `.github/dependabot.yml` — ecosistemas `maven` y `github-actions`, cadencia
  semanal.
- `.github/CODEOWNERS` — `@Isma-L154` como dueño por defecto.
- `.github/workflows/codeql.yml` — análisis de CodeQL para Java en PR y semanal.

**Modificado:** `workflow-CI.yml` — fijar todas las acciones a SHA con el
comentario de versión al lado, y bajar `permissions` del nivel de workflow al de
cada job (hoy `contents: write` es global; solo el job `release` lo necesita).

**Verificación:** releer los rulesets y los ajustes por `gh api` tras aplicarlos, y
comprobar que el ruleset de tags rechaza de verdad un intento de mover un tag de
prueba. Una regla configurada y nunca provocada es una suposición.

---

## 5. Bloque 2 — UI/UX

Cuatro PRs secuenciales, cada uno con su Issue y en verde antes del siguiente. Se
parte así porque un PR único tocaría 13 CSS, 13 FXML y casi todos los controllers:
irrevisable, e imposible de bisecar si algo se rompe.

### PR 2.1 — Fundaciones del sistema de diseño

Sin cambios visibles de comportamiento. Prepara el terreno.

- Ampliar `css/theme.css`: paleta completa, escala de espaciado, escala
  tipográfica, radios y elevaciones, todos como looked-up colors sobre `.root`.
- Reemplazar los 411 hexadecimales de los 13 CSS por tokens.
- Hacer que las 13 vistas importen `theme.css`.
- Eliminar `controlsfx`, `formsfx-core`, `validatorfx` y `bootstrapfx-core` del
  `pom.xml`.
- Sustituir los emojis por iconos de `ikonli-fontawesome5`.

**Criterio de aceptación:** el conteo baja de 411 a 0. Comando exacto:

```bash
grep -rno '#[0-9a-fA-F]\{3,8\}' src/main/resources/css --include='*.css' \
  | grep -v '/theme\.css:' | wc -l
```

`theme.css` se excluye porque es donde los literales deben vivir. La app arranca
y las pantallas se ven igual que antes del cambio.

### PR 2.2 — Shell de la aplicación

- `Sidebar.fxml`: rail con grupos Gestión / Análisis, ítems como controles
  enfocables (no `Label`), con `accessibleText`, navegación por Tab y flechas, y
  activación con Enter.
- Botón de colapso circular flotante sobre el borde del rail, con `StackPane` y
  z-order explícito. Gira `‹`/`›`, tiene tooltip y atajo de teclado.
- Estado colapsado y última sección abierta persistidos entre sesiones.
- `Header.fxml`: quitar el logo de 150px, añadir título de sección, buscador
  global (Ctrl+K) e indicador de sincronización.
- Añadir Vacunas como sección propia de primer nivel.
- Sustituir el `instanceof` de `PortalController.loadContent` por un método
  `cleanup()` en `IPortalAwareController`, invocado en todo controller saliente.

**Criterio de aceptación:** la aplicación entera es navegable solo con teclado.
Colapsar el rail nunca deja la navegación inaccesible. Cambiar de sección diez
veces no acumula listeners (verificado con un test que cuenta suscripciones en
`SyncEventManager`).

### PR 2.3 — Pantalla de Inicio

- `HomeView.fxml` + `HomeController` nuevos.
- Consulta nueva en `AnimalDAO`: animales sin vacunas registradas (`LEFT JOIN`).
- Todas las consultas del panel corren en `Task` de fondo, con skeletons mientras
  cargan. Ninguna en el hilo de UI.
- Los ítems de "Requieren atención" navegan a la lista correspondiente ya
  filtrada.
- Arreglar de paso `AnimalManagementController.initialize()`: mover la consulta a
  un `Task` y paginar en SQL con `LIMIT`/`OFFSET` en vez de `subList()` sobre la
  tabla entera.

**Criterio de aceptación:** el panel pinta su esqueleto de inmediato y rellena a
medida que llegan los datos. Ninguna consulta ocurre en el FX Application Thread.

### PR 2.4 — Sistema de feedback

- `ToastManager` nuevo: toasts no bloqueantes abajo a la derecha, apilables, con
  autocierre.
- `ValidationSupport` nuevo: mensajes de error inline por campo, con el estilo de
  error del tema y el botón de guardar deshabilitado mientras haya errores.
- Componente de banner para fallos persistentes con acción Reintentar.
- Migrar los 62 puntos de llamada a `NavigationHelper.show*Alert` según la tabla
  de patrones. Los modales sobreviven **solo** en confirmaciones destructivas.

**Criterio de aceptación:** ningún error de validación abre un diálogo. Guardar un
formulario válido no exige ningún clic extra. Borrar sigue pidiendo confirmación
explícita.

---

## 6. Bloques 3 y 4 — spec propio

Se diseñarán cuando lleguemos. Alcance previsto:

**Bloque 3 — Credenciales.** Rotar la passphrase; eliminar `LEGACY_PASSPHRASE` y
el fallback silencioso (la app debe fallar de forma ruidosa y explícita si falta la
clave); migrar a AES-256-GCM con derivación PBKDF2 y sal; borrar la constante ECB
muerta; barrer el historial de git en busca de secretos; y documentar por qué un
cliente de escritorio con credenciales de administrador evade las reglas de
Firestore, con la recomendación de migrar a un backend intermediario.

**Bloque 4 — Sincronización.** Trocear los batches en lotes de 500; pull
incremental por `lastModified`; tombstones para que los borrados offline no
resuciten; introducir un framework de logging y reemplazar las 76 llamadas a
`println`/`printStackTrace`; propagar los fallos de sincronización a la interfaz
en vez de tragarlos.

---

## 7. Cómo se verifica

Yo actúo como QA. Cada PR se acompaña de tests que **ejecuto de verdad**, y el
resultado se reporta con la salida real de `./mvnw test`. Si algo falla, se dice
con el output; si algo no se pudo verificar, se dice cuál y por qué.

La cobertura actual es de 478 líneas de test contra 6.700 de código, así que no
hay red de seguridad que atrape lo que no se verifique explícitamente. Cada PR
sube cobertura en la zona que toca.

Lo que no se puede cubrir con tests automáticos —que el rail se vea bien colapsado,
que un toast no tape un botón— se verifica ejecutando la aplicación, y se dice
explícitamente que la comprobación fue manual.

---

## 8. Fuera de alcance

- **Modo oscuro.** Los tokens del PR 2.1 lo dejan viable, pero verificar 13
  pantallas en dos temas es un proyecto propio.
- **Rehacer los FXML desde cero.** Se descartó: máximo riesgo de romper
  funcionalidad a cambio de una ganancia que los PRs 2.1–2.4 ya consiguen.
- **Firma y notarización de instaladores.** Real y valioso, pero exige
  certificados de firma de código y decisiones de presupuesto.
- **Migrar a un backend intermediario.** Es la solución de fondo al problema de
  las credenciales de administrador. Se documenta en el Bloque 3 como
  recomendación, no se ejecuta.
- **Autenticación de usuarios.** La app no tiene concepto de usuario. Añadirlo
  cambiaría el modelo de datos y el de sincronización.

---

## 9. Convenciones de trabajo

- Todo cambio entra por **Issue + Pull Request** a `main`. Nunca commits directos.
- Un Issue por PR, enlazados con `Closes #N`.
- Los mensajes de commit siguen el estilo del historial existente (`UI: …`,
  `CI: …`).
- Ningún artefacto del repositorio menciona herramientas de IA: ni commits, ni
  descripciones de PR, ni comentarios de código, ni documentación.
- `security-audit-prompt.md` se usó como guía de esta auditoría y **no se
  commitea**.
