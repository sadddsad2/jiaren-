package com.randomnpcs;

import com.randomnpcs.bot.BotManager;
import com.randomnpcs.command.BotsCommand;
import com.randomnpcs.listener.BotDeathListener;
import com.randomnpcs.net.MinecraftProtocol;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.Socket;
import java.util.Collections;
import java.util.Enumeration;

public class RandomBotsPlugin extends JavaPlugin {

    private static RandomBotsPlugin instance;
    private BotManager botManager;
    private int serverPort;
    private String resolvedHost;

    private File keepaliveCacheFile;

    @Override
    public void onEnable() {
        instance = this;
        saveDefaultConfig();

        String envPort = System.getenv("SERVER_PORT");
        if (envPort != null && !envPort.isEmpty()) {
            try {
                serverPort = Integer.parseInt(envPort.trim());
            } catch (NumberFormatException e) {
                serverPort = getConfig().getInt("server-port", 25565);
            }
        } else {
            serverPort = getConfig().getInt("server-port", 25565);
        }

        resolvedHost = detectLocalHost();

        keepaliveCacheFile = new File(getDataFolder(), "keepalive-cache.yml");
        loadKeepaliveCache();

        botManager = new BotManager(this);

        getServer().getPluginManager().registerEvents(
                new BotDeathListener(this, botManager), this);

        BotsCommand botsCommand = new BotsCommand(this, botManager);
        getCommand("randombots").setExecutor(botsCommand);
        getCommand("randombots").setTabCompleter(botsCommand);

        getServer().getScheduler().runTaskLater(this, () -> {
            botManager.startBots();
        }, 40L);
    }

    @Override
    public void onDisable() {
        if (botManager != null) botManager.stopAllBots();
    }

    private String detectLocalHost() {
        try (Socket s = new Socket()) {
            s.connect(new java.net.InetSocketAddress("8.8.8.8", 80), 1000);
            String ip = s.getLocalAddress().getHostAddress();
            if (ip != null && !ip.startsWith("127.") && !ip.startsWith("0.")) return ip;
        } catch (Exception ignored) {}

        try {
            Enumeration<NetworkInterface> nics = NetworkInterface.getNetworkInterfaces();
            if (nics != null) {
                for (NetworkInterface nic : Collections.list(nics)) {
                    if (!nic.isUp() || nic.isLoopback() || nic.isVirtual()) continue;
                    String nicName = nic.getName().toLowerCase();
                    if (nicName.startsWith("docker") || nicName.startsWith("veth")
                            || nicName.startsWith("virbr") || nicName.startsWith("tun")
                            || nicName.startsWith("tap")) continue;
                    for (InetAddress addr : Collections.list(nic.getInetAddresses())) {
                        if (!(addr instanceof Inet4Address)) continue;
                        String ip = addr.getHostAddress();
                        if (ip.startsWith("127.") || ip.startsWith("169.254.")) continue;
                        return ip;
                    }
                }
            }
        } catch (Exception ignored) {}

        String cfgHost = getConfig().getString("server-host", "localhost");
        if (cfgHost != null && !cfgHost.isEmpty()
                && !cfgHost.equals("localhost") && !cfgHost.equals("127.0.0.1")) {
            return cfgHost;
        }

        return "localhost";
    }

    private void loadKeepaliveCache() {
        if (!keepaliveCacheFile.exists()) return;
        try {
            YamlConfiguration yml = YamlConfiguration.loadConfiguration(keepaliveCacheFile);
            for (String key : yml.getKeys(false)) {
                int cb = yml.getInt(key + ".cb", -1);
                int sb = yml.getInt(key + ".sb", -1);
                int sp = yml.getInt(key + ".sp", -1);
                if (cb > 0 && sb > 0) {
                    MinecraftProtocol.injectCache(key, cb, sb, sp > 0 ? sp : 0x3E);
                }
            }
        } catch (Exception ignored) {}
    }

    public void saveKeepaliveCache(String cacheKey, int cb, int sb, int sp) {
        getServer().getScheduler().runTaskAsynchronously(this, () -> {
            try {
                YamlConfiguration yml = keepaliveCacheFile.exists()
                        ? YamlConfiguration.loadConfiguration(keepaliveCacheFile)
                        : new YamlConfiguration();
                yml.set(cacheKey + ".cb", cb);
                yml.set(cacheKey + ".sb", sb);
                yml.set(cacheKey + ".sp", sp);
                yml.save(keepaliveCacheFile);
            } catch (Exception ignored) {}
        });
    }

    /** 完全静默，不输出任何日志 */
    public void debugLog(String msg) { /* 静默 */ }

    public static RandomBotsPlugin getInstance() { return instance; }
    public BotManager getBotManager()            { return botManager; }
    public int        getServerPort()            { return serverPort; }
    public String     getServerHost()            { return resolvedHost != null ? resolvedHost : getConfig().getString("server-host", "localhost"); }
}
