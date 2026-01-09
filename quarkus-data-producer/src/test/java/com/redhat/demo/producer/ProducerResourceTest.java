package com.redhat.demo.producer;

import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;
import static org.hamcrest.CoreMatchers.is;

@QuarkusTest
public class ProducerResourceTest {

    @Test
    public void testHealthEndpoint() {
        given()
            .when().get("/api/producer/health")
            .then()
                .statusCode(200)
                .body("status", is("UP"));
    }
}

