package org.touchhome.bundle.api.converter;

import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.touchhome.bundle.api.EntityContext;

import javax.persistence.AttributeConverter;
import javax.persistence.Converter;
import java.util.Map;

@Log4j2
@Converter
@RequiredArgsConstructor
public class JsonBeanConverter implements AttributeConverter<Object, String> {

    private final EntityContext entityContext;

    @Override
    public String convertToDatabaseColumn(Object provider) {
        if (provider == null) {
            return null;
        }
        return entityContext.getBeansOfTypeWithBeanName(provider.getClass()).entrySet().stream()
                .filter(e -> e.getValue().getClass().equals(provider.getClass()))
                .map(Map.Entry::getKey).findAny().orElse(null);
    }

    @Override
    public Object convertToEntityAttribute(String name) {
        if (name == null) {
            return null;
        }
        try {
            return entityContext.getBean(name, Object.class);
        } catch (Exception ex) {
            log.warn("Unable to find bean with name: {}", name);
            return null;
        }
    }
}
