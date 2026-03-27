package com.lumina.dto;

import lombok.Data;

@Data
public class RequestLogPayloadDto {
    private String id;
    private String requestContent;
    private String responseContent;
}
