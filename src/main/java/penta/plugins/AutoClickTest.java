package penta.plugins;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.*;
import org.bukkit.entity.Player;
import org.bukkit.event.*;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public final class AutoClickTest extends JavaPlugin implements Listener, CommandExecutor {

    private final Map<UUID, Sample> active = new ConcurrentHashMap<>();

    @Override
    public void onEnable() {
        saveDefaultConfig();
        getServer().getPluginManager().registerEvents(this, this);
        Objects.requireNonNull(getCommand("autoclick")).setExecutor(this);
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.MONITOR)
    public void onHit(EntityDamageByEntityEvent e) {
        if (!(e.getDamager() instanceof Player p)) return;
        Sample s = active.get(p.getUniqueId());
        if (s == null) return;

        long tick = System.currentTimeMillis() / 50L;
        if (s.lastTick == tick) return;

        s.lastTick = tick;
        long nowNanos = System.nanoTime();
        if (s.lastTimeNanos != 0L) {
            long delta = nowNanos - s.lastTimeNanos;
            s.intervalsMillis.add(delta / 1_000_000.0);
        }
        s.lastTimeNanos = nowNanos;
        s.events++;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (args.length == 2 && args[0].equalsIgnoreCase("test")) {
            Player target = Bukkit.getPlayerExact(args[1]);
            if (target == null) {
                sender.sendMessage(ChatColor.RED + "Player not found or not online.");
                return true;
            }
            if (active.containsKey(target.getUniqueId())) {
                sender.sendMessage(ChatColor.YELLOW + "A test is already running for that player.");
                return true;
            }

            int windowSec = getConfig().getInt("window_seconds", 30);
            Sample sample = new Sample(target.getUniqueId(), getPingSafe(target));
            active.put(target.getUniqueId(), sample);

            if (getConfig().getBoolean("notify_target", true)) {
                target.sendMessage(ChatColor.GRAY + "An administrator is sampling your attack timing for " + windowSec + "s.");
            }
            sender.sendMessage(ChatColor.GRAY + "Sampling attacks from " + target.getName() + " for " + windowSec + " seconds…");

            new BukkitRunnable() {
                @Override
                public void run() {
                    Sample s = active.remove(target.getUniqueId());
                    if (s == null) return;
                    Summary sum = summarise(s);
                    sender.sendMessage(formatSummary(target.getName(), sum));
                }
            }.runTaskLater(this, 20L * windowSec);
            return true;
        }
        sender.sendMessage(ChatColor.RED + "Usage: /autoclick test <player>");
        return true;
    }

    private record Summary(int events, double cps, double cv, double duplicateRatio,
                           boolean suspicious, boolean borderline, int ping) {}
    private static class Sample {
        final UUID uuid;
        final List<Double> intervalsMillis = new ArrayList<>();
        long lastTimeNanos = 0L;
        long lastTick = -1L;
        int events = 0;
        final int ping;
        Sample(UUID uuid, int ping){ this.uuid = uuid; this.ping = ping; }
    }

    private Summary summarise(Sample s) {
        int n = s.intervalsMillis.size();
        if (n == 0) return new Summary(s.events, 0, 0, 0, false, false, s.ping);

        List<Double> xs = new ArrayList<>(s.intervalsMillis);
        Collections.sort(xs);
        int lo = (int)Math.floor(0.05 * n), hi = (int)Math.ceil(0.95 * n) - 1;
        double loV = xs.get(lo), hiV = xs.get(hi);
        List<Double> clamped = new ArrayList<>(n);
        for (double v : s.intervalsMillis) clamped.add(Math.max(loV, Math.min(hiV, v)));

        double mean = clamped.stream().mapToDouble(d -> d).average().orElse(0.0);
        double var = 0.0;
        for (double v : clamped) var += (v - mean)*(v - mean);
        var /= clamped.size();
        double sd = Math.sqrt(var);
        double cv = (mean > 0) ? sd / mean : 0.0;
        double cps = (mean > 0) ? 1000.0 / mean : 0.0;

        Map<Long,Integer> bins = new HashMap<>();
        for (double v : clamped) {
            long bin = Math.round(v / 50.0);
            bins.put(bin, bins.getOrDefault(bin, 0) + 1);
        }
        int maxBin = bins.values().stream().max(Integer::compareTo).orElse(0);
        int sumTop2 = bins.values().stream().sorted(Comparator.reverseOrder())
                .limit(2).mapToInt(i->i).sum();
        double duplicateRatio = (double)sumTop2 / Math.max(1, clamped.size());

        double cpsMin = getConfig().getDouble("cps_min", 3.0);
        double cpsMax = getConfig().getDouble("cps_max", 9.0);
        double cvSusp = getConfig().getDouble("cv_suspicious", 0.08);
        double cvBorder = getConfig().getDouble("cv_borderline", 0.10);
        double dupSusp = getConfig().getDouble("duplicate_ratio_suspicious", 0.60);
        double dupBorder = getConfig().getDouble("duplicate_ratio_borderline", 0.50);
        int minIntervals = getConfig().getInt("min_intervals", 20);

// Evaluate plausible CPS range
        boolean cpsPlausible = cps >= cpsMin && cps <= cpsMax;

// NEW: hard periodic rule (ignores CPS/CV entirely)
        double hardThresh = getConfig().getDouble("duplicate_ratio_hard", 0.995);
        int hardMinN = getConfig().getInt("hard_min_intervals", 12);
        boolean periodicHard = (duplicateRatio >= hardThresh && n >= hardMinN);

// Existing “very regular” rule that also ignores CPS
        boolean periodicStrong = (cv < 0.05 && duplicateRatio >= 0.90 && n >= 16);

// Existing CPS-gated rules
        boolean ruleSuspicious = (cv < cvSusp) && (duplicateRatio >= dupSusp) && cpsPlausible && n >= minIntervals;
        boolean ruleBorderline = (cv < cvBorder) && (duplicateRatio >= dupBorder) && cpsPlausible && n >= minIntervals;

// Combine: any hard/strong periodicity OR the CPS-gated rule
        boolean suspicious = periodicHard || periodicStrong || ruleSuspicious;
        boolean borderline = !suspicious && ruleBorderline;

        Bukkit.getLogger().info(String.format(
                java.util.Locale.UK,
                "[AutoClickTest DEBUG] n=%d cps=%.3f cv=%.5f dup=%.3f | periodicStrong=%s ruleSuspicious=%s ruleBorderline=%s cpsPlausible=%s",
                n, cps, cv, duplicateRatio, periodicStrong, ruleSuspicious, ruleBorderline, cpsPlausible
        ));


        return new Summary(s.events, cps, cv, duplicateRatio, suspicious, borderline, s.ping);
    }

    private String formatSummary(String name, Summary s) {
        String status = s.suspicious ? (ChatColor.RED + "Highly suspicious")
                : s.borderline ? (ChatColor.GOLD + "Borderline")
                : (ChatColor.GREEN + "No strong evidence");
        return ChatColor.AQUA + "[AutoClickTest] " + ChatColor.WHITE + name + " — " + status + ChatColor.GRAY +
                "\nEvents: " + s.events +
                " | CPS: " + String.format(Locale.UK, "%.2f", s.cps) +
                " | CV: " + String.format(Locale.UK, "%.3f", s.cv) +
                " | Duplicate-interval ratio: " + String.format(Locale.UK, "%.2f", s.duplicateRatio) +
                " | Avg ping: " + s.ping + " ms";
    }

    private int getPingSafe(Player p) {
        try { return p.getPing(); } catch (Throwable t) { return -1; }
    }
}
