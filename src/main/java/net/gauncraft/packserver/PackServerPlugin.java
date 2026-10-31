package net.gauncraft.packserver;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerResourcePackStatusEvent;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.util.UUID;
import java.util.concurrent.Executors;

public final class PackServerPlugin extends JavaPlugin implements Listener {

    private static final String PACK_FILE = "GaunCraft_Resourcepack-4.zip";

    private HttpServer http;
    private Path packPath;
    private byte[] packBytes;
    private byte[] sha1;
    private String sha1Hex;
    private String url;
    private UUID packId;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        try {
            preparePack();
        } catch (Exception e) {
            getLogger().severe("Resource pack could not be loaded: " + e.getMessage());
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        String external = getConfig().getString("public-url", "").trim();
        if (!external.isEmpty()) {
            url = external;
            getLogger().info("Using external pack URL, built-in web server stays off.");
        } else {
            try {
                startHttpServer();
            } catch (IOException e) {
                getLogger().severe("Web server could not start on port " + getConfig().getInt("port", 8767) + ": " + e.getMessage());
                getServer().getPluginManager().disablePlugin(this);
                return;
            }
            url = "http://" + hostOnly() + ":" + getConfig().getInt("port", 8767) + "/" + PACK_FILE;
            if (!getConfig().getBoolean("suppress-host-warning", false)) {
                getLogger().warning("Check that '" + hostOnly() + "' is reachable by your players and that port "
                        + getConfig().getInt("port", 8767) + " is open in your firewall.");
            }
        }

        getLogger().info("Pack: " + PACK_FILE + " (" + (packBytes.length / 1024 / 1024) + " MB)");
        getLogger().info("URL: " + url);
        getLogger().info("SHA-1: " + sha1Hex);
        getServer().getPluginManager().registerEvents(this, this);
    }

    @Override
    public void onDisable() {
        if (http != null) {
            http.stop(0);
            http = null;
        }
    }

    private void preparePack() throws Exception {
        packPath = getDataFolder().toPath().resolve(PACK_FILE);
        Files.createDirectories(getDataFolder().toPath());
        // jar'daki paket her zaman esas alinir (GitHub'dan guncel derleme = guncel paket)
        try (InputStream in = getResource(PACK_FILE)) {
            if (in != null) {
                Files.copy(in, packPath, StandardCopyOption.REPLACE_EXISTING);
                getLogger().info("Extracted " + PACK_FILE + " from the jar.");
            }
        }
        if (!Files.exists(packPath)) {
            throw new IllegalStateException(PACK_FILE + " not found in the plugin jar or data folder");
        }
        packBytes = Files.readAllBytes(packPath);
        if (packBytes.length == 0) {
            throw new IllegalStateException(PACK_FILE + " is empty");
        }
        MessageDigest md = MessageDigest.getInstance("SHA-1");
        sha1 = md.digest(packBytes);
        StringBuilder sb = new StringBuilder();
        for (byte b : sha1) sb.append(String.format("%02x", b));
        sha1Hex = sb.toString();
        packId = UUID.nameUUIDFromBytes(("GaunCraft:" + PACK_FILE).getBytes(StandardCharsets.UTF_8));
    }

    private void startHttpServer() throws IOException {
        FileConfiguration c = getConfig();
        int port = c.getInt("port", 8767);
        String bind = c.getString("bind-address", "0.0.0.0");
        http = HttpServer.create(new InetSocketAddress(InetAddress.getByName(bind), port), 0);
        http.createContext("/" + PACK_FILE, this::serve);
        http.createContext("/pack.zip", this::serve);
        http.setExecutor(Executors.newFixedThreadPool(Math.max(1, c.getInt("http-threads", 4))));
        http.start();
    }

    private String hostOnly() {
        String h = getConfig().getString("host", "").trim();
        if (!h.isEmpty()) return h;
        try {
            return InetAddress.getLocalHost().getHostAddress();
        } catch (Exception e) {
            return "127.0.0.1";
        }
    }

    private void serve(HttpExchange ex) throws IOException {
        try {
            String m = ex.getRequestMethod();
            ex.getResponseHeaders().add("Content-Type", "application/zip");
            ex.getResponseHeaders().add("Content-Disposition", "attachment; filename=\"" + PACK_FILE + "\"");
            if ("HEAD".equalsIgnoreCase(m)) {
                ex.getResponseHeaders().add("Content-Length", String.valueOf(packBytes.length));
                ex.sendResponseHeaders(200, -1);
            } else if ("GET".equalsIgnoreCase(m)) {
                ex.sendResponseHeaders(200, packBytes.length);
                try (OutputStream os = ex.getResponseBody()) {
                    os.write(packBytes);
                }
            } else {
                ex.sendResponseHeaders(405, -1);
            }
        } finally {
            ex.close();
        }
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent e) {
        Player p = e.getPlayer();
        long delay = Math.max(0, getConfig().getLong("send-delay-ticks", 60));
        Bukkit.getScheduler().runTaskLater(this, () -> {
            if (p.isOnline()) send(p);
        }, delay);
    }

    private void send(Player p) {
        String prompt = getConfig().getString("prompt", "");
        String msg = prompt.isEmpty() ? null : color(prompt);
        try {
            if (getConfig().getBoolean("force", true)) {
                p.setResourcePack(packId, url, sha1, msg == null ? null : net.kyori.adventure.text.Component.text(msg), true);
            } else {
                p.setResourcePack(packId, url, sha1, msg == null ? null : net.kyori.adventure.text.Component.text(msg), false);
            }
        } catch (Exception ex) {
            getLogger().warning(p.getName() + " could not get the pack: " + ex.getMessage());
        }
    }

    @EventHandler
    public void onStatus(PlayerResourcePackStatusEvent e) {
        if (!packId.equals(e.getID())) return;
        Player p = e.getPlayer();
        switch (e.getStatus()) {
            case SUCCESSFULLY_LOADED -> {
                String s = getConfig().getString("message-accepted", "");
                if (!s.isEmpty()) p.sendMessage(color(s));
            }
            case DECLINED -> {
                if (getConfig().getBoolean("kick-on-decline", false)) {
                    p.kickPlayer(color(getConfig().getString("kick-message", "")));
                }
            }
            case FAILED_DOWNLOAD, FAILED_RELOAD, INVALID_URL, DISCARDED -> {
                String s = getConfig().getString("message-failed", "");
                if (!s.isEmpty()) p.sendMessage(color(s));
                getLogger().warning(p.getName() + " could not get the pack: " + e.getStatus());
            }
            default -> { }
        }
    }

    @Override
    public boolean onCommand(CommandSender s, Command cmd, String label, String[] a) {
        String sub = a.length == 0 ? "info" : a[0].toLowerCase();
        switch (sub) {
            case "send" -> {
                if (a.length > 1) {
                    Player t = Bukkit.getPlayerExact(a[1]);
                    if (t == null) {
                        s.sendMessage(ChatColor.RED + "Player not found.");
                        return true;
                    }
                    send(t);
                    s.sendMessage("Sent the pack to " + t.getName() + ".");
                } else {
                    Bukkit.getOnlinePlayers().forEach(this::send);
                    s.sendMessage("Sent the pack to everyone online.");
                }
            }
            case "reload" -> {
                reloadConfig();
                s.sendMessage("Config reloaded. Change host/port/public-url? Restart the server instead.");
            }
            default -> {
                s.sendMessage("Pack file: " + PACK_FILE + " (" + packBytes.length + " bytes)");
                s.sendMessage("URL: " + url);
                s.sendMessage("SHA-1: " + sha1Hex);
            }
        }
        return true;
    }

    private static String color(String t) {
        return ChatColor.translateAlternateColorCodes('&', t);
    }
}
