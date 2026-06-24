package dev.taika.api;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class AgentTypeTest {

    @Test
    void shouldHaveCorrectEnumConstants() {
        // Verify that all expected enum constants exist.
        assertNotNull(AgentType.INTERFACE_DESIGNER);
        assertNotNull(AgentType.CODER);
        assertNotNull(AgentType.TESTER);
        assertNotNull(AgentType.VALIDATOR);

        // Verify that valueOf works as expected.
        assertEquals(AgentType.CODER, AgentType.valueOf("CODER"));
    }
}