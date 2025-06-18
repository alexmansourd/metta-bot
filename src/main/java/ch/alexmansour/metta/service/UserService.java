package ch.alexmansour.metta.service;

import ch.alexmansour.metta.entity.MettaUser;

import java.util.Optional;

public interface UserService {
    void saveUser(MettaUser department);
    Optional<MettaUser> fetchUser(Long id);
    Iterable<MettaUser> fetchAll();
    void deleteUser(Long id);
}
