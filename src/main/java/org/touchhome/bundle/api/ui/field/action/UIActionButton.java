package org.touchhome.bundle.api.ui.field.action;

import org.touchhome.bundle.api.ui.action.UIActionHandler;

import java.lang.annotation.*;

@Target({ElementType.FIELD, ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
@Repeatable(UIActionButtons.class)
public @interface UIActionButton {

    String name();

    String icon();

    String color() default "inherit";

    String style() default "";

    Class<? extends UIActionHandler> actionHandler();

    UIActionInput[] inputs() default {};
}
