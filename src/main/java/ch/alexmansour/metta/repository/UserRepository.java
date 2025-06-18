package ch.alexmansour.metta.repository;

import ch.alexmansour.metta.entity.MettaUser;
import org.springframework.data.repository.CrudRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface UserRepository extends CrudRepository<MettaUser, Long> {
}
