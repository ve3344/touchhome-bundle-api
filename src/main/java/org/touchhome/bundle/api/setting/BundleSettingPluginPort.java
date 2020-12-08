package org.touchhome.bundle.api.setting;

import com.fazecast.jSerialComm.SerialPort;
import org.touchhome.bundle.api.EntityContext;
import org.touchhome.bundle.api.model.OptionModel;

import java.util.Collection;

public interface BundleSettingPluginPort extends BundleSettingPlugin<SerialPort>, BundleSettingOptionsSettingPlugin<SerialPort> {

    @Override
    default SettingType getSettingType() {
        return SettingType.SelectBoxDynamic;
    }

    @Override
    default Collection<OptionModel> loadAvailableValues(EntityContext entityContext) {
        return OptionModel.listOfPorts(withEmpty());
    }

    default boolean withEmpty() {
        return false;
    }

    @Override
    default String writeValue(SerialPort value) {
        return value == null ? "" : value.getSystemPortName();
    }

    @Override
    default Class<SerialPort> getType() {
        return SerialPort.class;
    }
}
