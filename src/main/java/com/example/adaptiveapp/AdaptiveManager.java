package com.example.adaptiveapp;

import android.content.Context;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;

import java.util.HashMap;
import java.util.Map;

public class AdaptiveManager implements SensorEventListener {

    // ── Estado adaptativo (clase estática pública) ──────────────────────────
    public static class AdaptiveState {
        public boolean isDarkMode;
        public boolean isHighContrast;
        public boolean isMoving;
        public boolean isIntenseMotion;
        public float   textScaleFactor;
        public float   lux;
        public float   acceleration;
        public String  adaptiveLog;
    }

    // ── Interfaz pública ─────────────────────────────────────────────────────
    public interface AdaptiveListener {
        void onLightChanged(float lux);
        void onMotionChanged(float acceleration);
        void onAdaptiveUpdate(AdaptiveState state);
    }

    // Umbrales
    private static final float LUX_DARK_THRESHOLD       = 20f;
    private static final float LUX_BRIGHT_THRESHOLD     = 5000f;
    private static final float MOTION_THRESHOLD         = 3.0f;
    private static final float INTENSE_MOTION_THRESHOLD = 8.0f;
    private static final float INTENSE_MOTION_THRESHOLD_FCM = 15.0f;

    // Firebase
    private DatabaseReference dbRef;
    private boolean firebaseInitialized = false;
    private long lastFirebasePublishTime = 0;
    private static final long FIREBASE_COOLDOWN_MS = 120_000L;

    private final SensorManager    sensorManager;
    private final Sensor           lightSensor;
    private final Sensor           accelerometer;
    private final AdaptiveListener listener;
    private final Handler          handler = new Handler(Looper.getMainLooper());

    private float currentLux   = 200f;
    private float currentAccel = 0f;
    private long  lastUpdateTime = 0;

    public AdaptiveManager(Context context, AdaptiveListener listener) {
        this.listener   = listener;
        sensorManager   = (SensorManager) context.getSystemService(Context.SENSOR_SERVICE);
        lightSensor     = sensorManager.getDefaultSensor(Sensor.TYPE_LIGHT);
        accelerometer   = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
    }

    public void inicializarFirebase(Context context) {
        try {
            dbRef = FirebaseDatabase.getInstance().getReference();
            firebaseInitialized = true;
            Log.d("AdaptiveManager", "Firebase initialized");
        } catch (Exception e) {
            Log.e("AdaptiveManager", "Firebase init failed", e);
        }
    }

    public void publicarEvento(String tipo, float valorLuz, float aceleracion) {
        if (!firebaseInitialized || dbRef == null) return;
        Map<String, Object> evento = new HashMap<>();
        evento.put("tipo", tipo);
        evento.put("valorLuz", valorLuz);
        evento.put("aceleracion", aceleracion);
        evento.put("timestamp", System.currentTimeMillis());
        evento.put("estado", calcularEstado(valorLuz, aceleracion));
        dbRef.child("eventos").push().setValue(evento)
                .addOnSuccessListener(v -> Log.d("FB", "Evento publicado: " + tipo))
                .addOnFailureListener(e -> Log.e("FB", "Error publicando", e));
    }

    private String calcularEstado(float lux, float accel) {
        if (accel > INTENSE_MOTION_THRESHOLD_FCM) return "ALERTA";
        if (lux < 10f) return "LUZ_CRITICA";
        if (lux < LUX_DARK_THRESHOLD) return "OSCURO";
        if (lux > 8000f) return "LUZ_EXCESIVA";
        if (lux > LUX_BRIGHT_THRESHOLD) return "BRILLANTE";
        return "NORMAL";
    }

    public void start() {
        if (lightSensor != null)
            sensorManager.registerListener(this, lightSensor, SensorManager.SENSOR_DELAY_NORMAL);
        if (accelerometer != null)
            sensorManager.registerListener(this, accelerometer, SensorManager.SENSOR_DELAY_NORMAL);
    }

    public void stop() {
        sensorManager.unregisterListener(this);
    }

    @Override
    public void onSensorChanged(SensorEvent event) {
        if (event.sensor.getType() == Sensor.TYPE_LIGHT) {
            currentLux = event.values[0];
            listener.onLightChanged(currentLux);

        } else if (event.sensor.getType() == Sensor.TYPE_ACCELEROMETER) {
            float x = event.values[0];
            float y = event.values[1];
            float z = event.values[2];
            float magnitude = (float) Math.sqrt(x * x + y * y + z * z);
            currentAccel = Math.abs(magnitude - SensorManager.GRAVITY_EARTH);
            listener.onMotionChanged(currentAccel);
        }

        long now = System.currentTimeMillis();
        if (now - lastUpdateTime > 500) {
            lastUpdateTime = now;
            handler.post(this::computeAdaptiveState);
        }
    }

    private void computeAdaptiveState() {
        AdaptiveState state     = new AdaptiveState();
        state.lux               = currentLux;
        state.acceleration      = currentAccel;
        state.isDarkMode        = currentLux < LUX_DARK_THRESHOLD;
        state.isHighContrast    = currentLux > LUX_BRIGHT_THRESHOLD;
        state.isMoving          = currentAccel > MOTION_THRESHOLD;
        state.isIntenseMotion   = currentAccel > INTENSE_MOTION_THRESHOLD;

        if (currentLux < LUX_DARK_THRESHOLD) {
            state.textScaleFactor = 1.6f;
        } else if (state.isMoving) {
            state.textScaleFactor = 1.4f;
        } else {
            state.textScaleFactor = 1.0f;
        }

        StringBuilder log = new StringBuilder();
        log.append("💡 Luz: ").append(String.format("%.1f", currentLux)).append(" lux\n");
        log.append("📡 Movimiento: ").append(String.format("%.2f", currentAccel)).append(" m/s²\n");
        log.append("🎨 Tema: ").append(state.isDarkMode ? "OSCURO" : "CLARO").append("\n");
        log.append("🔤 Texto: x").append(state.textScaleFactor).append("\n");
        log.append("⚡ Contraste alto: ").append(state.isHighContrast ? "SÍ" : "NO").append("\n");
        log.append("🏃 En movimiento: ").append(state.isMoving ? "SÍ" : "NO").append("\n");
        log.append("🚨 Movimiento intenso: ").append(state.isIntenseMotion ? "SÍ" : "NO");
        state.adaptiveLog = log.toString();

        listener.onAdaptiveUpdate(state);

        // Publicar en Firebase si es evento crítico con cooldown
        if (firebaseInitialized) {
            long now2 = System.currentTimeMillis();
            String tipoEvento = calcularEstado(currentLux, currentAccel);
            boolean isCritical = state.isIntenseMotion || currentLux < 10f || currentLux > 8000f;
            if (isCritical && (now2 - lastFirebasePublishTime > FIREBASE_COOLDOWN_MS)) {
                lastFirebasePublishTime = now2;
                publicarEvento(tipoEvento, currentLux, currentAccel);
            } else if (!isCritical && (now2 - lastFirebasePublishTime > 30_000L)) {
                lastFirebasePublishTime = now2;
                publicarEvento(tipoEvento, currentLux, currentAccel);
            }
        }
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) { /* no-op */ }
}
