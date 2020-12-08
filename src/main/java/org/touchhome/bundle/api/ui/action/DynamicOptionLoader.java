package org.touchhome.bundle.api.ui.action;

import org.touchhome.bundle.api.EntityContext;
import org.touchhome.bundle.api.model.OptionModel;
import org.touchhome.bundle.api.entity.BaseEntity;

import java.util.Collection;
import java.util.List;

/**
 * Uses for load option.
 */
public interface DynamicOptionLoader<T> {

    Collection<OptionModel> loadOptions(T parameter, BaseEntity baseEntity, EntityContext entityContext);
}
