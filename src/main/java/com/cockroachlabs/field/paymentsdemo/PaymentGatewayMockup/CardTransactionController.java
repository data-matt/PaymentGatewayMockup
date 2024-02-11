package com.cockroachlabs.field.paymentsdemo.PaymentGatewayMockup;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.sql.Connection;

import java.sql.SQLException;
import java.sql.Statement;
import java.time.Duration;
import java.time.Instant;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import jakarta.persistence.EntityManager;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.hateoas.EntityModel;
import org.springframework.http.HttpStatus;
import org.springframework.orm.jpa.EntityManagerFactoryInfo;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import javax.sql.DataSource;

import static org.springframework.hateoas.server.mvc.WebMvcLinkBuilder.linkTo;
import static org.springframework.hateoas.server.mvc.WebMvcLinkBuilder.methodOn;

@RestController
public class CardTransactionController {

    private final CustomCardTransactionRepository customCardTransactionRepository;
    private final CardTransactionRepository cardTransactionRepository;
    private final WorkflowLogRepository workflowLogRepository;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient = HttpClient.newHttpClient();
    private final URI paymentsProcessorUri;

    CardTransactionController(CardTransactionRepository cardTransactionRepository,
                              EntityManager entityManager,
                              WorkflowLogRepository workflowLogRepository,
                              @Value("${payment_demo.processor_url}") String paymentsProcessorUriString) {
        //can't figure out how to get this custom repo to pass in with dependency injection
        this.customCardTransactionRepository = new CustomCardTransactionRepositoryImpl(entityManager);
        this.cardTransactionRepository = cardTransactionRepository;
        this.workflowLogRepository = workflowLogRepository;

        objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());

        createSchemaIfMissing(entityManager);

        if (paymentsProcessorUriString == null || paymentsProcessorUriString.length() == 0) {
            throw new IllegalArgumentException("The payments processor URI is a required setting.");
        }

        try {
            paymentsProcessorUri = URI.create(paymentsProcessorUriString);
        } catch (Exception ex) {
            throw new IllegalArgumentException("The payments processor URI must be a valid URI value.");
        }

    }

    private void createSchemaIfMissing(EntityManager entityManager) {
        EntityManagerFactoryInfo info = (EntityManagerFactoryInfo) entityManager.getEntityManagerFactory();
        DataSource dataSource = info.getDataSource();
        Connection connection;
        try {
            connection = dataSource.getConnection();
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
        Statement statement;
        try {
            statement = connection.createStatement();
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
        String[] statements = new String[] {
                "CREATE DATABASE IF NOT EXISTS payments;",
                "USE payments;",
                "CREATE TABLE IF NOT EXISTS payments.merchant\n" +
                        "(\n" +
                        "  merchant_code varchar(10) NOT NULL,\n" +
                        "  merchant_name varchar(100) NOT NULL,\n" +
                        "  PRIMARY KEY ( merchant_code ),\n" +
                        "  UNIQUE ( merchant_name )\n" +
                        ");",
                "INSERT INTO payments.merchant ( merchant_code, merchant_name )\n" +
                        "VALUES ( 'WELSHGOODS', 'Welsh Goods Emporium' )\n" +
                        "ON CONFLICT DO NOTHING;",
                "CREATE TABLE IF NOT EXISTS payments.payment_gateway\n" +
                        "(\n" +
                        "  gateway_code char(3) NOT NULL,\n" +
                        "  gateway_name varchar(25) NOT NULL,\n" +
                        "  PRIMARY KEY ( gateway_code ),\n" +
                        "  UNIQUE ( gateway_name )\n" +
                        ");",
                "INSERT INTO payments.payment_gateway ( gateway_code, gateway_name )\n" +
                        "VALUES ( 'PPL', 'PayPal' )\n" +
                        "ON CONFLICT DO NOTHING;",
                "CREATE TABLE IF NOT EXISTS payments.card_transaction\n" +
                        "(\n" +
                        "  transaction_id uuid NOT NULL DEFAULT gen_random_uuid(),\n" +
                        "  transaction_date timestamp NOT NULL DEFAULT now(),\n" +
                        "  amount decimal NOT NULL,\n" +
                        "  currency varchar(3) NOT NULL DEFAULT 'USD',\n" +
                        "  card_number_encrypted_base64 varchar NOT NULL,\n" +
                        "  card_expiration_month char(2) NOT NULL,\n" +
                        "  card_expiration_year char(4) NOT NULL,\n" +
                        "  card_holder_name varchar(100) NULL,\n" +
                        "  merchant_code varchar(10) NOT NULL DEFAULT 'WELSHGOODS',\n" +
                        "  merchant_reference_code varchar(50) NULL,\n" +
                        "  status char(3) NOT NULL DEFAULT 'PND',\n" +
                        "  authorization_code varchar(6) NULL,  \n" +
                        "  gateway_code char(3) NOT NULL DEFAULT 'PPL',\n" +
                        "  response_http_code char(3) NULL,\n" +
                        "  response_message varchar(300) NULL,\n" +
                        "\n" +
                        "  PRIMARY KEY ( transaction_id ),\n" +
                        "  CONSTRAINT check_amount CHECK (amount >= 0 AND amount < 1000000),\n" +
                        "  CONSTRAINT check_status CHECK (status IN ( 'PND', 'APP', 'DEC', 'ERR' )),\n" +
                        "  CONSTRAINT fk_merchant FOREIGN KEY ( merchant_code ) REFERENCES merchant ( merchant_code ),\n" +
                        "  CONSTRAINT fk_payment_gateway FOREIGN KEY ( gateway_code ) REFERENCES payment_gateway ( gateway_code )\n" +
                        "\n" +
                        ");",
                "CREATE TABLE IF NOT EXISTS payments.workflow_log\n" +
                        "(\n" +
                        "  log_id uuid NOT NULL DEFAULT gen_random_uuid(),\n" +
                        "  transaction_id uuid NOT NULL,\n" +
                        "  log_date timestamp NOT NULL DEFAULT now(),\n" +
                        "  event_description varchar(50),\n" +
                        "  PRIMARY KEY ( log_id )\n" +
                        ");"
        };
        for(String s : statements) {
            try {
                statement.execute(s);
            } catch (SQLException e) {
                throw new RuntimeException(e);
            }
        }

    }

    @PostMapping("/transactions")
    EntityModel<CardTransaction> processTransaction(@RequestBody CardTransaction newCardTransaction) {

        //TODO: do the setup so this service can run as https (i.e., TLS)

        if (newCardTransaction == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST);
        }

        if (newCardTransaction.getCardNumber() == null || newCardTransaction.getCardNumber().length() == 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST);
        }

        //TODO: we could add lots of other validation here or we could add more validation to the POJO/bean

        //TODO: wrap db calls in retry logic to handle 40001 errors

        //apply some defaults to the incoming record
        if (newCardTransaction.getTransactionDate() == null) {
            newCardTransaction.setTransactionDate(Instant.now());
        }
        if (newCardTransaction.getCurrency() == null) {
            newCardTransaction.setCurrency("USD");
        }
        if (newCardTransaction.getMerchantCode() == null) {
            newCardTransaction.setMerchantCode("WELSHGOODS");
        }
        newCardTransaction.setStatus("PND");
        newCardTransaction.setGatewayCode("PPL");

        //save to DB
        CardTransaction saved = customCardTransactionRepository.insertWithEncryption(newCardTransaction);
        //CardTransaction saved = (CardTransaction)repository.save(newCardTransaction);
        //save to logging table
        workflowLogRepository.save(new WorkflowLog(saved.getTransactionId(), "Card Transaction received"));

        //serialize the cardTransaction object to a string so we can pass it around
        String cardTransactionJson;
        try {
            cardTransactionJson = objectMapper.writeValueAsString(saved);
        } catch (JsonProcessingException e) {
            throw new RuntimeException(e);
        }

        //call the payment processor
        HttpResponse response = callPaymentsProcessor(cardTransactionJson);

        //parse the results of the call
        String httpStatusCode = String.valueOf(response.statusCode());
        saved.setResponseHttpCode(httpStatusCode);
        if (!httpStatusCode.equals("200")) {
            //trim the responseBody if it's too big to fit in the DB
            String responseBody = response.body().toString();
            if (responseBody.length() > 300) {
                responseBody = responseBody.substring(0, 300);
            }
            saved.setResponseMessage(responseBody);
        } else {

            CardTransaction processed;
            try {
                processed = objectMapper.readValue(response.body().toString(), CardTransaction.class);
            } catch (JsonProcessingException e) {
                throw new RuntimeException(e);
            }

            saved.setStatus(processed.getStatus());
            String authCode = processed.getAuthorizationCode();
            if (authCode != null) {
                saved.setAuthorizationCode(authCode);
            }
        }

        //save the transaction to the database again with the response fields in place
        cardTransactionRepository.save(saved);
        //save to logging table
        workflowLogRepository.save(new WorkflowLog(saved.getTransactionId(), "Card Transaction updated with response"));

        //clear the card number fields so we don't send these values back to the calling client
        saved.setCardNumber(null);
        saved.setCardNumberEncryptedBase64(null);

        System.out.println("Processing POST request for transactions - transaction ID: " + saved.getTransactionId() + " at " + Instant.now().toString());

        //return the object with HATEOAS links
        return EntityModel.of(saved, linkTo(methodOn(CardTransactionController.class).processTransaction(saved)).withSelfRel());
    }

    private HttpResponse callPaymentsProcessor(String cardTransactionJson) {
        HttpRequest postRequest = HttpRequest.newBuilder()
                .uri(paymentsProcessorUri)
                .timeout(Duration.ofSeconds(5))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(cardTransactionJson))
                .build();
        HttpResponse<String> httpResponse;
        try {
            httpResponse = httpClient.send(postRequest, HttpResponse.BodyHandlers.ofString());
        } catch (IOException e) {
            throw new RuntimeException(e);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }

        return httpResponse;

    }

}
