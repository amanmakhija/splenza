package com.splitwise.app.repository;

import com.splitwise.app.entity.Friend;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface FriendRepository extends JpaRepository<Friend, UUID> {

    @Query("select f from Friend f where f.user1.id = :userId or f.user2.id = :userId")
    List<Friend> findAllForUser(@Param("userId") UUID userId);

    @Query("select count(f) > 0 from Friend f where (f.user1.id = :u1 and f.user2.id = :u2) or (f.user1.id = :u2 and f.user2.id = :u1)")
    boolean areFriends(@Param("u1") UUID u1, @Param("u2") UUID u2);
}
