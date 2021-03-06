package org.touchhome.bundle.api;

import org.touchhome.bundle.api.entity.BaseEntity;
import org.touchhome.bundle.api.util.FlowMap;

import java.util.function.Consumer;

public interface EntityContextEvent {

    void removeEvents(String... keys);

    /**
     * Listen for event with key
     */
    void setListener(String key, Consumer<Object> listener);

    /**
     * Fire event with key
     */
    default void fireEvent(String key) {
        fireEvent(key, null);
    }

    /**
     * Add event key with same name
     */
    default String addEvent(String key) {
        return this.addEvent(key, key);
    }

    /**
     * Fire event with key and value
     */
    void fireEvent(String key, Object value);

    // return key
    String addEvent(String key, String name);

    default String addEvent(String key, String name, FlowMap nameParams) {
        return addEvent(key, Lang.getServerMessage(name, nameParams));
    }

    /**
     * Add event and fire it immediately
     */
    String addEventAndFire(String key, String name, Object value);

    /**
     * Add event and fire it immediately
     */
    default String addEventAndFire(String key, String name, FlowMap nameParams, Object value) {
        return addEventAndFire(key, Lang.getServerMessage(name, nameParams), value);
    }

    /**
     * Add event and fire it immediately
     */
    default String addEventAndFire(String key, String name) {
        return addEventAndFire(key, name, null);
    }

    <T extends BaseEntity> void addEntityUpdateListener(String entityID, String key, Consumer<T> listener);

    <T extends BaseEntity> void addEntityUpdateListener(String entityID, String key, EntityContext.EntityUpdateListener<T> listener);

    /**
     * Listen any changes fot BaseEntity of concrete type.
     *
     * @param entityClass type to listen
     * @param listener    handler invoke when entity update
     */
    <T extends BaseEntity> void addEntityUpdateListener(Class<T> entityClass, String key, Consumer<T> listener);

    /**
     * Listen any changes fot BaseEntity of concrete type.
     *
     * @param entityClass type to listen
     * @param listener    handler invoke when entity update. OldValue/NewValue
     */
    <T extends BaseEntity> void addEntityUpdateListener(Class<T> entityClass, String key, EntityContext.EntityUpdateListener<T> listener);

    <T extends BaseEntity> void addEntityRemovedListener(Class<T> entityClass, String key, Consumer<T> listener);

    <T extends BaseEntity> void addEntityRemovedListener(String entityID, String key, Consumer<T> listener);

    void removeEntityUpdateListener(String entityID, String key);

    void removeEntityRemoveListener(String entityID, String key);
}
