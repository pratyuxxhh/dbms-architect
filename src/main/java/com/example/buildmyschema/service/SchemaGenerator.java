package com.example.buildmyschema.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.IOException;

@Service
public class SchemaGenerator {
    @Autowired
    private MicrosoftSqlGenerator microsoftSqlGenerator;
    @Autowired
    private PostGressGenerator postGressGenerator;
    @Autowired
    private OracleSqlGenerator  oracleSqlGenerator;
    @Autowired
    private SqlGenerator sqlGenerator;
    public File createForSql(String json) throws SqlGenerator.InvalidSchemaException, IOException {
        File result = sqlGenerator.generateDownloadableFile(json);
        System.out.println("SQL file generated successfully: " + result.getAbsolutePath());
        return result;
    }
    public File createForPostgress(String json) throws PostGressGenerator.InvalidSchemaException, IOException {
        File result = postGressGenerator.generateDownloadableFile(json);
        System.out.println("Postgres SQL file generated successfully: " + result.getAbsolutePath());
        return result;
    }
    public File createForOracle(String json) throws IOException, OracleSqlGenerator.InvalidSchemaException {
        File result = oracleSqlGenerator.generateDownloadableFile(json);
        System.out.println("Postgres SQL file generated successfully: " + result.getAbsolutePath());
        return result;
    }
    public File createForMicrosoft(String json) throws MicrosoftSqlGenerator.InvalidSchemaException, IOException {
        File result = microsoftSqlGenerator.generateDownloadableFile(json);
        System.out.println("Postgres SQL file generated successfully: " + result.getAbsolutePath());
        return result;
    }

}
