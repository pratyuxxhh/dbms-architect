package com.example.buildmyschema.entity;

import lombok.Data;

@Data
public class SequenceEntity {

    private String name;
    private long startWith;
    private long incrementBy;
    private Long minValue;
    private Long maxValue;
    private boolean cycle;
}
