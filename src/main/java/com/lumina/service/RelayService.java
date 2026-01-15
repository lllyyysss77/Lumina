package com.lumina.service;

import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.http.ResponseEntity;
import reactor.core.publisher.Mono;
import java.util.Map;

public interface RelayService {
    Mono<ResponseEntity<?>> relay(String type, ObjectNode params, Map<String, String> queryParams);


    Mono<ResponseEntity<?>> relay(String type,String modelAction, ObjectNode params, Map<String, String> queryParams);
}