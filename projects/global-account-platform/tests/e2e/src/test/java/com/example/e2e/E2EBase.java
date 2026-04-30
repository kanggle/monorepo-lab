package com.example.e2e;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.restassured.RestAssured;
import io.restassured.config.RestAssuredConfig;
import io.restassured.http.ContentType;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.extension.ExtendWith;

/**
 * Base class for every E2E scenario. Brings up the shared compose fixture and
 * wires RestAssured to the admin-service base URL by default (individual tests
 * switch baseURI when they call account-/security-service endpoints directly).
 */
@ExtendWith(ComposeFixture.class)
public abstract class E2EBase {

    protected static final ObjectMapper MAPPER = new ObjectMapper();

    // Dev operator seeded by apps/admin-service migration-dev V0014.
    public static final String DEV_OPERATOR_ID = "00000000-0000-7000-8000-00000000dev1";
    public static final String DEV_OPERATOR_PASSWORD = "devpassword123!";

    @BeforeAll
    static void configureRestAssured() {
        RestAssured.baseURI = ComposeFixture.ADMIN_BASE_URL;
        RestAssured.config = RestAssuredConfig.config();
        RestAssured.urlEncodingEnabled = true;
        RestAssured.enableLoggingOfRequestAndResponseIfValidationFails();
        RestAssured.defaultParser = io.restassured.parsing.Parser.JSON;
    }

    protected static io.restassured.specification.RequestSpecification admin() {
        return RestAssured.given().baseUri(ComposeFixture.ADMIN_BASE_URL).contentType(ContentType.JSON);
    }

    protected static io.restassured.specification.RequestSpecification account() {
        return RestAssured.given().baseUri(ComposeFixture.ACCOUNT_BASE_URL).contentType(ContentType.JSON);
    }

    protected static io.restassured.specification.RequestSpecification security() {
        return RestAssured.given().baseUri(ComposeFixture.SECURITY_BASE_URL).contentType(ContentType.JSON);
    }
}
