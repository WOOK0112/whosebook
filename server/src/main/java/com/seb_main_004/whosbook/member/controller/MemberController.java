package com.seb_main_004.whosbook.member.controller;

import com.seb_main_004.whosbook.auth.jwt.JwtTokenizer;
import com.seb_main_004.whosbook.curation.entity.Curation;
import com.seb_main_004.whosbook.curation.mapper.CurationMapper;
import com.seb_main_004.whosbook.curation.service.CurationService;
import com.seb_main_004.whosbook.exception.BusinessLogicException;
import com.seb_main_004.whosbook.exception.ExceptionCode;
import com.seb_main_004.whosbook.member.dto.*;
import com.seb_main_004.whosbook.dto.MultiResponseDto;
import com.seb_main_004.whosbook.member.entity.Member;
import com.seb_main_004.whosbook.member.mapper.MemberMapperClass;
import com.seb_main_004.whosbook.member.service.MemberService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.validation.Valid;
import javax.validation.constraints.Positive;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/members")
@Validated
@Slf4j
public class MemberController {
    private final MemberService memberService;
    private final MemberMapperClass memberMapperClass;
    private final CurationService curationService;
    private final CurationMapper curationMapper;

    private final JwtTokenizer jwtTokenizer;

    public MemberController(MemberService memberService, MemberMapperClass memberMapperClass, CurationService curationService, CurationMapper curationMapper, JwtTokenizer jwtTokenizer) {
        this.memberService = memberService;
        this.memberMapperClass = memberMapperClass;
        this.curationService = curationService;
        this.curationMapper = curationMapper;
        this.jwtTokenizer = jwtTokenizer;
    }

    //일반 회원가입
    @PostMapping(consumes = {MediaType.APPLICATION_JSON_VALUE, MediaType.MULTIPART_FORM_DATA_VALUE})
    public ResponseEntity postMember(@Valid @RequestPart MemberPostDto memberPostDto,
                                     @RequestPart MultipartFile memberImage) {
        Member member = memberService.createMember(memberMapperClass.memberPostDtoToMember(memberPostDto), memberImage);

        return new ResponseEntity(memberMapperClass.memberToMemberResponseDto(member), HttpStatus.OK);
    }

    //소셜 회원가입
    @PostMapping(value = "/social", consumes = {MediaType.APPLICATION_JSON_VALUE, MediaType.MULTIPART_FORM_DATA_VALUE})
    public ResponseEntity postSocialMember(@Valid @RequestPart SocialMemberPostDto memberPostDto,
                                           @RequestPart MultipartFile memberImage,
                                           HttpServletResponse response,
                                           Authentication authentication) {
        Member member = memberService.createGoogleMember02(memberMapperClass.socialMemberPostDtoToMember(memberPostDto), memberImage);

        String accessToken=delegateAccessToken(member.getEmail(), member.getRoles());
        String refreshToken=delegateRefreshToken(member.getEmail());
        response.setHeader("access_token","Bearer "+accessToken);
        response.setHeader("refresh_token",refreshToken);

        return new ResponseEntity(memberMapperClass.memberToMemberResponseDto(member), HttpStatus.OK);
    }

    //회원정보 수정
    @PatchMapping(consumes = {MediaType.APPLICATION_JSON_VALUE, MediaType.MULTIPART_FORM_DATA_VALUE})
    public ResponseEntity patchMember(@Valid @RequestPart MemberPatchDto memberPatchDto,
                                      @RequestPart MultipartFile memberImage) {
        Member member = memberMapperClass.memberPatchDtoToMember(memberPatchDto);
        Member response = memberService.updateMember(member, memberPatchDto.isBasicImage(),
                                                     memberImage, getAuthenticatedEmail());
        return new ResponseEntity(memberMapperClass.memberToMemberResponseDto(response), HttpStatus.OK);
    }

    //마이페이지 조회
    @GetMapping("/mypage")
    public ResponseEntity getMyPage(Authentication authentication) {
        if(authentication == null){
            throw new BusinessLogicException(ExceptionCode.MEMBER_NOT_FOUND);
        }
        String userEmail = authentication.getPrincipal().toString();
        Member findMember = memberService.findVerifiedMemberByEmail(userEmail);

        return new ResponseEntity(memberMapperClass.memberToMemberResponseDto(findMember), HttpStatus.OK);
    }

    //관리자 페이지 조회
    @GetMapping("/adminpage")
    public ResponseEntity getAdminPage(Authentication authentication) {
        if(authentication == null){
            throw new BusinessLogicException(ExceptionCode.MEMBER_NOT_FOUND);
        }
        String userEmail = authentication.getPrincipal().toString();

        Long totalMembers = memberService.findTotalMembers(userEmail);
        Member mostSubsripedMember = memberService.findMemberByMostSubscription(userEmail);
        List<Member> mostCurationMembers = memberService.findMemberByMostCuration(userEmail);
        Long totalCurations = curationService.findTotalCurations(userEmail);

        return new ResponseEntity(memberMapperClass.adminResponseDto(totalMembers, mostSubsripedMember, mostCurationMembers, totalCurations)
            , HttpStatus.OK);
    }

    //타 유저 마이페이지 조회
    @GetMapping("/{member-id}")
    public ResponseEntity getOtherMemberPage(@Valid @PathVariable("member-id") long otherMemberId, HttpServletRequest request) {
        Exception exception = (Exception) request.getAttribute("exception");

        if (exception != null) {
            if (exception.getMessage().contains("JWT expired")){
                throw new BusinessLogicException(ExceptionCode.JWT_EXPIRED);
            }
        }

        Member otherMember = memberService.findVerifiedMemberByMemberId(otherMemberId);

        //비회원이 조회할 때
        if(SecurityContextHolder.getContext().getAuthentication().getPrincipal().toString().equals("anonymousUser")) {
            return new ResponseEntity(memberMapperClass.memberToOtherMemberResponseDto(otherMember, false), HttpStatus.OK);
        }
        //회원이 조회할 때
        boolean isSubscribed = memberService.findIsSubscribed(getAuthenticatedEmail(), otherMember);
        return new ResponseEntity(memberMapperClass.memberToOtherMemberResponseDto(otherMember, isSubscribed),
                HttpStatus.OK);
    }

    //내가 작성한 큐레이션 리스트 조회
    @GetMapping("/mypage/curations")
    public ResponseEntity getMyCurations(@Positive @RequestParam("page") int page,
                                          @Positive @RequestParam("size") int size) {
        Member member = memberService.findVerifiedMemberByEmail(getAuthenticatedEmail());
        Page<Curation> curationPage = curationService.getMyCurations(page-1, size, member);
        List<Curation> curations = curationPage.getContent();

        return new ResponseEntity(new MultiResponseDto<>(
                curationMapper.curationsToCurationMultiListResponseDtos(curations), curationPage),
                HttpStatus.OK);
    }

    //타 유저가 작성한 큐레이션 리스트 조회
    @GetMapping("/curations/{member-id}")
    public ResponseEntity getMyCurations(@Valid @PathVariable("member-id") long otherMemberId,
                                         @Positive @RequestParam("page") int page,
                                         @Positive @RequestParam("size") int size) {
        Member member = memberService.findVerifiedMemberByMemberId(otherMemberId);
        Page<Curation> curationPage = curationService.getOtherMemberCurations(page-1, size, member);
        List<Curation> curations = curationPage.getContent();

        return new ResponseEntity(new MultiResponseDto<>(
                curationMapper.curationsToCurationMultiListResponseDtos(curations), curationPage),
                HttpStatus.OK);
    }

    //내가 구독한 큐레이터 리스트 조회
    @GetMapping("/mypage/subscribe")
    public ResponseEntity getMyMembers(@Positive @RequestParam("page") int page,
                                       @Positive @RequestParam("size") int size) {
        Page<Member> pageMember = memberService.findMyMembers(page-1, size, getAuthenticatedEmail());
        List<Member> members = pageMember.getContent(); //구독한 멤버리스트

        return new ResponseEntity(
                new MultiResponseDto(memberMapperClass.subscribingMembersToMemberResponseDtos(members),
                        pageMember), HttpStatus.OK);
    }

    //내가 좋아요한 큐레이션 리스트 조회
    @GetMapping("/mypage/like")
    public ResponseEntity getMyLikeCurations(@Positive @RequestParam("page") int page,
                                             @Positive @RequestParam("size") int size) {
        Member member = memberService.findVerifiedMemberByEmail(getAuthenticatedEmail());
        Page<Curation> curationPage = curationService.getMyLikeCuration(page-1, size, member);
        List<Curation> curations = curationPage.getContent();

        return new ResponseEntity(new MultiResponseDto<>(
                curationMapper.curationsToCurationMultiListResponseDtos(curations), curationPage),
                HttpStatus.OK);
    }

    //타 유저가 좋아요한 큐레이션 리스트 조회
    @GetMapping("/like/{member-id}")
    public ResponseEntity getMyLikeCurations(@Valid @PathVariable("member-id") long otherMemberId,
                                             @Positive @RequestParam("page") int page,
                                             @Positive @RequestParam("size") int size) {
        Member member = memberService.findVerifiedMemberByMemberId(otherMemberId);
        Page<Curation> curationPage = curationService.getMyLikeCuration(page-1, size, member);
        List<Curation> curations = curationPage.getContent();

        return new ResponseEntity(new MultiResponseDto<>(
                curationMapper.curationsToCurationMultiListResponseDtos(curations), curationPage),
                HttpStatus.OK);
    }

    @GetMapping("/best")
    public ResponseEntity getBestCurators(@Positive @RequestParam("page") int page,
                                          @Positive @RequestParam("size") int size) {
        Page<BestCuratorDto> memberPage = memberService.findBestCurators(page - 1, size);
        List<BestCuratorDto> members = memberPage.getContent();

        return new ResponseEntity(new MultiResponseDto<>(members, memberPage
        ), HttpStatus.OK);
    }

    @DeleteMapping
    public ResponseEntity deleteMember() {
        memberService.deleteMember(getAuthenticatedEmail());

        return new ResponseEntity(HttpStatus.NO_CONTENT);
    }


    private String getAuthenticatedEmail(){
        return SecurityContextHolder
                .getContext()
                .getAuthentication()
                .getPrincipal()
                .toString();
    }

    private String delegateAccessToken(String username, List<String> authorities) {
        Map<String, Object> claims = new HashMap<>();
        claims.put("username", username);
        claims.put("roles", authorities);

        String subject = username;
        Date expiration = jwtTokenizer.getTokenExpiration(jwtTokenizer.getAccessTokenExpirationMinutes());

        String base64EncodedSecretKey = jwtTokenizer.encodeBase64SecretKey(jwtTokenizer.getSecretKey());

        String accessToken = jwtTokenizer.generateAccessToken(claims, subject, expiration, base64EncodedSecretKey);

        return accessToken;
    }

    private String delegateRefreshToken(String username) {
        String subject = username;
        Date expiration = jwtTokenizer.getTokenExpiration(jwtTokenizer.getRefreshTokenExpirationMinutes());
        String base64EncodedSecretKey = jwtTokenizer.encodeBase64SecretKey(jwtTokenizer.getSecretKey());

        String refreshToken = jwtTokenizer.generateRefreshToken(subject, expiration, base64EncodedSecretKey);

        return refreshToken;
    }
}
