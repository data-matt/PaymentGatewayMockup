package com.cockroachlabs.field.paymentsdemo.PaymentGatewayMockup;

import jakarta.persistence.EntityManager;

public interface CustomCardTransactionRepository {
    public CardTransaction insertWithEncryption(CardTransaction cardTransaction);
}
