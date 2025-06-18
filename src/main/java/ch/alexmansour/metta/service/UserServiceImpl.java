package ch.alexmansour.metta.service;

import ch.alexmansour.metta.entity.MettaUser;
import ch.alexmansour.metta.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.Optional;

@Service
public class UserServiceImpl implements UserService{

    @Autowired
    private UserRepository userRepository;

    @Override
    public void saveUser(MettaUser mettaUser) {
        userRepository.save(mettaUser);
    }

    @Override
    public Optional<MettaUser> fetchUser(Long id) {
        return userRepository.findById(id);
    }

    @Override
    public void deleteUser(Long id) {
        if (userRepository.findById(id).isPresent()) {
            userRepository.deleteById(id);
        }
    }

    @Override
    public Iterable<MettaUser> fetchAll() {
        return userRepository.findAll();
    }
}
