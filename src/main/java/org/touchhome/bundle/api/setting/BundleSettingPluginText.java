package org.touchhome.bundle.api.setting;

public interface BundleSettingPluginText extends BundleSettingPlugin<String> {
    @Override
    default Class<String> getType() {
        return String.class;
    }

    @Override
    default SettingType getSettingType() {
        return SettingType.Text;
    }
}
