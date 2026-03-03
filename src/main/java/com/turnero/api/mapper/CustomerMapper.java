package com.turnero.api.mapper;

import com.turnero.api.dto.CustomerRequestDto;
import com.turnero.api.model.Customer;
import org.springframework.stereotype.Component;

@Component
public class CustomerMapper {

    public Customer toEntity(CustomerRequestDto dtoCustomer){
        Customer customer = new Customer();

        customer.setId(dtoCustomer.getCustomerId());
        customer.setName(dtoCustomer.getNameCustomer());
        customer.setEmail(dtoCustomer.getEmail());
        customer.setPhoneNumber(dtoCustomer.getPhoneCustomer());
        customer.setCreatedIn(dtoCustomer.getCreationDate());

        return customer;
    }
}
