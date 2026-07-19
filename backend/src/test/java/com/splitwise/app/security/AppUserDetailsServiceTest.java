package com.splitwise.app.security;

import com.splitwise.app.entity.User;
import com.splitwise.app.entity.User.SubscriptionTier;
import com.splitwise.app.entity.User.Theme;
import com.splitwise.app.enums.AuthProvider;
import com.splitwise.app.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UsernameNotFoundException;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AppUserDetailsServiceTest {

    @Mock
    private UserRepository userRepository;

    private AppUserDetailsService userDetailsService;

    private UUID userId;

    @BeforeEach
    void setUp() {
        userDetailsService = new AppUserDetailsService(userRepository);
        userId = UUID.randomUUID();
    }

    private User user() {
        return User.builder()
                .id(userId)
                .name("John Doe")
                .email("john@example.com")
                .passwordHash("encoded-password")
                .provider(AuthProvider.LOCAL)
                .preferredCurrency("INR")
                .theme(Theme.SYSTEM)
                .subscriptionTier(SubscriptionTier.FREE)
                .deleted(false)
                .build();
    }

    @Test
    @DisplayName("Load user by id")
    void loadUserById_shouldReturnUserDetails() {

        User user = user();

        when(userRepository.findById(userId))
                .thenReturn(Optional.of(user));

        UserDetails details = userDetailsService.loadUserById(userId);

        assertNotNull(details);
        assertEquals(userId.toString(), details.getUsername());
        assertEquals("encoded-password", details.getPassword());

        assertTrue(
                details.getAuthorities()
                        .stream()
                        .anyMatch(a -> a.getAuthority().equals("ROLE_USER"))
        );

        verify(userRepository).findById(userId);
    }

    @Test
    @DisplayName("Load user by email")
    void loadUserByEmail_shouldReturnUserDetails() {

        User user = user();

        when(userRepository.findByEmailAndDeletedFalse(user.getEmail()))
                .thenReturn(Optional.of(user));

        UserDetails details
                = userDetailsService.loadUserByEmail(user.getEmail());

        assertNotNull(details);
        assertEquals(userId.toString(), details.getUsername());
        assertEquals("encoded-password", details.getPassword());

        verify(userRepository)
                .findByEmailAndDeletedFalse(user.getEmail());
    }

    @Test
    @DisplayName("Load user by id should throw when user not found")
    void loadUserById_shouldThrowWhenUserNotFound() {

        when(userRepository.findById(userId))
                .thenReturn(Optional.empty());

        assertThrows(
                UsernameNotFoundException.class,
                () -> userDetailsService.loadUserById(userId)
        );
    }

    @Test
    @DisplayName("Load user by email should throw when user not found")
    void loadUserByEmail_shouldThrowWhenUserNotFound() {

        when(userRepository.findByEmailAndDeletedFalse("john@example.com"))
                .thenReturn(Optional.empty());

        assertThrows(
                UsernameNotFoundException.class,
                () -> userDetailsService.loadUserByEmail("john@example.com")
        );
    }

    @Test
    @DisplayName("Load user by id should throw when user is deleted")
    void loadUserById_shouldThrowWhenUserDeleted() {

        User user = user();
        user.setDeleted(true);

        when(userRepository.findById(userId))
                .thenReturn(Optional.of(user));

        assertThrows(
                UsernameNotFoundException.class,
                () -> userDetailsService.loadUserById(userId)
        );
    }

    @Test
    @DisplayName("Password should default to empty string when password hash is null")
    void loadUserById_shouldReturnEmptyPasswordWhenPasswordHashIsNull() {

        User user = user();
        user.setPasswordHash(null);

        when(userRepository.findById(userId))
                .thenReturn(Optional.of(user));

        UserDetails details = userDetailsService.loadUserById(userId);

        assertEquals("", details.getPassword());
    }
}
