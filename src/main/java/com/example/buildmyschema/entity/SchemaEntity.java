package com.example.buildmyschema.entity;


import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class SchemaEntity {

    private String name;
    private List<TableEntity> tables;
    private List<RelationEntity> relationships;
    private List<ViewEntity> views;
    private List<SequenceEntity> sequences;
}
