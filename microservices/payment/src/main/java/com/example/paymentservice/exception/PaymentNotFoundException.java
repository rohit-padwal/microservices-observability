package com.example.paymentservice.exception;

/** Domain lookup failure converted by Payment's REST advice into HTTP 404. */
public class PaymentNotFoundException extends RuntimeException {

    /** @param id requested payment identifier that was absent */
    public PaymentNotFoundException(Long id) {
        super("Payment not found with id: " + id);
    }
}
