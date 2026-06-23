package com.example.adaptiveapp;

import android.animation.ValueAnimator;
import android.graphics.Color;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.view.animation.DecelerateInterpolator;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.cardview.widget.CardView;

import com.example.adaptiveapp.AdaptiveManager.AdaptiveState;
import com.github.mikephil.charting.charts.BarChart;
import com.github.mikephil.charting.components.XAxis;
import com.github.mikephil.charting.data.BarData;
import com.github.mikephil.charting.data.BarDataSet;
import com.github.mikephil.charting.data.BarEntry;
import com.github.mikephil.charting.formatter.IndexAxisValueFormatter;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.database.ChildEventListener;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.Query;

import java.util.ArrayList;
import java.util.List;

public class MainActivity extends AppCompatActivity
        implements AdaptiveManager.AdaptiveListener {

    private static final String TAG = "MainActivity";

    private AdaptiveManager adaptiveManager;

    private LinearLayout rootLayout;
    private CardView     statusCard, infoCard, logCard, dashboardCard;
    private TextView     tvTitle, tvThemeStatus, tvTextSize;
    private TextView     tvMotionStatus, tvContrastStatus;
    private TextView     tvLuxValue, tvAccelValue;
    private TextView     tvLog, tvWarning, tvFrequency, tvSimpleMode;
    private TextView     tvDashboardTitle, tvEventCount;
    private BarChart     barChart;

    private boolean lastDarkMode     = false;
    private boolean lastHighContrast = false;
    private float   lastTextScale    = 1.0f;

    // Firebase
    private DatabaseReference dbRef;
    private ChildEventListener eventosListener;
    private final List<Float> accelValues = new ArrayList<>();
    private final List<String> eventLabels = new ArrayList<>();
    private int totalEventos = 0;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        buildUIProgrammatically();
        adaptiveManager = new AdaptiveManager(this, this);
        adaptiveManager.inicializarFirebase(this);
        inicializarFirebaseAuth();
    }

    private void inicializarFirebaseAuth() {
        FirebaseAuth auth = FirebaseAuth.getInstance();
        if (auth.getCurrentUser() == null) {
            auth.signInAnonymously()
                    .addOnSuccessListener(result -> {
                        Log.d(TAG, "Autenticacion anonima exitosa");
                        dbRef = FirebaseDatabase.getInstance().getReference();
                        escucharEstado();
                    })
                    .addOnFailureListener(e -> {
                        Log.e(TAG, "Error de autenticacion", e);
                        dbRef = FirebaseDatabase.getInstance().getReference();
                        escucharEstado();
                    });
        } else {
            dbRef = FirebaseDatabase.getInstance().getReference();
            escucharEstado();
        }
    }

    private void escucharEstado() {
        if (dbRef == null) return;
        Query query = dbRef.child("eventos").orderByChild("timestamp").limitToLast(20);
        eventosListener = new ChildEventListener() {
            @Override
            public void onChildAdded(DataSnapshot snapshot, String previousChildName) {
                procesarEvento(snapshot);
            }

            @Override
            public void onChildChanged(DataSnapshot snapshot, String previousChildName) {
                procesarEvento(snapshot);
            }

            @Override
            public void onChildRemoved(DataSnapshot snapshot) { /* no-op */ }

            @Override
            public void onChildMoved(DataSnapshot snapshot, String previousChildName) { /* no-op */ }

            @Override
            public void onCancelled(DatabaseError error) {
                Log.e(TAG, "Error escuchando eventos: " + error.getMessage());
            }
        };
        query.addChildEventListener(eventosListener);
    }

    private void procesarEvento(DataSnapshot snapshot) {
        try {
            Float accel = snapshot.child("aceleracion").getValue(Float.class);
            String estado = snapshot.child("estado").getValue(String.class);
            if (accel == null) accel = 0f;
            if (estado == null) estado = "?";

            totalEventos++;
            accelValues.add(accel);
            eventLabels.add(estado.length() > 6 ? estado.substring(0, 6) : estado);

            // Mantener solo los ultimos 10
            if (accelValues.size() > 10) {
                accelValues.remove(0);
                eventLabels.remove(0);
            }

            runOnUiThread(this::actualizarDashboard);
        } catch (Exception e) {
            Log.e(TAG, "Error procesando evento", e);
        }
    }

    private void actualizarDashboard() {
        tvEventCount.setText("Total eventos: " + totalEventos);

        List<BarEntry> entries = new ArrayList<>();
        for (int i = 0; i < accelValues.size(); i++) {
            entries.add(new BarEntry(i, accelValues.get(i)));
        }

        BarDataSet dataSet = new BarDataSet(entries, "Aceleracion (m/s²)");
        dataSet.setColor(Color.parseColor("#3B4FCD"));
        dataSet.setValueTextColor(Color.parseColor("#333333"));
        dataSet.setValueTextSize(10f);

        BarData barData = new BarData(dataSet);
        barData.setBarWidth(0.7f);
        barChart.setData(barData);

        String[] labels = eventLabels.toArray(new String[0]);
        barChart.getXAxis().setValueFormatter(new IndexAxisValueFormatter(labels));
        barChart.invalidate();
    }

    @Override
    protected void onResume() {
        super.onResume();
        adaptiveManager.start();
    }

    @Override
    protected void onPause() {
        super.onPause();
        adaptiveManager.stop();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (dbRef != null && eventosListener != null) {
            dbRef.child("eventos").removeEventListener(eventosListener);
        }
    }

    // ── Implementacion de AdaptiveListener ────────────────────────────────────

    @Override
    public void onLightChanged(float lux) {
        runOnUiThread(() ->
                tvLuxValue.setText(String.format("💡 Luz ambiente: %.1f lux", lux)));
    }

    @Override
    public void onMotionChanged(float acceleration) {
        runOnUiThread(() ->
                tvAccelValue.setText(String.format("📡 Aceleración: %.2f m/s²", acceleration)));
    }

    @Override
    public void onAdaptiveUpdate(AdaptiveState state) {
        runOnUiThread(() -> applyAdaptations(state));
    }

    // ── Motor de adaptacion ───────────────────────────────────────────────────

    private void applyAdaptations(AdaptiveState state) {

        // PUNTO 1 — Tema oscuro/claro
        if (state.isDarkMode != lastDarkMode) {
            lastDarkMode = state.isDarkMode;
            applyTheme(state.isDarkMode);
        }

        // PUNTO 2 — Tamaño de texto
        if (state.textScaleFactor != lastTextScale) {
            lastTextScale = state.textScaleFactor;
            animateTextSize(state.textScaleFactor);
        }

        // PUNTO 3 — Estado
        tvThemeStatus.setText("🎨 Tema: " + (state.isDarkMode ? "OSCURO 🌙" : "CLARO ☀️"));
        tvTextSize.setText("🔤 Texto: ×" + state.textScaleFactor);

        // PUNTO 4 — Alerta de movimiento intenso
        if (state.isIntenseMotion) {
            tvWarning.setVisibility(View.VISIBLE);
            tvWarning.setText("⚠️ ¡Estás en movimiento! Detente para usar el dispositivo de forma segura.");
        } else {
            tvWarning.setVisibility(View.GONE);
        }

        // PUNTO 5 — Alto contraste
        if (state.isHighContrast != lastHighContrast) {
            lastHighContrast = state.isHighContrast;
            applyHighContrast(state.isHighContrast, state.isDarkMode);
        }
        tvContrastStatus.setText("⚡ Alto contraste: " + (state.isHighContrast ? "ACTIVO" : "inactivo"));

        // PUNTO 6 — Frecuencia de actualización
        tvFrequency.setText("🔄 Frecuencia UI: " + (state.isMoving ? "Alta (300ms)" : "Normal (1000ms)"));

        // PUNTO 7 — Layout simplificado
        if (state.isIntenseMotion) {
            infoCard.setVisibility(View.GONE);
            logCard.setVisibility(View.GONE);
            tvSimpleMode.setVisibility(View.VISIBLE);
            tvSimpleMode.setText("📱 MODO SIMPLIFICADO\n(movimiento intenso detectado)");
        } else {
            infoCard.setVisibility(View.VISIBLE);
            logCard.setVisibility(View.VISIBLE);
            tvSimpleMode.setVisibility(View.GONE);
        }

        // PUNTO 8 — Log en tiempo real
        tvLog.setText(state.adaptiveLog);
        tvMotionStatus.setText("🏃 Movimiento: " +
                (state.isMoving ? (state.isIntenseMotion ? "INTENSO 🚨" : "ACTIVO ✅") : "estático ⬛"));
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private void applyTheme(boolean darkMode) {
        if (darkMode) {
            AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES);
            rootLayout.setBackgroundColor(Color.parseColor("#1A1A2E"));
            statusCard.setCardBackgroundColor(Color.parseColor("#16213E"));
            infoCard.setCardBackgroundColor(Color.parseColor("#0F3460"));
            logCard.setCardBackgroundColor(Color.parseColor("#16213E"));
            dashboardCard.setCardBackgroundColor(Color.parseColor("#16213E"));
            tvTitle.setTextColor(Color.parseColor("#E94560"));
        } else {
            AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO);
            rootLayout.setBackgroundColor(Color.parseColor("#F0F4FF"));
            statusCard.setCardBackgroundColor(Color.WHITE);
            infoCard.setCardBackgroundColor(Color.parseColor("#EEF2FF"));
            logCard.setCardBackgroundColor(Color.WHITE);
            dashboardCard.setCardBackgroundColor(Color.WHITE);
            tvTitle.setTextColor(Color.parseColor("#3B4FCD"));
        }
    }

    private void applyHighContrast(boolean active, boolean darkMode) {
        if (active) {
            rootLayout.setBackgroundColor(Color.BLACK);
            tvTitle.setTextColor(Color.YELLOW);
            Toast.makeText(this, "⚡ Alto contraste activado", Toast.LENGTH_SHORT).show();
        } else {
            applyTheme(darkMode);
        }
    }

    private void animateTextSize(float scaleFactor) {
        float currentSp = tvThemeStatus.getTextSize() / getResources().getDisplayMetrics().scaledDensity;
        float targetSp  = 16f * scaleFactor;
        ValueAnimator animator = ValueAnimator.ofFloat(currentSp, targetSp);
        animator.setDuration(400);
        animator.setInterpolator(new DecelerateInterpolator());
        animator.addUpdateListener(anim -> {
            float val = (float) anim.getAnimatedValue();
            tvTitle.setTextSize(val * 1.5f);
            tvThemeStatus.setTextSize(val);
            tvTextSize.setTextSize(val);
            tvMotionStatus.setTextSize(val);
            tvContrastStatus.setTextSize(val);
            tvFrequency.setTextSize(val);
        });
        animator.start();
    }

    // ── UI programatica ───────────────────────────────────────────────────────

    private void buildUIProgrammatically() {
        int pad = dp(16);

        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);

        rootLayout = new LinearLayout(this);
        rootLayout.setOrientation(LinearLayout.VERTICAL);
        rootLayout.setPadding(pad, pad, pad, pad);
        rootLayout.setBackgroundColor(Color.parseColor("#F0F4FF"));

        tvTitle = new TextView(this);
        tvTitle.setText("🧠 Sistema Adaptativo");
        tvTitle.setTextSize(28f);
        tvTitle.setTextColor(Color.parseColor("#3B4FCD"));
        tvTitle.setPadding(0, 0, 0, dp(4));

        TextView tvSubtitle = new TextView(this);
        tvSubtitle.setText("Adaptación automática por sensores");
        tvSubtitle.setTextSize(13f);
        tvSubtitle.setTextColor(Color.parseColor("#888888"));
        tvSubtitle.setPadding(0, 0, 0, dp(16));

        // Warning banner
        tvWarning = new TextView(this);
        tvWarning.setTextColor(Color.WHITE);
        tvWarning.setBackgroundColor(Color.parseColor("#E53935"));
        tvWarning.setPadding(pad, dp(12), pad, dp(12));
        tvWarning.setTextSize(15f);
        tvWarning.setVisibility(View.GONE);

        // Modo simplificado banner
        tvSimpleMode = new TextView(this);
        tvSimpleMode.setTextColor(Color.WHITE);
        tvSimpleMode.setBackgroundColor(Color.parseColor("#FF6F00"));
        tvSimpleMode.setPadding(pad, dp(20), pad, dp(20));
        tvSimpleMode.setTextSize(22f);
        tvSimpleMode.setVisibility(View.GONE);

        // Card estado
        statusCard = makeCard();
        LinearLayout statusInner = cardInner(statusCard);
        tvThemeStatus    = makeLabel(statusInner, "🎨 Tema: —");
        tvTextSize       = makeLabel(statusInner, "🔤 Texto: —");
        tvMotionStatus   = makeLabel(statusInner, "🏃 Movimiento: —");
        tvContrastStatus = makeLabel(statusInner, "⚡ Alto contraste: —");
        tvFrequency      = makeLabel(statusInner, "🔄 Frecuencia UI: —");

        // Card sensores
        infoCard = makeCard();
        LinearLayout infoInner = cardInner(infoCard);
        TextView tvSensorTitle = makeLabel(infoInner, "📊 Valores de Sensores");
        tvSensorTitle.setTextColor(Color.parseColor("#3B4FCD"));
        tvLuxValue   = makeLabel(infoInner, "💡 Luz ambiente: —");
        tvAccelValue = makeLabel(infoInner, "📡 Aceleración: —");

        // Card log
        logCard = makeCard();
        LinearLayout logInner = cardInner(logCard);
        TextView tvLogTitle = makeLabel(logInner, "📋 Log Adaptativo");
        tvLogTitle.setTextColor(Color.parseColor("#3B4FCD"));
        tvLog = new TextView(this);
        tvLog.setTextSize(13f);
        tvLog.setTextColor(Color.parseColor("#444444"));
        tvLog.setTypeface(android.graphics.Typeface.MONOSPACE);
        tvLog.setText("Iniciando sensores...");
        logInner.addView(tvLog);

        // Card dashboard Firebase
        dashboardCard = makeCard();
        LinearLayout dashInner = cardInner(dashboardCard);
        tvDashboardTitle = makeLabel(dashInner, "☁️ Dashboard Firebase");
        tvDashboardTitle.setTextColor(Color.parseColor("#3B4FCD"));
        tvEventCount = makeLabel(dashInner, "Total eventos: 0");
        tvEventCount.setTextSize(13f);
        tvEventCount.setTextColor(Color.parseColor("#666666"));

        barChart = new BarChart(this);
        LinearLayout.LayoutParams chartParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(200));
        barChart.setLayoutParams(chartParams);
        barChart.getDescription().setEnabled(false);
        barChart.setDrawGridBackground(false);
        barChart.setNoDataText("Esperando datos de Firebase...");
        barChart.setNoDataTextColor(Color.parseColor("#888888"));
        barChart.getXAxis().setPosition(XAxis.XAxisPosition.BOTTOM);
        barChart.getXAxis().setGranularity(1f);
        barChart.getXAxis().setTextSize(9f);
        barChart.getAxisRight().setEnabled(false);
        barChart.getAxisLeft().setTextSize(10f);
        barChart.getLegend().setTextSize(11f);
        dashInner.addView(barChart);

        // Ensamblar
        rootLayout.addView(tvTitle);
        rootLayout.addView(tvSubtitle);
        rootLayout.addView(tvWarning);
        rootLayout.addView(tvSimpleMode);
        rootLayout.addView(statusCard);
        rootLayout.addView(space(8));
        rootLayout.addView(infoCard);
        rootLayout.addView(space(8));
        rootLayout.addView(logCard);
        rootLayout.addView(space(8));
        rootLayout.addView(dashboardCard);

        scroll.addView(rootLayout);
        setContentView(scroll);
    }

    private CardView makeCard() {
        CardView card = new CardView(this);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.setMargins(0, 0, 0, dp(8));
        card.setLayoutParams(lp);
        card.setRadius(dp(12));
        card.setCardElevation(dp(4));
        card.setCardBackgroundColor(Color.WHITE);
        return card;
    }

    private LinearLayout cardInner(CardView card) {
        LinearLayout inner = new LinearLayout(this);
        inner.setOrientation(LinearLayout.VERTICAL);
        inner.setPadding(dp(16), dp(16), dp(16), dp(16));
        card.addView(inner);
        return inner;
    }

    private TextView makeLabel(LinearLayout parent, String text) {
        TextView tv = new TextView(this);
        tv.setText(text);
        tv.setTextSize(16f);
        tv.setTextColor(Color.parseColor("#333333"));
        tv.setPadding(0, dp(4), 0, dp(4));
        parent.addView(tv);
        return tv;
    }

    private View space(int dpVal) {
        View v = new View(this);
        v.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(dpVal)));
        return v;
    }

    private int dp(int value) {
        return (int) (value * getResources().getDisplayMetrics().density);
    }
}
