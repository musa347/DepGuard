package io.depguard.project;

import org.springframework.core.convert.converter.Converter;
import org.springframework.stereotype.Component;

/**
 * Binds the {@code {id}} path variable of the project endpoints to a {@link ProjectId}.
 */
@Component
class StringToProjectIdConverter implements Converter<String, ProjectId> {

    @Override
    public ProjectId convert(String source) {
        return ProjectId.of(source);
    }
}
