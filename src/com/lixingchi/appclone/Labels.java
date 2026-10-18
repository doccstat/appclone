package com.lixingchi.appclone;

/**
 * Formats a shortcut label from the user's template. A label is free text, so
 * colons and other characters are fine; only line breaks are collapsed.
 */
final class Labels {
    static final String DEFAULT_FORMAT = "{id}: {name}";

    static String format(String template, int userId, String appLabel, String cloneName) {
        String name = appLabel == null ? "" : appLabel;
        String value = template == null || template.trim().isEmpty() ? DEFAULT_FORMAT : template;
        value = value.replace("{id}", String.valueOf(userId))
                .replace("{name}", name)
                .replace("{clone}", cloneName == null ? "" : cloneName);
        value = value.replaceAll("\\s+", " ").trim();
        if (value.isEmpty()) return name;
        // A template without {name} must not hide which app the shortcut opens.
        if (!name.isEmpty() && !value.contains(name)) value = value + " " + name;
        return value;
    }

    private Labels() {}
}
