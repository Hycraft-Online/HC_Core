package com.hccore.models;

/**
 * Defines a setting's default value, type, and description.
 * Used by plugins to register their defaults with HC_Core.
 */
public class SettingDef {

    private final String defaultValue;
    private final String valueType;
    private final String description;

    /**
     * @param defaultValue Default value as a string
     * @param valueType    One of: STRING, INT, FLOAT, BOOLEAN
     * @param description  Human-readable description
     */
    public SettingDef(String defaultValue, String valueType, String description) {
        this.defaultValue = defaultValue;
        this.valueType = valueType;
        this.description = description;
    }

    public String getDefaultValue() {
        return defaultValue;
    }

    public String getValueType() {
        return valueType;
    }

    public String getDescription() {
        return description;
    }
}
