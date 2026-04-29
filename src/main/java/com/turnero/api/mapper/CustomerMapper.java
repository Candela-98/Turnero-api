package com.turnero.api.mapper;

import com.turnero.api.dto.CustomerRequestDto;
import com.turnero.api.dto.CustomerResponseDto;
import com.turnero.api.model.Customer;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import java.util.List;

@Mapper(componentModel = "spring")
public interface CustomerMapper {

    @Mapping(source = "nameCustomer", target = "name")
    @Mapping(source = "phoneCustomer", target = "phoneNumber")
    Customer toEntity(CustomerRequestDto dtoCustomer);

    @Mapping(source = "id", target = "customerId")
    @Mapping(source = "name", target = "nameCustomer")
    @Mapping(source = "phoneNumber", target = "phoneCustomer")
    CustomerResponseDto toResponseDto(Customer customer);

    List<CustomerResponseDto> toResponseDtoList(List<Customer> customers);
}
