package com.turnero.api.service;

import com.turnero.api.dto.CustomerUpdateRequestDto;
import com.turnero.api.model.Customer;
import com.turnero.api.model.enums.CustomerStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Sort;

import java.util.List;

public interface CustomerService {

    Customer saveCustomer(Customer customer);

    Customer findCustomer(Long id);

    List<Customer> findAllCustomer();

    Page<Customer> listCustomers(String q, CustomerStatus status, int page, int size, Sort sort);

    Page<Customer> listCustomers(String q, CustomerStatus status, Boolean blocked, int page, int size, Sort sort);

    Customer updateCustomer(CustomerUpdateRequestDto customer, Long id);

    void deleteCustomer(Long id);

    Customer findOrCreateCustomerForBusiness(Long businessId, String name, String email, String phoneNumber);
}
