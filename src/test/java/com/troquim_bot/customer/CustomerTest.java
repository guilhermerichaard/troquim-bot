package com.troquim_bot.customer;

import com.troquim_bot.common.valueobject.CustomerName;
import com.troquim_bot.common.valueobject.PhoneNumber;
import com.troquim_bot.support.TestTenants;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Invariantes de tenancy do aggregate Customer (itens 1 e 6 — nível de domínio).
 */
class CustomerTest {

    @Test
    void exigeBusinessId() {
        assertThrows(IllegalArgumentException.class, () ->
                new Customer(CustomerId.generate(), null,
                        CustomerName.of("Ana Paula"), new PhoneNumber("5511999990001"), null));
    }

    @Test
    void guardaOBusinessIdInformado() {
        Customer customer = new Customer(CustomerId.generate(), TestTenants.PILOT,
                CustomerName.of("Ana Paula"), new PhoneNumber("5511999990001"), null);

        assertEquals(TestTenants.PILOT, customer.getBusinessId());
    }
}
