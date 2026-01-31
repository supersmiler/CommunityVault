package org.niels.communityVault.utils;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class UpdateChecker {

    private static final String PROJECT_SLUG = "communityvault";
    private static final String VERSIONS_URL = "https://api.modrinth.com/v2/project/" + PROJECT_SLUG + "/version";
    private static final String DOWNLOAD_URL = "https://modrinth.com/plugin/communityvault/versions";

    private UpdateChecker() {
    }

    public static void checkForUpdates(JavaPlugin plugin) {
        if (System.getProperty("org.mockbukkit.running") != null) {
            return;
        }

        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                String response = getJsonResponse(plugin);
                if (response == null || response.isBlank()) {
                    return;
                }

                VersionInfo latest = findLatestRelease(response);
                if (latest == null || latest.version == null || latest.version.isBlank()) {
                    return;
                }

                String currentVersion = plugin.getDescription().getVersion();
                if (isNewerVersion(latest.version, currentVersion)) {
                    String message = ChatColor.GOLD + "[CommunityVault] " + ChatColor.AQUA
                            + "Update available: " + currentVersion + " -> " + latest.version
                            + ChatColor.GRAY + " (" + DOWNLOAD_URL + ")";
                    Bukkit.getConsoleSender().sendMessage(message);
                }
            } catch (Exception ignored) {
                // Do not spam console if update check fails
            }
        });
    }

    private static String getJsonResponse(JavaPlugin plugin) {
        HttpURLConnection connection = null;
        try {
            URL url = new URL(VERSIONS_URL);
            connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod("GET");
            connection.setConnectTimeout(5000);
            connection.setReadTimeout(5000);
            connection.setRequestProperty("User-Agent",
                    "Niels/CommunityVault/" + plugin.getDescription().getVersion());

            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(connection.getInputStream(), StandardCharsets.UTF_8))) {
                StringBuilder sb = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) {
                    sb.append(line);
                }
                return sb.toString();
            }
        } catch (Exception ignored) {
            return null;
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }
    }

    private static VersionInfo findLatestRelease(String json) {
        List<String> objects = splitTopLevelObjects(json);
        VersionInfo latest = null;
        for (String obj : objects) {
            String version = extractString(obj, "version_number");
            String versionType = extractString(obj, "version_type");
            String status = extractString(obj, "status");
            String date = extractString(obj, "date_published");

            if (!"release".equalsIgnoreCase(versionType)) {
                continue;
            }
            if (!"listed".equalsIgnoreCase(status)) {
                continue;
            }

            Instant published;
            try {
                published = date != null ? Instant.parse(date) : Instant.EPOCH;
            } catch (Exception e) {
                published = Instant.EPOCH;
            }

            if (latest == null || published.isAfter(latest.published)) {
                latest = new VersionInfo(version, published);
            }
        }
        return latest;
    }

    private static List<String> splitTopLevelObjects(String json) {
        List<String> objects = new ArrayList<>();
        int depth = 0;
        boolean inString = false;
        boolean escape = false;
        StringBuilder current = null;

        for (int i = 0; i < json.length(); i++) {
            char c = json.charAt(i);
            if (inString) {
                if (escape) {
                    escape = false;
                } else if (c == '\\') {
                    escape = true;
                } else if (c == '"') {
                    inString = false;
                }
            } else if (c == '"') {
                inString = true;
            }

            if (!inString) {
                if (c == '{') {
                    if (depth == 0) {
                        current = new StringBuilder();
                    }
                    depth++;
                }
                if (c == '}' && depth > 0) {
                    depth--;
                }
            }

            if (depth > 0 && current != null) {
                current.append(c);
            } else if (depth == 0 && c == '}' && current != null) {
                current.append(c);
                objects.add(current.toString());
                current = null;
            }
        }

        return objects;
    }

    private static String extractString(String json, String key) {
        Pattern pattern = Pattern.compile("\"" + Pattern.quote(key) + "\"\\s*:\\s*\"([^\"]*)\"");
        Matcher matcher = pattern.matcher(json);
        if (matcher.find()) {
            return matcher.group(1);
        }
        return null;
    }

    private static boolean isNewerVersion(String latest, String current) {
        if (latest.equalsIgnoreCase(current)) {
            return false;
        }
        List<Integer> latestParts = extractVersionParts(latest);
        List<Integer> currentParts = extractVersionParts(current);
        int max = Math.max(latestParts.size(), currentParts.size());
        for (int i = 0; i < max; i++) {
            int l = i < latestParts.size() ? latestParts.get(i) : 0;
            int c = i < currentParts.size() ? currentParts.get(i) : 0;
            if (l != c) {
                return l > c;
            }
        }
        return false;
    }

    private static List<Integer> extractVersionParts(String version) {
        List<Integer> parts = new ArrayList<>();
        Matcher matcher = Pattern.compile("(\\d+)").matcher(version);
        while (matcher.find()) {
            try {
                parts.add(Integer.parseInt(matcher.group(1)));
            } catch (NumberFormatException ignored) {
                // skip
            }
        }
        return parts;
    }

    private static final class VersionInfo {
        private final String version;
        private final Instant published;

        private VersionInfo(String version, Instant published) {
            this.version = version;
            this.published = published;
        }
    }
}
