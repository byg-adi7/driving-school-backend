package com.drivingschool.backend.messaging.service;

import com.drivingschool.backend.messaging.dto.ContactResponse;
import com.drivingschool.backend.messaging.dto.ConversationResponse;
import com.drivingschool.backend.messaging.dto.MessageResponse;
import com.drivingschool.backend.messaging.dto.SendMessageRequest;
import com.drivingschool.backend.messaging.dto.StartConversationRequest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;

public interface MessagingService {

    List<ContactResponse> listContacts();

    ConversationResponse startConversation(StartConversationRequest request);

    List<ConversationResponse> listMyConversations();

    Page<MessageResponse> getMessages(Long conversationId, Pageable pageable);

    MessageResponse sendMessage(Long conversationId, SendMessageRequest request);

    void markRead(Long conversationId);
}
