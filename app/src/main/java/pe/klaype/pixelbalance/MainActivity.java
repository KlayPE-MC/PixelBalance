package pe.klaype.pixelbalance;

import android.app.ActivityManager;
import android.app.AppOpsManager;
import android.app.usage.UsageStats;
import android.app.usage.UsageStatsManager;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.os.BatteryManager;
import android.os.Bundle;
import android.os.PowerManager;
import android.os.Process;
import android.os.StatFs;
import android.os.storage.StorageManager;
import android.provider.Settings;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

/**
 * A deliberately conservative maintenance dashboard. It never force-stops
 * applications, kills RAM, deletes user files, or sends diagnostics online.
 */
public final class MainActivity extends android.app.Activity {
    private static final int BLUE = 0xFF2563EB;
    private static final int TEXT = 0xFF1F2937;
    private static final int MUTED = 0xFF64748B;
    private static final int CARD = 0xFFF1F5F9;
    private LinearLayout content;

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        render();
    }

    @Override public void onResume() {
        super.onResume();
        if (content != null) render();
    }

    private void render() {
        ScrollView scroll = new ScrollView(this);
        content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(20), dp(24), dp(20), dp(32));
        scroll.addView(content);

        text("Pixel Balance", 28, TEXT, true);
        text("Diagnóstico local, sin anuncios ni servidores.", 15, MUTED, false);
        addSpace(16);

        BatteryManager battery = getSystemService(BatteryManager.class);
        Intent b = registerReceiver(null, new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
        int level = b == null ? -1 : b.getIntExtra(BatteryManager.EXTRA_LEVEL, -1);
        int scale = b == null ? 100 : b.getIntExtra(BatteryManager.EXTRA_SCALE, 100);
        int temperature = b == null ? -1 : b.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, -1);
        int status = b == null ? 0 : b.getIntExtra(BatteryManager.EXTRA_STATUS, 0);
        long currentUa = battery.getLongProperty(BatteryManager.BATTERY_PROPERTY_CURRENT_NOW);
        String charge = status == BatteryManager.BATTERY_STATUS_CHARGING ? "Cargando" : "En batería";
        String current = currentUa == Long.MIN_VALUE ? "No disponible" : String.format("%.0f mA", Math.abs(currentUa) / 1000f);
        card("Batería", percent(level, scale) + " · " + charge + "\n" +
                (temperature < 0 ? "Temperatura no disponible" : String.format("%.1f °C", temperature / 10f)) +
                " · Corriente: " + current);

        PowerManager power = getSystemService(PowerManager.class);
        String thermal = thermalLabel(power.getCurrentThermalStatus());
        card("Estado térmico", thermal + "\nEvita optimizaciones pesadas mientras el teléfono esté caliente.");

        StatFs stat = new StatFs(getFilesDir().getAbsolutePath());
        long free = stat.getAvailableBytes();
        long total = stat.getTotalBytes();
        card("Almacenamiento", human(total - free) + " usados de " + human(total) + " · " + human(free) + " libres\n" +
                "La limpieza se realiza mediante la interfaz segura de Android: tú eliges qué borrar.");

        ActivityManager.MemoryInfo memory = new ActivityManager.MemoryInfo();
        getSystemService(ActivityManager.class).getMemoryInfo(memory);
        card("Memoria", human(memory.availMem) + " disponibles\nAndroid administra la RAM automáticamente; cerrar apps por la fuerza suele empeorar batería y fluidez.");

        addSpace(8);
        text("Herramientas seguras", 19, TEXT, true);
        action("Liberar espacio con Android", v -> openStorageManager());
        action("Revisar almacenamiento", v -> startActivity(new Intent(Settings.ACTION_INTERNAL_STORAGE_SETTINGS)));
        action("Revisar consumo de batería", v -> startActivity(new Intent(Settings.ACTION_BATTERY_SAVER_SETTINGS)));
        action("Gestionar aplicaciones en segundo plano", v -> startActivity(new Intent(Settings.ACTION_APPLICATION_SETTINGS)));
        action("Permitir análisis de apps sin uso", v -> startActivity(new Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)));

        if (hasUsageAccess()) {
            addSpace(12);
            text("Apps sin abrir en 30 días", 19, TEXT, true);
            List<String> unused = unusedApps();
            if (unused.isEmpty()) {
                card("Todo en orden", "No encontré aplicaciones de usuario claramente abandonadas.");
            } else {
                card("Revisión recomendada", TextUtils.join("\n", unused.subList(0, Math.min(12, unused.size()))) +
                        (unused.size() > 12 ? "\n…y " + (unused.size() - 12) + " más." : "") +
                        "\n\nToca una aplicación desde Ajustes para decidir si la conservas o desinstalas.");
                action("Abrir lista de aplicaciones", v -> startActivity(new Intent(Settings.ACTION_APPLICATION_SETTINGS)));
            }
        } else {
            card("Análisis de uso opcional", "Concede Acceso al uso para detectar apps restauradas que no estás utilizando. La información no sale del teléfono.");
        }

        addSpace(12);
        text("Principios de privacidad", 19, TEXT, true);
        card("Sin funciones engañosas", "Esta app no mata procesos ni borra fotos automáticamente. Android administra la RAM por sí mismo; para cada aplicación puedes limitar batería, desinstalarla o borrar su caché desde los Ajustes oficiales. No se envían datos y no hay publicidad.");
        setContentView(scroll);
    }

    private void openStorageManager() {
        try {
            startActivity(new Intent(StorageManager.ACTION_MANAGE_STORAGE));
        } catch (Exception ignored) {
            startActivity(new Intent(Settings.ACTION_INTERNAL_STORAGE_SETTINGS));
        }
    }

    private List<String> unusedApps() {
        long end = System.currentTimeMillis();
        Calendar c = Calendar.getInstance();
        c.add(Calendar.DAY_OF_YEAR, -30);
        UsageStatsManager usage = getSystemService(UsageStatsManager.class);
        Map<String, UsageStats> stats = usage.queryAndAggregateUsageStats(c.getTimeInMillis(), end);
        PackageManager pm = getPackageManager();
        List<String> result = new ArrayList<>();
        for (ApplicationInfo app : pm.getInstalledApplications(0)) {
            if ((app.flags & ApplicationInfo.FLAG_SYSTEM) != 0 || getPackageName().equals(app.packageName)) continue;
            UsageStats stat = stats.get(app.packageName);
            if (stat == null || stat.getLastTimeUsed() < c.getTimeInMillis()) {
                result.add(pm.getApplicationLabel(app).toString());
            }
        }
        result.sort(Comparator.naturalOrder());
        return result;
    }

    private boolean hasUsageAccess() {
        AppOpsManager appOps = getSystemService(AppOpsManager.class);
        return appOps.checkOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS,
                Process.myUid(), getPackageName()) == AppOpsManager.MODE_ALLOWED;
    }

    private void card(String title, String body) {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(16), dp(14), dp(16), dp(14));
        box.setBackgroundColor(CARD);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, -2);
        params.setMargins(0, 0, 0, dp(10));
        content.addView(box, params);
        TextView heading = makeText(title, 16, TEXT, true);
        TextView detail = makeText(body, 14, MUTED, false);
        detail.setPadding(0, dp(4), 0, 0);
        box.addView(heading);
        box.addView(detail);
    }

    private void action(String label, View.OnClickListener listener) {
        Button button = new Button(this);
        button.setText(label);
        button.setTextColor(BLUE);
        button.setAllCaps(false);
        button.setOnClickListener(listener);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, -2);
        params.setMargins(0, 0, 0, dp(6));
        content.addView(button, params);
    }

    private void text(String value, int size, int color, boolean bold) {
        TextView t = makeText(value, size, color, bold);
        content.addView(t);
    }

    private TextView makeText(String value, int size, int color, boolean bold) {
        TextView t = new TextView(this);
        t.setText(value);
        t.setTextSize(size);
        t.setTextColor(color);
        t.setGravity(Gravity.START);
        if (bold) t.setTypeface(t.getTypeface(), android.graphics.Typeface.BOLD);
        return t;
    }

    private void addSpace(int height) {
        View v = new View(this);
        content.addView(v, new LinearLayout.LayoutParams(1, dp(height)));
    }

    private int dp(int amount) { return Math.round(amount * getResources().getDisplayMetrics().density); }
    private static String percent(int level, int scale) { return level < 0 ? "No disponible" : Math.round(level * 100f / scale) + "%"; }
    private static String human(long bytes) {
        if (bytes < 1024L * 1024L * 1024L) return String.format("%.0f MB", bytes / 1024f / 1024f);
        return String.format("%.1f GB", bytes / 1024f / 1024f / 1024f);
    }
    private static String thermalLabel(int value) {
        switch (value) {
            case PowerManager.THERMAL_STATUS_NONE: return "Normal";
            case PowerManager.THERMAL_STATUS_LIGHT: return "Ligero";
            case PowerManager.THERMAL_STATUS_MODERATE: return "Moderado";
            case PowerManager.THERMAL_STATUS_SEVERE: return "Alto: deja enfriar el teléfono";
            case PowerManager.THERMAL_STATUS_CRITICAL: return "Crítico: evita usar y cargar el teléfono";
            case PowerManager.THERMAL_STATUS_EMERGENCY: return "Emergencia";
            case PowerManager.THERMAL_STATUS_SHUTDOWN: return "Apagado térmico inminente";
            default: return "No disponible";
        }
    }
}

