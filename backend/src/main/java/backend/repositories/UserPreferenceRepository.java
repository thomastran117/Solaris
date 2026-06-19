package backend.repositories;

import backend.models.core.UserPreference;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface UserPreferenceRepository extends JpaRepository<UserPreference, UUID> {

    @Query("SELECT p FROM UserPreference p WHERE p.birthDate IS NOT NULL AND MONTH(p.birthDate) = :month AND DAY(p.birthDate) = :day")
    List<UserPreference> findByBirthDateMonthAndDay(@Param("month") int month, @Param("day") int day);
}
