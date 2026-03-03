package com.turnero.api.service;

import com.turnero.api.model.Customer;

import java.util.List;

public interface CustomerService {

    Customer saveCustomer(Customer customer);

    Customer findCustomer(Long id);

    List<Customer> findAllCustomer();

    void updateCustomer(Customer customer, Long id);

    void deleteCustomer(Long id);
}
