# PolarReminder — app Android con widget

Recordatorios recurrentes en forma de anillo. Cada tarea es un círculo que se
va vaciando con el tiempo; cuando llega a cero, toca hacerla. Un toque en el
anillo (en la app **o directamente en el widget**) la marca como hecha y
reinicia el ciclo.

Gratis, sin anuncios, sin suscripción, sin límite de recordatorios, sin cuenta
y sin conexión a internet. Los datos se guardan solo en tu teléfono.

- **Kotlin + Jetpack Compose** para la app
- **Jetpack Glance** para el widget de pantalla de inicio (redimensionable)
- `minSdk 26` (Android 8.0 o superior)

---

## Opción A — compilar con un PC (Android Studio)

1. Instala [Android Studio](https://developer.android.com/studio) (gratis).
2. `File → Open…` y elige esta carpeta (`RitmoApp`). Espera al *Gradle sync*;
   la primera vez descarga bastante, ten paciencia.
   - Este proyecto no incluye el binario `gradle-wrapper.jar`. Android Studio
     no lo necesita: descarga Gradle 8.7 él solo leyendo
     `gradle/wrapper/gradle-wrapper.properties`. Si algún día quieres usar
     `./gradlew` desde la terminal, genera el wrapper con `gradle wrapper`.
   - Si te avisa de que falta algún SDK, acepta y deja que lo instale.
3. En el teléfono: `Ajustes → Acerca del teléfono → toca 7 veces "Número de
   compilación"` para activar Opciones de desarrollador, y dentro activa
   **Depuración por USB**.
4. Conecta el teléfono por USB, elígelo en la barra superior de Android Studio
   y pulsa ▶ **Run**. La app se instala sola.

## Opción B — compilar sin PC (GitHub Actions, gratis)

1. Crea una cuenta en [github.com](https://github.com) y un repositorio nuevo.
2. Sube esta carpeta al repositorio (desde el móvil puedes usar la opción
   "uploading an existing file" de la web de GitHub, o la app de GitHub).
3. Ve a la pestaña **Actions**. El flujo *Compilar APK de PolarReminder* arranca solo;
   si no, ábrelo y pulsa **Run workflow**.
4. Cuando termine (unos 5 minutos), entra en la ejecución y descarga el
   artefacto **ritmo-debug-apk**. Dentro está `app-debug.apk`.
5. Abre el APK en el teléfono y permite "Instalar apps desconocidas" cuando te
   lo pida. Listo.

> El APK de depuración está firmado con la clave de depuración, sirve
> perfectamente para uso personal. Solo necesitarías firmarlo con una clave
> propia si quisieras publicarlo en Google Play.

---

## Notificaciones con sonido

Cada recordatorio programa una alarma para el momento exacto en que empieza a
tocar; cuando llega, salta una notificación con sonido (el mismo sonido de
notificación de tu teléfono) y un botón para marcarlo como hecho sin abrir la
app. Funciona aunque la app esté cerrada.

La primera vez que abras la app, Android te pedirá permiso para mostrar
notificaciones (Android 13 o superior) — acéptalo. Si además ves un aviso
dentro de la app sobre "alarmas exactas", tócalo: te llevará a un ajuste del
sistema para permitir que las notificaciones lleguen justo a la hora, y no con
minutos de retraso. Este permiso es obligatorio a partir de Android 12.

Algunos fabricantes (Xiaomi, Huawei, Oppo, algunos Samsung...) matan las apps
en segundo plano de forma agresiva y pueden retrasar los avisos. Si notas que
llegan tarde, busca en Ajustes → Batería → PolarReminder y desactiva la
optimización de batería para esta app, o actívala como "sin restricciones".

## Cómo poner el widget

1. Abre PolarReminder y crea al menos un recordatorio.
2. Mantén pulsado un hueco vacío de la pantalla de inicio → **Widgets**.
3. Busca **PolarReminder · anillos** y arrástralo. Se puede redimensionar: cuanto más
   grande, más anillos muestra (se ordenan por urgencia, primero lo que ya toca).
4. Tocar un anillo lo marca como hecho sin abrir la app. Tocar el fondo del
   widget abre la app.

El widget se refresca solo cada 30 minutos (es el mínimo que permite Android
para no gastar batería) y también cada vez que cierras la app o completas algo.

## Traer tus datos desde la versión web

En la web: **Ajustes → Exportar** (copia el JSON).
En la app: **⋮ → Importar copia** y pégalo. El formato es el mismo en ambas.

## Estructura del código

```
app/src/main/java/com/ritmo/app/
├── Store.kt            Modelo Reminder + guardado en SharedPreferences
├── TimeText.kt         Textos de tiempo en castellano
├── WidgetSync.kt       Refresca los widgets al cambiar los datos
├── MainActivity.kt     Toda la interfaz de la app (Compose)
└── widget/
    ├── RitmoWidget.kt  Widget Glance + acción de "completar"
    └── RingBitmap.kt   Dibuja el anillo como bitmap (los widgets no admiten Canvas)
```

## Ideas para seguir

- Notificaciones cuando un recordatorio vence (`AlarmManager` + `POST_NOTIFICATIONS`).
- Historial y rachas por recordatorio.
- Carpetas o etiquetas.
- Sincronización entre dispositivos.
