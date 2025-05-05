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

public class tx12 extends JavaPlugin implements Listener, CommandExecutor {  // ʵ��CommandExecutor�ӿ�
    private String blacklistUrl;
    private JsonArray blacklist;
    private Integer scheduledTaskId; // ��ʱ����ID����

    @Override
    public void onEnable() {
        saveDefaultConfig();
        blacklistUrl = getConfig().getString("blacklist-url");
        getServer().getPluginManager().registerEvents(this, this);
        
        // ÿ5����ִ��һ��
        scheduledTaskId = Bukkit.getScheduler().scheduleSyncRepeatingTask(this, () -> {
            if (Bukkit.getOnlinePlayers().isEmpty()) {
                fetchBlacklist();
            }
        }, 0L, 6000L); // 6000 ticks = 5����
        
        // ע������
        getCommand("blacklist").setExecutor(this);
        
        getLogger().info("����ѳɹ�����");
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        // ��Ҽ���ʱ��ȡһ��
        fetchBlacklist();
        
        // ȡ����ʱ����
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
                Bukkit.getPlayer(playerName).kickPlayer("���ѱ������ƶ˺�����");
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
            
            // �޸ĵ㣺��JSON�����л�ȡbanned_players����
            blacklist = JsonParser.parseReader(reader)
                .getAsJsonObject()
                .getAsJsonArray("banned_players");
            getLogger().info("��ȡ�������ɹ�");
        } catch (Exception e) {
            getLogger().warning("��ȡ������ʧ��: " + e.getMessage());
        }
    }

    // �����
    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (args.length == 0) {
            sendHelp(sender);
            return true;
        }

        switch (args[0].toLowerCase()) {
            case "pull":
                fetchBlacklist();
                sender.sendMessage("��a�Ѵ��ƶ˻�ȡ���º���������");
                return true;
                
            case "list":
                int page = 1;
                if (args.length > 1) {
                    try {
                        page = Integer.parseInt(args[1]);
                    } catch (NumberFormatException e) {
                        sender.sendMessage("��c��Ч��ҳ�룬����ʾ��һҳ");
                    }
                }
                displayBlacklist(sender, page);
                return true;
                
            case "awa":
                if (args.length < 2) {
                    sender.sendMessage("��c������Ҫ��ѯ���������");
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
        // ҳ����Ч�Լ��
        if (page < 1) {
            page = 1;
        }
        if (blacklist == null || blacklist.size() == 0) {
            sender.sendMessage("��e��ǰ�ƶ˺�����Ϊ��");
            return;
        }

        int itemsPerPage = 20;
        int totalPages = (int) Math.ceil((double) blacklist.size() / itemsPerPage);
        page = Math.max(1, Math.min(page, totalPages));

        sender.sendMessage("��6==== �ƶ˺������б� ====");
        sender.sendMessage("��7�� " + page + " ҳ / �� " + totalPages + " ҳ");
        for (int i = (page - 1) * itemsPerPage; i < Math.min(page * itemsPerPage, blacklist.size()); i++) {
            // �޸ĵ㣺����JSON���󲢸�ʽ�����
            var entry = blacklist.get(i).getAsJsonObject();
            String info = String.format("��f%s - ����: ��c%s ��f| ʱ��: ��e%s", 
                entry.get("name").getAsString(),
                entry.get("reason").getAsString(),
                entry.get("time").getAsString());
            sender.sendMessage(info);
        }
    }

    private void searchPlayer(CommandSender sender, String playerName) {
        if (blacklist == null || blacklist.size() == 0) {
            sender.sendMessage("��e��ǰ�ƶ˺�����Ϊ��");
            return;
        }

        for (int i = 0; i < blacklist.size(); i++) {
            var entry = blacklist.get(i).getAsJsonObject();
            if (entry.get("name").getAsString().equalsIgnoreCase(playerName)) {
                sender.sendMessage("��a��� " + playerName + " �����ں������У�ԭ��" 
                    + entry.get("reason").getAsString() + "��");
                return;
            }
        }
        sender.sendMessage("��c��� " + playerName + " ���ں�������");
    }

    private void sendHelp(CommandSender sender) {
        sender.sendMessage("��6==== ������ϵͳ�����˵� ���ߣ���������-�Ǻ���====");
        sender.sendMessage("��a/blacklist pull ��7- ���ƶ˻�ȡ���º�����");
        sender.sendMessage("��a/blacklist list [ҳ��] ��7- �鿴���������ݣ�ÿҳ20��");
        sender.sendMessage("��a/blacklist awa <�������> ��7- ��ѯָ�����״̬");
        sender.sendMessage("��a/blacklist help ��7- ��ʾ�����˵�");
    }
}