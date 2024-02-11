package com.cockroachlabs.field.paymentsdemo.PaymentGatewayMockup;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.UUID;

public interface CardTransactionRepository extends JpaRepository<CardTransaction, UUID> {

};
