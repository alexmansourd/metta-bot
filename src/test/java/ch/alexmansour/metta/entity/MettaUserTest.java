package ch.alexmansour.metta.entity;

import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;

class MettaUserTest {

    @Test
    void constructorAndGetters_shouldInitializeFields() {
        Long id = 123L;
        String firstName = "Alice";
        String userName = "alice123";
        LocalDateTime joined = LocalDateTime.now().minusDays(1).withNano(0);
        Boolean reminded = Boolean.FALSE;

        MettaUser u = new MettaUser(id, firstName, userName, joined, reminded);

        assertEquals(id, u.getUserId());
        assertEquals(firstName, u.getFirstName());
        assertEquals(userName, u.getUserName());
        assertEquals(joined, u.getDateTimeJoined());
        assertEquals(reminded, u.hasBeenReminded());
    }

    @Test
    void hasBeenReminded_setterShouldToggleValue() {
        MettaUser u = new MettaUser(1L, "Bob", "bob", LocalDateTime.now().withNano(0), Boolean.FALSE);
        assertEquals(Boolean.FALSE, u.hasBeenReminded());

        u.setHasBeenReminded(Boolean.TRUE);
        assertEquals(Boolean.TRUE, u.hasBeenReminded());

        u.setHasBeenReminded(Boolean.FALSE);
        assertEquals(Boolean.FALSE, u.hasBeenReminded());
    }

    @Test
    void constructor_allowsNullOptionalFields() {
        LocalDateTime joined = LocalDateTime.now().withNano(0);
        MettaUser u = new MettaUser(2L, "Cara", null, joined, null);

        assertEquals(2L, u.getUserId());
        assertEquals("Cara", u.getFirstName());
        assertNull(u.getUserName());
        assertEquals(joined, u.getDateTimeJoined());
        assertNull(u.hasBeenReminded());
    }

    @Test
    void constructor_creation() {
        MettaUser mettaUser = new MettaUser();
        assertNotNull(mettaUser);
    }
}
