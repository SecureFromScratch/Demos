package com.example.demo.repository;

import com.example.demo.model.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface UserRepository extends JpaRepository<User, Long> {
    
    /**
     * Find a user by their username
     */
    Optional<User> findByUsername(String username);
    
    /**
     * Count total number of users in the database
     * Used to determine if this is the first user registration
     */
    long count();
}