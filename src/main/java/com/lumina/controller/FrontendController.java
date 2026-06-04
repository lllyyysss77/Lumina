package com.lumina.controller;

import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

@RestController
public class FrontendController {

    @GetMapping(value = "/", produces = MediaType.TEXT_HTML_VALUE)
    public Mono<ResponseEntity<Resource>> index() {
        return Mono.just(ResponseEntity.ok()
                .contentType(MediaType.TEXT_HTML)
                .body(new ClassPathResource("static/index.html")));
    }
}
