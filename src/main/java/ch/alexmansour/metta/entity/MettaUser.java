package ch.alexmansour.metta.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import org.springframework.lang.NonNull;

import java.time.LocalDateTime;

@Entity
public class MettaUser {
    @Id
    private Long userId;
    private String firstName;
    private String userName;
    private LocalDateTime dateTimeJoined;
    private Boolean hasBeenReminded;

    public MettaUser() {

    }

    public MettaUser(@NonNull Long id, @NonNull String firstName, String userName, LocalDateTime dateTimeJoined, Boolean hasBeenReminded) {
        this.userId = id;
        this.firstName = firstName;
        this.userName = userName;
        this.dateTimeJoined = dateTimeJoined;
        this.hasBeenReminded = hasBeenReminded;
    }

    public Long getUserId() {
        return userId;
    }

    public String getFirstName() {
        return firstName;
    }

    public String getUserName() {
        return userName;
    }

    public LocalDateTime getDateTimeJoined() {
        return dateTimeJoined;
    }

    public Boolean hasBeenReminded() {
        return hasBeenReminded;
    }

    public void setHasBeenReminded(Boolean hasBeenReminded) {
        this.hasBeenReminded = hasBeenReminded;
    }
}
