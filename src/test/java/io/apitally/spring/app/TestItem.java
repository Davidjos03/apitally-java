package io.apitally.spring.app;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;

public record TestItem(@Min(1) Integer id, @Size(min = 2, max = 10) String name) {
}
