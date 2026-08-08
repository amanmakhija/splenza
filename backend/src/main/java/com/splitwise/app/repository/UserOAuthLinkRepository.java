package com.splitwise.app.repository;

import com.splitwise.app.entity.UserOAuthLink;
import com.splitwise.app.enums.OAuthProviderType;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface UserOAuthLinkRepository extends JpaRepository<UserOAuthLink, UUID> {

    Optional<UserOAuthLink> findByProviderAndProviderUserId(OAuthProviderType provider, String providerUserId);
}
