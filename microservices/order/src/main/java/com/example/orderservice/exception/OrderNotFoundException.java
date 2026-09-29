package com.example.orderservice.exception;

/** Domain lookup failure mapped by Order's REST advice to HTTP 404. */
public class OrderNotFoundException extends RuntimeException {

    /** @param id requested order identifier that was absent */
    public OrderNotFoundException(Long id) {
        super("Order not found with id: " + id);
    }
}
