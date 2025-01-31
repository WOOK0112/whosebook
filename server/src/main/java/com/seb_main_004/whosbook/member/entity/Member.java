package com.seb_main_004.whosbook.member.entity;


import com.seb_main_004.whosbook.curation.entity.Curation;
import com.seb_main_004.whosbook.like.entity.CurationLike;
import com.seb_main_004.whosbook.subscribe.entity.Subscribe;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.Getter;
import lombok.Setter;

import javax.persistence.*;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Data
@AllArgsConstructor
public class Member {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private long memberId;

    @Column(nullable = false)
    private String email;

    @Column(nullable = false)
    private String nickname;

    @Column(nullable = false)
    private String password;

    private String introduction;

    //이미지 조회용 url
    private String imageUrl;

    //S3 Bucket 이미지 key
    private String imageKey;

    public Member() { }

    public Member(String email, String nickname,String imageUrl) {
        this.email = email;
        this.nickname = nickname;
        this.imageUrl=imageUrl;
    }


    @Enumerated(value = EnumType.STRING)
    @Column(nullable = false)
    private MemberStatus memberStatus= MemberStatus.MEMBER_ACTIVE;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt=LocalDateTime.now();

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt=LocalDateTime.now();

    //사용자의 권한을 등록하기 위한 권한 테이블
    @ElementCollection(fetch = FetchType.EAGER)
    private List<String> roles = new ArrayList<>();

    //큐레이션과 연관관계
    @OneToMany(mappedBy = "member", cascade = CascadeType.PERSIST, fetch = FetchType.LAZY)
    private List<Curation> curations = new ArrayList<>();

    //like와 연관관계
    @OneToMany(mappedBy = "member", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    private List<CurationLike> likeList= new ArrayList<>();

    //구독 연관관계: 날 구독한 멤버 리스트
    @OneToMany(mappedBy = "subscriber", cascade = CascadeType.PERSIST, fetch = FetchType.LAZY)
    private List<Subscribe> subscribedMembers = new ArrayList<>();

    //구독 연관관계: 내가 구독한 멤버 리스트
    @OneToMany(mappedBy = "subscribedMember", cascade = CascadeType.PERSIST, fetch = FetchType.LAZY)
    private List<Subscribe> subscribers = new ArrayList<>();

    @Getter
    public  enum  MemberStatus{

        MEMBER_ACTIVE("활동중"),
        MEMBER_DELETE("탈퇴 상태");

        @Getter
        @Setter
        private String status;

        MemberStatus(String status) {
            this.status = status;
        }
    }
}
