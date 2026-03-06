package com.turnero.api.service;

import com.turnero.api.model.Customer;
import com.turnero.api.repository.CustomerRepository;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class CustomerServiceImpl implements CustomerService {

    private final CustomerRepository customerRepository;

    public CustomerServiceImpl(CustomerRepository customerRepository){
        this.customerRepository = customerRepository;
    }

    @Override
    public Customer saveCustomer(Customer customer) {
        return customerRepository.save(customer);
    }

    @Override
    public Customer findCustomer(Long id) {
        return customerRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Customer not found"));
    }

    @Override
    public void updateCustomer(Customer customer, Long id) {
        Customer currentCustomer = findCustomer(id);

        currentCustomer.setName(customer.getName());
        currentCustomer.setEmail(customer.getEmail());
        currentCustomer.setPhoneNumber(customer.getPhoneNumber());
        currentCustomer.setCreatedIn(customer.getCreatedIn());

        customerRepository.save(currentCustomer);
        System.out.println("Customer with Id " + id + " successfully updated.");
    }

    public List<Customer> findAllCustomer() {
        return customerRepository.findAll();
    }

    @Override
    public void deleteCustomer(Long id) {

        if(customerRepository.existsById(id)) {
            customerRepository.deleteById(id);
            System.out.println("Customer with Id " + id + " successfully removed.");
        } else {
            throw new RuntimeException("Customer not found");
        }
    }
}
