package com.lumina.converter;

import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

@Component
public class ProtocolConverterRegistry {

    private final Map<String, ProtocolConverter> converters;

    public ProtocolConverterRegistry(List<ProtocolConverter> converterList) {
        this.converters = converterList.stream()
                .collect(Collectors.toMap(
                        c -> key(c.sourceType(), c.targetType()),
                        Function.identity()
                ));
    }

    public Optional<ProtocolConverter> getConverter(ProtocolType source, ProtocolType target) {
        if (source == target) return Optional.empty();
        return Optional.ofNullable(converters.get(key(source, target)));
    }

    public boolean needsConversion(ProtocolType source, ProtocolType target) {
        return source != target && converters.containsKey(key(source, target));
    }

    private static String key(ProtocolType source, ProtocolType target) {
        return source.name() + "_TO_" + target.name();
    }
}
