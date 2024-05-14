package com.techeersalon.moitda.domain.meetings.exception.meeting;

import com.techeersalon.moitda.global.error.ErrorCode;
import com.techeersalon.moitda.global.error.exception.BusinessException;

public class MeetingPageNotFoundException extends BusinessException {
    public MeetingPageNotFoundException(){
        super(ErrorCode.MEETING_PAGE_NOT_FOUND);
    }
}
