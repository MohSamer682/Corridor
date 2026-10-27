package com.corridor.office;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.modulith.core.ApplicationModules;

class ModularityTests {

    private final ApplicationModules modules = ApplicationModules.of(CorridorApplication.class);

    @Test
    void shouldKeepModuleBoundaries() {
        modules.verify();
    }

    @Test
    void shouldTreatCommonAsSharedModule() {
        assertThat(modules.getModuleByName("common")).isPresent();
    }
}
