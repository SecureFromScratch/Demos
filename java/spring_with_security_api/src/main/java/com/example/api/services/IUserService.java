package com.example.api.services;

import com.example.api.models.AppUser;
import java.util.Optional;

public interface IUserService {
    boolean isFirstUser();
    AppUser registerUser(String userName, String rawPassword, boolean isAdmin);
    AppUser registerFirstAdmin(String userName, String rawPassword);
    Optional<AppUser> findByUserName(String userName);
    boolean verifyPassword(AppUser user, String rawPassword);
}