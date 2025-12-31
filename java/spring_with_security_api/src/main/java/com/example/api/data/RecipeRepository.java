package com.example.api.data;

import com.example.api.models.Recipe;
import com.example.api.models.Recipe.RecipeStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface RecipeRepository extends JpaRepository<Recipe, Long> {
    List<Recipe> findByStatus(RecipeStatus status);
    List<Recipe> findByNameContainingIgnoreCase(String name);
}