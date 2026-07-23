package com.example.buildmyschema.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Slf4j
@Service
public class VectorService {
    @Autowired
    private VectorStore vectorStore;

    public void addVectorDocuments() {

        List<Document> documents = List.of(

                new Document("""
        This assistant specializes exclusively in relational database schema generation.

        Supported tasks:
        - Generate SQL schema
        - Design relational databases
        - Design entities
        - Create tables
        - Generate ER models
        - Define relationships
        - Generate DDL
        - Create indexes
        - Add constraints
        - Normalize schemas
        - Generate seed data
        - Improve existing database schemas

        Unsupported tasks:
        - Programming
        - Mathematics
        - History
        - Politics
        - Sports
        - Movies
        - General knowledge
        """),
                new Document("""
        Database concepts include:
        Database
        Schema
        Table
        Column
        Row
        Entity
        Attribute
        Primary Key
        Foreign Key
        Composite Key
        Candidate Key
        Alternate Key
        Unique Key
        Check Constraint
        Default Constraint
        Index
        View
        Sequence
        Trigger
        Stored Procedure
        """),
                new Document("""
        Normalization rules.
        Apply First Normal Form.
        Apply Second Normal Form.
        Apply Third Normal Form.
        Apply Boyce Codd Normal Form when applicable.
        Remove duplicate data.
        Reduce redundancy.
        Eliminate insertion anomalies.
        Eliminate update anomalies.
        Eliminate deletion anomalies.
        """),
                new Document("""
        SQL generation rules.
        Always generate:
        CREATE SCHEMA
        CREATE TABLE
        PRIMARY KEY
        FOREIGN KEY
        NOT NULL
        UNIQUE
        CHECK
        DEFAULT
        CREATE INDEX
        INSERT seed data
        Output only valid SQL syntax.
        """),
                new Document("""
        Naming conventions.
        Use snake_case.
        Table names should be plural.
        Column names should be meaningful.
        Primary key column should be id.
        Foreign keys should use table_id format.
        Avoid reserved SQL keywords.
        """),
                new Document("""
        Relationship types.
        One to One
        One to Many
        Many to Many
        Recursive Relationships
        Junction tables should resolve many-to-many relationships.
        """),
                new Document("""
        Constraints.
        Primary Key
        Foreign Key
        Unique
        Not Null
        Check
        Default
        Auto Increment
        Identity
        """),
                new Document("""
        Supported SQL databases.
        PostgreSQL
        MySQL
        MariaDB
        Oracle
        SQL Server
        SQLite
        """),
                new Document("""
        Supported business domains.
        Restaurant
        Hospital
        Banking
        School
        College
        University
        Hotel
        Inventory
        Warehouse
        E-Commerce
        Food Delivery
        Ride Sharing
        Library
        CRM
        ERP
        Payroll
        HRMS
        Hospital Management
        Pharmacy
        Airline Reservation
        Hotel Booking
        Movie Ticket Booking
        Gym Management
        UPI Payment System
        """),
                new Document("""
        Restaurant schema usually contains:
        Restaurant
        Customer
        Employee
        Menu Item
        Category
        Order
        Order Item
        Payment
        Reservation
        Table
        Kitchen Order
        """),
                new Document("""
        Hospital schema usually contains:
        Patient
        Doctor
        Appointment
        Prescription
        Medicine
        Department
        Ward
        Billing
        Laboratory
        Medical Record
        """),
                new Document("""
        Banking schema usually contains:
        Customer
        Account
        Transaction
        Branch
        Loan
        Card
        Beneficiary
        UPI
        Payment
        Statement
        """),
                new Document("""
        Ecommerce schema usually contains:
        User
        Product
        Category
        Inventory
        Supplier
        Cart
        Cart Item
        Order
        Order Item
        Payment
        Shipment
        Review
        Wishlist
        """),
                new Document("""
        Common SQL data types.
        INTEGER
        BIGINT
        SMALLINT
        DECIMAL
        NUMERIC
        FLOAT
        DOUBLE
        VARCHAR
        CHAR
        TEXT
        BOOLEAN
        DATE
        TIME
        TIMESTAMP
        UUID
        JSON
        JSONB
        BLOB
        """),
                new Document("""
        Schema generation workflow.
        Read requirements.
        Identify entities.
        Identify attributes.
        Identify relationships.
        Determine cardinality.
        Select primary keys.
        Add foreign keys.
        Choose data types.
        Normalize schema.
        Add indexes.
        Add constraints.
        Generate SQL.
        """)
        );
        vectorStore.add(documents);
    }


    public boolean isRelevantPrompt(String prompt) {
        List<Document> docs = vectorStore.similaritySearch(
                SearchRequest.builder()
                        .query(prompt)
                        .topK(1)
                        .build()
        );

        if (docs.isEmpty()) {
            return false;
        }

        double distance = ((Number) docs.getFirst()
                .getMetadata()
                .get("distance"))
                .doubleValue();
        log.info("{}", distance);
        return distance <= 0.60;
    }

    public void testSimilaritySearch() {

        List<String> prompts = List.of(
                "Create a hospital database",
                "Design a student attendance schema",
                "Generate SQL for an ecommerce app",
                "Make a banking database",
                "Create a restaurant management system",

                "Who is Virat Kohli?",
                "Write a Java program",
                "Explain Spring Boot",
                "Tell me a joke",
                "What's the weather today?"
        );

        for (String prompt : prompts) {

            System.out.println("\n====================================");
            System.out.println("Prompt: " + prompt);

            List<Document> docs = vectorStore.similaritySearch(
                    SearchRequest.builder()
                            .query(prompt)
                            .topK(1)
                            .build()
            );

            if (docs.isEmpty()) {
                System.out.println("No document found.");
                continue;
            }

            Document doc = docs.getFirst();
//
//            System.out.println("Retrieved Document:");
//            System.out.println(doc.getText());

            System.out.println("Metadata:");
            System.out.println(doc.getMetadata());
        }
    }
}
