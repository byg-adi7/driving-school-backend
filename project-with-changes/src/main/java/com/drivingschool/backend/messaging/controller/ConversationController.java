package com.drivingschool.backend.messaging.controller;

import com.drivingschool.backend.common.response.ApiResponse;
import com.drivingschool.backend.messaging.dto.ContactResponse;
import com.drivingschool.backend.messaging.dto.ConversationResponse;
import com.drivingschool.backend.messaging.dto.MessageResponse;
import com.drivingschool.backend.messaging.dto.SendMessageRequest;
import com.drivingschool.backend.messaging.dto.StartConversationRequest;
import com.drivingschool.backend.messaging.service.MessagingService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/conversations")
@Tag(name = "Messaging", description = "Private student-instructor conversations within a school")
@SecurityRequirement(name = "Bearer Authentication")
@PreAuthorize("hasAnyRole('STUDENT', 'INSTRUCTOR')")
public class ConversationController {

    private final MessagingService messagingService;

    public ConversationController(MessagingService messagingService) {
        this.messagingService = messagingService;
    }

    @GetMapping("/contacts")
    @Operation(summary = "People I can message: a student's school instructors, or an instructor's school students")
    public ResponseEntity<ApiResponse<List<ContactResponse>>> contacts() {
        return ResponseEntity.ok(ApiResponse.success(messagingService.listContacts()));
    }

    @PostMapping
    @Operation(summary = "Open (or reuse) my conversation with a student/instructor of my school")
    public ResponseEntity<ApiResponse<ConversationResponse>> start(@Valid @RequestBody StartConversationRequest request) {
        return ResponseEntity.ok(ApiResponse.success(messagingService.startConversation(request)));
    }

    @GetMapping
    @Operation(summary = "My conversations, most recent activity first, with unread counts")
    public ResponseEntity<ApiResponse<List<ConversationResponse>>> myConversations() {
        return ResponseEntity.ok(ApiResponse.success(messagingService.listMyConversations()));
    }

    @GetMapping("/{id}/messages")
    @Operation(summary = "A conversation's messages, newest first (paginated)")
    public ResponseEntity<ApiResponse<Page<MessageResponse>>> messages(@PathVariable Long id,
                                                                       @PageableDefault(size = 50) Pageable pageable) {
        return ResponseEntity.ok(ApiResponse.success(messagingService.getMessages(id, pageable)));
    }

    @PostMapping("/{id}/messages")
    @Operation(summary = "Send a message in a conversation")
    public ResponseEntity<ApiResponse<MessageResponse>> send(@PathVariable Long id,
                                                             @Valid @RequestBody SendMessageRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Message sent", messagingService.sendMessage(id, request)));
    }

    @PostMapping("/{id}/read")
    @Operation(summary = "Mark every message the other participant sent me in this conversation as read")
    public ResponseEntity<ApiResponse<Void>> markRead(@PathVariable Long id) {
        messagingService.markRead(id);
        return ResponseEntity.ok(ApiResponse.success("Conversation marked as read", null));
    }
}
