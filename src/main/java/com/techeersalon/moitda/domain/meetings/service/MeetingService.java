
package com.techeersalon.moitda.domain.meetings.service;

import com.techeersalon.moitda.domain.meetings.dto.request.CreateMeetingRequest;
import com.techeersalon.moitda.domain.meetings.dto.response.GetMeetingDetailResponse;
import com.techeersalon.moitda.domain.meetings.entity.Meeting;
import com.techeersalon.moitda.domain.meetings.entity.MeetingParticipant;
import com.techeersalon.moitda.domain.meetings.repository.MeetingParticipantRepository;
import com.techeersalon.moitda.domain.meetings.repository.MeetingRepository;
import com.techeersalon.moitda.domain.user.entity.User;
import com.techeersalon.moitda.domain.user.service.UserService;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor(access = AccessLevel.PROTECTED)
public class MeetingService {
    private final MeetingRepository meetingRepository;
    private final MeetingParticipantRepository meetingParticipantRepository;
    private final UserService userService;

    public Long addMeeting(CreateMeetingRequest dto) {
        User loginUser = userService.getLoginUser();
        Meeting entity = dto.toEntity(loginUser);
        Meeting meeting = meetingRepository.save(entity);
        return meeting.getId();
    }

    public GetMeetingDetailResponse findMeetingById(Long meetingId) {
        Meeting meeting = this.getMeetingById(meetingId);

        return GetMeetingDetailResponse.of(meeting);
    }

    public Meeting getMeetingById(Long id) {
        // 아직 예외처리는 안함
        return meetingRepository.findById(id).orElse(null);
    }

    public void addParticipantOfMeeting(Long meetingId) {
        User loginUser = userService.getLoginUser();
        MeetingParticipant participant = new MeetingParticipant(meetingId, loginUser.getId());
        meetingParticipantRepository.save(participant);
    }

    public void approvalParticipant(Long userIdOfParticipant, Boolean isApproval) {
        MeetingParticipant participant = meetingParticipantRepository.findById(userIdOfParticipant).orElse(null);
        if(isApproval){
            participant.setIsWaiting(false);
        }else{
            participant.delete();
        }
        meetingParticipantRepository.save(participant);
    }

    public List<Meeting> getUserMeetingList(Long userId){
        return meetingRepository.findByUserId(userId);
    }
}