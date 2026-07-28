package com.example.minagent.session;

import com.example.minagent.session.repository.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;

@DataJpaTest
@ActiveProfiles("test")
class SessionServiceTest {

    @Autowired
    private AppUserRepository appUserRepository;
    @Autowired
    private ChatSessionRepository sessionRepository;
    @Autowired
    private ChatMessageRepository messageRepository;
    @Autowired
    private SessionSummaryRepository summaryRepository;

    private SessionService service;

    private static final UUID USER_A = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID USER_B = UUID.fromString("22222222-2222-2222-2222-222222222222");

    @BeforeEach
    void setUp() {
        service = new SessionService(appUserRepository, sessionRepository, messageRepository, summaryRepository);
    }

    @Test
    void createsSessionForNewUser() {
        ChatSession session = service.createSession(USER_A, "Test Session");

        assertThat(session.getId()).isNotNull();
        assertThat(session.getTitle()).isEqualTo("Test Session");
        assertThat(session.getUserId()).isEqualTo(USER_A);
    }

    @Test
    void listsSessionsForUser() {
        service.createSession(USER_A, "Session 1");
        service.createSession(USER_A, "Session 2");

        List<ChatSession> sessions = service.listSessions(USER_A);

        assertThat(sessions).hasSize(2);
        assertThat(sessions).extracting(ChatSession::getTitle)
                .contains("Session 1", "Session 2");
    }

    @Test
    void rejectsSessionOwnedByAnotherUser() {
        ChatSession sessionOfA = service.createSession(USER_A, "A's Session");

        assertThatThrownBy(() -> service.requireOwnedSession(USER_B, sessionOfA.getId()))
                .isInstanceOf(SessionNotFoundException.class)
                .hasMessageContaining("not found");
    }

    @Test
    void throwsNotFoundForNonexistentSession() {
        UUID nonexistentId = UUID.randomUUID();

        assertThatThrownBy(() -> service.requireOwnedSession(USER_A, nonexistentId))
                .isInstanceOf(SessionNotFoundException.class);
    }

    @Test
    void savesAndRetrievesMessages() {
        ChatSession session = service.createSession(USER_A, "Test");
        ChatMessage msg1 = ChatMessage.user(UUID.randomUUID(), session, "Hello", 1);
        ChatMessage msg2 = ChatMessage.assistant(UUID.randomUUID(), session, "Hi there", 2);

        service.saveMessage(msg1);
        service.saveMessage(msg2);

        List<ChatMessage> messages = service.getMessages(USER_A, session.getId());
        assertThat(messages).hasSize(2);
        assertThat(messages.get(0).getContent()).isEqualTo("Hello");
        assertThat(messages.get(1).getContent()).isEqualTo("Hi there");
    }

    @Test
    void userBCannotReadUserAMessages() {
        ChatSession sessionOfA = service.createSession(USER_A, "A's Session");
        service.saveMessage(ChatMessage.user(UUID.randomUUID(), sessionOfA, "Secret", 1));

        assertThatThrownBy(() -> service.getMessages(USER_B, sessionOfA.getId()))
                .isInstanceOf(SessionNotFoundException.class);
    }

    @Test
    void sequenceNumbersAreMonotonic() {
        ChatSession session = service.createSession(USER_A, "Test");

        assertThat(service.nextSequenceNo(session.getId())).isEqualTo(1);

        service.saveMessage(ChatMessage.user(UUID.randomUUID(), session, "msg", 1));
        assertThat(service.nextSequenceNo(session.getId())).isEqualTo(2);
    }

    @Test
    void countMessagesReturnsCorrectCount() {
        ChatSession session = service.createSession(USER_A, "Test");

        assertThat(service.countMessages(session.getId())).isEqualTo(0);

        service.saveMessage(ChatMessage.user(UUID.randomUUID(), session, "msg1", 1));
        service.saveMessage(ChatMessage.assistant(UUID.randomUUID(), session, "msg2", 2));

        assertThat(service.countMessages(session.getId())).isEqualTo(2);
    }
}
