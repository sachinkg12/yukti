package io.yukti.engine.optimizer;

import io.yukti.core.api.Optimizer;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class OptimizerRegistryTest {

    @Test
    void select_returnsMilpV1ByDefault() {
        OptimizerRegistry registry = new OptimizerRegistry();
        Optimizer selected = registry.select();
        assertEquals("milp-v1", selected.id());
    }

    @Test
    void get_returnsRegisteredOptimizer() {
        OptimizerRegistry registry = new OptimizerRegistry();
        assertEquals("greedy-v1", registry.get("greedy-v1").id());
        assertEquals("cap-aware-greedy-v1", registry.get("cap-aware-greedy-v1").id());
    }

    @Test
    void get_throwsForUnknownId() {
        OptimizerRegistry registry = new OptimizerRegistry();
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () -> registry.get("unknown-optimizer"));
        assertTrue(ex.getMessage().contains("Unknown optimizer id"));
        assertTrue(ex.getMessage().contains("unknown-optimizer"));
    }

    @Test
    void getOrDefault_returnsDefaultForUnknownId() {
        OptimizerRegistry registry = new OptimizerRegistry();
        Optimizer fallback = registry.getOrDefault("unknown-optimizer");
        assertEquals("milp-v1", fallback.id());
    }

    @Test
    void select_respectsSystemProperty() {
        String prev = System.getProperty("yukti.optimizer");
        try {
            System.setProperty("yukti.optimizer", "cap-aware-greedy-v1");
            OptimizerRegistry registry = new OptimizerRegistry();
            Optimizer selected = registry.select();
            assertEquals("cap-aware-greedy-v1", selected.id());
        } finally {
            if (prev != null) System.setProperty("yukti.optimizer", prev);
            else System.clearProperty("yukti.optimizer");
        }
    }

    @Test
    void availableIds_includesRegisteredOptimizers() {
        OptimizerRegistry registry = new OptimizerRegistry();
        var ids = registry.availableIds();
        assertTrue(ids.contains("milp-v1"));
        assertTrue(ids.contains("greedy-v1"));
        assertTrue(ids.contains("cap-aware-greedy-v1"));
    }
}
