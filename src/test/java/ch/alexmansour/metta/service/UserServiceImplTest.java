package ch.alexmansour.metta.service;

import ch.alexmansour.metta.entity.MettaUser;
import ch.alexmansour.metta.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class UserServiceImplTest {

    private UserRepository userRepository;
    private UserServiceImpl service;

    @BeforeEach
    void setUp() throws Exception {
        userRepository = mock(UserRepository.class);
        service = new UserServiceImpl();

        // Inject the mocked repository into the service (@Autowired field) without changing production code
        Field f = UserServiceImpl.class.getDeclaredField("userRepository");
        f.setAccessible(true);
        f.set(service, userRepository);
    }

    @Test
    void saveUser_delegatesToRepository() {
        MettaUser u = new MettaUser(1L, "Alice", "alice", LocalDateTime.now(), false);

        service.saveUser(u);

        verify(userRepository, times(1)).save(u);
    }

    @Test
    void fetchUser_returnsRepositoryResult() {
        MettaUser u = new MettaUser(2L, "Bob", null, LocalDateTime.now(), null);
        when(userRepository.findById(2L)).thenReturn(Optional.of(u));

        Optional<MettaUser> result = service.fetchUser(2L);

        assertTrue(result.isPresent());
        assertEquals(u, result.get());
        verify(userRepository, times(1)).findById(2L);
    }

    @Test
    void fetchAll_returnsAllFromRepository() {
        MettaUser u1 = new MettaUser(10L, "Cora", null, LocalDateTime.now(), false);
        MettaUser u2 = new MettaUser(11L, "Dan", "dan", LocalDateTime.now(), true);
        when(userRepository.findAll()).thenReturn(Arrays.asList(u1, u2));

        Iterable<MettaUser> all = service.fetchAll();

        int count = 0;
        MettaUser[] received = new MettaUser[2];
        for (MettaUser u : all) {
            received[count++] = u;
        }
        assertEquals(2, count);
        assertEquals(u1, received[0]);
        assertEquals(u2, received[1]);

        verify(userRepository, times(1)).findAll();
    }

    @Test
    void deleteUser_whenPresent_deletesById() {
        when(userRepository.findById(5L)).thenReturn(Optional.of(new MettaUser(5L, "Eve", null, LocalDateTime.now(), false)));

        service.deleteUser(5L);

        verify(userRepository, times(1)).findById(5L);
        verify(userRepository, times(1)).deleteById(5L);
    }

    @Test
    void deleteUser_whenAbsent_doesNothing() {
        when(userRepository.findById(6L)).thenReturn(Optional.empty());

        service.deleteUser(6L);

        verify(userRepository, times(1)).findById(6L);
        verify(userRepository, never()).deleteById(anyLong());
    }
}
