package com.queqiao.server.controller;

import com.queqiao.server.common.BizException;
import com.queqiao.server.common.Ids;
import com.queqiao.server.entity.MeetingRoom;
import com.queqiao.server.repo.MeetingRoomRepository;
import com.queqiao.server.security.RequestContext;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

/** 视频相亲房间（1-C）：红娘主持三方，信令走已建 WebSocket */
@RestController
@RequestMapping("/api/meetings")
public class MeetingController {

    private final MeetingRoomRepository rooms;
    private final SimpMessagingTemplate broker;

    public MeetingController(MeetingRoomRepository rooms, SimpMessagingTemplate broker) {
        this.rooms = rooms;
        this.broker = broker;
    }

    /** 发起视频相亲房间：建房间 → 广播 room-created 信号（会员端小程序凭 roomId 加入） */
    @PostMapping("/video")
    @Transactional
    public Map<String, Object> createVideo(@RequestBody Map<String, String> body) {
        RequestContext.requireAuth();
        String host = RequestContext.get().userName();
        String memberA = body.get("memberA");
        String memberB = body.get("memberB");
        if ((memberA == null || memberA.isBlank()) && (memberB == null || memberB.isBlank())) {
            throw new BizException("请指定参会会员");
        }
        MeetingRoom r = new MeetingRoom();
        r.setId(Ids.biz("MR"));
        r.setType("video");
        r.setHostAgentId(host);
        r.setMemberA(memberA);
        r.setMemberB(memberB);
        r.setStatus("live");
        r.setCreatedAt(LocalDateTime.now());
        rooms.save(r);

        Map<String, Object> signal = new LinkedHashMap<>();
        signal.put("type", "room-created");
        signal.put("roomId", r.getId());
        signal.put("host", host);
        signal.put("memberA", memberA);
        signal.put("memberB", memberB);
        signal.put("at", r.getCreatedAt().toString());
        broker.convertAndSend("/topic/meeting/" + r.getId(), signal);

        Map<String, Object> res = new LinkedHashMap<>();
        res.put("roomId", r.getId());
        res.put("type", r.getType());
        res.put("host", host);
        res.put("memberA", memberA);
        res.put("memberB", memberB);
        res.put("status", r.getStatus());
        res.put("wsTopic", "/topic/meeting/" + r.getId());
        res.put("createdAt", r.getCreatedAt());
        return res;
    }

    /** 房间状态（后端可见，供运营核查） */
    @GetMapping("/{id}")
    public MeetingRoom get(@PathVariable String id) {
        return rooms.findById(id).orElseThrow(() -> BizException.notFound("视频房间"));
    }

    /** 结束房间：红娘挂断 → 广播 room-closed 信号 */
    @PostMapping("/{id}/close")
    @Transactional
    public Map<String, Object> close(@PathVariable String id) {
        MeetingRoom r = rooms.findById(id).orElseThrow(() -> BizException.notFound("视频房间"));
        r.setStatus("closed");
        r.setClosedAt(LocalDateTime.now());
        rooms.save(r);
        Map<String, Object> signal = new LinkedHashMap<>();
        signal.put("type", "room-closed");
        signal.put("roomId", id);
        signal.put("at", r.getClosedAt().toString());
        broker.convertAndSend("/topic/meeting/" + id, signal);
        return Map.of("ok", true, "roomId", id, "status", "closed");
    }
}
