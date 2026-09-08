package com.queqiao.server.entity;

import jakarta.persistence.*;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

/** 会话消息 */
@Data
@Entity
@Table(name = "t_message")
public class MessageItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String conversationId;

    private String sender;

    @Column(columnDefinition = "TEXT")
    private String content;

    @Column(name = "msg_type")
    private String type;

    private Boolean readFlag;

    private LocalDateTime createdAt;

}
