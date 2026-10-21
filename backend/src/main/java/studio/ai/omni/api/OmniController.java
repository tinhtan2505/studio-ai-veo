package studio.ai.omni.api;

import jakarta.validation.Valid;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.ResourceRegion;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpRange;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import studio.ai.omni.output.OutputStore;
import studio.ai.omni.service.OmniService;

import java.io.IOException;
import java.util.List;
import java.util.UUID;

@Validated
@RestController
@RequestMapping("/api/omni")
public class OmniController {
    private final OmniService service;

    public OmniController(OmniService service) {
        this.service = service;
    }

    @GetMapping("/config")
    public OmniConfigResponse config() {
        return service.config();
    }

    @PostMapping(value = "/media", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<OmniMediaResponse> upload(@RequestPart("file") MultipartFile file) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.upload(file));
    }

    @GetMapping("/media/{fileId}")
    public OmniMediaResponse media(@PathVariable String fileId) {
        return service.media(fileId);
    }

    @DeleteMapping("/media/{fileId}")
    public ResponseEntity<Void> deleteMedia(@PathVariable String fileId) {
        service.deleteMedia(fileId);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/jobs")
    public ResponseEntity<OmniJobResponse> start(@Valid @RequestBody OmniJobRequest request) {
        return ResponseEntity.accepted().body(service.start(request));
    }

    @GetMapping("/jobs/{jobId}")
    public OmniJobResponse job(@PathVariable UUID jobId) {
        return service.job(jobId);
    }

    @GetMapping("/outputs/{interactionId}")
    public ResponseEntity<?> output(
            @PathVariable String interactionId,
            @RequestParam(defaultValue = "false") boolean download,
            @org.springframework.web.bind.annotation.RequestHeader(value = HttpHeaders.RANGE, required = false)
            String rangeHeader
    ) throws IOException {
        OutputStore.StoredOutput output = service.output(interactionId);
        Resource resource = output.resource();
        long length = resource.contentLength();
        HttpHeaders headers = outputHeaders(output, download);
        headers.set(HttpHeaders.ACCEPT_RANGES, "bytes");

        if (rangeHeader == null || rangeHeader.isBlank()) {
            headers.setContentLength(length);
            return ResponseEntity.ok().headers(headers).body(resource);
        }

        List<HttpRange> ranges = HttpRange.parseRanges(rangeHeader);
        HttpRange range = ranges.getFirst();
        long start = range.getRangeStart(length);
        long end = range.getRangeEnd(length);
        ResourceRegion region = new ResourceRegion(resource, start, end - start + 1);
        return ResponseEntity.status(HttpStatus.PARTIAL_CONTENT).headers(headers).body(region);
    }

    @DeleteMapping("/outputs/{interactionId}")
    public ResponseEntity<Void> deleteOutput(@PathVariable String interactionId) {
        service.deleteOutput(interactionId);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/interactions/{interactionId}")
    public ResponseEntity<Void> deleteInteraction(@PathVariable String interactionId) {
        service.deleteInteraction(interactionId);
        return ResponseEntity.noContent().build();
    }

    private HttpHeaders outputHeaders(OutputStore.StoredOutput output, boolean download) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.parseMediaType(output.metadata().mimeType()));
        String shot = output.metadata().shotId().replaceAll("[^A-Za-z0-9._-]", "_");
        String fileName = shot + "-" + output.metadata().interactionId() + ".mp4";
        ContentDisposition disposition = download
                ? ContentDisposition.attachment().filename(fileName).build()
                : ContentDisposition.inline().filename(fileName).build();
        headers.setContentDisposition(disposition);
        headers.setCacheControl("private, max-age=3600");
        return headers;
    }
}
