package kr.busan.safetrip.photo.controller;

import jakarta.validation.Valid;
import org.springframework.http.MediaType;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import kr.busan.safetrip.common.api.Lang;
import kr.busan.safetrip.photo.dto.PhotoDtos;
import kr.busan.safetrip.photo.service.PhotoMatchService;
import kr.busan.safetrip.photo.service.PlaceNarrationService;
import kr.busan.safetrip.photo.service.PlaceSearchService;

/**
 * [사진 담당] COMP-P02 PhotoController
 * 요구사항: FR-P001~P003, P009, P011, P016
 * 상태: 엔드포인트 완료(위임만)
 * 수정 규칙: 사진 담당만 수정. 코스·긴급 담당은 수정 금지(필요하면 사진 담당에게 요청).
 *
 * HTTP 입출구, 입력 검증(형식 jpg/jpeg/png·10MB·언어 코드). 사진은 메모리에서만 처리(NFR-P002). 경로는 SDD SD-P01·P02 그대로.
 */
@RestController
@RequestMapping("/api/v1")
public class PhotoController {
    private final PhotoMatchService service;
    private final PlaceSearchService search;
    private final PlaceNarrationService narration;

    public PhotoController(PhotoMatchService service, PlaceSearchService search, PlaceNarrationService narration) {
        this.service = service;
        this.search = search;
        this.narration = narration;
    }

    /** API-P01 간판 매칭. 좌표는 요청 처리에만 쓰고 저장하지 않는다. */
    @PostMapping(value = "/photo/match", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public PhotoDtos.MatchResponse match(@RequestPart("image") MultipartFile image,
                                         @RequestParam(required = false) Double lat,
                                         @RequestParam(required = false) Double lng,
                                         @RequestParam(defaultValue = "ko") String lang) {
        return service.match(image, lat, lng, Lang.parse(lang));
    }

    /** API-P02 가게 설명(식별과 분리) */
    @PostMapping("/places/{contentId}/narration")
    public PhotoDtos.NarrationResponse narrate(@PathVariable String contentId, @Valid @RequestBody PhotoDtos.NarrationRequest req) {
        return narration.narrate(contentId, Lang.parse(req.lang()), req.matchId());
    }

    /** API-P03 이름 직접 검색 */
    @GetMapping("/places/search")
    public PhotoDtos.MatchResponse search(@RequestParam("q") String q, @RequestParam(defaultValue = "ko") String lang) {
        return search.search(q, Lang.parse(lang));
    }
}
