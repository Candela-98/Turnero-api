package com.turnero.api.mapper;

import com.turnero.api.dto.CustomerRequestDto;
import com.turnero.api.dto.CustomerResponseDto;
import com.turnero.api.model.Customer;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import java.util.List;

@Mapper(componentModel = "spring")
public interface CustomerMapper {

    @Mapping(source = "name", target = "name")
    @Mapping(source = "phone", target = "phoneNumber")
    Customer toEntity(CustomerRequestDto dtoCustomer);

    @Mapping(source = "id", target = "id")
    @Mapping(source = "name", target = "name")
    @Mapping(source = "phoneNumber", target = "phone")
    CustomerResponseDto toResponseDto(Customer customer);

    List<CustomerResponseDto> toResponseDtoList(List<Customer> customers);
}
