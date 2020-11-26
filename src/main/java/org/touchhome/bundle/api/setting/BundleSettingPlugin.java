package org.touchhome.bundle.api.setting;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fazecast.jSerialComm.SerialPort;
import org.apache.commons.lang3.StringUtils;
import org.json.JSONObject;
import org.touchhome.bundle.api.EntityContext;
import org.touchhome.bundle.api.json.NotificationEntityJSON;
import org.touchhome.bundle.api.json.Option;

import java.util.Collection;
import java.util.stream.Stream;

@JsonInclude(JsonInclude.Include.NON_NULL)
public interface BundleSettingPlugin<T> {

    default Class<T> getType() {
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
        return "";
    }

    // min/max/step (Slider)
    default JSONObject getParameters(EntityContext entityContext, String value) {
        return null;
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
        switch (getSettingType()) {
            case Float:
                return (T) Float.valueOf(value);
            case Boolean:
            case Toggle:
                return (T) Boolean.valueOf(value);
            case Integer:
            case Slider:
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
        if (SerialPort.class.equals(getType())) {
            return (T) (StringUtils.isEmpty(value) ? null :
                    Stream.of(SerialPort.getCommPorts())
                            .filter(p -> p.getSystemPortName().equals(value)).findAny().orElse(null));
        }
        return (T) value;
    }

    default Collection<Option> loadAvailableValues(EntityContext entityContext) {
        if (SerialPort.class.equals(getType())) {
            return Option.listOfPorts();
        }
        throw new IllegalStateException("Must be implemented in sub-classes");
    }

    /**
     * Values of settings with transient state doesn't save to db
     */
    default boolean transientState() {
        return (this.getSettingType() == SettingType.Button && this.getParameters(null, null) == null)
                || this.getSettingType() == SettingType.Info;
    }

    int order();

    /**
     * Advances settings opens in additional panel on ui
     */
    default boolean isAdvanced() {
        return false;
    }

    default NotificationEntityJSON buildToastrNotificationEntity(T value, String raw, EntityContext entityContext) {
        return null;
    }

    /**
     * Covnerter from target type to string
     */
    default String writeValue(T value) {
        if (value == null) {
            return "";
        }
        if (SerialPort.class.equals(getType())) {
            return ((SerialPort) value).getSystemPortName();
        }
        return value.toString();
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
        // Input text with additional button that able to fetch values from server
        TextSelectBoxDynamic,
        // Button that fires server action
        Button,
        Toggle,
        Info
    }
}
