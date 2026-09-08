package com.queqiao.server.entity;

import jakarta.persistence.*;
import lombok.Data;

import java.time.LocalDateTime;

/** 视频相亲房间（红娘主持三方，复用 WebSocket 实时通道） */
@Data
@Entity
@Table(name = "t_meeting_room")
public class MeetingRoom {

    @Id
    @Column(length = 64)
    private String id;

    /** 房间类型，目前固定 video */
    private String type;

    /** 主持红娘（房主） */
    private String hostAgentId;

    /** 参会会员 A / B（会员端小程序可凭 roomId 加入） */
    private String memberA;

    private String memberB;

    /** 状态：live / closed */
    private String status;

    private LocalDateTime createdAt;

    private LocalDateTime closedAt;
}
