package com.example.demo.controller;

import com.example.demo.service.UserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

@Controller
public class RegistrationController {

    @Autowired
    private UserService userService;

    /**
     * Show the first-time setup page (only accessible when no users exist)
     * If users already exist, redirect to login page
     */
    @GetMapping("/setup")
    public String showSetupPage(Model model) {
        // If users already exist, don't allow setup
        if (!userService.isFirstUser()) {
            return "redirect:/login";
        }
        
        model.addAttribute("isSetup", true);
        return "register";
    }

    /**
     * Handle first admin account creation
     * Creates the first user with ADMIN privileges
     */
    @PostMapping("/setup")
    public String setupFirstAdmin(@RequestParam String username, 
                                   @RequestParam String password,
                                   Model model) {
        try {
            // Create the first admin user
            userService.registerFirstAdmin(username, password);
            
            // Redirect to login with success message
            model.addAttribute("message", "Admin account created successfully! Please login.");
            return "redirect:/login?setup=success";
            
        } catch (IllegalStateException e) {
            // Someone already created the first user
            model.addAttribute("error", "Setup already completed. Please use login.");
            return "redirect:/login";
            
        } catch (Exception e) {
            // Other errors (validation, database, etc.)
            model.addAttribute("error", e.getMessage());
            model.addAttribute("isSetup", true);
            return "register";
        }
    }

    /**
     * Show the regular registration page
     * If no users exist yet, redirect to setup page
     */
    @GetMapping("/register")
    public String showRegistrationPage(Model model) {
        // If this is the first user, redirect to setup instead
        if (userService.isFirstUser()) {
            return "redirect:/setup";
        }
        
        model.addAttribute("isSetup", false);
        return "register";
    }

    /**
     * Handle regular user registration
     * Creates a new user with USER role only
     */
    @PostMapping("/register")
    public String registerUser(@RequestParam String username, 
                                @RequestParam String password,
                                Model model) {
        try {
            // Create regular user (not admin)
            userService.registerUser(username, password, false);
            
            // Redirect to login with success message
            model.addAttribute("message", "Registration successful! Please login.");
            return "redirect:/login?registered=true";
            
        } catch (IllegalArgumentException e) {
            // Username already exists
            model.addAttribute("error", e.getMessage());
            model.addAttribute("isSetup", false);
            return "register";
            
        } catch (Exception e) {
            // Other errors
            model.addAttribute("error", "Registration failed. Please try again.");
            model.addAttribute("isSetup", false);
            return "register";
        }
    }
}