package com.seb_main_004.whosbook.subscribe.repository;

import com.seb_main_004.whosbook.member.entity.Member;
import com.seb_main_004.whosbook.subscribe.entity.Subscribe;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import org.springframework.data.jpa.repository.Query;

public interface SubscribeRepository extends JpaRepository<Subscribe, Long> {

    //구독한 멤버와 구독된 멤버 외래키를 통해 구독객체를 탐색
    Optional<Subscribe> findBySubscriberAndSubscribedMember(Member subscriber, Member subscribedMember);
    Page<Subscribe> findBySubscriber(Member subscriber, Pageable pageable);
    
    //가장 많이 구독된 멤버아이디 탐색
    @Query("SELECT s.subscribedMember.id " +
        "FROM Subscribe s " +
        "GROUP BY s.subscribedMember " +
        "ORDER BY COUNT(s.subscribedMember) DESC")
    Long findMostSubscribedMemberId();
}
