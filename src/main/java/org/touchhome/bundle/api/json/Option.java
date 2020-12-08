package org.touchhome.bundle.api.json;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fazecast.jSerialComm.SerialPort;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.SneakyThrows;
import lombok.experimental.Accessors;
import org.apache.commons.lang3.StringUtils;
import org.json.JSONObject;
import org.touchhome.bundle.api.model.BaseEntity;
import org.touchhome.bundle.api.model.HasDescription;

import javax.validation.constraints.NotNull;
import java.util.*;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;

@Getter
@Accessors(chain = true)
@NoArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class Option implements Comparable<Option> {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private String key;
    private String title;

    @Setter
    private String imageRef;

    private JSONObject json = new JSONObject();

    private Collection<Option> children;

    public Option(Object key, Object title) {
        this.key = String.valueOf(key);
        this.title = String.valueOf(title);
    }

    public static Option key(String key) {
        Option option = new Option();
        option.key = key;
        return option;
    }

    public void addChild(Option child) {
        if (this.children == null) {
            children = new ArrayList<>();
        }
        children.add(child);
    }

    public static Option of(String key, String title) {
        return new Option(key, title);
    }

    public static List<Option> listWithEmpty(Class<? extends KeyValueEnum> enumClass) {
        return withEmpty(list(enumClass));
    }

    public static List<Option> enumWithEmpty(Class<? extends Enum> enumClass) {
        return withEmpty(enumList(enumClass));
    }

    public static List<Option> withEmpty(List<Option> list) {
        list.add(Option.of("", "no_value"));
        return list;
    }

    public static List<Option> listOfPorts(boolean withEmpty) {
        List<Option> options = Arrays.stream(SerialPort.getCommPorts()).map(p ->
                new Option(p.getSystemPortName(), p.getSystemPortName() + "/" + p.getDescriptivePortName())).collect(Collectors.toList());
        return withEmpty ? withEmpty(options) : options;
    }

    public static List<Option> enumList(Class<? extends Enum> enumClass) {
        return Stream.of(enumClass.getEnumConstants()).map(n -> Option.of(n.name(), n.toString())).collect(Collectors.toList());
    }

    public static List<Option> list(Class<? extends KeyValueEnum> enumClass) {
        return Stream.of(enumClass.getEnumConstants()).map(n -> Option.of(n.getKey(), n.getValue())).collect(Collectors.toList());
    }

    public static List<Option> list(String... values) {
        return Stream.of(values).map(v -> Option.of(v, v)).collect(Collectors.toList());
    }

    public static List<Option> list(Option... options) {
        return Stream.of(options).collect(Collectors.toList());
    }

    public static List<Option> range(int min, int max) {
        return IntStream.range(min, max).mapToObj(value -> Option.key(String.valueOf(value))).collect(Collectors.toList());
    }

    public static <T> List<Option> list(Collection<T> list, Function<T, String> keyFn, Function<T, String> valueFn) {
        return list.stream().map(e -> Option.of(keyFn.apply(e), valueFn.apply(e))).collect(Collectors.toList());
    }

    public static <T extends BaseEntity> List<Option> list(Collection<T> list) {
        return list.stream().map(e -> Option.of(e.getEntityID(), StringUtils.defaultIfEmpty(e.getName(), e.getTitle()))).collect(Collectors.toList());
    }

    public static List<Option> simpleNamelist(Collection list) {
        List<Option> options = new ArrayList<>();
        for (Object o : list) {
            Option option = Option.key(o.getClass().getSimpleName());
            if (o instanceof HasDescription) {
                option.json.put("description", ((HasDescription) o).getDescription());
            }
            options.add(option);
        }
        return options;
    }

    public Option json(Consumer<JSONObject> consumer) {
        consumer.accept(json);
        return this;
    }

    @Override
    @SneakyThrows
    public String toString() {
        return OBJECT_MAPPER.writeValueAsString(this);
    }

    @Override
    public int compareTo(@NotNull Option other) {
        return this.title.compareTo(other.title);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof Option)) {
            return false;
        }
        Option option = (Option) o;
        return Objects.equals(key, option.key);
    }

    @Override
    public int hashCode() {
        return Objects.hash(key);
    }
}
