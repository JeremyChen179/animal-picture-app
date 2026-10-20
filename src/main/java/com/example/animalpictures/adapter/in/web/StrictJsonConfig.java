package com.example.animalpictures.adapter.in.web;

import org.springframework.boot.jackson.autoconfigure.JsonMapperBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import tools.jackson.databind.cfg.CoercionAction;
import tools.jackson.databind.cfg.CoercionInputShape;
import tools.jackson.databind.type.LogicalType;

/**
 * Strict JSON numbers for request bodies: by default Jackson silently turns {@code 1.5} into 1 and
 * {@code "3"} into 3. Integer fields (like {@code count}) now only accept JSON integers; anything
 * else is a 400.
 */
@Configuration
class StrictJsonConfig {

    @Bean
    JsonMapperBuilderCustomizer strictIntegers() {
        return builder -> builder.withCoercionConfig(LogicalType.Integer, config -> config
                .setCoercion(CoercionInputShape.Float, CoercionAction.Fail)
                .setCoercion(CoercionInputShape.String, CoercionAction.Fail));
    }
}
