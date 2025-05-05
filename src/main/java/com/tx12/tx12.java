package com.tx12;

import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import com.google.gson.JsonArray;
import com.google.gson.JsonParser;
import java.io.InputStreamReader;

public class tx12 extends JavaPlugin implements Listener, CommandExecutor {  // 实现CommandExecutor接口
    private String blacklistUrl;
    private JsonArray blacklist;
    private Integer scheduledTaskId; // 定时任务ID变量

    @Override
    public void onEnable() {
        saveDefaultConfig();
        blacklistUrl = getConfig().getString("blacklist-url");
        getServer().getPluginManager().registerEvents(this, this);
        
        // 每5分钟执行一次
        scheduledTaskId = Bukkit.getScheduler().scheduleSyncRepeatingTask(this, () -> {
            if (Bukkit.getOnlinePlayers().isEmpty()) {
                fetchBlacklist();
            }
        }, 0L, 6000L); // 6000 ticks = 5分钟
        
        // 注册命令
        getCommand("blacklist").setExecutor(this);
        
        getLogger().info("插件已成功加载");
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        // 玩家加入时获取一次
        fetchBlacklist();
        
        // 取消定时任务
        if (scheduledTaskId != null) {
            Bukkit.getScheduler().cancelTask(scheduledTaskId);
            scheduledTaskId = null;
        }
        
        Bukkit.getScheduler().runTaskAsynchronously(this, () -> {
            checkCloudBlacklist(event.getPlayer().getName(), event.getPlayer().getUniqueId().toString());
        });
    }

    private void checkCloudBlacklist(String playerName, String uuid) {
        if (blacklist != null && (blacklist.toString().contains(uuid) || blacklist.toString().contains(playerName))) {
            Bukkit.getScheduler().runTask(this, () -> {
                Bukkit.getPlayer(playerName).kickPlayer("您已被列入云端黑名单");
            });
        }
    }

    private void fetchBlacklist() {
        try (CloseableHttpClient httpClient = HttpClients.createDefault()) {
            HttpGet request = new HttpGet(blacklistUrl);
            InputStreamReader reader = new InputStreamReader(
                httpClient.execute(request).getEntity().getContent(), 
                "UTF-8"
            );
            
            // 修改点：从JSON对象中获取banned_players数组
            blacklist = JsonParser.parseReader(reader)
                .getAsJsonObject()
                .getAsJsonArray("banned_players");
            getLogger().info("获取黑名单成功");
        } catch (Exception e) {
            getLogger().warning("获取黑名单失败: " + e.getMessage());
        }
    }

    // 命令处理
    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (args.length == 0) {
            sendHelp(sender);
            return true;
        }

        switch (args[0].toLowerCase()) {
            case "pull":
                fetchBlacklist();
                sender.sendMessage("§a已从云端获取最新黑名单数据");
                return true;
                
            case "list":
                int page = 1;
                if (args.length > 1) {
                    try {
                        page = Integer.parseInt(args[1]);
                    } catch (NumberFormatException e) {
                        sender.sendMessage("§c无效的页码，将显示第一页");
                    }
                }
                displayBlacklist(sender, page);
                return true;
                
            case "awa":
                if (args.length < 2) {
                    sender.sendMessage("§c请输入要查询的玩家名称");
                    return true;
                }
                searchPlayer(sender, args[1]);
                return true;
                
            case "help":
                sendHelp(sender);
                return true;
        }
        return false;
    }

    private void displayBlacklist(CommandSender sender, int page) {
        // 页码有效性检查
        if (page < 1) {
            page = 1;
        }
        if (blacklist == null || blacklist.size() == 0) {
            sender.sendMessage("§e当前云端黑名单为空");
            return;
        }

        int itemsPerPage = 20;
        int totalPages = (int) Math.ceil((double) blacklist.size() / itemsPerPage);
        page = Math.max(1, Math.min(page, totalPages));

        sender.sendMessage("§6==== 云端黑名单列表 ====");
        sender.sendMessage("§7第 " + page + " 页 / 共 " + totalPages + " 页");
        for (int i = (page - 1) * itemsPerPage; i < Math.min(page * itemsPerPage, blacklist.size()); i++) {
            // 修改点：解析JSON对象并格式化输出
            var entry = blacklist.get(i).getAsJsonObject();
            String info = String.format("§f%s - 理由: §c%s §f| 时间: §e%s", 
                entry.get("name").getAsString(),
                entry.get("reason").getAsString(),
                entry.get("time").getAsString());
            sender.sendMessage(info);
        }
    }

    private void searchPlayer(CommandSender sender, String playerName) {
        if (blacklist == null || blacklist.size() == 0) {
            sender.sendMessage("§e当前云端黑名单为空");
            return;
        }

        for (int i = 0; i < blacklist.size(); i++) {
            var entry = blacklist.get(i).getAsJsonObject();
            if (entry.get("name").getAsString().equalsIgnoreCase(playerName)) {
                sender.sendMessage("§a玩家 " + playerName + " 存在于黑名单中（原因：" 
                    + entry.get("reason").getAsString() + "）");
                return;
            }
        }
        sender.sendMessage("§c玩家 " + playerName + " 不在黑名单中");
    }

    private void sendHelp(CommandSender sender) {
        sender.sendMessage("§6==== 黑名单系统帮助菜单 作者：哔哩哔哩-星涵煦====");
        sender.sendMessage("§a/blacklist pull §7- 从云端获取最新黑名单");
        sender.sendMessage("§a/blacklist list [页码] §7- 查看黑名单内容，每页20条");
        sender.sendMessage("§a/blacklist awa <玩家名称> §7- 查询指定玩家状态");
        sender.sendMessage("§a/blacklist help §7- 显示帮助菜单");
    }
}