package org.touchhome.bundle.api.setting;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fazecast.jSerialComm.SerialPort;
import org.apache.commons.lang3.StringUtils;
import org.json.JSONObject;
import org.touchhome.bundle.api.EntityContext;
import org.touchhome.bundle.api.model.KeyValueEnum;

import java.nio.file.Paths;
import java.util.stream.Stream;

import static org.touchhome.bundle.api.util.TouchHomeUtils.putOpt;

@JsonInclude(JsonInclude.Include.NON_NULL)
public interface SettingPlugin<T> {

    Class<T> getType();

    // specify max width of rendered ui item. Uses with SelectBox/SelectBoxDynamic
    default Integer getMaxWidth() {
        return null;
    }

    default String getIcon() {
        return "";
    }

    default String getIconColor() {
        return "";
    }

    default String getDefaultValue() {
        switch (getSettingType()) {
            case Integer:
            case Slider:
            case Float:
                return "0";
            case Boolean:
            case Toggle:
                return Boolean.FALSE.toString();
        }
        if (KeyValueEnum.class.isAssignableFrom(getType())) {
            return ((KeyValueEnum) getType().getEnumConstants()[0]).getKey();
        }
        if (getType().isEnum()) {
            return getType().getEnumConstants()[0].toString();
        }
        return "";
    }

    // min/max/step (Slider)
    default JSONObject getParameters(EntityContext entityContext, String value) {
        JSONObject parameters = new JSONObject();
        putOpt(parameters, "maxWidth", getMaxWidth());
        return parameters;
    }

    SettingType getSettingType();

    // if secured - users without admin privileges can't see values
    default boolean isSecuredValue() {
        return false;
    }

    // add revert button to ui
    default boolean isReverted() {
        return false;
    }

    default boolean isRequired() {
        return false;
    }

    // disabled input/button on ui
    default boolean isDisabled(EntityContext entityContext) {
        return false;
    }

    // visible on ui or not
    default boolean isVisible(EntityContext entityContext) {
        return true;
    }

    // grouping settings by group name
    default String group() {
        return null;
    }

    default T parseValue(EntityContext entityContext, String value) {
        if (value == null) {
            return null;
        }
        switch (getType().getSimpleName()) {
            case "Integer":
                return parseInteger(entityContext, value);
            case "Path":
                return (T) Paths.get(value);
        }
        switch (getSettingType()) {
            case Float:
                return (T) Float.valueOf(value);
            case Boolean:
            case Toggle:
                return (T) Boolean.valueOf(value);
            case Integer:
            case Slider:
                return parseInteger(entityContext, value);
        }
        if (getType().isEnum()) {
            return (T) Enum.valueOf((Class) getType(), value);
        }
        if (SerialPort.class.equals(getType())) {
            return (T) (StringUtils.isEmpty(value) ? null :
                    Stream.of(SerialPort.getCommPorts())
                            .filter(p -> p.getSystemPortName().equals(value)).findAny().orElse(null));
        }
        return (T) value;
    }

    // Is it able to save value to database or local variable
    default boolean isStorable() {
        return true;
    }

    /**
     * Values of settings with transient state doesn't save to db
     */
    default boolean transientState() {
        if (this.getSettingType() == SettingType.Button) {
            JSONObject parameters = this.getParameters(null, null);
            if (parameters == null || parameters.length() == 1 && parameters.has("confirm")) {
                return true;
            }
        }
        return this.getSettingType() == SettingType.Info || !this.isStorable();
    }

    int order();

    /**
     * Advances settings opens in additional panel on ui
     */
    default boolean isAdvanced() {
        return false;
    }

    /**
     * Covnerter from target type to string
     */
    default String writeValue(T value) {
        if (value == null) {
            return "";
        }
        return value.toString();
    }

    default T parseInteger(EntityContext entityContext, String value) {
        Integer parseValue;
        try {
            parseValue = Integer.valueOf(value);
        } catch (NumberFormatException ex) {
            throw new IllegalArgumentException("Unable parse setting value <" + value + "> as integer value");
        }
        JSONObject parameters = getParameters(entityContext, value);
        if (parameters != null) {
            if (parameters.has("min") && parseValue < parameters.getInt("min")) {
                throw new IllegalArgumentException("Setting value <" + value + "> less than minimum value: " + parameters.getInt("min"));
            }
            if (parameters.has("max") && parseValue > parameters.getInt("max")) {
                throw new IllegalArgumentException("Setting value <" + value + "> more than maximum value: " + parameters.getInt("max"));
            }
        }
        return (T) parseValue;
    }

    enum SettingType {
        // Description type uses for showing text inside setting panel on whole width
        Description,
        ColorPicker,
        Float,
        Boolean,
        Integer,
        SelectBox,
        SelectBoxButton,
        // Slider with min/max/step parameters
        Slider,
        // Select box with options fetched from server
        SelectBoxDynamic,
        // Just a text
        Text,
        Chips, // https://material.angular.io/components/chips/examples
        // Input text with additional button that able to fetch values from server
        TextSelectBoxDynamic,
        // Button that fires server action
        Button,
        Toggle,
        Info,
        Upload,
        TextArea
    }
}
