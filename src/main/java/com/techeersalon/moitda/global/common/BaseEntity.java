package com.techeersalon.moitda.global.common;

import com.fasterxml.jackson.annotation.JsonFormat;
import jakarta.persistence.Column;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.MappedSuperclass;
import lombok.Getter;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;

@Getter
@MappedSuperclass
@EntityListeners(AuditingEntityListener.class)
public class BaseEntity {
    @CreatedDate
    @JsonFormat(timezone = "Asia/Seoul")
    private LocalDateTime createAt = LocalDateTime.now().truncatedTo(ChronoUnit.MINUTES);

    @LastModifiedDate
    @JsonFormat(timezone = "Asia/Seoul")
    private LocalDateTime updateAt = LocalDateTime.now().truncatedTo(ChronoUnit.MINUTES);

    @Column(name = "is_deleted", nullable = false)
    @JsonFormat(timezone = "Asia/Seoul")
    private Boolean isdeleted = Boolean.FALSE;

//    public void delete() {
//        this.is_deleted = Boolean.TRUE;
//    }
}