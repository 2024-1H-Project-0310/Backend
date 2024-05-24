package com.techeersalon.moitda.domain.chat.dto.mapper;

import com.techeersalon.moitda.domain.chat.entity.ChatMessage;
import com.techeersalon.moitda.domain.chat.entity.ChatRoom;

import com.techeersalon.moitda.domain.chat.dto.request.ChatMessageReq;
import com.techeersalon.moitda.domain.chat.dto.response.ChatMessageRes;
import com.techeersalon.moitda.domain.chat.dto.response.ChatRoomRes;
import com.techeersalon.moitda.domain.chat.exception.MessageNotFoundException;
import com.techeersalon.moitda.domain.meetings.dto.response.GetLatestMeetingListRes;
import com.techeersalon.moitda.domain.meetings.entity.MeetingImage;
import com.techeersalon.moitda.domain.meetings.exception.meeting.MeetingPageNotFoundException;
import com.techeersalon.moitda.domain.user.dto.response.UserProfileRes;
import com.techeersalon.moitda.domain.user.entity.User;
import com.techeersalon.moitda.domain.user.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.stream.Collectors;

import static java.time.LocalTime.now;

@Component
@RequiredArgsConstructor
public class ChatMapper {

    @Autowired
    private static UserService userService;

    public static ChatMessage toChatMessage(User user, Long meetingId, ChatMessageReq request) {
        return ChatMessage.builder()
                .userid(user.getId())
                .meetingId(meetingId)
                .message(request.getMessage())
                .sendDate(LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")))
                //.sendDate(LocalDateTime.from(now()))
                .build();
    }

    public static ChatMessageRes toChatMessageDto(ChatMessage chatMessage, Long userId) {
        // userId를 사용하여 사용자 정보를 조회합니다.
        UserProfileRes userProfile = userService.findUserProfile(userId);

        return ChatMessageRes.builder()
                .userid(chatMessage.getUserid())
                .sender(userProfile.getUsername())
                .profileImage(userProfile.getProfileImage())
                .content(chatMessage.getMessage())
                .sendDate(chatMessage.getSendDate())
                .build();
    }

    public static ChatRoomRes toChatRoomDto(ChatRoom chatRoom) {
        return ChatRoomRes.builder()
                .id(chatRoom.getId())
                .members(chatRoom.getMembers().stream()
                        .map(user -> ChatRoomRes.MemberDetail.builder()
                                .id(user.getId())
                                .name(user.getUsername())
                                .build())
                        .collect(Collectors.toList()))
                        //.stream().map(this::toMemberDetail).collect(Collectors.toList()))
                .build();
    }
    public static List<ChatRoomRes> toChatRoomDtoList(List<ChatRoom> rooms) {
        return rooms.stream()
                .map(ChatMapper::toChatRoomDto)
                .collect(Collectors.toList());
    }


    public static List<ChatMessageRes> toChatMessageDtoList(List<ChatMessage> messages) {
        ChatMapper chatMapper = new ChatMapper();;
        return messages.stream()
                .map(message -> toChatMessageDto(message, message.getUserid()))
                .collect(Collectors.toList());
    }

    public static List<ChatMessageRes> PageToChatMessageDto(Page<ChatMessage> messages){
        if (messages.isEmpty()) {
            throw new MessageNotFoundException();
        }
        return toChatMessageDtoList(messages.getContent());
    }


//     public static GetLatestMessageListResponseDto of(ChatMessage chatMessage){
//        return GetLatestMessageListResponseDto.builder()
//                .userid(chatMessage.getUserid())
//                .sender(userService.findUserProfile(chatMessage.getUserid()).getUsername())
//                .profileImage(userService.findUserProfile(chatMessage.getUserid()).getProfileImage())
//                .content(chatMessage.getMessage())
//                .sendDate(chatMessage.getSendDate())
//                .build();
//    }
}