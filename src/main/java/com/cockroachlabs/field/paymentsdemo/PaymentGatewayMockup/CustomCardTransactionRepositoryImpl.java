package com.cockroachlabs.field.paymentsdemo.PaymentGatewayMockup;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.Query;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public class CustomCardTransactionRepositoryImpl implements CustomCardTransactionRepository {

    private EntityManager entityManager;
    //TODO: pull this from a secrets managers like Hashicorp Vault or similar
    private final String encryptionKey = "my_key";

    CustomCardTransactionRepositoryImpl(EntityManager entityManager) {
        this.entityManager = entityManager;
    }


    @Override
    public CardTransaction insertWithEncryption(CardTransaction cardTransaction) {

        if (cardTransaction == null) {
            return null;
        }

        Query q = entityManager.createNativeQuery("INSERT INTO payments.card_transaction\n" +
                "(\n" +
                "  amount,\n" +
                "  currency,\n" +
                "  card_number_encrypted_base64,\n" +
                "  card_expiration_month,\n" +
                "  card_expiration_year,\n" +
                "  card_holder_name,\n" +
                "  merchant_code,\n" +
                "  merchant_reference_code\n" +
                ")\n" +
                "VALUES\n" +
                "(\n" +
                "  ?,\n" +
                "  ?,\n" +
                "  encode(encrypt(convert_to(?, 'UTF8'), convert_to(?, 'UTF8'),'aes'),'base64'),\n" +
                "  ?,\n" +
                "  ?,\n" +
                "  ?,\n" +
                "  ?,\n" +
                "  ?\n" +
                ")\n" +
                "RETURNING *;\n",
                CardTransaction.class);

        //set parameters
        q.setParameter(1, cardTransaction.getAmount());
        q.setParameter(2, cardTransaction.getCurrency());
        q.setParameter(3, cardTransaction.getCardNumber());
        q.setParameter(4, encryptionKey);
        q.setParameter(5, cardTransaction.getCardExpirationMonth());
        q.setParameter(6, cardTransaction.getCardExpirationYear());
        q.setParameter(7, cardTransaction.getCardHolderName());
        q.setParameter(8, cardTransaction.getMerchantCode());
        q.setParameter(9, cardTransaction.getMerchantReferenceCode());

        CardTransaction result = (CardTransaction) q.getSingleResult();
        if (result != null) {
            UUID transactionId = result.getTransactionId();
            String cardNumberEncryptedBase64 = result.getCardNumberEncryptedBase64();
            cardTransaction.setTransactionId(transactionId);
            cardTransaction.setCardNumberEncryptedBase64(cardNumberEncryptedBase64);
        }

        return cardTransaction;
    }

}
