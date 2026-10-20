package nqt.base_java_spring_be.veo;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.UUID;

@RestController
@CrossOrigin
@RequestMapping("/api/veo/videos")
@RequiredArgsConstructor
public class VeoVideoController {
    private final VeoVideoService service;

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<VeoVideoStartResponse> start(
            @RequestParam String prompt,
            @RequestParam(required = false) String shotId,
            @RequestParam(defaultValue = "veo-3.1-generate-preview") String model,
            @RequestParam(defaultValue = "16:9") String aspectRatio,
            @RequestParam(defaultValue = "720p") String resolution,
            @RequestParam(defaultValue = "8") int durationSeconds,
            @RequestPart(value = "image", required = false) MultipartFile image
    ) {
        return ResponseEntity.accepted().body(
                service.start(prompt, shotId, model, aspectRatio, resolution, durationSeconds, image)
        );
    }

    @GetMapping("/{id}")
    public ResponseEntity<VeoVideoStatusResponse> status(@PathVariable UUID id) {
        return ResponseEntity.ok(service.status(id));
    }

    @GetMapping("/{id}/download")
    public ResponseEntity<byte[]> download(@PathVariable UUID id) {
        VeoVideoService.DownloadedVideo video = service.download(id);
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.parseMediaType("video/mp4"));
        headers.setContentDisposition(ContentDisposition.attachment().filename(video.fileName()).build());
        headers.setContentLength(video.bytes().length);
        return ResponseEntity.ok().headers(headers).body(video.bytes());
    }
}
