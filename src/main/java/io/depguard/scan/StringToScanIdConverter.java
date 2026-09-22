package io.depguard.scan;

import io.depguard.shared.ScanId;
import org.springframework.core.convert.converter.Converter;
import org.springframework.stereotype.Component;

/**
 * Binds the {@code {id}} path variable of the scan endpoints to a {@link ScanId}.
 */
@Component
class StringToScanIdConverter implements Converter<String, ScanId> {

    @Override
    public ScanId convert(String source) {
        return ScanId.of(source);
    }
}
