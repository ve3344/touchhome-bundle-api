package org.touchhome.bundle.api;

import lombok.SneakyThrows;
import org.touchhome.bundle.api.setting.SettingPluginStatus;
import org.touchhome.bundle.api.ui.BellNotification;
import org.touchhome.bundle.api.util.TouchHomeUtils;

import javax.validation.constraints.NotNull;
import java.net.URL;
import java.util.Set;

public interface BundleEntryPoint extends Comparable<BundleEntryPoint> {
    String BUNDLE_PREFIX = "org.touchhome.bundle.";

    static String getBundleName(Class clazz) {
        String name = clazz.getName();
        if (name.startsWith(BUNDLE_PREFIX)) {
            return name.substring(BUNDLE_PREFIX.length(), name.indexOf('.', BUNDLE_PREFIX.length()));
        }
        return null;
    }

    void init();

    default void destroy() {

    }

    default String getSettingDescription() {
        return null;
    }

    default String getBundleImage() {
        return "image.png";
    }

    @SneakyThrows
    default URL getBundleImageURL() {
        return getResource(getBundleImage());
    }

    // a-z or at most one '-' and nothing else
    default String getBundleId() {
        return BundleEntryPoint.getBundleName(getClass());
    }

    int order();

    default BundleImageColorIndex getBundleImageColorIndex() {
        return BundleImageColorIndex.ZERO;
    }

    @Override
    default int compareTo(@NotNull BundleEntryPoint o) {
        return Integer.compare(this.order(), o.order());
    }

    /**
     * Notifications that visible in ui header
     */
    default Set<BellNotification> getBellNotifications() {
        return null;
    }

    /**
     * Get main bundle status setting. Will be shown on header ui
     */
    default Class<? extends SettingPluginStatus> getBundleStatusSetting() {
        return null;
    }

    @SneakyThrows
    default URL getResource(String resource) {
        return TouchHomeUtils.getResource(getBundleId(), resource);
    }

    enum BundleImageColorIndex {
        ZERO, ONE, TWO, THREE, FOUR
    }
}
