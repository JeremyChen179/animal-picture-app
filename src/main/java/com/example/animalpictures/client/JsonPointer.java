package com.example.animalpictures.client;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/** Minimal RFC 6901 JSON Pointer lookup over a parsed JSON tree of {@link Map}s and {@link List}s. */
final class JsonPointer {

    private JsonPointer() {
    }

    static Optional<Object> find(Object root, String pointer) {
        if (pointer.isEmpty()) {
            return Optional.ofNullable(root);
        }
        if (!pointer.startsWith("/")) {
            throw new IllegalArgumentException("JSON Pointer must start with '/': " + pointer);
        }
        Object current = root;
        for (String rawToken : pointer.substring(1).split("/", -1)) {
            String token = rawToken.replace("~1", "/").replace("~0", "~");
            if (current instanceof Map<?, ?> map) {
                current = map.get(token);
            } else if (current instanceof List<?> list && token.matches("\\d+")) {
                int index = Integer.parseInt(token);
                current = index < list.size() ? list.get(index) : null;
            } else {
                return Optional.empty();
            }
            if (current == null) {
                return Optional.empty();
            }
        }
        return Optional.of(current);
    }
}
