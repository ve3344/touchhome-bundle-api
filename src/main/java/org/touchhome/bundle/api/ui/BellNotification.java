package org.touchhome.bundle.api.ui;

import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;
import org.touchhome.bundle.api.util.NotificationLevel;

import javax.validation.constraints.NotNull;

@Getter
@Accessors(chain = true)
public class BellNotification extends BaseNotificationModel<BellNotification> {

    @Setter
    private NotificationLevel level = NotificationLevel.info;

    public BellNotification(String entityID) {
        super(entityID);
    }

    public static BellNotification danger(String entityID) {
        return new BellNotification(entityID).setLevel(NotificationLevel.error);
    }

    public static BellNotification warn(String entityID) {
        return new BellNotification(entityID).setLevel(NotificationLevel.warning);
    }

    public static BellNotification info(String entityID) {
        return new BellNotification(entityID).setLevel(NotificationLevel.info);
    }

    public static BellNotification success(String entityID) {
        return new BellNotification(entityID).setLevel(NotificationLevel.success);
    }

    @Override
    public int compareTo(@NotNull BellNotification other) {
        int i = this.level.name().compareTo(other.level.name());
        return i == 0 ? super.compareTo(other) : i;
    }

    @Override
    public BellNotification setTitle(String title) {
        return (BellNotification) super.setTitle(title);
    }

    @Override
    public BellNotification setValue(Object value) {
        return (BellNotification) super.setValue(value);
    }
}
