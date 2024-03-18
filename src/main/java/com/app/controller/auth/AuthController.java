package com.app.controller.auth;

import com.app.entity.User;
import com.app.repository.UserRepository;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.mindrot.jbcrypt.BCrypt;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.session.Session;
import org.springframework.session.SessionRepository;
import org.springframework.web.bind.annotation.*;


@RestController
@RequestMapping("/api")
public class AuthController {
    private final UserRepository userRepository;
    private final SessionRepository sessionRepository;

    @Autowired
    public AuthController(UserRepository userRepository, SessionRepository sessionRepository) {
        this.userRepository = userRepository;
        this.sessionRepository = sessionRepository;
    }

    @PostMapping(value = "/register", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<String> registerUser(@RequestBody User user) {
        try {
            String username = user.getUsername();
            String email = user.getEmail();
            String password = user.getPassword();
            String firstName = user.getFirstName();
            String lastName = user.getLastName();

            if (userRepository.existsByUsername(username)) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("Username already exists");
            }
            if (userRepository.existsByEmail(email)) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("Email already exists");
            }

            String hashedPassword = BCrypt.hashpw(password, BCrypt.gensalt());

            User newUser = new User(username, email, hashedPassword, firstName, lastName);

            userRepository.save(newUser);

            return ResponseEntity.status(HttpStatus.CREATED).body("User registered successfully");
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Register failed: " + e.getMessage());
        }
    }

    @PostMapping(value = "/login", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<String> loginUser(@RequestBody User user, HttpServletResponse response) {
        try {
            String username = user.getUsername();
            String password = user.getPassword();

            User fetchedUser = userRepository.findByUsername(username);

            if (fetchedUser != null && BCrypt.checkpw(password, fetchedUser.getPassword())) {
                Session session = sessionRepository.createSession();

                sessionRepository.save(session);

                Cookie sessionCookie = new Cookie("SESSION", session.getId());
                sessionCookie.setMaxAge(60 * 60);
                sessionCookie.setPath("/");
                sessionCookie.setHttpOnly(true);
                response.addCookie(sessionCookie);

                return ResponseEntity.ok("Login successful");
            } else {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Invalid credentials");
            }
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Login failed: " + e.getMessage());
        }
    }

    @GetMapping(value = "/logout")
    public ResponseEntity<String> logoutUser(@CookieValue(name = "SESSION", required = false) String sessionId, HttpServletResponse response) {
        try {
            if (sessionId != null) {
                sessionRepository.deleteById(sessionId);

                Cookie sessionCookie = new Cookie("SESSION", null);
                sessionCookie.setMaxAge(0);
                sessionCookie.setPath("/");
                sessionCookie.setHttpOnly(true);
                response.addCookie(sessionCookie);
            }

            return ResponseEntity.ok("Logout successful");
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Login failed: " + e.getMessage());
        }
    }
}