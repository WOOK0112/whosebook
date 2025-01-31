package com.seb_main_004.whosbook.curation.service;

import com.seb_main_004.whosbook.book.BookService;
import com.seb_main_004.whosbook.book.entity.Book;
import com.seb_main_004.whosbook.book.entity.BookCuration;
import com.seb_main_004.whosbook.book.repository.BookCurationRepository;
import com.seb_main_004.whosbook.curation.category.CategoryService;
import com.seb_main_004.whosbook.curation.dto.CurationPatchDto;
import com.seb_main_004.whosbook.curation.dto.CurationPostDto;
import com.seb_main_004.whosbook.curation.entity.Curation;
import com.seb_main_004.whosbook.curation.entity.CurationImage;
import com.seb_main_004.whosbook.curation.entity.CurationSaveImage;
import com.seb_main_004.whosbook.curation.repository.CurationRepository;
import com.seb_main_004.whosbook.curation.repository.CurationSaveImageRepository;
import com.seb_main_004.whosbook.exception.BusinessLogicException;
import com.seb_main_004.whosbook.exception.ExceptionCode;
import com.seb_main_004.whosbook.like.entity.CurationLike;
import com.seb_main_004.whosbook.like.repository.CurationLikeRepository;
import com.seb_main_004.whosbook.member.entity.Member;
import com.seb_main_004.whosbook.member.service.MemberService;
import com.seb_main_004.whosbook.subscribe.entity.Subscribe;
import com.seb_main_004.whosbook.subscribe.repository.SubscribeRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class CurationService {
    // 추후 리팩토링 : 1. 삭제된 큐레이션을 검증하는 부분을 AOP로 뺄 순 없을까?
    //             : 2. 큐레이션 이미지 등록 로직을 어떻게 분리하고 구성해야 더 효율적일까?

    private final CurationRepository curationRepository;
    private final MemberService memberService;
    private final CurationSaveImageRepository curationSaveImageRepository;
    private final CurationImageService curationImageService;
    private final CategoryService categoryService;
    private final CurationLikeRepository curationLikeRepository;
    private final SubscribeRepository subscribeRepository;
    private final BookService bookService;
    private final BookCurationRepository bookCurationRepository;

    @Transactional
    public Curation createCuration(Curation curation, CurationPostDto postDto, String authenticatedEmail){

        Member member = memberService.findVerifiedMemberByEmail(authenticatedEmail);

        curation.setMember(member);
        curation.setCategory(categoryService.findVerifiedCategory(postDto.getCategoryId()));

        Curation savedCuration = curationRepository.save(curation);

        // 저장된 큐레이션과 책 연결
        Book savedBook = bookService.getSavedBook(postDto.getBooks());
        bookCurationRepository.save(new BookCuration(savedBook, savedCuration));

        // 이미지 저장 로직
        if (!postDto.getImageIds().isEmpty()){
            log.info("# 포스트 중 삭제된 이미지 없는지 검증실행 ");
            List<CurationImage> curationImages = curationImageService.verifyCurationSaveImages(postDto, member.getMemberId());

            log.info("# 검증된 이미지와 큐레이션 DB 연결 실행");
            for (CurationImage curationImage : curationImages) {
                curationSaveImageRepository.save(new CurationSaveImage(savedCuration, curationImage));
                log.info("# 작성된 큐레이션과 이미지 연결 완료!");
            }
        }

        return savedCuration;
    }

    public Curation updateCuration(CurationPatchDto patchDto, long curationId, String authenticatedEmail){

        Curation findCuration = findVerifiedCurationById(curationId);

        checkCurationIsDeleted(findCuration);
        verifyCuratorForUpdate(findCuration, authenticatedEmail);

        findCuration.updateCurationData(patchDto);
        findCuration.setCategory(categoryService.findVerifiedCategory(patchDto.getCategoryId()));

        // TODO: 추후에 여러개의 책을 등록하게 된다면 수정 방식 변경 필요, 지금은 단일 등록 상황만 고려
        BookCuration bookCuration = findCuration.getBookCurations().get(0);
        if (bookCuration.getBook().getIsbn().equals(patchDto.getBooks().getIsbn()) == false){
            Book book = bookService.getSavedBook(patchDto.getBooks());
            bookCuration.setBook(book);
            BookCuration savedBookCuration = bookCurationRepository.save(bookCuration);
        }

        if (!patchDto.getImageIds().isEmpty()){
            log.info("# 포스트 중 삭제된 이미지 없는지 검증실행 ");

            List<CurationImage> curationImages = curationImageService.verifyCurationSaveImages(patchDto, findCuration.getMember().getMemberId());

            log.info("# 검증된 이미지와 큐레이션 DB 연결 실행");
            for (CurationImage curationImage : curationImages) {
                curationSaveImageRepository.save(new CurationSaveImage(findCuration, curationImage));
                log.info("# 작성된 큐레이션과 이미지 연결 완료!");
            }
        }

        return curationRepository.save(findCuration);
    }

    public void deleteCuration(long curationId, String authenticatedEmail){
        Curation curation = findVerifiedCurationById(curationId);

        if (curation.getMember().getEmail().equals(authenticatedEmail) == false){
            throw new BusinessLogicException(ExceptionCode.CURATION_CANNOT_DELETE);
        }

        // 이미 삭제된 큐레이션을 또 삭제하려는 요청에 대한 에러처리
        checkCurationIsDeleted(curation);

        curation.setCurationStatus(Curation.CurationStatus.CURATION_DELETE);
        curationRepository.save(curation);
        log.info("# Curation ID : {} 삭제되었습니다.", curation.getCurationId());
    }

    //총 게시글 조회(관리자 전용)
    public Long findTotalCurations(String email) {
        if(email.equals("admin@email.com")){
            return curationRepository.count();
        }
        throw new BusinessLogicException(ExceptionCode.MEMBER_NO_HAVE_AUTHORIZATION);
    }

    public Curation getCuration(long curationId, String authenticatedEmail) {

        Curation curation = findVerifiedCurationById(curationId);

        checkCurationIsDeleted(curation);

        if (curation.getVisibility() == Curation.Visibility.SECRET) {
            if ((curation.getMember().getEmail().equals(authenticatedEmail)) == false)
                throw new BusinessLogicException(ExceptionCode.CURATION_ACCESS_DENIED);
        }

        if(!authenticatedEmail.equals("anonymousUser")) {
            Member member =  memberService.findVerifiedMemberByEmail(authenticatedEmail);
            Optional<CurationLike> curationLike = curationLikeRepository.findByCurationAndMember(curation, member);

            Optional<Subscribe> subscribe = subscribeRepository.findBySubscriberAndSubscribedMember(
                    member, curation.getMember()
            );

            if(curationLike.isPresent()) curation.setLiked(true);
            if(subscribe.isPresent()) curation.setSubscribed(true);
        }


        return curation;
    }
    @Transactional(readOnly = true)
    public Page<Curation> getNewCurations(int page, int size, Long categoryId){
        // 추후 리팩토링 필요
        return curationRepository.findCurationList(
                categoryId,
                PageRequest.of(page, size, Sort.by("curationId").descending()));
    }

    @Transactional(readOnly = true)
    public Page<Curation> getBestCurations(int page, int size, Long categoryId){
        // 추후 리팩토링 필요
        return curationRepository.findCurationList(
                categoryId,
                PageRequest.of(page, size, Sort.by("curationLikeCount").descending()));
    }

    @Transactional(readOnly = true)
    public Page<Curation> getCategoryCurations(long categoryId, int page, int size){
        return curationRepository.findByCategoryAndCurationStatusAndVisibility(
                categoryService.findVerifiedCategory(categoryId),
                Curation.CurationStatus.CURATION_ACTIVE,
                Curation.Visibility.PUBLIC,
                PageRequest.of(page, size, Sort.by("curationId").descending())
        );
    }

    public List<Curation> getMyCurations(Member member) {
        List<Curation> myCurations = curationRepository.findByMemberAndCurationStatus(
                member,
                Curation.CurationStatus.CURATION_ACTIVE);

        return myCurations;
    }

    //내가 쓴 큐레이션 목록 조회
    public Page<Curation> getMyCurations(int page, int size, Member member) {
        Page<Curation> myCurations = curationRepository.findByMemberAndCurationStatus(
                member,
                Curation.CurationStatus.CURATION_ACTIVE,
                PageRequest.of(page, size));

        return myCurations;
    }

    //내가 좋아요한 큐레이션 목록 조회
    public Page<Curation> getMyLikeCuration(int page, int size, Member member) {
        Page<Curation> myCurations = curationRepository.findByLikeCurations(member.getMemberId(), PageRequest.of(page, size));

        return myCurations;
    }

    //타 유저가 쓴 큐레이션 목록 조회
    public Page<Curation> getOtherMemberCurations(int page, int size, Member member) {
        Page<Curation> myCurations = curationRepository.findByMemberAndCurationStatusAndVisibility(
                member,
                Curation.CurationStatus.CURATION_ACTIVE,
                Curation.Visibility.PUBLIC,
                PageRequest.of(page, size));

        return myCurations;
    }

    public Curation findVerifiedCurationById(long curationId) {
        Optional<Curation> optionalCuration = curationRepository.findById(curationId);
        return optionalCuration.orElseThrow(
                () -> new BusinessLogicException(ExceptionCode.CURATION_NOT_FOUND)
        );
    }

    public void checkCurationIsDeleted(Curation curation){
        if (curation.isDeleted()) throw new BusinessLogicException(ExceptionCode.CURATION_HAS_BEEN_DELETED);
    }

    @Transactional
    public Curation saveCuration(Curation curation) {

        return curationRepository.save(curation);
    }

    public void verifyCuratorForUpdate(Curation curation, String authenticatedEmail){
        if(curation.getMember().getEmail()
                .equals(authenticatedEmail) == false) {
            throw new BusinessLogicException(ExceptionCode.CURATION_CANNOT_CHANGE);
        }
    }
}
