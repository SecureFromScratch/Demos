package com.example.api.data;

import com.example.api.models.AppUser;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface UserRepository extends JpaRepository<AppUser, Long> {
    Optional<AppUser> findByUserName(String userName);

    boolean existsByUserNameIgnoreCase(String userName);

}
