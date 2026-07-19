package com.splitwise.app.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.splitwise.app.dto.waitlist.WaitlistRequest;
import com.splitwise.app.dto.waitlist.WaitlistResponse;
import com.splitwise.app.entity.Waitlist;
import com.splitwise.app.repository.WaitlistRepository;

@ExtendWith(MockitoExtension.class)
class WaitlistServiceTest {

    @Mock
    private WaitlistRepository repository;

    @InjectMocks
    private WaitlistService waitlistService;

    @Test
    void joinWaitlist_shouldCreateNewEntry() {

        WaitlistRequest request
                = new WaitlistRequest("Aman@Test.COM");

        when(repository.existsByEmail("aman@test.com"))
                .thenReturn(false);

        WaitlistResponse response
                = waitlistService.joinWaitlist(request);

        assertEquals(
                "You've successfully joined the Splenza waitlist!",
                response.message()
        );

        verify(repository).save(any(Waitlist.class));
    }

    @Test
    void joinWaitlist_shouldTrimAndLowercaseEmail() {

        WaitlistRequest request
                = new WaitlistRequest("   Aman@Test.COM   ");

        when(repository.existsByEmail("aman@test.com"))
                .thenReturn(false);

        waitlistService.joinWaitlist(request);

        verify(repository).existsByEmail("aman@test.com");

        verify(repository).save(argThat(waitlist
                -> waitlist.getEmail().equals("aman@test.com")
        ));
    }

    @Test
    void joinWaitlist_shouldReturnAlreadyExistsMessage() {

        WaitlistRequest request
                = new WaitlistRequest("aman@test.com");

        when(repository.existsByEmail("aman@test.com"))
                .thenReturn(true);

        WaitlistResponse response
                = waitlistService.joinWaitlist(request);

        assertEquals(
                "You're already on the waitlist!",
                response.message()
        );

        verify(repository, never()).save(any());
    }
}
