
package com.techeersalon.moitda.domain.meetings.service;

import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.model.CannedAccessControlList;
import com.amazonaws.services.s3.model.ObjectMetadata;
import com.amazonaws.services.s3.model.PutObjectRequest;
import com.amazonaws.util.IOUtils;
import com.techeersalon.moitda.domain.meetings.dto.mapper.MeetingParticipantListMapper;
import com.techeersalon.moitda.domain.meetings.dto.mapper.MeetingParticipantMapper;
import com.techeersalon.moitda.domain.meetings.dto.mapper.PointMapper;
import com.techeersalon.moitda.domain.meetings.dto.request.*;
import com.techeersalon.moitda.domain.meetings.dto.response.*;
import com.techeersalon.moitda.domain.meetings.entity.Meeting;
import com.techeersalon.moitda.domain.meetings.entity.MeetingImage;
import com.techeersalon.moitda.domain.meetings.entity.MeetingParticipant;
import com.techeersalon.moitda.domain.meetings.exception.meeting.MeetingIsFullException;
import com.techeersalon.moitda.domain.meetings.exception.meeting.MeetingNotFoundException;
import com.techeersalon.moitda.domain.meetings.exception.meeting.MeetingOwnerLeavingException;
import com.techeersalon.moitda.domain.meetings.exception.meeting.MeetingPageNotFoundException;
import com.techeersalon.moitda.domain.meetings.exception.participant.AlreadyParticipatingOrAppliedException;
import com.techeersalon.moitda.domain.meetings.exception.participant.MeetingParticipantNotFoundException;
import com.techeersalon.moitda.domain.meetings.exception.participant.NotAuthorizedToAppproveException;
import com.techeersalon.moitda.domain.meetings.exception.review.AlreadyReviewedException;
import com.techeersalon.moitda.domain.meetings.exception.review.InvalidRatingScoreException;
import com.techeersalon.moitda.domain.meetings.exception.review.MeetingNotEndedException;
import com.techeersalon.moitda.domain.meetings.repository.MeetingImageRepository;
import com.techeersalon.moitda.domain.meetings.repository.MeetingParticipantRepository;
import com.techeersalon.moitda.domain.meetings.repository.MeetingRepository;
import com.techeersalon.moitda.domain.user.entity.User;
import com.techeersalon.moitda.domain.user.exception.UserNotFoundException;
import com.techeersalon.moitda.domain.user.repository.UserRepository;
import com.techeersalon.moitda.domain.user.service.UserService;
import com.techeersalon.moitda.global.s3.exception.S3Exception;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.Point;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;


@Service
@Slf4j
@Transactional
@RequiredArgsConstructor(access = AccessLevel.PROTECTED)
public class MeetingService {

    private final MeetingRepository meetingRepository;
    private final MeetingParticipantRepository meetingParticipantRepository;
    private final UserService userService;
    private final UserRepository userRepository;
    private final MeetingImageRepository meetingImageRepository;
    private final AmazonS3 amazonS3;

    @Value("${cloud.aws.s3.bucket}")
    private String bucketName;

    /*
     * 미팅 생성 메소드
     * Meeting, MeetingParticipant 생성
     * Meeting에 참가자 1명 추가
     * */
    public CreateMeetingRes createMeeting(CreateMeetingReq dto, List<MultipartFile> images) throws IOException {
        User loginUser = userService.getLoginUser();
        Meeting entity = dto.toEntity(loginUser);
        // 최대 참가 인원 유효성 검사
        entity.validateMaxParticipantsCount();
        Meeting meeting = meetingRepository.save(entity);

        // MeetingParticipant 엔티티 생성 및 저장
        MeetingParticipant participant = MeetingParticipantMapper.toEntity(meeting);
        participant.notNeedToApprove();
        meetingParticipantRepository.save(participant);

        if (images != null && !images.isEmpty()) {
            for (MultipartFile image : images) {
                String imageName = image.getOriginalFilename();
                String extension = imageName.substring(imageName.lastIndexOf(".") + 1);
                String s3imageFileName = "meeting/" + UUID.randomUUID().toString().substring(0, 10) + "_" + imageName;
                InputStream is = image.getInputStream();
                byte[] bytes = IOUtils.toByteArray(is);
                ObjectMetadata metadata = new ObjectMetadata();
                metadata.setContentType("image/" + extension);
                metadata.setContentLength(bytes.length);
                ByteArrayInputStream byteArrayInputStream = new ByteArrayInputStream(bytes);

                try {
                    PutObjectRequest putObjectRequest =
                            new PutObjectRequest(bucketName, s3imageFileName, byteArrayInputStream, metadata)
                                    .withCannedAcl(CannedAccessControlList.PublicRead);
                    amazonS3.putObject(putObjectRequest);

                    // 이미지의 URL을 저장
                    String imageUrl = amazonS3.getUrl(bucketName, s3imageFileName).toString();

                    // MeetingImage 엔티티에 저장
                    MeetingImage meetingImage = new MeetingImage(imageUrl, entity.getId()); // 생성자를 사용하여 설정

                    // MeetingImage 저장
                    meetingImageRepository.save(meetingImage);

                } catch (Exception e) {
                    throw new S3Exception();
                } finally {
                    byteArrayInputStream.close();
                    is.close();
                }
            }
        }

        return CreateMeetingRes.from(meeting.getId());
    }

    /*
     * 미팅 상세 조회 메소드
     * 미팅 데이터 값 중 필요한 내용과 미팅 참가자 리스트 reponse에 담음
     * */
    public GetMeetingDetailRes findMeetingById(Long meetingId) {
        Meeting meeting = this.getMeetingById(meetingId);

        // 생성자 유저 정보.
        User user = userRepository.findById(meeting.getUserId())
                .orElseThrow(UserNotFoundException::new);

        User currentUser = userService.getLoginUser();

        List<MeetingImage> imageList = meetingImageRepository.findByMeetingId(meetingId);

        List<MeetingParticipant> participants = meetingParticipantRepository.findByMeetingIdAndIsWaiting(meetingId, Boolean.FALSE);
        List<MeetingParticipant> waitingList = meetingParticipantRepository.findByMeetingIdAndIsWaiting(meetingId, Boolean.TRUE);

        // user.getId()가 participants 또는 waitingList에 있는 userId와 일치하는지 확인
        boolean participantValid = participants.stream()
                .anyMatch(participant -> participant.getUserId().equals(currentUser.getId())) ||
                waitingList.stream()
                        .anyMatch(participant -> participant.getUserId().equals(currentUser.getId()));

        if (participants.isEmpty() && waitingList.isEmpty()) {
            throw new MeetingNotFoundException();
        }

        List<MeetingParticipantListMapper> participantDtoList = participants.stream()
                .map(participant -> {
                    User participantUser = userRepository.findById(participant.getUserId())
                            .orElseThrow(UserNotFoundException::new);
                    return MeetingParticipantListMapper.from(participant, participantUser);
                })
                .collect(Collectors.toList());

        List<MeetingParticipantListMapper> waitingDtoList = waitingList.stream()
                .map(participant -> {
                    User participantUser = userRepository.findById(participant.getUserId())
                            .orElseThrow(UserNotFoundException::new);
                    return MeetingParticipantListMapper.from(participant, participantUser);
                })
                .collect(Collectors.toList());

        return GetMeetingDetailRes.of(meeting, user, participantDtoList, waitingDtoList, imageList, participantValid);
    }

//    private MeetingParticipantMapper mapToDto(MeetingParticipant meetingParticipant) {
//
//        return new MeetingParticipantMapper(
//                meetingParticipant.getId()
//        );
//    }

    /*
     * 참가자 신청 메소드
     * 미팅에 선착순인 경우와 승인이 필요한 경우
     * */
    public CreateParticipantRes addParticipantOfMeeting(Long meetingId) {
        User loginUser = userService.getLoginUser();
        Long loginUserId = loginUser.getId();
        Meeting meeting = null;

        // 이미 유저가 미팅에 참가자 거나 참가 신청을 했을 경우 예외처리
        if (meetingParticipantRepository.existsByMeetingIdAndUserId(meetingId, loginUserId)) {
            throw new AlreadyParticipatingOrAppliedException();
        }

        meeting = this.getMeetingById(meetingId);

        // 미팅 참가자가 이미 최대 참가자수를 넘을 경우 예외처리
        if (meeting.getParticipantsCount() >= meeting.getMaxParticipantsCount()) {
            throw new MeetingIsFullException();
        }

        MeetingParticipant entity = MeetingParticipantMapper.toEntity(meeting, loginUser);

        // 미팅이 선착순일 경우 바로 미팅 참가자로 변경
        if (!meeting.getApprovalRequired()) {
            entity.notNeedToApprove();
            meeting.increaseParticipantsCnt();
        }

        MeetingParticipant participant = meetingParticipantRepository.save(entity);

        return CreateParticipantRes.from(participant.getId());
    }

    public void approvalParticipant(ApprovalParticipantReq dto) {
        // 참가자 존재 예외처리
        MeetingParticipant participant = meetingParticipantRepository.findById(dto.getParticipantId()).orElseThrow(MeetingParticipantNotFoundException::new);
        // 승인한 미팅이 참가자 미팅이 다른 경우 예외처리
        if (participant.getMeetingId().longValue() != dto.getMeetingId().longValue()) {
            throw new NotAuthorizedToAppproveException();
        }
        participant.notNeedToApprove();

        if (dto.getIsApproval()) { // 승인 할 경우
            Meeting meeting = this.getMeetingById(participant.getMeetingId());
            meeting.increaseParticipantsCnt();
            meetingParticipantRepository.save(participant);
        } else { // 거절 할 경우
            meetingParticipantRepository.delete(participant);
            //participant.delete();
        }
    }

    /*
     * 미팅 리스트 조회 메소드
     * 간략화 된 미팅 내용을 최대 32개인 한 페이지로 준다.
     * */
    public GetSearchPageRes getUserMeetingCreatingRecords(Long userId, Pageable pageable) {
        Page<Meeting> meetings = meetingRepository.findCreatingRecordsByUserId(userId, pageable);

        return transformMeetingsToResponse(meetings);
    }

    public GetSearchPageRes getUserMeetingParticipationRecords(Long userId, Pageable pageable) {
        Page<Meeting> meetings = meetingRepository.findParticipationRecordsByUserId(userId, pageable);

        return transformMeetingsToResponse(meetings);
    }

    public GetSearchPageRes getMeetingsNearLocation(PointMapper pointMapper, Pageable pageable) {
        Point point = mappingPoint(pointMapper);
        Page<Meeting> meetings = meetingRepository.findMeetingByDistance(point, pageable);
        return transformMeetingsToResponse(meetings);
    }

    public GetSearchPageRes getAllMeetings(PointMapper pointMapper, Pageable pageable) {
        Point point = mappingPoint(pointMapper);
        Page<Meeting> meetings = meetingRepository.findAllMeetingByDistance(point, pageable);
        return transformMeetingsToResponse(meetings);
    }

    public GetSearchPageRes getMeetingsCategory(PointMapper pointMapper, Long categoryId, Pageable pageable) {
        Point point = mappingPoint(pointMapper);
        Page<Meeting> meetings = meetingRepository.findByLocationNearAndCategory(point, categoryId, pageable);
        return transformMeetingsToResponse(meetings);
    }

    private GetSearchPageRes transformMeetingsToResponse(Page<Meeting> meetings) {
        if (meetings.isEmpty()) {
            throw new MeetingPageNotFoundException();
        }
        List<GetLatestMeetingListRes> meetingList = meetings.stream()
                .map(meeting -> {
                    List<MeetingImage> images = meetingImageRepository.findByMeetingId(meeting.getId());
                    return GetLatestMeetingListRes.from(meeting, images);
                })
                .collect(Collectors.toList());

        return GetSearchPageRes.from(meetingList, meetings.getTotalPages(), meetings.getNumber(), (int) meetings.getTotalElements(), meetings.getSize());
    }


    /*
     * 미팅 삭제 메소드
     * 미팅, 미팅의 참가자 모두 softDelete
     * */
    public void deleteMeeting(Long meetingId) {
        Meeting meeting = this.getMeetingById(meetingId);

        Optional<MeetingParticipant> participantOptional = meetingParticipantRepository.findByMeetingId(meetingId);
        participantOptional.orElseThrow(MeetingParticipantNotFoundException::new);

        meetingRepository.delete(meeting);
        meetingImageRepository.deleteByMeetingId(meetingId);
        //meetingParticipantRepository.save(participant);
        meetingParticipantRepository.deleteAll(participantOptional.stream().toList());
        //meetingRepository.save(meeting);
    }

    private Meeting getMeetingById(Long meetingId) {
        return meetingRepository.findById(meetingId)
                .orElseThrow(MeetingNotFoundException::new);
    }

    /*
     * 미팅 수정 메소드
     * 미팅 전체 내용 수정
     * */
    public void modifyMeeting(Long meetingId, ChangeMeetingInfoReq dto) {
        //미팅 값 업데이트
        Meeting meeting = this.getMeetingById(meetingId);
        meeting.updateInfo(dto);

        // 미팅 최대 값 확인
        meeting.validateMaxParticipantsCount();

        // 미팅 참가자가 수정한 최대 참가자수를 넘을 경우 예외처리
        if (meeting.getParticipantsCount() >= meeting.getMaxParticipantsCount()) {
            throw new MeetingIsFullException();
        }

        meetingRepository.save(meeting);
    }

    public void endMeeting(Long meetingId) {
        Meeting meeting = this.getMeetingById(meetingId);
        meeting.updateEndTime(LocalDateTime.now().toString());
    }

    public Boolean determineMeetingOwner(Long meetingId) {
        User loginUser = userService.getLoginUser();
        Meeting meeting = this.getMeetingById(meetingId);
        if (meeting.getUserId().equals(loginUser.getId())) {
            return Boolean.TRUE;
        }
        return Boolean.FALSE;
    }

    public void createReview(CreateReviewReq createReviewReq) {
        Long meetingId = createReviewReq.getMeetingId();
        Optional<Meeting> meetingOptional = meetingRepository.findById(meetingId);
        Meeting meeting = meetingOptional.orElseThrow(MeetingNotFoundException::new);
        User loginUser = userService.getLoginUser();

        if (meeting.getEndTime() == null) {
            throw new MeetingNotEndedException();
        }
        // userId를 통해 participant table을 조회해서 userId가 같은지
        MeetingParticipant meetingParticipant = meetingParticipantRepository.findByMeetingIdAndUserId(meetingId, loginUser.getId());
        if (meetingParticipant.getIsReviewed()) {
            throw new AlreadyReviewedException();
        }

        List<CreateReviewReq.Review> reviews = createReviewReq.getReviews();

        for (CreateReviewReq.Review review : reviews) {
            Long userId = review.getUserId();
            int rating = review.getRating();

            User user = userRepository.findById(userId)
                    .orElseThrow(UserNotFoundException::new);

            int adjustedMannerStat = adjustUserMannerStat(user.getMannerStat(), rating);
            user.updateMannerStat(adjustedMannerStat);

            meetingParticipant.updateReviewed();
            userRepository.save(user);
            meetingParticipantRepository.save(meetingParticipant);
        }
    }

    private int adjustUserMannerStat(int existingMannerStat, int ratingScore) {

        if (ratingScore < 1 || ratingScore > 10) {
            throw new InvalidRatingScoreException();
        }

        int adjustment = switch (ratingScore) {
            case 1 -> -4;
            case 2 -> -3;
            case 3 -> -2;
            case 4 -> -1;
            case 7 -> 1;
            case 8 -> 2;
            case 9 -> 3;
            case 10 -> 4;
            default -> 0;
        };

        return Math.max(Math.min(existingMannerStat + adjustment, 100), 0);
    }

    public List<GetParticipantListRes> getParticipantsOfMeeting(Long meetingId) {
        List<MeetingParticipant> participants = meetingParticipantRepository.findByMeetingIdAndIsWaiting(meetingId, Boolean.TRUE);
        if (participants.isEmpty()) {
            throw new MeetingParticipantNotFoundException();
        }

        return participants.stream()
                .map(participant -> {
                    User user = userRepository.findById(participant.getUserId())
                            .orElseThrow(UserNotFoundException::new);
                    return GetParticipantListRes.from(user);
                })
                .collect(Collectors.toList());
    }

    public GetSearchPageRes searchMeetingsByKeyword(String keyword, PointMapper pointMapper, Pageable pageable) {
        Point point = mappingPoint(pointMapper);
        Page<Meeting> meetings = meetingRepository.findPageByKeyword(keyword, point, pageable);
        return transformMeetingsToResponse(meetings);
    }

    public GetSearchPageRes searchMeetingsByClosestDeadline(PointMapper pointMapper, Pageable pageable) {
        Point point = mappingPoint(pointMapper);
        Page<Meeting> meetings = meetingRepository.findPageByClosest(point, pageable);
        return transformMeetingsToResponse(meetings);
    }

    private Point mappingPoint(PointMapper pointMapper) {
        GeometryFactory geometryFactory = new GeometryFactory();
        Coordinate coord = new Coordinate(pointMapper.getLongitude(), pointMapper.getLatitude());
        Point point = geometryFactory.createPoint(coord);
        return point;
    }

    public boolean hasReviewedMeeting(Long meetingId) {

        User loginUser = userService.getLoginUser();

        MeetingParticipant meetingParticipant = meetingParticipantRepository.findByMeetingIdAndUserId(meetingId, loginUser.getId());
        return meetingParticipant.getIsReviewed();
    }

    public void removeParticipantFromMeeting(Long meetingId, Long userId) {

        // MeetingParticipant 검색 및 삭제
        MeetingParticipant meetingParticipant = meetingParticipantRepository.findByMeetingIdAndUserId(meetingId, userId);

        Optional<Meeting> optionalMeeting = meetingRepository.findById(meetingId);
        Meeting meeting = optionalMeeting
                .orElseThrow(MeetingNotFoundException::new);

        if (meetingParticipant != null) {
            // 방장인지 확인
            if (meetingParticipant.getUserId() == meeting.getUserId()) {
                throw new MeetingOwnerLeavingException();
            }
        } else {
            // 참가자를 찾지 못한 경우 예외 처리
            throw new MeetingParticipantNotFoundException();
        }

        meetingParticipantRepository.delete(meetingParticipant);

        // Meeting 업데이트
        meeting.decreaseParticipantsCnt();
    }
}