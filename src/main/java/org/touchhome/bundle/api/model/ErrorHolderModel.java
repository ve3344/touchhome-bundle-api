package org.touchhome.bundle.api.model;

import lombok.Getter;
import org.touchhome.bundle.api.util.TouchHomeUtils;

@Getter
public class ErrorHolderModel {
    private String title;
    private String message;
    private String cause;
    private String errorType;

    public ErrorHolderModel(String title, String message, Exception ex) {
        this.title = title;
        this.message = message;
        this.cause = TouchHomeUtils.getErrorMessage(ex);
        this.errorType = ex.getClass().getSimpleName();
    }
}
