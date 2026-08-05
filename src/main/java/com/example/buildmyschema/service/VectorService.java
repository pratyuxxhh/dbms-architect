package com.example.buildmyschema.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

@Slf4j
@Service
public class VectorService {

    @Autowired
    private VectorStore vectorStore;

    private static final String LABEL_KEY = "intent";
    private static final String POSITIVE = "schema";
    private static final String NEGATIVE = "off_topic";

    public void addVectorDocuments() {

        List<Document> positive = List.of(
                // ---------- Core task phrasing (natural sentences, no bare word lists) ----------
                new Document("Generate a SQL database schema based on my requirements.", Map.of(LABEL_KEY, POSITIVE)),
                new Document("Design a relational database for the following application.", Map.of(LABEL_KEY, POSITIVE)),
                new Document("Create the tables, primary keys and foreign keys for this system.", Map.of(LABEL_KEY, POSITIVE)),
                new Document("Generate an ER diagram and corresponding SQL DDL for this domain.", Map.of(LABEL_KEY, POSITIVE)),
                new Document("Normalize this database design and remove redundant data.", Map.of(LABEL_KEY, POSITIVE)),
                new Document("Add appropriate indexes and constraints to this schema.", Map.of(LABEL_KEY, POSITIVE)),
                new Document("Generate CREATE TABLE statements with proper relationships.", Map.of(LABEL_KEY, POSITIVE)),
                new Document("Improve and optimize this existing database schema.", Map.of(LABEL_KEY, POSITIVE)),
                new Document("Convert these requirements into a normalized relational schema.", Map.of(LABEL_KEY, POSITIVE)),
                new Document("Generate seed data or sample INSERT statements for this schema.", Map.of(LABEL_KEY, POSITIVE)),
                new Document("Add a new column and a foreign key constraint to this table.", Map.of(LABEL_KEY, POSITIVE)),
                new Document("Suggest a many-to-many relationship design using a junction table.", Map.of(LABEL_KEY, POSITIVE)),
                new Document("Review my schema and point out normalization issues.", Map.of(LABEL_KEY, POSITIVE)),
                new Document("Convert this schema from MySQL syntax to PostgreSQL syntax.", Map.of(LABEL_KEY, POSITIVE)),
                new Document("What primary key and indexing strategy should I use for this table?", Map.of(LABEL_KEY, POSITIVE)),
                new Document("Explain the difference between a primary key and a foreign key.", Map.of(LABEL_KEY, POSITIVE)),
                new Document("What is the difference between one-to-many and many-to-many relationships?", Map.of(LABEL_KEY, POSITIVE)),
                new Document("How do I design a junction table for a many-to-many relationship?", Map.of(LABEL_KEY, POSITIVE)),
                new Document("Should this column be nullable or have a default value?", Map.of(LABEL_KEY, POSITIVE)),
                new Document("Explain first, second and third normal form with an example.", Map.of(LABEL_KEY, POSITIVE)),

                // ---------- Domain-specific requests ----------
                new Document("Design a database schema for a restaurant management system with menu items, orders, and payments.", Map.of(LABEL_KEY, POSITIVE)),
                new Document("Create tables for a restaurant app that handles reservations, tables, and kitchen orders.", Map.of(LABEL_KEY, POSITIVE)),
                new Document("I need a schema for a food ordering system with customers, menu categories, and order items.", Map.of(LABEL_KEY, POSITIVE)),
                new Document("Design a hospital management database with patients, doctors, appointments, and prescriptions.", Map.of(LABEL_KEY, POSITIVE)),
                new Document("Create a schema for a pharmacy system tracking medicines, suppliers, and billing.", Map.of(LABEL_KEY, POSITIVE)),
                new Document("I need tables for a clinic system with departments, wards, and medical records.", Map.of(LABEL_KEY, POSITIVE)),
                new Document("Design a banking database schema with accounts, transactions, branches, and loans.", Map.of(LABEL_KEY, POSITIVE)),
                new Document("Create a schema for a UPI payment system with beneficiaries and transaction history.", Map.of(LABEL_KEY, POSITIVE)),
                new Document("I need tables for a credit card and billing system with statements and payments.", Map.of(LABEL_KEY, POSITIVE)),
                new Document("Design an e-commerce database with products, categories, cart, orders, and payments.", Map.of(LABEL_KEY, POSITIVE)),
                new Document("Create a schema for an online store with inventory, suppliers, shipments, and reviews.", Map.of(LABEL_KEY, POSITIVE)),
                new Document("I need tables for a wishlist and cart feature linked to users and products.", Map.of(LABEL_KEY, POSITIVE)),
                new Document("Design a school management schema with students, teachers, classes, and attendance.", Map.of(LABEL_KEY, POSITIVE)),
                new Document("Create a database for a college with courses, enrollments, grades, and departments.", Map.of(LABEL_KEY, POSITIVE)),
                new Document("I need a library management schema with books, members, and borrowing records.", Map.of(LABEL_KEY, POSITIVE)),
                new Document("Design a hotel booking system schema with rooms, guests, reservations, and billing.", Map.of(LABEL_KEY, POSITIVE)),
                new Document("Create a database for an airline reservation system with flights, seats, and passengers.", Map.of(LABEL_KEY, POSITIVE)),
                new Document("I need a schema for a movie ticket booking app with shows, screens, and seats.", Map.of(LABEL_KEY, POSITIVE)),
                new Document("Design a warehouse and inventory management database with stock levels and suppliers.", Map.of(LABEL_KEY, POSITIVE)),
                new Document("Create a CRM schema with leads, contacts, deals, and activities.", Map.of(LABEL_KEY, POSITIVE)),
                new Document("Design an HRMS schema with employees, payroll, attendance, and departments.", Map.of(LABEL_KEY, POSITIVE)),
                new Document("Create an ERP database schema covering purchasing, inventory, and finance modules.", Map.of(LABEL_KEY, POSITIVE)),
                new Document("Design a ride sharing app database with drivers, riders, trips, and payments.", Map.of(LABEL_KEY, POSITIVE)),
                new Document("Create a gym management schema with members, trainers, and subscription plans.", Map.of(LABEL_KEY, POSITIVE)),

                // ---------- Concept grounding, as full sentences ----------
                new Document("A primary key is a column or set of columns that uniquely identifies each row in a table.", Map.of(LABEL_KEY, POSITIVE)),
                new Document("A foreign key is a column that references the primary key of another table to enforce a relationship.", Map.of(LABEL_KEY, POSITIVE)),
                new Document("A composite key uses two or more columns together to uniquely identify a row.", Map.of(LABEL_KEY, POSITIVE)),
                new Document("Normalization is the process of organizing tables to reduce redundancy and avoid update anomalies.", Map.of(LABEL_KEY, POSITIVE)),
                new Document("A junction table is used to resolve a many-to-many relationship between two entities.", Map.of(LABEL_KEY, POSITIVE)),
                new Document("An index improves query performance on frequently searched columns.", Map.of(LABEL_KEY, POSITIVE)),
                new Document("A check constraint restricts the values that can be stored in a column.", Map.of(LABEL_KEY, POSITIVE)),
                new Document("Use snake_case naming, plural table names, and an id column as the primary key for every table.", Map.of(LABEL_KEY, POSITIVE)),
                new Document("Foreign key columns should follow the table_id naming convention.", Map.of(LABEL_KEY, POSITIVE))
        );

        List<Document> negative = List.of(
                // ---------- Realistic off-topic phrasing, NOT bare category words ----------
                new Document("Who is Virat Kohli?", Map.of(LABEL_KEY, NEGATIVE)),
                new Document("Who won the last cricket World Cup?", Map.of(LABEL_KEY, NEGATIVE)),
                new Document("Write a Java program to reverse a linked list.", Map.of(LABEL_KEY, NEGATIVE)),
                new Document("Explain how Spring Boot dependency injection works.", Map.of(LABEL_KEY, NEGATIVE)),
                new Document("Tell me a joke.", Map.of(LABEL_KEY, NEGATIVE)),
                new Document("What's the weather today?", Map.of(LABEL_KEY, NEGATIVE)),
                new Document("Who is the current Prime Minister of India?", Map.of(LABEL_KEY, NEGATIVE)),
                new Document("What is the capital of France?", Map.of(LABEL_KEY, NEGATIVE)),
                new Document("Solve this algebra equation for me.", Map.of(LABEL_KEY, NEGATIVE)),
                new Document("Recommend a good movie to watch this weekend.", Map.of(LABEL_KEY, NEGATIVE)),
                new Document("Summarize the history of World War 2.", Map.of(LABEL_KEY, NEGATIVE)),
                new Document("What do you think about the upcoming elections?", Map.of(LABEL_KEY, NEGATIVE)),
                new Document("Write a poem about the ocean.", Map.of(LABEL_KEY, NEGATIVE)),
                new Document("How do I lose weight fast?", Map.of(LABEL_KEY, NEGATIVE)),
                new Document("What's the best smartphone to buy right now?", Map.of(LABEL_KEY, NEGATIVE)),
                new Document("Translate this sentence into French.", Map.of(LABEL_KEY, NEGATIVE)),
                new Document("Give me a recipe for chocolate cake.", Map.of(LABEL_KEY, NEGATIVE)),
                new Document("Plan a 5-day travel itinerary for Goa.", Map.of(LABEL_KEY, NEGATIVE)),
                new Document("What stocks should I invest in?", Map.of(LABEL_KEY, NEGATIVE)),
                new Document("Debug this Python function for me.", Map.of(LABEL_KEY, NEGATIVE))
        );

        vectorStore.add(positive);
        vectorStore.add(negative);
    }

    /**
     * Instead of a single distance threshold on one nearest neighbor,
     * pull the top-K nearest documents and vote on their labels.
     * This is much more robust because it doesn't rely on one lucky/unlucky
     * embedding match, and it directly separates "close to schema talk"
     * from "close to off-topic talk" rather than just "close to something".
     */
    public boolean isRelevantPrompt(String prompt) {
        int k = 5;

        List<Document> docs = vectorStore.similaritySearch(
                SearchRequest.builder()
                        .query(prompt)
                        .topK(k)
                        .build()
        );

        if (docs.isEmpty()) {
            return false;
        }

        int positiveVotes = 0;
        int negativeVotes = 0;
        double bestPositiveDistance = Double.MAX_VALUE;

        for (Document doc : docs) {
            String label = String.valueOf(doc.getMetadata().get(LABEL_KEY));
            double distance = ((Number) doc.getMetadata().get("distance")).doubleValue();

            if (POSITIVE.equals(label)) {
                positiveVotes++;
                bestPositiveDistance = Math.min(bestPositiveDistance, distance);
            } else if (NEGATIVE.equals(label)) {
                negativeVotes++;
            }
        }

        log.info("prompt='{}' positiveVotes={} negativeVotes={} bestPositiveDistance={}",
                prompt, positiveVotes, negativeVotes, bestPositiveDistance);

        // Accept only if:
        // 1) positive examples actually outnumber (or tie) negative examples among neighbors, AND
        // 2) the closest positive match is still reasonably close in absolute terms
        return positiveVotes >= negativeVotes && bestPositiveDistance <= 0.60;
    }
}