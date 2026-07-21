package com.splitwise.app.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.splitwise.app.integration.config.IntegrationTestConfig;
import com.splitwise.app.repository.CategoryRepository;
import com.splitwise.app.repository.ExpenseRepository;
import com.splitwise.app.repository.FriendRepository;
import com.splitwise.app.repository.GroupMemberRepository;
import com.splitwise.app.repository.GroupRepository;
import com.splitwise.app.repository.PasswordResetTokenRepository;
import com.splitwise.app.repository.PendingSignupRepository;
import com.splitwise.app.repository.RefreshTokenRepository;
import com.splitwise.app.repository.UserRepository;
import com.splitwise.app.security.JwtService;

import jakarta.mail.BodyPart;
import jakarta.mail.Multipart;
import jakarta.mail.internet.MimeMessage;

import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.containers.PostgreSQLContainer;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.splitwise.app.entity.User;
import com.splitwise.app.entity.Friend;
import com.splitwise.app.enums.AuthProvider;
import com.splitwise.app.entity.Category;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
@Import(IntegrationTestConfig.class)
public abstract class BaseIntegrationTest {

    // NOTE: no @Testcontainers / @Container here on purpose - see chat
    // history for why (shared static container gets stopped between test
    // classes under the standard annotation-driven lifecycle). Starting it
    // once here and letting Ryuk reap it on JVM exit avoids that.
    protected static final PostgreSQLContainer<?> postgres =
            new PostgreSQLContainer<>("postgres:16-alpine")
                    .withDatabaseName("splitwise_test")
                    .withUsername("test")
                    .withPassword("test");

    static {
        postgres.start();
    }

    @DynamicPropertySource
    static void configure(DynamicPropertyRegistry registry) {

        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.datasource.driver-class-name", () -> "org.postgresql.Driver");
    }

    @Autowired
    protected MockMvc mockMvc;

    @Autowired
    protected ObjectMapper objectMapper;

    @Autowired
    protected UserRepository userRepository;

    @Autowired
    protected PendingSignupRepository pendingSignupRepository;

    @Autowired
    protected RefreshTokenRepository refreshTokenRepository;

    @Autowired
    protected PasswordResetTokenRepository passwordResetTokenRepository;

    @Autowired
    protected IntegrationTestConfig integrationTestConfig;

    @Autowired
    protected PasswordEncoder passwordEncoder;

    @Autowired
    protected JwtService jwtService;

    @Autowired
    protected GroupRepository groupRepository;

    @Autowired
    protected GroupMemberRepository groupMemberRepository;

    @Autowired
    protected FriendRepository friendRepository;

    @Autowired
    protected ExpenseRepository expenseRepository;

    @Autowired
    protected CategoryRepository categoryRepository;

    @BeforeEach
    void setup() {

        expenseRepository.deleteAll();
        groupMemberRepository.deleteAll();
        groupRepository.deleteAll();
        categoryRepository.deleteAll();
        friendRepository.deleteAll();
        refreshTokenRepository.deleteAll();
        passwordResetTokenRepository.deleteAll();
        pendingSignupRepository.deleteAll();
        userRepository.deleteAll();
    }

    protected String getLastOtp() throws Exception {

        MimeMessage message = integrationTestConfig.getLastMessage();

        String html = extractText(message.getContent());

        Matcher matcher =
                Pattern.compile("\\b\\d{6}\\b")
                        .matcher(html);

        assertThat(matcher.find()).isTrue();

        return matcher.group();
    }

    /**
     * MimeMessageHelper(message, true, ...) builds a nested multipart
     * structure (e.g. multipart/mixed containing a multipart/related
     * containing the actual text/html part), so the real content usually
     * isn't at the top level or even at bodyPart(0) directly - it can be
     * another Multipart several levels down. This walks the tree until it
     * finds an actual String part.
     */
    private String extractText(Object content) throws Exception {

        if (content instanceof String text) {
            return text;
        }

        if (content instanceof Multipart multipart) {

            for (int i = 0; i < multipart.getCount(); i++) {

                BodyPart bodyPart = multipart.getBodyPart(i);

                String found = extractText(bodyPart.getContent());

                if (found != null && !found.isBlank()) {
                    return found;
                }
            }
        }

        return null;
    }

    /**
     * Bypasses the signup/OTP flow to directly create an already-verified
     * user, for tests (login, refresh, logout, password flows) that don't
     * care about the signup process itself.
     */
    protected User createVerifiedUser(String email, String rawPassword) {

        User user = User.builder()
                .name("Aman")
                .email(email)
                .passwordHash(passwordEncoder.encode(rawPassword))
                .provider(AuthProvider.LOCAL)
                .build();

        return userRepository.save(user);
    }

    /**
     * Generates a real, validly-signed access token for the given user so
     * that authenticated endpoints (logout, change-password, set-password)
     * can be called via MockMvc with a proper Authorization header - matches
     * exactly what JwtAuthenticationFilter expects (subject = user id).
     */
    protected String bearerTokenFor(User user) {
        return "Bearer " + jwtService.generateAccessToken(
                user.getId(),
                user.getEmail(),
                user.getSubscriptionTier().name()
        );
    }

    /**
     * Creates a bidirectional friend link between two users - required before
     * either can be added to a group together, since GroupService gates both
     * initial-member-at-creation and invite-member on friendRepository.areFriends().
     */
    protected void makeFriends(User a, User b) {
        User user1 = a.getId().toString().compareTo(b.getId().toString()) < 0 ? a : b;
        User user2 = a.getId().toString().compareTo(b.getId().toString()) < 0 ? b : a;

        friendRepository.save(Friend.builder()
                .user1(user1)
                .user2(user2)
                .build());
    }

    protected Category createCategory(String name) {
        return categoryRepository.save(Category.builder()
                .name(name)
                .icon("🍕")
                .system(false)
                .build());
    }
}