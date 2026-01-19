package com.lumina.dto;

import lombok.Data;

import java.util.List;

@Data
public class ModelGroupConfig {

    private String id;

    private String name;

    private Integer balanceMode;

    private Integer firstTokenTimeout;

    private List<ModelGroupConfigItem> items;
}
