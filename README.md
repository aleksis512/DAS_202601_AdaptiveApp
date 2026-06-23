# 📱 AdaptiveApp

> **Sistema Adaptativo Inteligente con Dashboard en Tiempo Real y Notificaciones**
> Arquitectura Orientada a Eventos · Android · Firebase · DAS 2026-01

---

## 👥 Integrantes

| Nombre |
|--------|
| Camac Alcalá, Alexis Jock |
| Palacios Palacios, Rafael Enrique |
| Varillas Barahona, Fernando Piero |

**Curso:** Diseño y Arquitectura de Software — DAS 2026-01
**Universidad:** Universidad Nacional de Ingeniería (UNI)
**Taller:** Taller 5 — Dashboard Inteligente con Monitoreo en Tiempo Real y Notificaciones

---

## 📋 Tabla de Contenidos

- [Descripción del Proyecto](#-descripción-del-proyecto)
- [Problemática](#-problemática)
- [Objetivos](#-objetivos)
- [Arquitectura del Sistema](#-arquitectura-del-sistema)
- [Patrones de Diseño](#-patrones-de-diseño)
- [Funcionalidades](#-funcionalidades)
- [Tecnologías y Herramientas](#-tecnologías-y-herramientas)
- [Estructura del Proyecto](#-estructura-del-proyecto)
- [Integración con Firebase](#-integración-con-firebase)
- [Requisitos](#-requisitos)
- [Instalación y Configuración](#-instalación-y-configuración)
- [Metodología Ágil](#-metodología-ágil)
- [Criterios de Evaluación](#-criterios-de-evaluación)

---

## 📌 Descripción del Proyecto

**AdaptiveApp** es una aplicación móvil Android desarrollada en Java que implementa un sistema de **software adaptativo** capaz de reaccionar automáticamente al contexto físico del usuario. Usando los sensores del dispositivo (luz y acelerómetro), la app ajusta su interfaz en tiempo real, registra los eventos en Firebase Realtime Database y envía notificaciones push a través de Firebase Cloud Messaging (FCM) ante situaciones críticas.

El proyecto demuestra la implementación práctica de una **Arquitectura Orientada a Eventos (EDA)** combinada con una arquitectura en 4 capas, patrones de diseño clásicos y consumo de servicios en tiempo real.

---

## 🚨 Problemática

Las aplicaciones móviles tradicionales presentan interfaces **estáticas** que ignoran el contexto físico del usuario, generando:

| Problema | Descripción |
|----------|-------------|
| 👁️ **Fatiga Visual** | Interfaces brillantes en ambientes oscuros dañan la vista y reducen la legibilidad |
| 🚨 **Riesgo en Movimiento** | Interactuar con interfaces complejas mientras el usuario se mueve genera distracciones peligrosas |
| ♿ **Baja Accesibilidad** | Sin ajuste dinámico de contraste y texto, personas con dificultades visuales quedan excluidas |
| 🔕 **Sin Alertas Críticas** | El sistema no notifica al usuario sobre eventos relevantes detectados por sensores |

---

## 🎯 Objetivos

### Objetivo General
Desarrollar un módulo complementario dinámico que responda al contexto real del usuario en entornos dinámicos y cambiantes, incorporando notificaciones automáticas ante eventos críticos detectados por sensores del dispositivo.

### Objetivos Específicos

1. Integrar sensores (luz y acelerómetro) con un sistema de eventos reactivo en Android
2. Implementar un dashboard con gráficas en tiempo real usando MPAndroidChart
3. Configurar Firebase Realtime Database como fuente de eventos centralizados
4. Enviar notificaciones push vía Firebase Cloud Messaging (FCM) ante eventos críticos
5. Documentar la arquitectura orientada a eventos aplicada al proyecto

---

## 🏛️ Arquitectura del Sistema

### Estilo Arquitectónico Principal: Event-Driven Architecture (EDA)

AdaptiveApp adopta **EDA** como estilo primario porque los sensores generan eventos continuos e impredecibles que deben consumirse de forma desacoplada, sin polling activo.

```
[Sensor Manager] ──sensorEvent──▶ [AdaptiveManager] ──publish()──▶ [EventBus]
                                          │                              │
                                     classify()                    notify()  trigger()
                                          │                         │            │
                                   [AdaptiveState]           [MainActivity]  [FCM Push]
                                                                   │
                                                            [FirebaseRepo] ──write/read──▶ [Firebase DB]
```

### Arquitectura en 4 Capas

```
┌─────────────────────────────────────────────────────────────┐
│  CAPA 4 — PRESENTACIÓN (UI)                                 │
│  MainActivity · CardView · MPAndroidChart · NotificationUI  │
├─────────────────────────────────────────────────────────────┤
│  CAPA 3 — DOMINIO / LÓGICA DE NEGOCIO                       │
│  AdaptiveManager · EventClassifier · AdaptiveState          │
│  EventBus                                                    │
├─────────────────────────────────────────────────────────────┤
│  CAPA 2 — DATOS / REPOSITORIO                               │
│  SensorRepository · FirebaseRepository · LocalLogRepository │
│  SharedPreferences                                           │
├─────────────────────────────────────────────────────────────┤
│  CAPA 1 — INFRAESTRUCTURA / SERVICIOS EXTERNOS              │
│  SensorManager (SDK) · Firebase DB · Firebase FCM           │
│  NotificationManager                                         │
└─────────────────────────────────────────────────────────────┘
```

### Comparativa EDA vs Polling Tradicional

| Criterio | Polling Tradicional | Event-Driven (EDA) |
|----------|--------------------|--------------------|
| Consumo de batería | Alto (consulta continua) | Bajo (respuesta a eventos) |
| Latencia de respuesta | Depende del intervalo | Inmediata al ocurrir el evento |
| Acoplamiento | Alto | Bajo (productores/consumidores independientes) |
| Escalabilidad | Difícil | Fácil (nuevos eventos sin cambiar consumidores) |
| Integración Firebase | Requiere sincronización manual | Natural: Firebase es un bus de eventos |

---

## 🎨 Patrones de Diseño

### Observer
**Dónde:** `AdaptiveManager` → `MainActivity`
**Por qué:** Permite que la UI se actualice automáticamente cuando cambia el estado adaptativo, sin acoplamiento directo entre clases.

```java
// Interface
public interface AdaptiveListener {
    void onStateChanged(AdaptiveState state);
    void onCriticalEvent(AdaptiveEvent event);
}

// MainActivity implementa el listener
public class MainActivity extends AppCompatActivity
    implements AdaptiveListener {

    @Override
    public void onStateChanged(AdaptiveState state) {
        runOnUiThread(() -> updateDashboard(state));
    }
}
```

### Repository
**Dónde:** `FirebaseRepository`, `SensorRepository`
**Por qué:** Abstrae las fuentes de datos del resto del sistema, permitiendo cambiar la implementación sin afectar la lógica de dominio.

### Singleton
**Dónde:** `AdaptiveManager`
**Por qué:** Garantiza una única instancia del manager de eventos en toda la aplicación, evitando inconsistencias de estado.

```java
public static synchronized AdaptiveManager getInstance(Context context) {
    if (instance == null) {
        instance = new AdaptiveManager(context);
    }
    return instance;
}
```

### Strategy
**Dónde:** `IAdaptiveStrategy` → `DarkModeStrategy`, `HighContrastStrategy`, `SimplifiedModeStrategy`
**Por qué:** Permite intercambiar el algoritmo de adaptación en tiempo de ejecución según el evento clasificado.

---

## ✨ Funcionalidades

### Adaptaciones de Interfaz

| Evento Detectado | Condición | Acción |
|-----------------|-----------|--------|
| `LUZ_BAJA` | luz < 50 lux | Activa modo oscuro |
| `LUZ_ALTA` | luz > 5000 lux | Activa alto contraste |
| `MOV_NORMAL` | 3–15 m/s² | Activa modo simplificado |
| `MOV_INTENSO` | > 15 m/s² | Muestra alerta de seguridad + envía FCM |
| `REPOSO` | < 3 m/s² | Restaura interfaz normal |

### Dashboard en Tiempo Real
- Cards con métricas: nivel de luz (lux), aceleración (m/s²), modo UI activo, alertas del día
- Gráfico de barras con historial de nivel de luz (últimas 24h) usando **MPAndroidChart**
- Log cronológico de eventos adaptativos con timestamp
- Indicador de conectividad con Firebase (en línea / sin conexión)

### Sistema de Notificaciones
- **Notificaciones push (FCM):** enviadas ante eventos críticos, llegan aunque la app esté en segundo plano
- **Notificaciones locales (NotificationManager):** funcionan sin conexión a internet
- **Cooldown de 120 segundos** por tipo de evento para evitar spam de notificaciones

---

## 🛠️ Tecnologías y Herramientas

| Componente | Tecnología | Versión |
|------------|-----------|---------|
| Lenguaje | Java | 11 |
| IDE | Android Studio | Ladybug+ |
| SDK mínimo | Android 8.0 (API 26) | — |
| Sensores | SensorManager (Android SDK) | — |
| Base de datos cloud | Firebase Realtime Database | BOM 32.7.0 |
| Notificaciones push | Firebase Cloud Messaging (FCM) | BOM 32.7.0 |
| Autenticación | Firebase Auth (anónima) | BOM 32.7.0 |
| Gráficos | MPAndroidChart | 3.1.0 |
| UI Components | CardView, AppCompat | AndroidX |
| Persistencia local | SharedPreferences | — |

---

## 📁 Estructura del Proyecto

```
DAS_202601_AdaptiveApp/
├── app/
│   ├── src/
│   │   └── main/
│   │       ├── java/com/example/adaptiveapp/
│   │       │   ├── domain/
│   │       │   │   ├── AdaptiveManager.java       # Core — Singleton/EDA
│   │       │   │   ├── AdaptiveState.java         # Value Object
│   │       │   │   ├── AdaptiveEvent.java         # Evento del sistema
│   │       │   │   ├── EventClassifier.java       # Strategy — clasifica eventos
│   │       │   │   └── EventBus.java              # Dispatcher interno
│   │       │   ├── ui/
│   │       │   │   └── MainActivity.java          # Observer — UI principal + Dashboard
│   │       │   ├── data/
│   │       │   │   ├── FirebaseRepository.java    # Repository — Firebase DB
│   │       │   │   ├── SensorRepository.java      # Repository — Sensores
│   │       │   │   └── LocalLogRepository.java    # Repository — Log local
│   │       │   ├── listeners/
│   │       │   │   └── AdaptiveListener.java      # Interface Observer
│   │       │   ├── strategies/
│   │       │   │   ├── IAdaptiveStrategy.java     # Interface Strategy
│   │       │   │   ├── DarkModeStrategy.java
│   │       │   │   ├── HighContrastStrategy.java
│   │       │   │   └── SimplifiedModeStrategy.java
│   │       │   └── notifications/
│   │       │       └── MyFirebaseMessagingService.java  # FCM Service
│   │       ├── res/
│   │       │   ├── layout/
│   │       │   │   └── activity_main.xml
│   │       │   └── values/
│   │       │       ├── strings.xml
│   │       │       └── colors.xml
│   │       └── AndroidManifest.xml
│   └── build.gradle
├── build.gradle
├── google-services.json        # ⚠️ NO incluido (ver .gitignore)
├── .gitignore
└── README.md
```

---

## 🔥 Integración con Firebase

### Configuración de Dependencias (`build.gradle`)

```gradle
dependencies {
    // Firebase BOM — gestiona versiones automáticamente
    implementation platform('com.google.firebase:firebase-bom:32.7.0')

    implementation 'com.google.firebase:firebase-database'   // Realtime DB
    implementation 'com.google.firebase:firebase-messaging'  // FCM
    implementation 'com.google.firebase:firebase-auth'       // Auth anónima
}
apply plugin: 'com.google.gms.google-services'
```

### Estructura JSON en Firebase

```json
{
  "adaptiveapp-db": {
    "eventos": {
      "-NxKj1a...": {
        "tipo": "MOV_INTENSO",
        "valorLuz": 42.5,
        "aceleracion": 17.3,
        "estado": "ALERTA",
        "timestamp": 1717000000
      }
    },
    "notificaciones": {
      "-NxKj3c...": {
        "tipo": "MOV_INTENSO",
        "enviada": true,
        "timestamp": 1717000000
      }
    },
    "estado_actual": {
      "modo": "OSCURO",
      "conectado": true,
      "ultimaActualizacion": 1717000200
    }
  }
}
```

### Reglas de Seguridad (`database.rules.json`)

```json
{
  "rules": {
    "adaptiveapp-db": {
      "eventos": {
        ".read": "auth != null",
        ".write": "auth != null",
        "$eventoId": {
          ".validate": "newData.hasChildren(['tipo','timestamp','valorLuz','aceleracion'])"
        }
      },
      "notificaciones": {
        ".read": "auth != null",
        ".write": "auth != null"
      },
      "estado_actual": {
        ".read": true,
        ".write": "auth != null"
      }
    }
  }
}
```

### Publicar Evento en Firebase

```java
public void publicarEvento(String tipo, float valorLuz, float aceleracion) {
    Map<String, Object> evento = new HashMap<>();
    evento.put("tipo", tipo);
    evento.put("valorLuz", valorLuz);
    evento.put("aceleracion", aceleracion);
    evento.put("timestamp", System.currentTimeMillis());
    evento.put("estado", calcularEstado(valorLuz, aceleracion));

    dbRef.child("eventos").push().setValue(evento)
        .addOnSuccessListener(v -> Log.d("FB", "✅ Publicado"))
        .addOnFailureListener(e -> Log.e("FB", "❌ Error", e));
}
```

### Escuchar Cambios en Tiempo Real

```java
dbRef.child("eventos").limitToLast(20)
    .addChildEventListener(new ChildEventListener() {
        @Override
        public void onChildAdded(DataSnapshot snapshot, String prevKey) {
            String tipo = snapshot.child("tipo").getValue(String.class);
            float luz  = snapshot.child("valorLuz").getValue(Float.class);
            runOnUiThread(() -> actualizarDashboard(tipo, luz));
        }
        @Override
        public void onCancelled(DatabaseError error) {
            Log.e("FB", "Error: ", error.toException());
        }
    });
```

---

## ⚙️ Requisitos

### Funcionales

| ID | Requisito |
|----|-----------|
| RF01 | Detectar nivel de iluminación mediante el sensor de luz |
| RF02 | Detectar movimiento con el acelerómetro del dispositivo |
| RF03 | Aplicar modo oscuro cuando la luz sea < 50 lux |
| RF04 | Aplicar alto contraste cuando la luz sea > 5000 lux |
| RF05 | Mostrar alerta visual cuando el movimiento sea intenso (> 15 m/s²) |
| RF06 | Publicar eventos en Firebase Realtime Database |
| RF07 | Enviar notificación push mediante FCM ante evento crítico |
| RF08 | Dashboard con gráfico de historial de eventos del sensor |

### No Funcionales

| Atributo | Métrica |
|----------|---------|
| Rendimiento | Respuesta a eventos < 300 ms desde la lectura del sensor |
| Disponibilidad | Firebase garantiza 99.95% uptime |
| Seguridad | Reglas de Firebase Database para lectura/escritura autenticada |
| Escalabilidad | Arquitectura modular permite agregar nuevos sensores sin cambiar consumidores |
| Usabilidad | Cambios de interfaz perceptibles en < 1 segundo |
| Portabilidad | Compatible con Android 8.0 (API 26) en adelante |

---

## 🚀 Instalación y Configuración

### Prerrequisitos
- Android Studio Ladybug o superior
- JDK 11+
- Cuenta de Firebase (gratuita)
- Dispositivo Android con API 26+ o emulador

### Paso 1 — Clonar el repositorio

```bash
git clone https://github.com/aleksis512/DAS_202601_AdaptiveApp.git
cd DAS_202601_AdaptiveApp
```

### Paso 2 — Configurar Firebase

1. Ir a [Firebase Console](https://console.firebase.google.com/)
2. Crear un proyecto nuevo → **AdaptiveApp**
3. Agregar una app Android con el package name: `com.example.adaptiveapp`
4. Descargar el archivo `google-services.json`
5. Colocarlo en `app/google-services.json`
6. Activar **Realtime Database** en modo de prueba
7. Activar **Authentication** → método anónimo
8. Activar **Cloud Messaging** en la consola

### Paso 3 — Abrir en Android Studio

```
File → Open → seleccionar la carpeta del proyecto
```

Esperar a que Gradle sincronice las dependencias.

### Paso 4 — Ejecutar

Conectar un dispositivo físico (recomendado para sensores reales) o usar emulador y presionar ▶ **Run**.

> ⚠️ Los sensores de luz y acelerómetro pueden no funcionar correctamente en emuladores. Se recomienda usar un dispositivo físico para la demo completa.

---

## 📐 Metodología Ágil — Scrum

El proyecto se desarrolló bajo el framework **Scrum** con sprints de 1 semana.

### Plan de Sprints

| Sprint | Tema | Objetivo | Story Points |
|--------|------|----------|-------------|
| Sprint 1 | Sensores + Adaptación Core | App funcional con detección de sensores y adaptación de UI | 21 SP |
| Sprint 2 | Firebase Realtime Database | Persistencia de eventos en la nube | 18 SP |
| Sprint 3 | Dashboard + Notificaciones | Gráficas en tiempo real y push FCM | 20 SP |
| Sprint 4 | Pulido + Documentación | Sistema estable, README y demo | 16 SP |

**Total: 75 Story Points**

### Épicas del Proyecto

| ID | Épica | Historias | SP |
|----|-------|-----------|----|
| EP-01 | Sensores & Adaptación Core | HU-01 al HU-04 | 21 |
| EP-02 | Integración Firebase Realtime DB | HU-05 al HU-08 | 18 |
| EP-03 | Dashboard & Visualización | HU-09 al HU-12 | 20 |
| EP-04 | Notificaciones & Alertas | HU-13 al HU-16 | 16 |

### Historias de Usuario Principales

```
HU-01 (8 SP) — Como usuario de la app, quiero que la aplicación detecte
               automáticamente el nivel de iluminación, para que la interfaz
               se ajuste sin que yo tenga que hacerlo manualmente.

HU-06 (5 SP) — Como usuario de la app, quiero que cada cambio de estado del
               sensor se registre automáticamente en Firebase, para tener un
               historial accesible desde cualquier dispositivo.

HU-09 (8 SP) — Como usuario de la app, quiero ver un panel con los valores
               actuales de luz, aceleración y modo UI, para tener una visión
               completa del sistema de un vistazo.

HU-13 (5 SP) — Como usuario en situación de riesgo, quiero recibir una
               notificación push al detectarse movimiento intenso, para ser
               alertado aunque la app esté en segundo plano.
```

---

## 📊 Criterios de Evaluación

| Criterio | Puntos |
|----------|--------|
| Implementación de la arquitectura orientada a eventos | 6 pts |
| Correcto funcionamiento en tiempo real y notificación | 6 pts |
| Documentación técnica | 4 pts |
| Presentación clara y defensa técnica | 4 pts |
| **Total** | **20 pts** |

---

## 🔒 Seguridad

- El archivo `google-services.json` está incluido en `.gitignore` y **no debe subirse al repositorio**
- Las reglas de Firebase Database requieren autenticación para leer y escribir
- El token FCM se almacena localmente y no se expone en logs de producción
- Se usa autenticación anónima de Firebase para identificar sesiones sin datos personales

---

## 📄 .gitignore recomendado

```gitignore
# Firebase — NUNCA subir al repo
google-services.json

# Android
*.iml
.gradle/
local.properties
.idea/
build/
*.apk
*.aab
captures/
.externalNativeBuild/
.cxx/
```

---

## 📚 Referencias

- [Firebase Android Documentation](https://firebase.google.com/docs/android/setup)
- [MPAndroidChart](https://github.com/PhilJay/MPAndroidChart)
- [Android SensorManager](https://developer.android.com/reference/android/hardware/SensorManager)
- [Firebase Cloud Messaging](https://firebase.google.com/docs/cloud-messaging/android/client)
- [Event-Driven Architecture — Martin Fowler](https://martinfowler.com/articles/201701-event-driven.html)

---

<div align="center">

**AdaptiveApp** · Universidad Nacional de Ingeniería · DAS 2026-01

Made with ❤️ by Camac · Palacios · Varillas

</div>
