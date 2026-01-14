package com.lumina.service;

import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.Map;

public interface RelayService {
    Object relay(String type, ObjectNode params, Map<String, String> queryParams);
}